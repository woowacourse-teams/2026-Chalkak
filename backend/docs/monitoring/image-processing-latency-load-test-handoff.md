# 사진 처리 지연 조사 및 부하 테스트 인계

작성일: 2026-10-14 KST

## 2026-10-15 운영 상시 감시 방향 추가

아래 본문은 10월 13일 사건의 조사 기록이며, 그 사건의 원인은 당시 미확정이었다. 이후 별도 세션의 10월 15일 인계에는 Serial Full GC(CodeCache GC Threshold), 약 7.6초 정지, 같은 구간의 대량 swap-in과 Java major fault 증가·VmSwap 감소를 근거로 스왑 관련 GC 지연을 확인했다고 보고돼 있다. 이 후속 사건의 관측을 10월 13일의 직접 증거로 소급하지 않는다.

운영의 memwatch는 조사용 임시 장치이므로 그대로 상시화하지 않는다. CloudWatch Agent에 swap의 `swap_used_percent`와 백엔드 Java 프로세스를 대상으로 하는 procstat의 `memory_rss`, `memory_swap`을 추가해 GC 2초 이상 정지 알람과 함께 본다. 다른 Java 프로세스를 잘못 선택하지 않도록 실행 명령 패턴을 검증한다. 새 수집의 권한·실제 지표 유입·GC 로그와 알람을 확인하고 조사 자료를 보존한 뒤 운영 memwatch 타이머·스크립트·로그를 정리한다. 아직 이 문서의 계획을 서버에 적용한 것은 아니다.

이 기본 지표는 스왑 사용량을 보여주며 swap-in/out이나 Java major fault를 대체하지 않는다. 상시 장애 감지에는 기본 지표를 사용하고, 향후 새 사건의 스왑 인과관계 검증이 필요하면 조사용 기록을 다시 활성화한다. 부하 테스트 서버의 10초 간격 스왑 입출력 기록은 시험 종료까지 유지한다.

참고: [CloudWatch Agent procstat 공식 문서](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Agent-procstat-process-metrics.html)

## 1. 목적과 현재 결론

이 문서는 2026-10-13 운영 앱의 사진 표시 지연을 조사한 결과를 부하 테스트 전용 세션에 인계한다. 사용자는 이 맥락을 바탕으로 부하 테스트를 진행하려 한다. 이 문서 작성 시점에는 부하 테스트, GC 상세 로그 활성화, JVM 설정 변경, 개선 배포를 수행하지 않았다.

사진은 최종적으로 앱에 표시됐다. 조사 대상은 업로드의 최종 실패가 아니라 **사진이 표시되기까지 오래 걸린 이유**다.

- 확인: Lambda가 운영 백엔드의 업로드 URL 발급 API 응답을 기다리다가 read timeout을 겪었다.
- 확인: 같은 시간대 운영 JVM의 GC 누적 시간이 8.070초 증가했고, 다른 API 요청 하나가 8.086초 걸렸다.
- 확인: 애플리케이션 로그에 약 9초의 공백이 있고, 그 직후 여러 스레드의 요청 종료 로그가 밀집했다.
- 유력 가설: GC 관련 JVM 정지가 백엔드 응답을 지연시켰다.
- 보조 가설: Java 메모리의 스왑이 GC 지연에 영향을 줬을 수 있다.
- 지연 증폭 가설: 최초 타임아웃 이후 SQS의 6분 visibility timeout 때문에 재시도까지 수분을 기다렸다.
- 미확정: 실제 GC 종류·정지 시각·정지 길이, 당시 스왑 입출력, 동일 사진의 재시도 성공 실행과의 연결, 정확한 사용자 체감 지연.

**GC 누적 시간 증가를 단일 STW 정지 시간으로, 현재 스왑 사용량을 과거 스왑 입출력의 증거로 취급하면 안 된다.** 사진 요청이 GC를 유발했다고도 확인되지 않았다. 다른 요청이 만든 객체나 JVM 상태 때문에 발생한 정지에 우연히 겹쳤을 수 있다.

## 2. 현재 사진 처리 구조

```text
앱이 백엔드에서 staging 업로드용 presigned URL 발급
  → 앱이 S3 staging 경로에 사진 업로드
  → S3 ObjectCreated 이벤트
  → SQS chalkak-image-processing
  → 이벤트 소스 매핑이 메시지를 읽고 Lambda chalkak-image-processor 호출
  → Lambda가 S3 원본 다운로드, 이미지 검증·변환·메타데이터 추출
  → 운영/개발 백엔드에 최종 원본·썸네일 저장용 URL 발급 요청
  → Lambda가 presigned URL로 S3에 원본·썸네일 저장
  → 백엔드에 완료 콜백
  → 업로드 READY 반영, 이미 존재하는 게시물은 검수 대기 처리
```

SQS에 사진 바이트를 넣는 것이 아니라 S3 버킷·키 정보가 담긴 이벤트를 넣는다. staging 키의 `dev`/`prod`로 백엔드 환경을 선택한다. 사인 이미지와 게시물 사진이 큐·Lambda를 공유하므로, 큐 전체 지표를 특정 사진의 지표로 간주하면 안 된다.

관련 API:

- `POST /internal/v1/post-image-processing/{uploadId}/upload-urls`
- `POST /internal/v1/post-image-processing/{uploadId}/complete`
- `POST /internal/v1/post-image-processing/{uploadId}/failed`

## 3. 10월 13일 사건과 추적 결과

### 3.1 사용자 제보와 식별자

- 사용자 제보: 10월 13일 오전 08:05~08:10 KST 무렵 사진을 올렸고, 표시되기까지 오래 걸렸다. 최종적으로 앱에 사진이 표시됐다.
- 제보한 사진과 아래 오류 업로드의 동일성은 앱 요청 기록으로 직접 대조하지 못했다. 앱 측 별도 HTTP 로깅은 없다고 전달받았다.
- 업로드 ID: `01a13264-6d1e-778b-add1-2d64e9df398d`
- S3 키: `chalkak/staging/prod/posts/01a13264-6d1e-778b-add1-2d64e9df398d.webp`
- SQS messageId: `e596fbe0-924c-4d75-bae1-060917dc464f`
- 실패 Lambda requestId: `04361bd9-b454-57f3-bbc7-789edf9a9c5f`
- 백엔드에서 확인한 느린 일반 API requestId: `82663f81-8f9c-4e81-a2c2-663486122cc3`

Lambda와 백엔드는 requestId가 서로 다르다. 현재 접근 로그에는 실제 uploadId 대신 경로 템플릿이 남으므로 시간과 API가 가깝다는 이유만으로 동일 요청이라고 확정할 수 없다.

### 3.2 시간 순서

UTC는 KST보다 9시간 느리다. 이 사건의 10월 13일 08시 KST는 10월 12일 23시 UTC다.

| 시각(KST) | 관측 내용 |
| --- | --- |
| 08:12:41.198 | 런타임 GC 누적 2,669ms, 힙 사용 101,163,352 bytes |
| 08:13:28.386 | 요청 종료 로그. 이후 약 9초간 애플리케이션 로그 공백 |
| 08:13:29.032 | 실패한 Lambda 실행 시작 |
| 약 08:13:29.298 | 느린 일반 API의 추정 시작 시각: 종료 시각에서 durationMs를 뺀 값 |
| 08:13:34.898 | Lambda `image_processing_failed`, `TimeoutError: The read operation timed out` |
| 08:13:37.384 | `/api/v1/notifications/unread-status` 200, durationMs=8086 |
| 08:13:37.389~37.396 | 여러 스레드에서 요청 종료 로그가 연속 발생 |
| 08:13:37.396 | `upload-urls` 200, durationMs=18. 실패 Lambda 요청과의 동일성 미확인 |
| 08:13:41.198 | 런타임 GC 누적 10,739ms, 힙 사용 70,815,176 bytes |
| 08:13~08:19경 | SQS 가장 오래된 메시지 나이 증가, 최대 약 4.7분 |
| 08:20경 | 위 SQS 지표가 다시 0으로 감소 |

시간 대조에는 가능한 한 JSON 본문의 `@timestamp`를 사용했다. CloudWatch 바깥 `@timestamp`와 본문 시각이 수 초 다른 기록이 있어, 수집 시각을 실제 처리 완료 시각처럼 사용하면 잘못 연결할 수 있다. `durationMs`는 서버 필터 진입부터 종료까지의 시간이며 서버 도달 전 대기 등 모든 네트워크 시간을 측정하지 않는다.

### 3.3 실패 위치

스택의 핵심:

```text
post.py: issue_upload_urls(environment, upload_id)
→ callback.py: request.urlopen(...)
→ HTTPConnection.getresponse()
→ _read_status()
→ TimeoutError: The read operation timed out
```

S3 변환·저장 자체의 오류나 Lambda 실행 제한 초과가 아니다. **백엔드의 HTTP 응답 상태를 읽기 전에 타임아웃**됐다. 코드의 백엔드 통신 timeout 기본값은 3초지만 실제 운영 환경변수 값은 확인하지 못했다.

같은 예외가 우리 코드의 `image_processing_failed` 로그와 Lambda의 예외 로그에 각각 남았다. 이 두 줄을 실패 2회로 세면 안 된다.

### 3.4 런타임과 시스템 관측

두 런타임 기록 모두 최대 힙은 642,646,016 bytes(약 613MiB), `hikariActive=0`, `hikariPending=0`이었다. 스레드 수는 35 → 34다.

- GC 누적 시간 차이: `10739 - 2669 = 8070ms`
- 코드에서 `GarbageCollectorMXBean.getCollectionTime()`을 여러 수집기에 대해 합산한다. 단일 pause나 연속 STW 8.07초를 직접 측정한 값이 아니다.
- DB 연결 대기 0은 각 측정 순간의 값이다. 그 사이의 일시적 대기를 배제하지 못한다.

운영 EC2에서 사용자가 실행해 공유한 결과:

- RAM 약 903MiB, 당시 조회 시 available 약 247MiB.
- 스왑 1GiB 중 약 511MiB 사용.
- Java VmRSS 약 355MiB, VmSwap 약 192MiB.
- `vmstat 1 10`의 두 번째 이후 샘플은 `si=0`, `so=0`, `wa=0`. **사후 측정 당시** 스왑 입출력·I/O 대기는 관측되지 않았다.
- 사건 구간 커널 journal은 `No entries`. 사건 구간 서비스 출력에는 GC 상세 기록이나 재시작 증거가 보이지 않았다.
- Java 시작 옵션: `/usr/bin/java -XX:MaxRAMPercentage=70 -jar /opt/chalkak/app/application.jar`
- `/opt/chalkak/logs/`에는 application.log 및 회전 파일만 확인. 시작 옵션에 GC 상세 로깅 옵션 없음.
- 실제 GC 종류, Java 버전, cgroup 제한과 JVM이 인식하는 CPU·메모리는 미확인.

CloudWatch EC2 08:00~08:30 KST 그래프:

- CPU 평균 약 1~2%, CPU 크레딧 잔액 약 289로 고갈 징후 없음.
- mem_used_percent 약 66~76%, disk_used_percent 약 22%.
- 수 분 집계 지표이므로 몇 초짜리 CPU·메모리·I/O 변화까지 배제할 수 없음.

## 4. 별도 과거 사례: 10월 7일 관리자 알림 지연

조회 범위 10월 5일 00:00 UTC~10월 12일 23:59:59 UTC에서 `image_processing_failed`와 timeout 문구를 함께 검색했을 때, 이번 건 외에 과거 한 건이 추가로 나왔다. 모든 종류의 실패가 2건뿐이었다는 뜻은 아니다.

과거 건은 `complete` 콜백에서 실패했다.

- 업로드 ID: `01a11453-2f16-7f5d-88d8-4e37f78217b2`
- Lambda requestId: `9cd6c02a-5ea9-5a30-838f-a315e7e3a51e`
- 백엔드 requestId: `74651b62-65bd-45c6-87d2-1cf9ac5dbba3`

| 시각(KST) | 관측 |
| --- | --- |
| 12:06:11.982 | 백엔드 검수 대기 로그 |
| 12:06:14.980 | Lambda complete 콜백 read timeout |
| 12:06:27.938 | 같은 백엔드 요청에서 관리자 알림 발송 실패 |
| 12:06:27.939 | complete API 204, durationMs=15961 |

현재 코드의 `PostModerationPendingNotificationListener`는 `AFTER_COMMIT` 리스너이지만 비동기가 아니라 요청 스레드에서 알림 발송을 기다린다. **커밋 이후 실행된다고 HTTP 응답과 분리되는 것은 아니다.** 알림 실패 직후 응답한 로그 순서와 코드가 응답 지연의 강한 근거다.

단, 알림 통신만의 16초를 별도 계측한 것은 아니며 공급자 통신이 왜 실패했는지도 미확정이다. 오늘 `upload-urls`의 GC 의심 사건과 별도 가설로 관리한다. 부하 테스트에서 동기 알림 지연을 GC 지연으로 잘못 분류하지 않는다.

## 5. SQS 재시도 동작과 주의점

- 운영 큐에서 확인한 visibility timeout은 6분이다. 수신 시점부터 메시지를 숨기며, 실패 후 즉시 재전달되지 않는다. 만료 후 재전달 가능한 상태가 돼도 정확히 그 시각에 실행된다는 보장은 없다.
- 처리 성공 후 메시지는 Lambda 이벤트 소스 연동에 의해 삭제된다.
- 일시적 오류를 예외로 전파하면 재전달 대상이 된다. partial batch response를 쓰지 않으면 배치 전체가 재시도될 수 있으므로 운영 batch size·부분 실패 설정을 확인해야 한다.
- 업로드 URL 발급 오류는 HTTP 상태와 관계없이 재시도 대상으로 처리한다.
- 완료/실패 콜백 timeout·네트워크 오류·5xx 등은 재시도한다. 일부 영구 4xx는 종료한다.
- 손상·크기 초과·허용하지 않는 이미지 등은 실패 콜백 후 반려한다.
- 재시도 시 해당 메시지의 사진 처리 흐름을 다시 실행한다. 실패한 HTTP 호출만 이어서 실행하는 구조가 아니다.
- 수신 횟수 상한은 `SQS_MAX_RECEIVE_COUNT`, 코드 기본값 5. 실제 운영값은 미확인.
- DLQ 없이 상한 도달 시 포기 로그와 실패 콜백을 시도한 뒤 메시지를 종료하는 구현이다.
- SQS 메시지 나이 최대 4.7분은 큐 전체의 근사 지표다. 특정 사진의 대기 시간·성공·총 6분 대기를 직접 증명하지 않는다.

## 6. 다음 세션의 준비 작업

### 6.1 실제 환경 기준선 확인

운영 서버에는 우선 조회만 수행한다. 현재 PID는 다시 확인하고 과거 PID를 그대로 사용하지 않는다.

```bash
java -version
command -v jcmd
sudo systemctl show chalkak-backend.service --property=MainPID --value
sudo systemctl show chalkak-backend.service --property=ExecStart --no-pager
free -h
swapon --show
```

추가 확인: 인스턴스 타입·vCPU, 실제 GC, 최대/초기 힙, Java 프로세스 메모리, swap 정책, JVM의 CPU·메모리 인식, Lambda 실제 timeout 설정, SQS 이벤트 소스 batch size·batch window·부분 실패 설정. 비밀값이나 presigned URL 전체를 출력·기록하지 않는다.

### 6.2 GC·safepoint 상세 로그

현재는 활성화하지 않았다. 다음은 검토용 명령 예시다. 대상 JDK 지원과 경로 권한을 확인한 후 적용한다.

실행 중 임시 활성화(`jcmd`가 있고 attach가 가능할 때, `<PID>`는 실제 값으로 치환):

```bash
sudo -u chalkak jcmd <PID> help VM.log
sudo -u chalkak jcmd <PID> VM.log list
sudo -u chalkak jcmd <PID> VM.log \
  output=/opt/chalkak/logs/gc.log \
  what='gc*=info,safepoint*=info' \
  decorators='utctime,uptime,level,tags' \
  output_options='filecount=5,filesize=10M'
```

이 명령은 실행 중인 JVM의 로깅 설정을 변경하며, 재시작 후에는 유지되지 않는다. systemd의 PrivateTmp 등으로 attach가 실패할 수도 있으므로, 실패 시 서비스 설정을 무계획하게 변경하지 않는다.

영구 적용하려면 Java 시작 옵션에 추가한다. 이미 실행 중인 JVM에는 반영되지 않고, 다음 서비스 재시작부터 유효하다.

```text
-Xlog:gc*=info,safepoint*=info:file=/opt/chalkak/logs/gc.log:utctime,uptime,level,tags:filecount=5,filesize=10M
```

GCの種類・原因・pause時間、safepoint到達時間と停止中の時間、GCログが提供するCPU時間と実経過時間を確認する。負荷試験環境で情報が足りなければタグ・レベルの追加を検討する。GCを強制するコマンドを観測のためだけに実行しない。

이 파일은 기존 CloudWatch Agent 수집 대상과 별개다. EC2에서 직접 확인하거나 별도 수집 설정이 필요하며, Java 로그 옵션만으로 CloudWatch에 나타나지는 않는다.

참고:

- [Java -Xlog 명세](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html)
- [jcmd VM.log 명세](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html)

### 6.3 추적과 측정

- 요청 시작·종료·API별 응답 시간·timeout을 기록하고 GC 로그와 시각을 맞춘다.
- uploadId 같은 비밀값이 아닌 상관 ID로 앱·S3·SQS·Lambda·백엔드 단계를 연결한다. 현재 로그의 부족을 고려해 시험 관측을 설계한다.
- 시험 환경에서 힙 사용량, GC 횟수·pause, RSS·VmSwap, vmstat의 si/so/wa, CPU, DB 연결 대기를 기록한다. 운영 기본 지표와 시험용 원인 조사 기록은 구분한다.
- 사진 처리 구간을 나눈다: S3 PUT 완료, 이벤트 배송·첫 실행 대기, Lambda 처리, 백엔드 통신, 실패 후 재실행 대기, 앱의 상태 확인·표시.
- 외부 알림·폴링 간격·DB 잠금 등의 지연을 GC와 구분한다.

## 7. 부하 테스트에서 확인할 것

### 7.1 검증할 가설

1. 운영과 비슷한 자원 제약에서 긴 GC/safepoint 정지가 재현되는가.
2. 정지와 일반 API 지연·Lambda의 백엔드 통신 timeout이 같은 시각에 발생하는가.
3. 정지 구간에 swap-in이 발생하며 GC 실제 경과 시간 증가와 관련이 있는가.
4. 한 번의 일시적 실패가 visibility timeout 때문에 사진 표시 시간을 얼마나 늘리는가.
5. 힙 크기·메모리 여유 등을 조정하면 같은 조건에서 pause와 timeout이 감소하는가.

### 7.2 권장 실험 순서

1. 별도 환경에 Java/GC/CPU/RAM/힙/스왑 등을 운영과 비슷하게 구성한다. 공유 SQS·Lambda·알림 대상으로 시험 부하가 흐르지 않는지 확인한다.
2. 관측을 켜고 낮은 부하에서 기준값을 얻는다. 실제 사용자 수·요청 빈도·사진 크기 분포는 아직 확보하지 않았으므로 시험 가정을 명시한다.
3. 조회 등 일반 API 혼합 부하에서 JVM 정지의 영향을 관측하고, 이후 사진 파이프라인 전체를 확인한다. 동시 실행 수와 요청 빈도를 단계적으로 늘린다.
4. 지속 부하 외에 유휴 상태 이후 첫 요청, 오래 유지되는 객체의 축적 등도 검토한다. 고부하에서만 생기는 현상을 이번 사건 재현으로 단정하지 않는다.
5. 조건을 하나씩 바꿔 비교한다. 힙·GC 종류·RAM·스왑·timeout·SQS 설정을 한꺼번에 바꾸지 않는다. 스왑 비활성화 실험은 격리 환경에서만 검토하고 OOM 가능성을 다룬다.
6. visibility timeout의 영향은 시험 환경에 의도적인 일시 실패를 주는 별도 실험으로 확인한다. GC 원인 재현과 재시도 메커니즘 검증을 혼동하지 않는다.

### 7.3 평가와 종료 조건

- API별 p50/p95/p99/최대 응답 시간, timeout 건수와 전체 요청 수, 사진 사용 가능·표시까지 시간, GC 최대 pause·총 pause·횟수, swap-in/out, SQS 재수신을 비교한다.
- 허용 정지 길이·오류율·메모리 여유 등의 중단 기준과 시험 데이터 정리 방법을 미리 정한다.
- 공유 큐·Lambda·RDS·S3·알림 대상의 경계를 먼저 확인한다. 이 문서는 운영 환경에 대한 부하 실행 승인이 아니다.
- 재현 실패는 원인 부정이 아니라 미재현으로 기록하고, 시험 조건과 관측 범위를 남긴다.
- 개선 성과는 동등한 조건의 전후 측정으로 확인한다. 현재 상태를 개선 완료·원인 확정으로 보고하지 않는다.

## 8. 다음 세션의 코드·문서 참조

아래 경로는 backend 기준이다. 코드와 설정은 바뀔 수 있으므로 시험 시작 시 실제 코드와 운영 배포 버전을 다시 확인한다.

- `lambda/image-processor/README.md`: 처리 흐름, 실패 정책, SQS 설정
- `lambda/image-processor/handler.py`: 예외 전파, 배치 응답, 수신 횟수 상한, 포기 처리
- `lambda/image-processor/image_processor/post.py`: 이미지 처리와 콜백 순서
- `lambda/image-processor/image_processor/callback.py`: HTTP 요청과 재시도하지 않는 오류
- `lambda/image-processor/image_processor/config.py`: timeout 기본값
- `src/main/java/com/chalkak/backend/common/logging/RequestIdFilter.java`: 접근 로그 측정 범위
- `src/main/java/com/chalkak/backend/common/logging/RuntimeSnapshotReader.java`: GC 누적값·연결 풀 값
- `src/main/java/com/chalkak/backend/post/service/PostCommandService.java`: READY 반영과 검수 이벤트
- `src/main/java/com/chalkak/backend/notification/service/PostModerationPendingNotificationListener.java`: 동기 AFTER_COMMIT 알림
- `deploy/systemd/chalkak-backend.service`: JVM 시작 설정
- `deploy/docs/monitoring.md`: CloudWatch 로그 그룹·SQS/Lambda 지표

이 문서는 조사·시험 인계이며 구현 변경·커밋·운영 설정 변경의 포괄 승인이 아니다. 다음 세션은 AGENTS.md와 해당 스킬을 읽고 시험 계획과 환경 경계를 구체화한다.
