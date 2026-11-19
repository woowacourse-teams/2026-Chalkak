# Jev 관리자 초안 시험 실행

#1056의 세 번째 개발 단위다. PR 미등록·운영 미배포이며 실제 TypeSafe 유료 호출은 검증하지 않았다. 단위/통합 검증은 외부 HTTP·AWS 전송을 대체한다. 동결 실험 파일과 지표는 변경하지 않는다.

## 시험 요청과 조회

관리자는 `POST /api/v1/admin/jev-previews`에 `postId`, `topicId`, `draft`를 보낸다. 사진은 삭제되지 않은 공개 게시물의 운영 원본 키를 사용하고 주제는 활성 주제 중 선택한다. 시험 요청은 새 프롬프트 버전·운영 활성화·게시물 상태·사용자 알림을 변경하지 않는다. 원래 GPT 미리보기의 무저장 동작도 유지한다.

draft는 `model`, `classification`, `photo` 세 항목이며 두 configuration의 구조는 [프롬프트 관리 계약](../../docs/business-rules/guides/admin-jev-prompts.md)과 같다. model은 jev-1.14.0이다. 두 역할을 함께 제출해야 하며 분류 선택지별 사진 규칙이 있어야 한다. 초안은 시험 재현과 결과 확인을 위해 시험 작업에만 저장한다. 운영 작업에 초안 전체를 복사하는 방식을 뜻하지 않는다.

201 응답의 jobId로 `GET /api/v1/admin/jev-previews/{jobId}`를 조회한다. 분류/사진 물리 호출의 ID·시작/수신 시각·원응답·오류·HTTP 왕복 시간과 질문별 선택·확률·Confidence·서버 추천이 나온다. 분류와 사진의 사용량을 구분하며 여러 시험이 공유한 분류는 classificationId 기준 한 번만 합산해야 한다. 확인할 수 없는 토큰/지연은 null이고 0회 사용으로 표시하지 않는다. `evaluation`이 null이면 아직 실행 대기/진행 중이다.

## 처리와 복구

1. Spring이 기존 posts/사진·주제를 확인하고 두 신규 테이블 중 시험 행을 저장한다.
2. 동일 주제 ID·주제 제목·모델·분류 질문의 정규화된 SHA-256 키가 있으면 DB 분류를 재사용한다. 동시 생성은 DB 잠금과 유니크 키로 직렬화한다. 분류 실패도 보존하여 새 사진마다 같은 실패 호출을 무한 반복하지 않는다. 관리자 초안의 분류 내용이나 주제 내용이 바뀌면 다른 키가 된다.
3. Spring의 Jev 전용 예약 작업이 DB의 미완료 행을 SQS에 전달한다. DB 전송 예약과 실제 SQS 통신을 분리하므로 HTTP 동안 DB 트랜잭션을 잡지 않는다. 전송 실패/서버 중단은 행을 지우지 않고 다음 전달 시점 이후 복구한다.
4. Lambda가 `POST /internal/v1/jev-moderation-jobs/claims`로 jobId를 보낸다. 응답은 CLASSIFY/INSPECT/WAIT/STOP이다. WAIT/STOP이면 대기 루프 없이 종료하고 DB 예약 작업에 재개를 맡긴다. 동일 분류/사진 단계의 소유권은 한 호출만 갖는다.
5. CLASSIFY는 주제와 분류 질문만 보낸다. INSPECT는 최종 S3 WebP를 실제로 읽고 PNG로 변환하여 topic_fit/image_safety/image_expression 세 질문을 한 HTTP 요청으로 보낸다. 제목·본문은 보내지 않는다.
6. 각 결과는 같은 큐의 JEV_RESULT 메시지로 먼저 보관하고 Spring의 `POST /internal/v1/jev-moderation-jobs/{jobId}/results`로 전달한다. 응답이 유실되면 결과 메시지만 다시 전달하고 Jev 재호출 횟수를 초기화하지 않는다. 큐와 콜백이 모두 실패한 경우 응답 자체가 유실될 수 있으며, 이 경우를 확실한 미접수로 단정하지 않는다.
7. 콜백은 기존 HMAC을 환경·경로·원문 본문 해시와 묶어 검증한다. 발급되지 않은 callId는 거절하고 중복 결과는 적용·호출 기록을 늘리지 않는다. 늦게 도착한 유실 응답은 원문을 복구하되 새 호출을 완료한 것으로 취급하지 않는다.

분류와 사진은 각 첫 호출 확보부터 독립적으로 최대 100초다. 공급자 HTTP는 연결 3초, 전체 최대 30초로 제한하며 Linux Lambda의 순차 handler에서 실행한다. 호출 결과 전달 여유를 포함한 실행 소유권은 최대 45초이며 단계의 100초를 넘기지 않는다. 소유권 만료는 공급자 미접수의 증명이 아니다. 단계별 물리 시도는 최초 포함 최대 3회이고 일시 오류만 약 2초·4초+jitter 이후 재시도한다. Retry-After를 존중하되 남은 예산 안에서만 다시 시작한다. 큐 대기/이미지 준비 시간은 공급자 HTTP 시간과 구분한다.

성공 응답의 질문 누락·형식 오류·직접 검토 선택은 재호출로 통과시키지 않는다. API·스키마 오류는 기술 검토이며 일부 반려 점수가 있어도 누락된 응답으로 확정하지 않는다. 유효한 질문은 기존 동결 실험의 60% 조건부 승인/반려 및 모델 직접 검토 유지 기준을 적용하고 평균내지 않는다. 이 추천을 실제 운영 게시물에 적용하는 기능은 후속 단위다. 운영 정책은 승인 추천만 자동 승인하고 반려 추천·검토는 관리자 결정 대기다.

## PNG 규격

- EXIF 방향 보정 후 짧은 변 300px, 비율 유지, 무여백·무크롭·무왜곡 PNG. 1600×1200은 400×300, 세로는 300×400이다. 300px 미만 입력도 같은 규격으로 확대하며 화질 개선을 보장하지 않는다.
- 픽셀만 새 RGB/RGBA 이미지로 옮겨 EXIF·ICC·텍스트를 제거하고 투명도를 유지한다.
- 각 변 280~1568px와 PNG 16MiB 상한을 만족하지 않는 극단 비율은 자동 크롭/패딩하지 않고 기술 검토로 보낸다. 애니메이션·손상·픽셀/파일 한도 초과도 기술 오류다. 최종 WebP는 재인코딩 후 staging 5MiB보다 커질 수 있어 별도 32MiB 읽기 상한을 쓰며, S3 읽기와 변환은 8초 안으로 제한한다. 이는 공급자 PNG 16MiB 상한과 별개다.
- S3 읽기·전처리 오류는 기존 이미지 실패 API에 보내지 않는다. 이 경로를 통해 게시물을 자동 반려시키지 않는다.

## 수동 연결과 배포

코드만으로 AWS 자원을 만들거나 운영을 활성화하지 않는다. 처음에는 `JEV_WORKER_ENABLED=false`다. 전용 Standard SQS 큐를 기존 image processor Lambda의 이벤트 소스로 연결하고 배치 크기 1, 부분 실패 응답 옵션과 코드의 SQS_PARTIAL_BATCH_RESPONSE 값을 맞춘다. Lambda 실행 제한은 분류/사진 예산 200초와 전달 여유를 고려해 최소 240초를 확보하고, 큐 visibility timeout은 실제 Lambda timeout의 AWS 권고값에 맞춰 운영자가 확인한다. 기존 이미지 처리 이벤트 소스의 제한을 임의 변경하지 않는다.

- Spring dev/prod: JEV_WORKER_ENABLED, JEV_WORK_QUEUE_URL. 작업 큐는 ap-northeast-2 Standard 큐이며 기존 AWS SDK 자격 증명에 해당 큐 SendMessage 권한이 필요하다. 변경은 `/etc/chalkak/application.env`와 CodeDeploy 재배포로 반영한다.
- Lambda: TYPESAFE_API_KEY, JEV_WORK_QUEUE_URL. 작업 큐 Receive/Delete/ChangeVisibility와 결과 메시지 SendMessage, 최종 S3 원본 GetObject 및 기존 업로드 권한을 확인한다. 기존 환경별 내부 API 주소와 HMAC 비밀키는 재사용한다. TypeSafe 키는 Spring에 두지 않는다.
- Lambda↔Spring HTTPS 접근과 시간 동기화, 이미지 bucket/root/environment의 일치, 실제 S3 접근, 큐 재전달 설정은 배포 전에 별도로 확인해야 한다. 로그에는 키·프롬프트·사진·원응답을 남기지 않고 작업/호출 식별자와 안전한 오류 유형만 남긴다.

요청/응답 형식은 [TypeSafe Choice](https://docs.typesafe.ai/primitives/choice), 이미지 asset·헤더·크기 제한은 [이미지 문서](https://docs.typesafe.ai/images)를 근거로 한다. 이미지 입력은 공급자가 실험 단계로 안내하고 있으므로 이 연동 테스트를 실제 사진 정확도 검증으로 표시하지 않는다.
