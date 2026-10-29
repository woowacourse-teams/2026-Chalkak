# 사진·DB 백업 및 복구 검증

이 문서는 2026-10-26에 사람이 AWS 콘솔과 EC2 터미널에서 수행한 복구 훈련의 절차와 결과를 기록한다. 근거는 대화에 공유된 콘솔 화면과 명령 출력이다. 이후 AWS 설정은 달라질 수 있으므로 실행 전에 다시 조회한다.

공개 문서의 리소스 이름·접속 정보는 자리표시자 또는 예시로 바꿨다. 실제 값과 접근 구성은 팀의 비공개 운영 자료에서 확인한다. 예시를 실제 리소스로 간주해 실행하지 않는다.

이번에는 기존 S3 Versioning과 RDS 자동 백업을 검증했다. 이후 운영 RDS 보존 기간을 1일에서 5일로 늘리는 변경 예약에 성공했으며 실제 적용은 확인 대기 중이다. 별도 사진 백업 구축과 운영 연결 전환은 수행하지 않았다. RDS 실습 자원 삭제는 권한 거부로 완료되지 않았다.

## 1. 보호 대상과 확인한 설정

사진의 사용자 연결은 다음 관계로 유지된다. 파일 이름은 업로드 UUID로 생성되며, 파일 이름만으로 사용자를 복원하지 않는다.

```text
social_accounts.user_id → users.id
posts.user_id          → users.id
posts.photo_id         → photos.id
photos.original_storage_key / thumbnail_storage_key → S3 객체 key
```

DB에는 전체 공개 URL 대신 객체 key가 저장된다. 버킷과 CloudFront 설정은 별도로 필요하다. 사용자 서명 이미지의 key도 `users`에 있으므로 향후 전체 백업 범위를 정할 때 포함한다.

| 대상 | 훈련 시 확인한 상태 |
| --- | --- |
| 사진 저장소 | 서울 리전 `<운영_버킷>`의 `<팀_접두사>/` 경로 |
| S3 Versioning | 활성화, MFA 삭제 비활성화 |
| 운영 DB | `<운영_RDS_식별자>`, PostgreSQL 18.4, DB 이름 `<DB_이름>` |
| 운영 DB 구성 | `db.t4g.micro`, Single-AZ, gp3 20GiB, 암호화 활성화 |
| RDS 자동 백업 | 활성화, 보존 기간 1일, 다른 리전 복제 미설정 |
| 개발 DB | EC2의 Docker PostgreSQL. 배포 전 덤프는 같은 EC2 디스크에 저장하며 독립 사본은 아님 |

S3 Versioning은 일반 삭제·덮어쓰기의 복구 수단이다. 특정 사진 버전의 영구 삭제나 DB 유실까지 해결하지는 않는다. RDS의 1일은 허용 손실 시간이 아니라 자동 백업 보존 기간이다. 실제 PITR 범위는 가장 이른/최근 복원 가능 시간으로 확인한다.

## 2. 재실행 전 준비

1. 담당자, 비용 부담, 실행·정리 시간을 확인한다. 임시 DB의 생성뿐 아니라 **삭제 권한 또는 관리자 정리 담당자**도 확보한다.
2. RDS 자동 백업에서 현재 복원 가능 구간을 조회하고, 복원 대상 시각과 시간대를 기록한다. 사고 복구에서는 손상 이전 시각을 선택한다.
3. 복원용 이름을 `<복구용_RDS_식별자>`처럼 운영과 구분한다. 이미 같은 이름의 리소스가 있다면 소유·용도를 확인한다.
4. 공용 버킷·보안 그룹·서브넷·KMS 키는 수정하지 않는다. 복구용 DB도 실제 사용자 데이터를 포함하므로 접근 범위를 확인한다.
5. 접속할 EC2, RDS 보안 그룹의 5432 허용, EC2 아웃바운드 및 네트워크 경로를 확인한다. 보안 그룹 일치만으로 실제 연결 성공을 보장하지 않는다.
6. 비용은 실행 시점의 서울 리전 단가로 산정한다. 2026-10-26 확인한 기본 단가는 Single-AZ `db.t4g.micro` $0.025/시간, gp3 $0.131/GB-월이었다. 20GiB·4시간의 기본 사용료는 월 730시간 환산 시 약 $0.114이며, 세금·초과 CPU 크레딧·백업·전송 등은 별도다. 확정 청구액이나 상한이 아니다.

## 3. S3 일반 삭제·복구 절차

실제 사용자 사진이 아닌, 개인정보 없는 이미지 한 장을 사용한다. 이 테스트 사진은 앱을 거치지 않아 DB·사용자 연결이 없다.

1. S3 콘솔에서 `<운영_버킷>/<팀_접두사>/recovery-drill/`에 업로드한다. 폴더 생성 이름은 `recovery-drill`로 입력하고 끝 슬래시는 붙이지 않는다. 기본 암호화 설정을 사용한다.
2. 사진이 열리는지 확인하고 key, 크기, 버전 ID를 기록한다. ETag는 버전 ID와 다르다.
3. 폴더 객체 목록으로 돌아와 **버전 표시를 끈 상태**에서 테스트 파일 한 개만 선택해 일반 삭제한다. 사진 버전 자체의 영구 삭제를 선택하지 않는다.
4. 기본 목록에서 사라진 뒤 버전 표시를 켠다. 삭제 마커와 기존 사진 버전이 함께 남는지 확인한다.
5. **유형이 삭제 마커인 행만** 선택해 영구 삭제한다. 사진 버전은 선택하지 않는다. 사진 버전 영구 삭제는 해당 파일 데이터를 제거한다.
6. 버전 표시를 끄고 동일 key의 사진이 다시 보이고 열리는지 확인한다.

### 수행 결과

테스트 key는 `<팀_접두사>/recovery-drill/<테스트_사진>.jpeg`, 크기는 18.4KB였다. 11:32:30 KST 업로드 후 11:34:40 KST에 일반 삭제했다. 삭제 마커와 JPEG 버전이 함께 남았으며, 마커 제거 후 정상 열람을 사용자가 확인했다.

이 결과는 단일 테스트 객체의 일반 삭제 복구를 증명한다. 영구 삭제 복구, 대량 복구, 덮어쓰기 복구는 이번에 수행하지 않았다. 테스트 객체의 최종 정리는 미확인이다.

## 4. RDS PITR 및 읽기 전용 검증 절차

### 별도 DB 복원

1. RDS → 자동 백업 → 현재 인스턴스에서 원본 `<운영_RDS_식별자>`를 선택하고 특정 시점으로 복원한다.
2. 이번 훈련에서는 최근 복원 가능 시간을 선택했다. 이 선택은 실행 시 갱신될 수 있어 오래 열린 화면의 시각만으로 실제 복원 시각을 확정하지 않는다.
3. 새 식별자는 `<복구용_RDS_식별자>`, 클래스는 `db.t4g.micro`, **단일 AZ**, gp3 20GiB, 퍼블릭 액세스는 아니요로 설정한다. 초기 DB 이름은 빈칸으로 두고 원본 DB를 복원했다.
4. 사용할 VPC·서브넷·보안 그룹과 암호화 설정을 검토한다. 훈련 시 허용된 EC2에서 복구 DB로 접속하는 데 성공했다. 구체적인 보안 그룹·허용 소스는 비공개 운영 자료에서 확인하며 팀별 격리 여부는 별도로 점검한다.
5. 요청 시각을 기록하고 실행한다. 운영 앱의 연결 주소는 바꾸지 않는다. 상태와 로그 및 이벤트를 확인하고 사용 가능 확인 시각, 첫 접속 성공 시각, 검증 완료 시각을 각각 기록한다.

PITR은 원본을 덮어쓰지 않고 새 DB를 만든다. 훈련 시에는 복원 DB에 앱·예약 작업을 연결하지 않았다. 향후 앱 검증을 추가하면 알림 발송 등 외부 부작용을 차단하는 별도 계획이 필요하다.

### EC2에서 접속

SSH 또는 SSM으로 접속한 EC2에서 실행한다. 이번 EC2에는 psql 18.6이 설치돼 있었으며 서버는 18.4였다. 아래 인증서 다운로드와 접속 명령의 호스트는 **RDS 콘솔 복사 버튼에서 가져온 복구용 주소**로 채운다. 이미지에서 주소를 옮겨 적으면 오타로 DNS 조회가 실패할 수 있다.

```bash
psql --version
curl -fsSL -o /tmp/chalkak-rds-ca.pem \
  https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem

PGOPTIONS='-c default_transaction_read_only=on' \
psql -X -W "host=<복구용_RDS_엔드포인트> port=5432 dbname=<DB_이름> user=<DB_사용자> sslmode=verify-full sslrootcert=/tmp/chalkak-rds-ca.pem connect_timeout=10"
```

비밀번호는 프롬프트에만 입력하고 문서·채팅·명령 인자에 남기지 않는다. 복원 시점의 DB 자격 증명을 사용한다. 읽기 전용 설정은 이번 세션의 기본값이며 권한을 제한한 전용 DB 계정을 만든 것은 아니다.

psql에서 `\conninfo`는 단독으로 입력하고 실행한 뒤 다음 SQL을 입력한다. 이번에는 뒤 SQL까지 한 줄의 메타 명령 인자로 붙어 무시된 일이 있어 분리했다.

```text
\conninfo
```

```sql
SHOW transaction_read_only;
SELECT current_database(), current_user;
```

호스트가 복구용 주소이고 읽기 전용이 `on`, DB·사용자가 복구 대상의 예상 값인지 확인한다.

### DB 내부 참조 검사

```sql
BEGIN READ ONLY;
SET LOCAL statement_timeout = '30s';

SELECT 'users' AS table_name, count(*) AS row_count FROM users
UNION ALL SELECT 'posts', count(*) FROM posts
UNION ALL SELECT 'photos', count(*) FROM photos
UNION ALL SELECT 'social_accounts', count(*) FROM social_accounts;

SELECT count(*) AS total_posts,
       count(*) FILTER (WHERE u.id IS NULL) AS missing_users,
       count(*) FILTER (WHERE ph.id IS NULL) AS missing_photos
FROM posts p
LEFT JOIN users u ON u.id = p.user_id
LEFT JOIN photos ph ON ph.id = p.photo_id;

SELECT count(*) AS social_accounts_without_user
FROM social_accounts sa
LEFT JOIN users u ON u.id = sa.user_id
WHERE u.id IS NULL;

ROLLBACK;
```

`ROLLBACK`은 조회 트랜잭션을 종료하며 RDS 복원을 취소하지 않는다. 참조 누락 0건은 잘못된 사용자 연결이나 원본과의 전체 동일성까지 증명하지 않는다.

### S3 파일 표본 대조

```sql
SELECT p.id AS post_id, p.user_id, ph.id AS photo_id,
       ph.original_storage_key, ph.thumbnail_storage_key
FROM posts p
JOIN photos ph ON ph.id = p.photo_id
WHERE p.deleted_at IS NULL AND ph.deleted_at IS NULL
ORDER BY p.created_at DESC, p.id DESC
LIMIT 3;
```

출력의 원본·썸네일 key를 S3에서 확인하고 둘 다 정상 열람되는지 대조한다. `original/` 또는 `thumbnail/` 폴더 안에서는 전체 key 대신 파일 이름만 검색하고 Enter를 누른다. 검색 결과가 비었다는 이유만으로 유실로 판정하지 않는다.

이번 EC2에는 AWS CLI가 없어 `head-object`는 실행되지 않았다. 설치하지 않고 콘솔에서 3건의 원본·썸네일 총 6개를 확인했다. `head-object` 조회 성공만으로 이미지 내용이나 파일 무결성까지 확인한 것으로 처리하지 않는다.

### 수행 결과와 시간

| 검증 | 결과 |
| --- | --- |
| TLS 접속 및 읽기 전용 | 성공, `transaction_read_only=on` |
| public 스키마 기본 테이블 | 24개 조회 |
| 사용자 / 게시물 / 사진 / 소셜 계정 | 290 / 1,556 / 1,556 / 280건 |
| 게시물의 사용자·사진 참조 누락 | 각각 0건 |
| 소셜 계정의 사용자 참조 누락 | 0건 |
| 최근 활성 게시물 3건의 S3 원본·썸네일 | 6개 존재 및 정상 열람을 사용자 확인 |

사용자와 소셜 계정 건수 차이만으로 복원 실패라고 판단하지 않았다. 이번에는 전체 DB를 복원해 기존 ID·관계를 유지했으며 사용자를 새로 생성하거나 사진 소유자를 재배정하지 않았다.

| 2026-10-26 시각(KST) | 근거 |
| --- | --- |
| 12:21:20 | 사용자가 기록한 복원 요청 시각 |
| 12:30 | `Restored from DB instance ...`, `DB instance restarted` 이벤트 |
| 12:32 | `Backing up DB instance` 이벤트 |
| 12:43 | `Finished DB Instance backup` 이벤트 |

복원 이벤트 본문의 대상 시각은 `2026-10-26 03:16:47.0`이었다. UTC로 해석하면 12:16:47 KST이다. 본문에 시간대가 직접 표시되지 않았으므로 콘솔의 명시적 복원 시각/API 등으로 대조하기 전까지 시간대 환산 해석임을 구분한다.

요청부터 복구용 DB의 백업 완료 이벤트까지 **약 22분**이다. 이벤트 화면은 분 단위이며 이는 정확한 사용 가능 전환 시각이나 전체 서비스 RTO 측정치가 아니다. 첫 접속·검증 완료의 정확한 시각은 기록되지 않았다. RPO도 이번 훈련으로 측정하지 않았다.

## 5. 정리 상태와 남은 작업

### 실습 자원 정리 — 미완료

복구용 DB에 접속 중이면 `\q`로 종료한다. RDS 목록에서 실습용 `<복구용_RDS_식별자>`만 선택하고 삭제한다. 이번 실습 사본은 최종 스냅샷 생성·자동 백업 보존을 해제해 정리하는 것으로 안내했다. 운영 DB·운영 백업·공용 네트워크·KMS 키는 대상이 아니다.

실제 삭제 시도는 계정 관리 정책의 명시적 거부로 `rds:DeleteDBInstance`가 차단됐다. **관리자 삭제 요청용 문구를 준비했으며 발송·삭제 완료는 아직 확인되지 않았다.** 실행 중이면 비용이 계속 발생한다. 동일 요청 반복이나 다른 자격 증명으로의 우회 대신 관리자에게 정리를 요청한다.

정리 완료 시 다음을 갱신한다.

- 삭제 완료 시각 및 목록에서 사라진 사실
- 실습용 최종 스냅샷·보존 자동 백업의 잔존 여부
- S3 `<팀_접두사>/recovery-drill/` 테스트 객체 정리 여부

### 운영 RDS 보존 기간 변경 — 예약 완료, 적용 확인 대기

사용자는 사고 발견·조사에 사용할 복구 범위를 확보하기 위해 보존 기간을 5일로 선택했다. 운영 `<운영_RDS_식별자>` 수정 요약에서 보존 기간 1 → 5만 표시된 것을 확인하고, 다음 유지 관리 기간 적용을 선택해 제출에 성공했다고 보고했다.

- 적용 예정 구간: 2026-10-27 유지 관리 기간(정확한 시간대는 비공개 운영 자료에서 확인)
- 현재 확인한 상태: 변경 예약 제출 성공. 실제 5일 적용과 적용 완료 시각은 미확인
- 후속 확인: 유지 관리 이후 운영 DB의 보존 기간이 5일인지, 보류 중인 변경에 남아 있지 않은지 확인한다. 이후 가장 이른/최근 복원 가능 시각이 시간 경과에 따라 확장되는지도 확인한다.
- 이미 만료된 백업은 복구되지 않으며, 변경 직후 과거 5일치의 복원 지점이 즉시 생기는 것은 아니다.

### 남은 운영 결정·검증 항목

- 허용 손실(RPO), 목표 복구 시간(RTO), 선택한 5일 보존으로 감당할 수 있는 사고 발견·조사 지연. 4시간 RTO는 후보이며 확정·달성한 값이 아니다.
- 사진 버전 영구 삭제 위험을 감수할지, 별도 사진 백업을 둘지. 현재 계정의 새 버킷 생성은 불가하고 계정 내 비용은 지원된다는 사용자 제약을 반영한다.
- 백업 실패 및 최근 복원 가능 시점 지연의 확인·알림과 담당자, 정기 복구 훈련 주기.
- 전체 원본·썸네일·서명 파일의 존재·내용 검증. 이번에는 사진 3건만 검사했다.
- 로그인 후 사용자별 접근, 삭제·탈퇴 상태 보존, 실제 장애 시 전환·되돌리기 절차. 과거 DB 복원으로 사고 이후의 정상 변경도 사라질 수 있어 사고별 대조가 필요하다.

운영 DB를 사고 전 시점으로 복원한 사본과 **현재 S3**를 대조한 것이며, S3를 동일 시점으로 복원하거나 두 시스템의 원자적 백업을 검증한 것은 아니다. 단일 사진 삭제와 DB 복원 훈련만으로 계정 침해·리전 장애·전체 서비스 복구를 보장하지 않는다.

## 참고

- [S3 Versioning](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html)
- [RDS 특정 시점 복원](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_PIT.html)
- [RDS 이벤트 메시지](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_Events.Messages.html)
- [RDS 요금 구성](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/User_DBInstanceBilling.html)
- [훈련 비용 산정에 사용한 서울 리전 공식 가격표](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonRDS/current/ap-northeast-2/index.json)
