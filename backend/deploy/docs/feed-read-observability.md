# 피드 좋아요 수 조회 패턴 관측

같은 게시물의 좋아요 수가 사용자·정렬을 넘어 다시 필요한지 관찰한다. 응답에 포함된 게시물은 알 수 있지만 실제 화면 노출이나 DB SQL 실행·캐시 Hit를 기록한 것은 아니다.

## 기록과 수집 시작

정상 `GET /api/v1/posts`의 기존 접근 로그 한 줄에 다음을 더한다.

| 필드 | 의미 |
| --- | --- |
| `sort` | 기본값을 적용한 `recent`·`popular`·`random` |
| `page` / `pageSize` | 응답의 현재 페이지·페이지 크기 |
| `postIds` | 응답 순서의 게시물 ID JSON 배열. 빈 피드는 `[]`, 최대 100개 |

요청 시각은 기존 `@timestamp`, 요청 식별자는 `requestId`를 사용한다. 시각은 접근 로그를 쓰는 **응답 처리 종료 시점**이다. SQL 시작 시각이나 사용자가 본 시각은 아니다. 정상 응답의 메타데이터만 기록하며 실패 요청·다른 경로에는 추가하지 않는다. 제목·사진 URL·토큰·랜덤 시드는 추가하지 않는다.

```json
{"@timestamp":"2026-10-19T06:00:00Z","type":"access","requestId":"request-1","method":"GET","route":"/api/v1/posts","status":200,"durationMs":18,"topicDate":"2026-10-19","sort":"recent","page":1,"pageSize":20,"postIds":["0198f6c1-62ba-7d30-8b12-0f733b6570d4","0198f6c1-62ba-7d30-8b12-0f733b6570d5"]}
```

앱의 일반 배포 이후 기존 CloudWatch Agent가 같은 파일·로그 그룹으로 수집한다. **운영 배포 전에는 새 데이터가 없고, 과거 요청의 정렬·게시물 목록은 복원할 수 없다.** 환경변수·IAM·메트릭 필터 추가는 필요 없다. `dashboard.json`은 기존 모니터링 런북 9단계에 따라 별도 수동 반영한다. 이 변경 자체가 운영 배포나 대시보드 자동 적용은 아니다.

## 1. 정렬별 10초 조회량

서울 리전 Logs Insights에서 `/chalkak/prod/application`만 선택한다. 개발은 `/chalkak/dev/application`으로 바꾸고, 처음에는 배포 이후 짧은 구간부터 조회한다.

```sql
filter type = "access" and method = "GET"
  and route = "/api/v1/posts" and status = 200 and ispresent(sort)
| stats count(*) as requests by bin(10s), `sort`
```

세로축은 **10초 구간의 요청 건수**이며 RPS·캐시 적중률이 아니다. 자동 새로고침 주기와도 다르다. `sort`가 없는 과거 로그는 기본값으로 추정하지 않고 제외한다. 숫자가 안 나오면 배포·로그 유입·조회 기간부터 확인한다. 대시보드에도 이 쿼리를 사용한다.

## 2. 같은 게시물에 요청이 겹친 구간

```sql
filter type = "access" and method = "GET"
  and route = "/api/v1/posts" and status = 200 and ispresent(sort)
| fields jsonParse(@message) as accessLog
| unnest accessLog.postIds into postId
| stats count(*) as lookups, count_distinct(`sort`) as sortKinds
    by postId, datefloor(@timestamp, 10s) as window
| filter lookups >= 2
| sort lookups desc
| limit 100
```

`lookups`는 해당 게시물의 좋아요 수를 포함한 피드 응답 수다. `sortKinds`가 2 이상이면 서로 다른 정렬에서 요청했다. 사용자 ID를 묶음 기준으로 사용하지 않으므로 여러 사용자의 요청도 함께 센다.

**이 표는 실제 10초 TTL 적중률이 아니다.** 구간 첫 요청도 포함하고, 구간 경계를 넘어 일어난 재조회는 놓친다. 저장 성공 후 TTL·용량 제거·로딩 중 공유·실패까지 재현해야 실제 캐시 효과를 판단할 수 있다.

## 3. 실제 재조회 간격과 로컬 비교

```sql
filter type = "access" and method = "GET"
  and route = "/api/v1/posts" and status = 200 and ispresent(sort)
| fields jsonParse(@message) as accessLog
| unnest accessLog.postIds into postId
| fields @timestamp, requestId, `sort`, page, pageSize, postId
| sort postId asc, @timestamp asc
| limit 10000
```

결과를 내보내 같은 `postId`의 연속 응답 시각 차이를 계산하면 10초 이내 재조회 여부를 볼 수 있다. **10,000행은 출력 상한**이다. 전체 일주일이라고 가정하지 말고 짧은 시간대로 나눠 누락 없이 확보한다. 분할 경계에서는 게시물별 마지막 시각을 이어서 비교하고, 겹친 구간을 수집했다면 `requestId + postId`로 중복을 제거한다.

이후 정렬 비율·페이지 분포·요청 간격·게시물 겹침을 참고해 같은 로컬 부하에서 OFF/ON을 비교한다. 재조회 간격이 짧아도 Hit가 만료를 연장하지 않으므로 간격 비율을 곧바로 적중률로 바꾸지 않는다. SQL 횟수·응답 시간·메모리·GC와 함께 측정하고, 인기순 한정 적용은 최신순에서 잃는 재사용도 포함해 판단한다.

## 비용과 관측 한계

- 한 요청당 기존 로그 한 줄을 유지하고, 페이지 크기 상한 안에서 ID 배열만 추가한다. 게시물별 로그·추가 SQL·DB 조회수 컬럼은 만들지 않는다.
- UUID 문자열 배열은 ID당 약 39바이트로, 20개 약 780바이트·100개 약 3.9KB가 추가된다. 다른 필드·전송 비용을 제외한 설명용 추정이다. 실제 수집량과 Logs Insights 스캔량을 확인한다.
- 기존 로그 그룹 보관 정책을 따른다(prod 60일·dev 7일). 불필요한 장기 조회·자동 새로고침을 피하고 분석 후 필요 기간만 보관한다. 게시물 ID를 메트릭 라벨로 만들지 않는다.
- 기존 `userId`가 있는 접근 로그와 같은 접근 통제를 적용한다. 요청 ID와 게시물 ID가 포함된 원본을 공개 자료에 그대로 게시하지 않는다. 팀원·테스트 트래픽 포함 여부도 기록한다.
- 응답 시점의 수요 관찰이며 클라이언트가 실제로 표시한 사진이나 정확한 DB 읽기 시각은 알 수 없다. 운영 관측만으로 캐시 성능 개선을 검증했다고 표현하지 않는다.

공식 문서: [배열 펼치기(unnest)](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/CWL_QuerySyntax-Unnest.html), [JSON·시간 함수](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/CWL_QuerySyntax-operations-functions.html).
