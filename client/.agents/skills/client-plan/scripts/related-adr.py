#!/usr/bin/env python3
"""바꿀 파일과 겹치는 ADR(설계 결정 문서)만 골라 준다.

역할
  client/<플랫폼>/docs/architecture/*.md 의 맨 위에 적힌 `관련 경로:` 줄을 읽고,
  이번 작업에서 바꿀 파일과 겹치는 ADR만 출력한다. 모든 ADR을 읽지 않게 하려는 도구다.

입력
  바꿀 파일 경로들. 레포 루트 기준(client/android/...), 절대 경로, 현재 폴더 기준 모두 된다.
  인자가 없으면 origin/client/develop과 갈라진 지점 이후 바뀐 파일과 새 파일을 쓴다.

출력 (표준 출력)
  겹친 ADR마다 한 줄: `<ADR 경로>  ← <바꿀 파일> (<맞은 패턴>)`
  하나도 없으면: `겹치는 ADR 없음`
  `관련 경로:` 줄이 없는 ADR은 표준 오류로 경고한다. 그 ADR은 이 스크립트로는 영영 찾히지 않는다.

`관련 경로:` 줄 형식
  ADR 첫 10줄 안에 `관련 경로: 패턴, 패턴, ...` 한 줄을 둔다.
  패턴은 그 ADR이 있는 플랫폼 폴더(client/android/ 또는 client/iOS/) 기준이다.
  - `*`  는 폴더 구분자(/)를 넘지 않는 아무 글자
  - `**` 는 폴더 여러 단계
  - `/` 로 시작하지 않는 패턴은 어느 폴더 깊이에서 시작해도 맞는다.
    예) `feature/**/*ViewModel.kt` 는
        client/android/app/src/main/java/com/stonefive/chalkak/feature/feed/FeedViewModel.kt 와 맞는다.
  - `/` 로 시작하면 플랫폼 폴더 바로 아래부터 맞춘다. 예) `/app/build.gradle.kts`

예시
  python3 related-adr.py client/android/app/src/main/java/com/stonefive/chalkak/feature/feed/FeedViewModel.kt
  python3 related-adr.py            # 지금 브랜치에서 바뀐 파일 전체로
"""

import os
import re
import subprocess
import sys

BASE_REF = "origin/client/develop"
HEADER = "관련 경로:"
HEADER_LINES = 10


def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True, check=True).stdout


def glob_to_regex(pattern):
    """`관련 경로` 패턴 하나를 정규식으로 바꾼다. 형식은 모듈 설명을 본다."""
    anchored = pattern.startswith("/")
    body = pattern.lstrip("/")
    out = []
    i = 0
    while i < len(body):
        if body.startswith("**/", i):
            out.append("(?:.*/)?")
            i += 3
        elif body.startswith("**", i):
            out.append(".*")
            i += 2
        elif body[i] == "*":
            out.append("[^/]*")
            i += 1
        elif body[i] == "?":
            out.append("[^/]")
            i += 1
        else:
            out.append(re.escape(body[i]))
            i += 1
    prefix = "" if anchored else "(?:.*/)?"
    return re.compile("^" + prefix + "".join(out) + "$")


def read_patterns(adr_path):
    """ADR 첫 10줄에서 `관련 경로:` 패턴 목록을 읽는다. 줄이 없으면 None."""
    with open(adr_path, encoding="utf-8") as f:
        for _, line in zip(range(HEADER_LINES), f):
            line = line.strip()
            if line.startswith(HEADER):
                raw = line[len(HEADER):]
                return [p.strip().strip("`") for p in re.split(r"[,\s]+", raw) if p.strip().strip("`")]
    return None


def changed_files(root):
    """인자가 없을 때 쓸 파일 목록: 기준 브랜치와 갈라진 뒤 바뀐 파일 + 아직 커밋 안 한 파일."""
    try:
        base = git("merge-base", "HEAD", BASE_REF).strip()
    except subprocess.CalledProcessError:
        sys.exit(f"{BASE_REF}를 찾지 못했어요. git fetch 후 다시 실행하거나 파일을 인자로 주세요.")
    files = set(git("diff", "--name-only", base).split())
    files |= set(git("ls-files", "--others", "--exclude-standard").split())
    return sorted(files)


def to_repo_relative(path, root):
    absolute = os.path.abspath(path)
    if absolute.startswith(root + os.sep):
        return os.path.relpath(absolute, root)
    return path  # 이미 레포 기준 경로로 준 경우


def main():
    root = git("rev-parse", "--show-toplevel").strip()
    targets = [to_repo_relative(p, root) for p in sys.argv[1:]] or changed_files(root)

    adrs = []
    client_dir = os.path.join(root, "client")
    for platform in sorted(os.listdir(client_dir)):
        arch_dir = os.path.join(client_dir, platform, "docs", "architecture")
        if not os.path.isdir(arch_dir):
            continue
        for name in sorted(os.listdir(arch_dir)):
            if name.endswith(".md"):
                adrs.append((f"client/{platform}/", os.path.join(arch_dir, name)))

    found = False
    for platform_prefix, adr_path in adrs:
        rel_adr = os.path.relpath(adr_path, root)
        patterns = read_patterns(adr_path)
        if patterns is None:
            print(f"경고: {rel_adr} 에 `{HEADER}` 줄이 없어 찾을 수 없어요.", file=sys.stderr)
            continue
        compiled = [(p, glob_to_regex(p)) for p in patterns]
        for target in targets:
            if not target.startswith(platform_prefix):
                continue
            inside = target[len(platform_prefix):]
            hit = next((p for p, rx in compiled if rx.match(inside)), None)
            if hit:
                print(f"{rel_adr}  ← {target} ({hit})")
                found = True

    if not found:
        print("겹치는 ADR 없음")


if __name__ == "__main__":
    main()
