# devwiki — 찰캌 5개 플랫폼 개발 지식베이스

찰캌을 iOS native, Android native, Flutter, React Native, Web app 다섯 가지로 각각 구현할 때 공통으로 참고하는 개발 지식이다. UI는 플랫폼 관례를 따를 수 있지만 **기능 동작은 다섯 클라이언트가 같아야 한다**는 전제로 정리했다.

## 구조

```text
devwiki/
├── raw/    주제별 리서치 원본(출처 URL 포함). 수정하지 않고 근거 확인용으로만 읽는다.
└── wiki/   raw를 중복 없이 분류·정리한 문서. 작업할 때는 여기부터 읽는다.
```

- 시작점: [wiki/README.md](wiki/README.md)
- 원본 목록과 검증 상태: [raw/README.md](raw/README.md)

## 우선순위

문서끼리 충돌하면 아래 순서를 따른다.

1. `docs/business-rules/**` — 서비스 정책과 API 의미의 원본. client 작업에서는 읽기 전용이다.
2. 백엔드 OpenAPI(springdoc)와 실제 서버 동작
3. `devwiki/wiki/**` — 플랫폼 공통 구현 기준
4. `devwiki/raw/**` — 리서치 근거

wiki가 business-rules와 다르게 적혀 있으면 business-rules가 맞다. 그 차이는 wiki를 고쳐서 맞춘다.

## 표기

| 표기 | 의미 |
| --- | --- |
| `[공식]` | 리서치 중 공식 문서 본문을 직접 열어 확인했다. |
| `[공식·미열람]` | 공식 문서 URL은 맞지만 본문을 열지 못했다. 구현 전에 원문을 다시 본다. |
| `[일반지식]` | 공식 근거를 찾지 못한 업계 관행이다. |
| `[찰캌규칙]` | `docs/business-rules`나 서버 코드에서 확인한 찰캌 고유 사실이다. |
| `[제안]` | 이 위키가 제안하는 팀 기준이다. 합의 후 확정한다. |

## 갱신 방법

- 새 리서치는 `raw/<주제>.md`로 추가하고 [raw/README.md](raw/README.md) 표에 한 줄 적는다.
- wiki에는 결론과 체크리스트만 옮기고, 근거는 raw의 해당 절을 링크한다. 같은 내용을 두 wiki 문서에 쓰지 않는다.
- 버전·날짜가 붙은 사실(targetSdk, Expo SDK, OS 최소 버전 등)은 수집일을 함께 적는다. 이번 수집일은 2026-10-27이다.
