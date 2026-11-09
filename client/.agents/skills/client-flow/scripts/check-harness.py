#!/usr/bin/env python3
"""하네스 문서 검사: 클라이언트 하네스 파일이 서로 맞게 이어져 있는지 본다.

하네스 작업(client/.agents/ 등을 고치는 작업)은 돌릴 유닛 테스트가 없어서,
client-tdd의 "하네스 작업일 때"가 Gradle·xcodebuild 대신 이 검사를 돌린다.

보는 것
  1. 상대 링크: client/.agents 아래 .md 파일의 [글](상대/경로)가 실제 파일을 가리키는지
     (http 링크, #앵커만 있는 링크, <자리표시> 링크, 코드 블록 안은 보지 않는다)
  2. 스킬 이름: .agents/skills/<폴더>/SKILL.md 머리말의 name이 폴더 이름과 같은지
  3. Claude 링크: .claude/skills/<이름>이 .agents/skills/<이름>을 가리키는 심볼릭 링크인지, 깨진 링크가 없는지
  4. 스크립트 문법: .agents 아래 .py는 컴파일, .sh는 bash -n
  5. Codex 에이전트 정의: .codex/agents/*.toml에 name·description·developer_instructions가 있고 name이 파일 이름과 같은지

입력: (선택) --root <client 폴더>. 없으면 git 맨 위의 client/.
출력: 문제마다 "FAIL <경로>: <이유>" 한 줄, 마지막에 요약 한 줄.
종료 코드: 문제가 없으면 0, 하나라도 있으면 1.

예) python3 client/.agents/skills/client-flow/scripts/check-harness.py
    → OK: 링크 42개, 스킬 8개, 스크립트 4개, Codex 정의 1개
"""
import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
FENCE = re.compile(r"^\s*```")
TOML_KEY = re.compile(r"^(\w+)\s*=\s*(.*)$", re.M)


def client_root(arg):
    if arg:
        return Path(arg).resolve()
    top = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True)
    return Path(top.stdout.strip()) / "client"


def links_outside_code(text):
    """코드 블록(```) 밖의 줄에서만 링크 대상을 꺼낸다. 코드 블록 안은 예시라 실제 파일이 아닐 수 있다."""
    in_code = False
    for line in text.splitlines():
        if FENCE.match(line):
            in_code = not in_code
            continue
        if in_code:
            continue
        line = re.sub(r"`[^`]*`", "", line)  # 인라인 코드 안의 [a](b)도 예시로 본다
        for target in LINK.findall(line):
            yield target


def check_links(root, fails):
    count = 0
    for md in sorted((root / ".agents").rglob("*.md")):
        for target in links_outside_code(md.read_text(encoding="utf-8")):
            if re.match(r"^[a-z]+:", target) or target.startswith("#") or "<" in target or "$" in target:
                continue
            count += 1
            path = target.split("#", 1)[0]
            if not (md.parent / path).exists():
                fails.append(f"FAIL {md.relative_to(root)}: 없는 파일로 링크 ({target})")
    return count


def check_skills(root, fails):
    skills = root / ".agents" / "skills"
    claude = root / ".claude" / "skills"
    names = []
    for skill_md in sorted(skills.glob("*/SKILL.md")):
        folder = skill_md.parent.name
        names.append(folder)
        head = skill_md.read_text(encoding="utf-8").split("---")
        m = re.search(r"^name:\s*(\S+)", head[1] if len(head) > 2 else "", re.M)
        if not m:
            fails.append(f"FAIL {skill_md.relative_to(root)}: 머리말에 name이 없음")
        elif m.group(1) != folder:
            fails.append(f"FAIL {skill_md.relative_to(root)}: name '{m.group(1)}'이 폴더 이름 '{folder}'과 다름")
        link = claude / folder
        if not link.is_symlink():
            fails.append(f"FAIL .claude/skills/{folder}: 심볼릭 링크가 없음 (Claude Code가 이 스킬을 못 찾음)")
        elif link.resolve() != skill_md.parent.resolve():
            fails.append(f"FAIL .claude/skills/{folder}: {os.readlink(link)}를 가리킴 (.agents/skills/{folder}이어야 함)")
    if claude.exists():
        for link in sorted(claude.iterdir()):
            if link.is_symlink() and not link.exists():
                fails.append(f"FAIL .claude/skills/{link.name}: 깨진 링크 ({os.readlink(link)})")
    return len(names)


def check_scripts(root, fails):
    count = 0
    for py in sorted((root / ".agents").rglob("*.py")):
        count += 1
        try:
            compile(py.read_text(encoding="utf-8"), str(py), "exec")  # 파일을 쓰지 않고 문법만 본다
        except SyntaxError as e:
            fails.append(f"FAIL {py.relative_to(root)}: 파이썬 문법 오류 ({e.lineno}번 줄 {e.msg})")
    for sh in sorted((root / ".agents").rglob("*.sh")):
        count += 1
        r = subprocess.run(["bash", "-n", str(sh)], capture_output=True, text=True)
        if r.returncode != 0:
            fails.append(f"FAIL {sh.relative_to(root)}: 셸 문법 오류 ({r.stderr.strip().splitlines()[-1]})")
    return count


def check_codex_agents(root, fails):
    count = 0
    for toml in sorted((root / ".codex" / "agents").glob("*.toml")):
        count += 1
        keys = dict(TOML_KEY.findall(toml.read_text(encoding="utf-8")))
        for key in ("name", "description", "developer_instructions"):
            if key not in keys:
                fails.append(f"FAIL {toml.relative_to(root)}: {key}가 없음")
        name = keys.get("name", "").strip().strip('"')
        if name and name != toml.stem:
            fails.append(f"FAIL {toml.relative_to(root)}: name '{name}'이 파일 이름 '{toml.stem}'과 다름")
    return count


def main():
    parser = argparse.ArgumentParser(description="클라이언트 하네스 문서 검사")
    parser.add_argument("--root", help="client 폴더 (기본: git 맨 위의 client/)")
    root = client_root(parser.parse_args().root)
    fails = []
    n_links = check_links(root, fails)
    n_skills = check_skills(root, fails)
    n_scripts = check_scripts(root, fails)
    n_codex = check_codex_agents(root, fails)
    for f in fails:
        print(f)
    summary = f"링크 {n_links}개, 스킬 {n_skills}개, 스크립트 {n_scripts}개, Codex 정의 {n_codex}개"
    print(f"{'FAIL' if fails else 'OK'}: {summary}" + (f", 문제 {len(fails)}개" if fails else ""))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
