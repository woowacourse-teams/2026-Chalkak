# Jev 운영·관리자 API 연동

백엔드 구현을 위한 계약이다. PR 병합/배포 완료를 뜻하지 않으며 관리자 화면은 백엔드 PR 리뷰 반영·병합 이후 별도 개발한다. 관련 규칙: POST-003·006, ADMIN-003·008·010·011·013, NOTIFICATION-001.

## 앱 등록과 검수

1. 기존 업로드/게시물 작성 API를 사용한다. 하루 총 10회는 성공한 posts 생성으로 집계하고 삭제·반려·생성 후 처리 실패도 포함한다. 새 일일 카운터 테이블은 없다. [잔여 횟수 안내](post-registration-limit.md)를 따른다.
2. 등록 시 자동/수동 대상과 사용할 프롬프트 참조를 DB에 예약한다. 사진 준비 전에는 Jev를 부르지 않는다. 이미지 먼저/게시물 먼저 모두 지원한다. 첫 활성화 전에 접수한 사진을 소급 자동 처리하지 않는다.
3. Lambda가 운영 S3 최종 WebP를 실제로 읽고 짧은 변 300px·원본 비율·무여백 PNG를 만들어 검사한다. 공급자 이미지 한계 때문에 긴 변 300px 고정은 사용하지 않는다. [시험/전처리·AWS 설정](https://github.com/woowacourse-teams/2026-Chalkak/blob/f4b6725653649b5175026a0198e3de3c75665d56/backend/docs/jev-trial-execution.md)을 확인한다.
4. 주제 분류는 운영 주제 내용/분류 버전 키로 DB 재사용한다. 시험 분류는 운영과 격리한다. 최종 실패한 분류도 보존해 매 사진마다 실패 호출을 무한 반복하지 않는다. 원인 해결 후 새 분류 버전을 적용하면 새 분류 키를 사용한다.
5. 사진 한 HTTP 요청에 topic_fit·image_safety·image_expression 세 질문을 보낸다. 제목/본문은 보내거나 검사하지 않는다. 기존 서버 조건부60% 후처리를 보존하며 점수를 평균내지 않는다.
6. 서버 추천이 모두 승인일 때만 APPROVED 공개와 사용자 승인 알림을 만든다. 반려 추천/검토/최종 기술 오류는 PENDING이며 관리자가 실제 결정한다. 모델 추천만으로 사용자 반려 알림은 보내지 않는다.
7. 사용자는 기존 허용 상태에서 사진을 삭제할 수 있다. 대기 중이면 새 호출을 막고 이미 나간 결과는 이력만 보존한다. 삭제된 사진을 다시 공개하거나 늦은 사용자 알림을 만들지 않는다.

분류/사진 단계마다 첫 확보 후100초, 물리 Jev HTTP 최대30초·연결3초·최초 포함 최대3회다. 이 시간에는 별도의 이미지 준비/큐 대기가 포함되지 않으며 전체1분 완료를 보장하지 않는다. 모델 응답 유실 시 외부 과금/중복 가능성은 기록하고 확실한 미접수로 단정하지 않는다.

## 관리자 API

| 용도 | 요청 | 주의 |
| --- | --- | --- |
| 수동 프롬프트 | 기존 `/api/v1/admin/jev-prompt-versions` | [프롬프트 관리](admin-jev-prompts.md). 저장과 적용 분리, 초기 시드 없음, GPT와 분리 |
| 초안 시험 | POST `/api/v1/admin/jev-previews` | 공개 사진+활성 주제+초안. 실제 상태/사용자 알림은 바꾸지 않음 |
| 시험 조회 | GET `/api/v1/admin/jev-previews/{jobId}` | 공유 분류는 classificationId로 구분 |
| 검수 목록 | GET `/api/v1/admin/jev-moderation-runs` | preview,postId,topicId,disposition 선택 필터. preview 생략은 양쪽 모두. page 기본0, pageSize 기본20·최대100. 요약만 반환 |
| 검수 상세 | GET `/api/v1/admin/jev-moderation-runs/{jobId}` | 원래 질문 선택/확률/Confidence, 서버 추천, 운영 처분, 실제 현재 게시물 상태, 호출별 원응답/토큰/HTTP/실패, Slack 상태/최종 실패, 정답과 고정 반려 제안 문구 |
| 평가 정답 | PUT `/api/v1/admin/jev-moderation-runs/{jobId}/evaluation` | 완료된 기록만, 아래 JSON. 사진 공개 상태/사용자 알림과 독립 |
| 실제 판정 정정 | PUT `/api/v1/admin/posts/{postId}/moderation-correction` | 확정된 APPROVED↔REJECTED 전용, 양방향 사유 필수. 기존 PENDING 최초 판정 API는 그대로 |
| 지표 | GET `/api/v1/admin/jev-moderation-runs/metrics?from=...&to=...&preview=false` | UTC Instant from 이상/to 미만, 최대31일, 운영/시험 구분. 최대10000건, truncated=true면 기간을 줄여 재조회 |

평가 입력 예:

```json
{
  "decision": "REJECT",
  "questions": {"topic_fit": "REJECT"},
  "reason": "사진 속 사물과 주제의 연결 근거가 없음",
  "sampling": "RANDOM"
}
```

questions는 빈 객체/일부 질문을 허용한다. 최종 정답만으로 나머지 질문의 정답을 추정하지 않는다. RANDOM은 무작위로 뽑은 평가이고 ERROR_ANALYSIS는 오류 분석용으로 골라 본 표본, UNSPECIFIED는 선택 방식을 모르는 표본이다. 실제 무작위 추출 기능을 제공한다는 뜻은 아니며 관리자가 표본 출처를 사실대로 기입해야 한다. 평가 변경 전후와 근거·평가자·시각은 기존 감사에 남는다.

실제 정정 예:

```json
{"status":"APPROVED","reason":"관리자가 원본을 다시 확인해 촬영 기준 충족을 확인"}
```

같은 현재 상태로 재요청하면 추가 변경/알림 없이 반환한다. 이때 응답의 moderatedBy/rejectionReason은 null이며 새 행위자/사유가 저장됐다고 표시하지 않는다. 삭제·탈퇴·주제 삭제와 이미지 처리 실패를 정정으로 복구하지 않는다. 이전 반려 후 새 활성 사진이 있으면 이전 사진을 승인할 수 없다. 실제 정정은 정답 라벨/모델 원응답을 바꾸지 않는다.

## 화면에 표시할 정보

목록은 사진·주제, 서버 추천, 실제 상태, 정답 유무와 Slack 상태로 간결하게 구성한다. 상세에서만 세 질문의 approve/reject/human_review 확률과 서버 후처리 결과, 단계별 물리 호출·입출력 토큰·HTTP 시간·오류를 펼친다. Confidence는 모델이 제공한 확신 값이며 실제 정답률이 아니다.

분류 토큰을 사진마다 더하면 비용이 중복된다. 공유 classificationId와 물리 callId를 기준으로 한 번만 합산한다. 호출별 토큰이 null이면 미확인이다. knownInputTokens/knownOutputTokens는 확인된 부분 합계이며 unknown...Calls가 있으면 전체 비용/총량으로 표시하지 않는다. 입력/출력 모두 알려진 물리 요청만 둘을 더해 총 토큰으로 표시한다.

서버 추천 REJECT와 실제 REJECTED를 같은 배지로 표시하지 않는다. 고정 반려 문구는 관리자가 실제 반려할 때 참고할 제안이며 모델이 자세한 시각적 사유를 반환했다는 의미가 아니다.

## 지표 읽기

- finalAgreement는 정답이 있는 모델/서버 추천 표본의 분모·일치·오승인·오반려를 반환한다. 꺼짐/미설정 등 모델 없이 수동 대기로 보낸 건은 모델 정확도에서 제외한다. 실제 기술 오류로 사람 검토가 된 결과는 서버 추천 검토로 비교한다.
- questions는 해당 질문 정답을 실제 입력한 표본만 평가한다. 정답은 있지만 해당 질문 응답이 누락된 건은 missingPredictions이며 분모에서 숨기지 않는다.
- bySampling은 무작위·오류 분석·미분류 표본을 구분한다. 골라 모은 오류 표본의 점수를 전체 운영 정확도로 표현하지 않는다.
- falseAutoApprovals/ratedAutoApprovals는 당시 AUTO_APPROVED 처분과 관리자 정답을 비교한다. 나중에 정정됐다고 원래 자동 오승인이 지워지지 않는다.
- consistencyPercent는 동일 사진/주제/설정의 정상 결과가5개 이상인 그룹의 엄격한 동일 추천 비율이다. 다른 그룹/실패 건을 반복 실행으로 가장하지 않으며 분모가 없으면 null이다.
- usage는 공유 분류와 callId 중복을 제거한다. reservedAttempts는 외부 호출 전에 실패한 예약도 포함한다. observedHttpCalls는 HTTP 상태/왕복 시간이 확인된 호출이고, timedHttpCalls만 p99에 쓰인다. p99는 개별 물리 HTTP nearest-rank이며 단계별 p99 합이나 다운로드/전처리/대기/콜백 시간이 아니다.
- truncated=true는 전체 기간 지표가 아니다. 더 좁은 기간으로 조회한다. 분모가 없으면 비율은 null이며 0%/100%로 채우지 않는다.

## 알림과 Android 전달 문구

관리자 Slack은 반려 추천/검토/최종 오류·수동 대기만 요약+관리자 링크로 보낸다. 최초 포함3회·1분/5분 재시도하며 성공 시 멈춘다. 서버 재시작도 DB 상태로 복구하며 Slack 실패 때문에 Jev를 다시 호출하지 않는다. 성공 응답 유실 시 외부 중복 가능성은 남는다.

Android 전달용: 하루 총10회 등록이며 삭제해도 횟수는 복구되지 않습니다. 삭제 전 잔여 횟수/다음 KST 초기화 시각을 조회해 안내해 주세요. 등록 직후 VALIDATING/PENDING은 유지되고 Jev 반려 추천/검토/오류도 PENDING에서 관리자 결정을 기다립니다. 사용자 반려 알림은 실제 관리자 반려 후에만 도착합니다. 실제 판정 정정은 기존 POST_APPROVED/POST_REJECTED 유형으로 오며 제목에 정정임을, body에 정정 사유를 담습니다. 반려 정정은 기존 rejectionReason도 유지합니다. 새 알림 유형은 없고 기존 클릭 경로/수신 설정을 사용합니다. 제목/본문 수정은 Jev 호출과 일일 등록 횟수에 영향을 주지 않습니다.

공통 배포본 버전은 이번 PR에서 새로 발행하지 않았고 서버/Lambda도 배포 전입니다. 관리자 웹 구현과 Android 반영은 별도이며 이 안내를 자동 발송하지 않습니다.
