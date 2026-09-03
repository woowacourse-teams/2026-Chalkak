#!/usr/bin/env python3
"""백엔드 하네스의 구조만 검사한다. 파일 수정·네트워크·AI 호출은 하지 않는다.

Python 3.10+ 사용. 최초 준비 예시(macOS/Linux, backend 디렉터리에서):
  python3 -m venv build/harness-venv
  source build/harness-venv/bin/activate
  python3 -m pip install -r scripts/requirements-harness.txt
같은 가상환경을 활성화한 뒤 실행(Windows에서는 Scripts/activate 사용):
  python3 scripts/check_harness.py
  python3 -B -m unittest discover -s scripts -p 'check_harness_test.py'

검사: 팀 공통 스킬 메타데이터, 대응 파일, 운영·테스트 paths, 로컬 Markdown
링크, 루트 문서의 스킬·규칙 참조. 코드 예제·외부 URL·앵커의 내용, 플랫폼
확장 필드 전체, 양쪽 문장의 의미와 실제 AI 행동은 검사하지 않는다.
종료 코드: 0 통과(경고 포함), 1 구조 오류, 2 의존성 부족으로 미실행.
기준: https://agentskills.io/specification
      https://code.claude.com/docs/en/memory#path-specific-rules
"""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit

try:
    import yaml
    from markdown_it import MarkdownIt
except ImportError:
    yaml = None
    MarkdownIt = None


RULE_PATHS = {
    "main-code": "src/main/java/**/*.java",
    "test-code": "src/test/java/**/*.java",
}
NAME = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*")


def parse_metadata(header: str) -> dict:
    """SafeLoader를 사용하되 조용히 덮어써지는 중복 키도 오류로 처리한다."""
    class UniqueKeysLoader(yaml.SafeLoader):
        def construct_mapping(self, node, deep=False):
            seen = set()
            for key_node, _ in node.value:
                if key_node.tag == "tag:yaml.org,2002:merge":
                    continue
                key = self.construct_object(key_node, deep=deep)
                try:
                    if key in seen:
                        raise ValueError(f"중복 YAML 키: {key}")
                    seen.add(key)
                except TypeError as exc:
                    raise ValueError("YAML 객체 키에 목록·객체를 사용할 수 없습니다") from exc
            return super().construct_mapping(node, deep=deep)

    value = yaml.load(header, Loader=UniqueKeysLoader)
    if not isinstance(value, dict):
        raise ValueError("메타데이터는 YAML 객체여야 합니다")
    if not all(isinstance(key, str) for key in value):
        raise ValueError("메타데이터 필드 이름은 문자열이어야 합니다")
    return value


def check(root: Path) -> tuple[list[str], list[str]]:
    errors, warnings = [], []
    documents = {}

    def report(path: Path, message: str):
        errors.append(f"{path.relative_to(root)}: {message}")

    def read(path: Path) -> str:
        if path not in documents:
            try:
                documents[path] = path.read_text(encoding="utf-8")
            except (OSError, UnicodeError) as exc:
                report(path, f"파일을 읽을 수 없습니다 ({exc.__class__.__name__})")
                documents[path] = ""
        return documents[path]

    def metadata(path: Path) -> dict:
        text = read(path)
        lines = text.splitlines()
        if not lines or lines[0] != "---":
            report(path, "첫 줄에서 YAML frontmatter가 시작해야 합니다")
            return {}
        try:
            end = lines.index("---", 1)
            data = parse_metadata("\n".join(lines[1:end]))
        except (ValueError, yaml.YAMLError) as exc:
            report(path, f"잘못된 YAML frontmatter: {str(exc).splitlines()[0]}")
            return {}
        body = "\n".join(lines[end + 1:])
        documents[path] = body
        if not body.strip():
            report(path, "규칙 본문이 비어 있습니다")
        if path.name == "SKILL.md" and len(lines[end + 1:]) >= 500:
            warnings.append(f"{path.relative_to(root)}: 본문이 500줄 이상입니다. 분리를 검토하세요")
        return data

    platforms = {}
    for platform in (".agents", ".claude"):
        directory = root / platform / "skills"
        skills, names = {}, set()
        if not directory.is_dir():
            report(directory, "스킬 디렉터리가 없습니다")
        else:
            for folder in sorted(directory.iterdir()):
                if not folder.is_dir() or folder.name.startswith("."):
                    continue
                path = folder / "SKILL.md"
                skills[folder.name] = path
                data = metadata(path)
                name, description = data.get("name"), data.get("description")
                # Claude에서는 선택 사항이지만 양쪽 파일 관리를 위한 팀 공통 규칙이다.
                if not isinstance(name, str) or not 1 <= len(name) <= 64 or not NAME.fullmatch(name):
                    report(path, "name은 1~64자의 소문자·숫자·단일 하이픈으로 작성해야 합니다")
                elif name != folder.name:
                    report(path, f"name({name})이 폴더명({folder.name})과 다릅니다")
                if isinstance(name, str):
                    if name in names:
                        report(path, f"중복 스킬 이름: {name}")
                    names.add(name)
                if not isinstance(description, str) or not description.strip() or len(description) > 1024:
                    report(path, "description은 비어 있지 않은 1~1024자 문자열이어야 합니다")
            for path in sorted(directory.rglob("*.md")):
                read(path)
        platforms[platform] = skills

    rules = root / ".claude/rules"
    for name, expected in RULE_PATHS.items():
        path = rules / f"{name}.md"
        data = metadata(path)
        if data.get("paths") != [expected]:
            report(path, f"팀 경로 규칙의 paths는 [{expected}]여야 합니다")
        if name in platforms[".claude"]:
            report(platforms[".claude"][name], "동일한 Claude 경로 규칙과 중복됩니다")
    for path in sorted(rules.rglob("*.md")):
        read(path)

    codex = set(platforms[".agents"])
    claude = set(platforms[".claude"]) | set(RULE_PATHS)
    for name in sorted(codex ^ claude):
        missing = ".claude" if name in codex else ".agents"
        report(root / missing / "skills" / name / "SKILL.md", "대응 스킬이 없습니다")

    for name in ("AGENTS.md", "CLAUDE.md"):
        path = root / name
        if not read(path).strip():
            report(path, "루트 안내문이 비어 있습니다")

    markdown = MarkdownIt("commonmark").enable("table")
    for path, text in sorted(documents.items()):
        for token in markdown.parse(text):
            children = token.children or []
            for index, child in enumerate(children):
                if child.type in ("link_open", "image"):
                    target = child.attrGet("href" if child.type == "link_open" else "src")
                    try:
                        url = urlsplit(target)
                    except ValueError:
                        report(path, f"잘못된 링크 주소: {target}")
                        continue
                    if not url.scheme and not url.netloc and url.path:
                        if not (path.parent / unquote(url.path)).exists():
                            report(path, f"로컬 링크 대상이 없습니다: {target}")
                if path.parent != root or child.type != "code_inline":
                    continue
                value = child.content
                # 유효한 이름만 찾으면 오타를 놓친다. 대문자 환경변수 표기는 제외한다.
                if (path.name == "AGENTS.md" and re.fullmatch(r"\$[\w-]+", value)
                        and not re.fullmatch(r"\$[A-Z][A-Z0-9_]*", value)):
                    if value[1:] not in platforms[".agents"]:
                        report(path, f"존재하지 않는 스킬 참조: {value}")
                if path.name == "CLAUDE.md":
                    following = children[index + 1].content if index + 1 < len(children) else ""
                    if re.match(r"\s+Skill\b", following):
                        if value not in platforms[".claude"]:
                            report(path, f"존재하지 않는 스킬 참조: {value}")
                    if value.startswith(".claude/rules/") and not (root / value).is_file():
                        report(path, f"존재하지 않는 경로 규칙 참조: {value}")
    return sorted(set(errors)), sorted(set(warnings))


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1], help="검사할 backend 경로")
    args = parser.parse_args(argv)
    if yaml is None or MarkdownIt is None:
        print("미실행: 검사 의존성이 없습니다. 가상환경에 아래 파일을 설치한 뒤 다시 실행하세요.", file=sys.stderr)
        print(f"python3 -m pip install -r {Path(__file__).with_name('requirements-harness.txt')}", file=sys.stderr)
        return 2
    errors, warnings = check(args.root.resolve())
    for message in warnings:
        print(f"경고: {message}")
    for message in errors:
        print(f"오류: {message}", file=sys.stderr)
    if errors:
        print(f"하네스 구조 검사 실패: 오류 {len(errors)}개", file=sys.stderr)
        return 1
    print(f"하네스 구조 검사 통과 (경고 {len(warnings)}개; AI 동작 평가는 별도)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
