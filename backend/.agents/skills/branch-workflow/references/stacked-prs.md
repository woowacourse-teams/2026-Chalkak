# 기존 Stacked PR 복구·정리

새 작업의 기본 흐름에는 사용하지 않는다. 모든 새 이슈는 앞 이슈의 병합·완료·종료 뒤 통합 브랜치에서 시작한다. 사용자가 기존 스택의 인계·정리를 명시적으로 요청한 경우에만 아래 절차를 읽는다.

- 작업 트리·저장소·확인된 통합 브랜치·원격 상태를 먼저 확인한다. 기존 부모 경계·후속 고유 변경의 근거가 없으면 재작성을 보류한다.
- 이력 재작성·안전한 갱신 푸시·PR base 변경은 대상과 영향을 설명하고 별도 승인받는다. 일반 분할안 승인으로 확대하지 않는다. 새 후속 이슈 개발·새 스택 생성·자동 병합은 포함하지 않는다.
- 팀원의 변경을 덮어쓰지 않는다. 실제 코드 수정과 새 커밋에는 커밋별 설명·검증·승인을 적용한다. 이미 승인된 동일 변경의 rebase는 승인된 정리 범위에서만 수행하며 충돌 해결로 동작이 바뀌면 변경을 재승인받는다.

## 선행 수정·병합 후 갱신

1. 작업 재개, 후속 단위 착수, PR 갱신 전에 선행 PR의 실제 상태·head SHA와 원격 브랜치를 조회한다. 선행 수정이 있으면 이전 부모 기준 SHA를 보존한 채 후속 고유 커밋만 새 부모 위로 재적용한다. 이미 반영한 변경이면 반복하지 않는다.
2. 선행 PR이 병합되면 원격의 자동 정리 여부와 실제 새 base를 먼저 확인한다. squash·rebase 병합은 커밋 식별자가 달라질 수 있으므로 부모의 옛 커밋을 다시 넣지 않는다. 수동 Git에서는 검증한 이전 부모 경계를 사용한 `rebase --onto`로 후속 고유 변경만 옮기고 PR base를 갱신한다. 경계를 복구할 근거가 없으면 해당 재작성을 멈추고 확인한다.
3. 아래 단계부터 순서대로 반영하고 관련 테스트·빌드 및 **새 base 대비 해당 PR 고유 diff**를 확인한다. 정책·계약 재결정이 필요한 충돌은 해당 쟁점을 확인한다. 검증 실패를 고치기 위해 다른 단위의 변경을 몰래 포함하지 않는다.
4. 원격 재작성은 작업 기록과 대조해 다른 사람의 미반영 변경이 없는지 확인하고, **예상 원격 SHA를 명시한 `--force-with-lease=<ref>:<sha>`**로 승인된 ref만 갱신한다. 일반 `--force`는 사용하지 않는다. lease가 거부되면 최신 상태를 조사하며 새 SHA만 받아 덮어쓰지 않는다.
5. 최종 head·base·스택 구성·CI 상태를 다시 조회하고 관련 이슈·PR의 범위·링크·읽는 순서를 갱신한다. 검증·푸시를 확인한 단위의 새 부모 기준 SHA와 원격 head SHA를 다음 실행 기준으로 기록하되, 아직 갱신하지 않은 후속 브랜치의 옛 부모 경계는 보존한다. 일부 푸시·API 갱신만 성공하면 성공/실패한 단위부터 구분해 재개한다. 종료 코드만으로 전체 성공을 판단하지 않는다.

- `gh stack sync`는 조회가 아니라 rebase·push·PR 갱신을 수행한다. `submit`도 여러 브랜치를 게시할 수 있다. 사용할 경우 위 승인·대상·변경 보호를 충족하는 옵션인지 확인하고 범위를 제한한다. `--prune`이나 자동 병합은 이 절차에 포함하지 않는다.
- 세션 종료 후 감시는 이 문서만으로 실행되지 않는다. 현재 상태·기준 SHA·다음 행동을 남기고 이후 실행에서 다시 확인한다.

## 공식 참고

- [생성·연결](https://docs.github.com/en/pull-requests/how-tos/create-pull-requests/creating-stacked-pull-requests), [Stacks API](https://docs.github.com/en/rest/pulls/stacks)
- [CLI 명령](https://docs.github.com/en/pull-requests/reference/stacked-prs-cli-commands), [스택·trunk·CI 제약](https://docs.github.com/en/pull-requests/reference/stacked-pull-requests)
- [선형 스택 설계](https://github.com/github/gh-stack/blob/main/skills/gh-stack/references/stack-design.md), [동기화 문제 해결](https://github.com/github/gh-stack/blob/main/skills/gh-stack/references/troubleshooting.md)
