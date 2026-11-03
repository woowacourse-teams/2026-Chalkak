# PR 본문 형식

레포 템플릿 `.github/pull_request_template.md`의 칸을 그대로 쓰고 채운다.
템플릿에 없는 칸은 `## 📸 화면 확인` 하나만 체크리스트 아래에 더한다. 그 밖의 칸은 새로 만들지 않는다.
템플릿 파일 자체는 고치지 않는다. 백엔드와 같이 쓰는 파일이라 고치면 백엔드 PR에도 칸이 생긴다.

```markdown
## 🔗 연관된 이슈

- close #123

## 📋 작업 내용

- 작가 프로필에 소개 표시

완료 조건
- [x] 소개가 있으면 화면 상태에 담는다
- [x] 소개가 비어 있으면 소개 없음 상태가 된다
- [x] (추가 TC) 소개 앞뒤 공백은 지운다
- [x] 소개가 3줄을 넘으면 말줄임으로 끝난다 (화면 확인)

검증 결과
- TC 1 소개가 있으면 화면 상태에 담는다
  Red: expected:<필름으로 찍어요> but was:<null>
  Green: 통과
- `./gradlew ktlintCheck lintDebug testDebugUnitTest` → 종료 코드 0

남긴 기록
- 화면 확인 방법: 작가 프로필 화면 (client/.agents/skills/android-screen-check/references/ProfileScreen.md)

백엔드 전달 필요
- 소개 글자 수 제한 확인 요청

## ✅ 변경 사항 체크리스트

- [x] PR 대상 브랜치가 `develop`인가요? (client/develop)
- [x] Linear 동기화 여부를 확인했나요?

## 📸 화면 확인

(화면 확인 자료 형식대로)

## 📎 참고 자료

## 🛠️ Technical Concerns
```

## 칸마다

- **완료 조건**: 계획의 TC 제목과 테스트 밖 확인 항목을 체크박스로. 확인 못 한 항목은 `- [ ] ... (기기 확인 못 함)`.
- **검증 결과**: `$EVID/evidence.md`를 옮긴다. TC가 없으면 `유닛 TC 없음 (모양 수정)`.
- **남긴 기록**: `client-record`가 남긴 것. 없으면 줄을 지운다.
- **백엔드 전달 필요**: `$EVID/backend-note.md`가 있을 때만.
- **📸 화면 확인**: [화면 확인 자료](screen-evidence.md)를 본다. 화면 코드가 바뀌지 않았으면 `없음 (화면 코드 변경 없음)`.
- **Linear 체크**: 확인하지 않았으면 체크하지 않는다.
