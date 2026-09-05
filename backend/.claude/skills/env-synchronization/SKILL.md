---
name: env-synchronization
description: .env, .env.example, Spring 환경변수 참조, deploy/examples의 배포 환경변수 예제 또는 환경변수를 처리하는 CD 스크립트를 생성·수정·리뷰할 때 사용한다. 환경변수 값만 바뀐 경우에도 계약 변경 여부를 판단하기 위해 사용한다.
---

# 환경변수 계약 동기화

## 보호 원칙

- `.env`의 실제 값, 비밀번호, 토큰과 키를 출력하거나 예제 파일·로그·커밋에 복사하지 않는다.
- `.env`를 확인해야 하면 변수 이름만 추출한다.
- 사용자가 요청하지 않은 `.env` 값은 수정하지 않는다.
- 값만 변경되고 변수 이름·사용 위치·필수 여부·기본값이 그대로라면 예제 파일과 배포 스크립트를 변경하지 않는다.

## 동기화 범위

### 로컬·테스트 계약

- `.env`
- `.env.example`
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/test/resources/application-test.yml`
- 운영 코드의 직접 환경변수 참조

### 배포 계약

- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`
- 운영 코드의 직접 환경변수 참조
- `deploy/examples/application.dev.env.example`
- `deploy/examples/application.prod.env.example`
- `deploy/scripts/check_configuration.sh`
- 환경변수를 소비하는 나머지 `deploy/**`

## 적용 방법

1. 변경이 값 변경인지 계약 변경인지 구분한다. 변수 추가·삭제·이름 변경, 사용 위치 변경, 필수 여부 또는 기본값 변경은 계약 변경이다.
2. 로컬·테스트에서 사용하는 변수의 계약 변경을 `.env.example`에 반영한다.
3. 애플리케이션의 dev·prod 실행에 필요한 변수의 계약 변경을 두 배포 예제에 모두 반영한다.
4. 배포 필수 변수의 추가·삭제·이름 변경을 `deploy/scripts/check_configuration.sh`의 `required_keys`와 관련 값 검증에 반영한다.
5. 이름을 변경하거나 삭제했으면 저장소에서 이전 이름을 검색하여 관련 설정과 문서에 남은 참조가 없는지 확인한다.
6. 예제 값에는 안전한 로컬 기본값 또는 `REPLACE_WITH_...` placeholder만 사용한다.
7. 완료 전에 다음 명령을 실행한다.

```bash
bash deploy/scripts/check_env_contract.sh
```

검증이 실패하면 누락되거나 불필요한 키를 의도에 맞게 수정한다. 로컬 전용 키는 배포 계약에 추가하지 않고, dev·prod 애플리케이션 실행에 필요한 키는 두 배포 예제와 CD 필수 키에서 누락하지 않는다.

## 최종 응답의 서버 반영 경고

배포 환경변수의 계약 또는 서버 값 변경이 필요한 경우 최종 응답의 맨 마지막을 아래 형식으로 끝낸다. 이 경고 뒤에는 다른 내용을 작성하지 않는다.

```markdown
## 🚨 배포 전 필수 수동 작업

사람이 다음 대상 서버의 `/etc/chalkak/application.env`를 직접 업데이트해야 합니다.

- 대상 환경: `dev`, `prod` 중 실제 대상
- 변경할 키: 키 이름만 나열

반영 후 서버에서 다음 명령을 실행해야 합니다.

`sudo systemctl restart chalkak-backend.service`

이 작업은 하네스와 CD가 자동으로 수행하지 않습니다.
```

- 실제 값, 비밀번호, 토큰과 키는 경고에 포함하지 않는다.
- 로컬 전용 변경이거나 서버 반영이 필요 없는 값 변경이면 이 경고를 사용하지 않는다.
- 대상 환경이 하나뿐이면 해당 환경만 적는다.

## 완료 전 확인

- 실제 비밀값을 출력하거나 추적 파일에 복사하지 않았는가?
- `.env`와 `.env.example`의 로컬 키 계약이 일치하는가?
- 두 배포 예제와 CD 스크립트의 필수 키 계약이 일치하는가?
- 변경한 키의 사용 범위에 맞춰 로컬 전용과 배포 공통을 구분했는가?
- `bash deploy/scripts/check_env_contract.sh`가 통과하는가?
- 서버 반영이 필요하면 최종 응답의 맨 마지막에 대상 환경과 키 이름을 포함한 필수 수동 작업 경고를 작성했는가?
