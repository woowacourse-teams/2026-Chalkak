# ADR 형식

```markdown
# <결정을 한 줄로: 삭제 결과를 UiState 목록으로 반영>

관련 경로: feature/feed/**/*ViewModel.kt, feature/feed/**/*UiState.kt

## 맥락
어떤 문제가 있었나. 무엇이 결정을 강요했나. (2~4줄)

## 선택지
- A: ... — 장점 / 단점
- B: ... — 장점 / 단점

## 결정
무엇을 골랐고 왜인가. 이 결정 때문에 앞으로 지켜야 하는 것.

## 근거
이슈 #123, PR #456, 재현 녹화나 측정값이 있으면 그 위치.
```

## `관련 경로:` 줄

- 제목 바로 아래(첫 10줄 안)에 한 줄로 둔다.
- 패턴은 플랫폼 폴더(`client/android/`, `client/iOS/`) 기준이다. `/`로 시작하지 않으면 어느 깊이에서 시작해도 맞는다.
  - `feature/feed/**` → `.../chalkak/feature/feed/` 아래 전부
  - `feature/**/*ViewModel.kt` → 모든 피처의 ViewModel
  - `/app/build.gradle.kts` → 플랫폼 폴더 바로 아래의 그 파일만
- 이 결정을 모르고 고치면 안 되는 파일만 넣는다. 너무 넓으면(`**`) 모든 계획에서 읽혀 의미가 없다.
- 넣은 뒤 확인한다.
  ```bash
  python3 client/.agents/skills/client-plan/scripts/related-adr.py <이번에 바꾼 파일>
  ```
