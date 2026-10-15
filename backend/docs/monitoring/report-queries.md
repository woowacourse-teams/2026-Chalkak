# 사용자 지표 리포트 쿼리

대시보드의 일별 지표(설계 문서 4.6절)를 보조하는 수동 조회용 SQL이다. 코호트별 리텐션을 한 표로 보거나, 검수 소요 시간의 주별 추세를 볼 때 prod DB에서 직접 실행한다. 정기 실행이나 저장은 하지 않는다.

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
