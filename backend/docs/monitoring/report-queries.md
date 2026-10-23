# 사용자 지표 리포트 쿼리

대시보드의 일별 지표(설계 문서 4.6절)를 보조하는 수동 조회용 SQL이다. 코호트별 리텐션을 한 표로 보거나, 검수 소요 시간의 주별 추세와 AI 리뷰의 코호트별 사용률·재사용률을 볼 때 prod DB에서 직접 실행한다. 정기 실행이나 저장은 하지 않는다.

- 모두 조회만 한다. 실수로 데이터를 바꾸지 않도록 `BEGIN TRANSACTION READ ONLY;`로 시작하고 `ROLLBACK;`으로 끝낸다.
- 주는 월요일 00:00부터 일요일 24:00까지(KST)다. 앱 집계와 같다.
- 게시물은 삭제됐거나 거절된 것도 업로드로 센다. 앱 집계와 같다.

## 1. 주별 코호트 리텐션

코호트 주에 게시물을 올린 회원 중 k주 뒤(W+1~W+4)에도 올린 회원의 비율(%)이다. 끝나지 않은 주가 필요한 칸은 `NULL`이다. 월요일 집계가 끝난 뒤에는 대시보드의 `RetentionW{k}Rate`가 이 표에서 칸마다 가장 최근에 끝난 값과 같다. 코호트가 비어 있으면 대시보드에는 값이 없고 표에는 그 주 행이 없다.

`excluded` 배열에는 `application-prod.yml`의 `chalkak.analytics.excluded-user-ids`와 같은 회원 ID를 넣는다. 비워 두면 모든 회원을 센다.

```sql
BEGIN TRANSACTION READ ONLY;

WITH params AS (
    SELECT
        ARRAY[]::uuid[] AS excluded, -- 예: ARRAY['0198...','0198...']::uuid[]
        date_trunc('week', now() AT TIME ZONE 'Asia/Seoul')::date AS current_week
),
weekly_authors AS (
    SELECT DISTINCT
        p.user_id,
        date_trunc('week', p.created_at AT TIME ZONE 'Asia/Seoul')::date AS week_start
    FROM posts p, params
    WHERE p.user_id <> ALL (params.excluded)
)
SELECT
    c.week_start AS cohort_week,
    COUNT(*) AS cohort_size,
    CASE WHEN c.week_start + 7 < params.current_week
        THEN ROUND(100.0 * COUNT(w1.user_id) / COUNT(*), 1) END AS w1_rate,
    CASE WHEN c.week_start + 14 < params.current_week
        THEN ROUND(100.0 * COUNT(w2.user_id) / COUNT(*), 1) END AS w2_rate,
    CASE WHEN c.week_start + 21 < params.current_week
        THEN ROUND(100.0 * COUNT(w3.user_id) / COUNT(*), 1) END AS w3_rate,
    CASE WHEN c.week_start + 28 < params.current_week
        THEN ROUND(100.0 * COUNT(w4.user_id) / COUNT(*), 1) END AS w4_rate
FROM weekly_authors c
CROSS JOIN params
LEFT JOIN weekly_authors w1 ON w1.user_id = c.user_id AND w1.week_start = c.week_start + 7
LEFT JOIN weekly_authors w2 ON w2.user_id = c.user_id AND w2.week_start = c.week_start + 14
LEFT JOIN weekly_authors w3 ON w3.user_id = c.user_id AND w3.week_start = c.week_start + 21
LEFT JOIN weekly_authors w4 ON w4.user_id = c.user_id AND w4.week_start = c.week_start + 28
WHERE c.week_start < params.current_week
GROUP BY c.week_start, params.current_week
ORDER BY cohort_week DESC
LIMIT 12;

ROLLBACK;
```

- `weekly_authors`는 회원·주마다 한 행이라 조인해도 회원이 중복되지 않는다.
- 진행 중인 이번 주는 코호트로 보여 주지 않는다.

## 2. 주별 검수 소요 시간

검수를 결정한 주(KST)마다 결정 건수와 소요 시간의 평균·p95(초)다. AI 검수 도입을 판단할 때 본다. 소요 시간은 게시물 생성(`created_at`)부터 관리자 결정(`moderated_at`)까지라 이미지 처리 시간도 들어간다. 접근 로그의 `waitSeconds`와 같은 기준이다.

```sql
BEGIN TRANSACTION READ ONLY;

SELECT
    date_trunc('week', p.moderated_at AT TIME ZONE 'Asia/Seoul')::date AS week_start,
    COUNT(*) AS moderated,
    COUNT(*) FILTER (WHERE p.moderation_status = 'REJECTED') AS rejected,
    ROUND(AVG(EXTRACT(EPOCH FROM p.moderated_at - p.created_at))) AS avg_wait_sec,
    ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP (
        ORDER BY EXTRACT(EPOCH FROM p.moderated_at - p.created_at))::numeric) AS p95_wait_sec
FROM posts p
WHERE p.moderated_at IS NOT NULL
GROUP BY week_start
ORDER BY week_start DESC
LIMIT 12;

ROLLBACK;
```

- 관리자가 승인·반려한 게시물만 센다. 이미지 처리 실패로 거절된 게시물은 `moderated_at`이 없어 빠진다.
- 검수는 게시물마다 한 번만 결정되므로(`PENDING`에서만 승인·반려) 다시 검수해 시간이 덮이는 경우는 없다.
- 내부 계정의 게시물도 같은 검수를 거치므로 제외하지 않는다.
- 대시보드의 Logs Insights 위젯은 로그 보존 기간(60일) 안에서 일별로 보고, 이 쿼리는 DB 전체 기간을 주별로 본다.

## 3. 기준일 이전·이후 가입자의 AI 리뷰 사용 비율

기준일(KST)을 정해 그 이전에 가입한 회원과 이후에 가입한 회원 각각의 수, AI 리뷰를 한 번이라도 요청한 회원 수와 비율(%)을 비교한다. `cohort = 'after'` 행의 `signups`가 기준일 이후 가입자 수다. AI 리뷰 도입이나 개선 전후로 신규 가입자의 사용 비율이 달라졌는지 볼 때 쓴다.

`base_date`에 기준일을 넣는다. 기준일 00:00(KST) 이후에 가입한 회원이 `after`다. `usage_from`·`usage_to`는 AI 사용(최초 요청)을 셀 기간이며, 비워 두면 기준일부터 지금까지다. `excluded`는 1번과 같다.

```sql
BEGIN TRANSACTION READ ONLY;

WITH params AS (
    SELECT
        ARRAY[]::uuid[] AS excluded,     -- 예: ARRAY['0198...','0198...']::uuid[]
        DATE '2026-11-01' AS base_date,  -- 기준일(KST). 이날 0시부터 가입한 회원이 "이후"
        NULL::date AS usage_from,        -- AI 사용을 볼 시작일(KST). NULL이면 기준일
        NULL::date AS usage_to           -- AI 사용을 볼 마지막 날(KST, 포함). NULL이면 지금까지
),
bounds AS (
    SELECT
        excluded,
        base_date::timestamp AT TIME ZONE 'Asia/Seoul' AS base_at,
        COALESCE(usage_from, base_date)::timestamp AT TIME ZONE 'Asia/Seoul' AS use_from,
        COALESCE((usage_to + 1)::timestamp AT TIME ZONE 'Asia/Seoul', now()) AS use_to
    FROM params
),
members AS (
    SELECT u.id,
           CASE WHEN u.created_at < b.base_at THEN 'before' ELSE 'after' END AS cohort
    FROM users u, bounds b
    WHERE u.id <> ALL (b.excluded)
      AND u.created_at < b.use_to
),
ai_users AS (
    SELECT DISTINCT r.user_id
    FROM ai_reviews r, bounds b
    WHERE r.requested_at >= b.use_from
      AND r.requested_at < b.use_to
),
posters AS (
    SELECT DISTINCT p.user_id
    FROM posts p, bounds b
    WHERE p.created_at >= b.use_from
      AND p.created_at < b.use_to
)
SELECT
    m.cohort,
    COUNT(*) AS signups,                                  -- after 행이 기준일 이후 가입자 수
    COUNT(p.user_id) AS posters,                          -- 같은 기간 게시물을 올린 회원
    COUNT(a.user_id) AS ai_users,
    ROUND(100.0 * COUNT(a.user_id) / NULLIF(COUNT(*), 0), 1) AS ai_rate,
    ROUND(100.0 * COUNT(a.user_id) FILTER (WHERE p.user_id IS NOT NULL)
        / NULLIF(COUNT(p.user_id), 0), 1) AS ai_rate_among_posters
FROM members m
LEFT JOIN ai_users a ON a.user_id = m.id
LEFT JOIN posters p ON p.user_id = m.id
GROUP BY m.cohort
ORDER BY m.cohort DESC;

ROLLBACK;
```

- 가입 시각은 `users.created_at`이다. 소셜 로그인으로 처음 가입할 때 회원 행이 만들어지므로 가입 시각과 같다.
- 사용 = 기간 안에 `ai_reviews.requested_at`(최초 요청)이 있는 게시물이 하나라도 있음. 재요청은 같은 행이라 한 번 더 세지 않고, 한 회원은 여러 번 요청해도 한 명이다.
- 분모는 탈퇴·정지 회원을 포함한 가입자 전체다. 시간이 지나도 코호트 크기가 줄지 않는다. 활동 가능한 회원만 보려면 `members`에 `AND u.deleted_at IS NULL AND u.status = 'ACTIVE'`를 더한다.
- 팀 계정은 `excluded`로 뺀다. 가입자 수와 사용자 수 모두에서 빠진다.
- 관찰 기간은 기본으로 기준일부터 지금까지이고 상한이 없다. 그래서 먼저 가입한 회원일수록 사용할 시간이 길어 비율이 높게 나온다. 공정하게 비교하려면 `usage_to`로 양쪽에 같은 기간을 주거나, 가입일 구간을 좁힌다. `before` 코호트는 기준일 이후의 사용만 센다.
- `ai_rate_among_posters`는 같은 기간에 게시물을 올린 회원 중 AI 리뷰도 요청한 회원의 비율이다. 분자는 게시자이면서 AI 사용자인 회원만 센다(기간 직전에 올린 게시물에 기간 안에 요청한 회원은 분자에서 빠진다). 이슈 요구 범위 밖의 보조 열이다.
- 가입자가 0명인 코호트(before 또는 after)는 행이 나오지 않는다.

## 4. AI 리뷰 일별 재사용률

날짜 d(KST)에 AI 리뷰를 요청한 회원 중 d+1에도 요청한 회원의 비율(%)이다. 요청이 있었던 날 기준 최근 30행을 보여 주고, d+1이 끝나지 않은 날(어제)은 `reused_next_day`와 `reuse_rate`가 `NULL`이다. 오늘은 아직 끝나지 않아 코호트로 보여 주지 않는다.

```sql
BEGIN TRANSACTION READ ONLY;

WITH params AS (
    SELECT
        ARRAY[]::uuid[] AS excluded,
        (now() AT TIME ZONE 'Asia/Seoul')::date AS today
),
daily_users AS (
    SELECT DISTINCT
        r.user_id,
        (r.requested_at AT TIME ZONE 'Asia/Seoul')::date AS d
    FROM ai_reviews r, params
    WHERE r.user_id <> ALL (params.excluded)
)
SELECT
    c.d AS request_date,
    COUNT(*) AS users,
    CASE WHEN c.d + 1 < params.today THEN COUNT(n.user_id) END AS reused_next_day,
    CASE WHEN c.d + 1 < params.today
        THEN ROUND(100.0 * COUNT(n.user_id) / COUNT(*), 1) END AS reuse_rate
FROM daily_users c
CROSS JOIN params
LEFT JOIN daily_users n ON n.user_id = c.user_id AND n.d = c.d + 1
WHERE c.d < params.today
GROUP BY c.d, params.today
ORDER BY request_date DESC
LIMIT 30;

ROLLBACK;
```

- 날짜는 최초 요청 시각(`requested_at`)이다. 행은 게시물당 하나이고 재요청해도 `requested_at`이 바뀌지 않으므로, 실패한 건을 다시 요청만 한 날은 사용으로 세지 않는다.
- `daily_users`가 회원·날짜마다 한 행이라 조인해도 회원이 중복되지 않는다.
- 주제가 하루에 하나라 다음 날 재사용은 "다음 날 올린 게시물에도 AI 리뷰를 요청했다"에 가깝다.
- 요청이 없는 날은 행이 없다. 탈퇴 회원의 요청도 센다.

## 5. AI 리뷰 주별 재사용률

주 W(월요일 시작, KST)에 AI 리뷰를 요청한 회원 중 W+1주에도 요청한 회원의 비율(%)이다. 4번의 주 단위 변형이며 요청이 있었던 주 기준 최근 12행을 보여 준다. W+1주가 끝나지 않았으면 `reused_next_week`와 `reuse_rate`가 `NULL`이고, 진행 중인 이번 주는 코호트로 보여 주지 않는다.

```sql
BEGIN TRANSACTION READ ONLY;

WITH params AS (
    SELECT
        ARRAY[]::uuid[] AS excluded,
        date_trunc('week', now() AT TIME ZONE 'Asia/Seoul')::date AS current_week
),
weekly_users AS (
    SELECT DISTINCT
        r.user_id,
        date_trunc('week', r.requested_at AT TIME ZONE 'Asia/Seoul')::date AS week_start
    FROM ai_reviews r, params
    WHERE r.user_id <> ALL (params.excluded)
)
SELECT
    c.week_start AS request_week,
    COUNT(*) AS users,
    CASE WHEN c.week_start + 7 < params.current_week THEN COUNT(n.user_id) END AS reused_next_week,
    CASE WHEN c.week_start + 7 < params.current_week
        THEN ROUND(100.0 * COUNT(n.user_id) / COUNT(*), 1) END AS reuse_rate
FROM weekly_users c
CROSS JOIN params
LEFT JOIN weekly_users n ON n.user_id = c.user_id AND n.week_start = c.week_start + 7
WHERE c.week_start < params.current_week
GROUP BY c.week_start, params.current_week
ORDER BY request_week DESC
LIMIT 12;

ROLLBACK;
```

- 기준과 해석은 4번과 같고 단위만 주다. 지난주는 W+1(이번 주)이 진행 중이라 `NULL`이다.

## 6. 실행과 검증

- 3~5번 쿼리는 1~2번과 같이 prod DB에서 직접 실행한다. dev DB에서는 `excluded`를 비워 그대로 실행해 문법과 결과를 먼저 본다.
- 손 계산으로 대조할 원본은 다음 두 쿼리로 본다. 몇 개 날짜를 직접 세어 결과와 맞춘다.

```sql
SELECT user_id, requested_at AT TIME ZONE 'Asia/Seoul' AS kst, status FROM ai_reviews ORDER BY 2;
SELECT id, created_at AT TIME ZONE 'Asia/Seoul' AS kst FROM users ORDER BY 2;
```

- 재요청 수는 DB의 `last_requested_at`이 재요청마다 덮여 일별로 합산할 수 없다. 로그 기반 저장 쿼리로 보며 이 문서의 범위가 아니다.
