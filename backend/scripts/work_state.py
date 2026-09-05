#!/usr/bin/env python3
"""로컬 이슈 재개 기록. 테스트·Git 변경·게시를 실행하지 않는다. Python 3.11+, macOS/Linux."""

from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import uuid

try:
    import fcntl
except ImportError:
    fcntl = None

LIMIT = 8192
FIELDS = {"goal", "done", "remaining", "next", "scope", "decisions", "blockers", "links", "stack"}


def git(root, *arguments):
    environment = {key: value for key, value in os.environ.items() if not key.startswith("GIT_")}
    environment.update(GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_NOSYSTEM="1", GIT_OPTIONAL_LOCKS="0")
    return subprocess.check_output(["git", *arguments], cwd=root, env=environment, stderr=subprocess.PIPE)


def snapshot(root):
    root = root.resolve(strict=True)
    repository = Path(os.fsdecode(git(root, "rev-parse", "--show-toplevel")).strip()).resolve()
    relative = root.relative_to(repository)
    head = git(root, "rev-parse", "HEAD").decode().strip()
    branch = git(root, "rev-parse", "--abbrev-ref", "HEAD").decode().strip()
    index = git(root, "ls-files", "--stage", "-z", "--full-name", "--", str(root)).split(b"\0")
    others = git(root, "ls-files", "--others", "--exclude-standard", "-z", "--full-name", "--", str(root)).split(b"\0")

    def relevant(name):
        path = Path(os.fsdecode(name)).relative_to(relative)
        return (path.parts[:2] != (".harness", "state") and path.parts[0] not in ("build", ".gradle", "out")
                and not any(part in ("__pycache__", ".pytest_cache") for part in path.parts))

    entries = [entry for entry in index if entry and relevant(entry.split(b"\t", 1)[1])]
    names = {entry.split(b"\t", 1)[1] for entry in entries} | {name for name in others if name and relevant(name)}
    digest = hashlib.sha256()

    def add(value):
        digest.update(str(len(value)).encode() + b":" + value)

    add(head.encode())
    for entry in sorted(entries):
        add(entry)
    for name in sorted(names):
        add(name)
        path = repository / os.fsdecode(name)
        parent_link = next((parent for parent in path.parents if parent != repository and parent.is_symlink()), None)
        if parent_link:
            add(b"symlink-parent:" + os.fsencode(os.readlink(parent_link)))
            continue
        try:
            info = path.lstat()
        except FileNotFoundError:
            add(b"missing")
            continue
        add(str(stat.S_IMODE(info.st_mode)).encode())
        if stat.S_ISLNK(info.st_mode):
            add(b"symlink:" + os.fsencode(os.readlink(path)))
        elif stat.S_ISREG(info.st_mode):
            content = hashlib.sha256()
            descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
            with os.fdopen(descriptor, "rb") as handle:
                for chunk in iter(lambda: handle.read(65536), b""):
                    content.update(chunk)
            add(content.digest())
        else:
            raise ValueError("코드 상태에 지원하지 않는 파일 종류가 있습니다")
    return {"repository": {"root": str(repository), "backend": relative.as_posix()},
            "branch": branch, "head": head, "fingerprint": digest.hexdigest()}


def decode(data):
    if len(data) > LIMIT:
        raise ValueError("기록은 8KiB 이하여야 합니다; 내용을 자동으로 자르지 않습니다")
    value = json.loads(data)
    if not isinstance(value, dict):
        raise ValueError("JSON object가 필요합니다")
    return value


def read_json(path):
    descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    with os.fdopen(descriptor, "rb") as handle:
        return decode(handle.read(LIMIT + 1))


def directory(root, create=False):
    path = root.resolve(strict=True)
    for part in (".harness", "state"):
        path /= part
        if path.is_symlink():
            raise ValueError("재개 기록 경로에 심볼릭 링크를 사용할 수 없습니다")
        if create:
            path.mkdir(mode=0o700, exist_ok=True)
    return path


def issue_path(root, issue):
    if not isinstance(issue, int) or isinstance(issue, bool) or issue <= 0:
        raise ValueError("이슈 번호는 양의 정수여야 합니다")
    return directory(root) / f"issue-{issue}.json"


@contextmanager
def locked(root):
    if fcntl is None:
        raise ValueError("기록 변경에는 macOS/Linux의 파일 잠금이 필요합니다")
    folder = directory(root, create=True)
    descriptor = os.open(folder / ".lock", os.O_RDWR | os.O_CREAT | getattr(os, "O_NOFOLLOW", 0), 0o600)
    with os.fdopen(descriptor, "w") as handle:
        try:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError("다른 세션이 기록을 변경 중입니다; 다시 읽고 재시도하세요") from None
        yield


def record(root, issue, current):
    path = issue_path(root, issue)
    try:
        value = read_json(path)
    except FileNotFoundError:
        return None
    def valid_state(candidate):
        return (isinstance(candidate, dict) and set(candidate) == {"head", "fingerprint"}
                and all(isinstance(item, str) and item for item in candidate.values()))

    verification = value.get("verification")
    if (value.get("schema") != 1 or value.get("issue") != issue or value.get("repository") != current["repository"]
            or not isinstance(value.get("revision"), str) or not re.fullmatch(r"[a-f0-9]{32}", value["revision"])
            or not isinstance(value.get("branch"), str) or not valid_state(value.get("code_state"))
            or (verification is not None and (not isinstance(verification, dict) or not valid_state(verification.get("code_state"))))):
        raise ValueError("현재 backend·저장소·이슈의 유효한 기록이 아닙니다")
    validate_work(value.get("work"))
    return value


def load(root, issue):
    current = snapshot(root)
    value = record(root, issue, current)
    state = {key: current[key] for key in ("head", "fingerprint")}
    verification = value.get("verification") if value else None
    return {"revision": value["revision"] if value else "missing", "record": value, "current": current,
            "matches": {"record": bool(value and value["branch"] == current["branch"] and value.get("code_state") == state),
                        "verification": bool(verification and verification.get("code_state") == state)}}


def merge(previous, patch):
    result = dict(previous)
    for key, value in patch.items():
        result[key] = merge(result[key], value) if isinstance(value, dict) and isinstance(result.get(key), dict) else value
    return result


def validate_work(work):
    if not isinstance(work, dict) or set(work) - FIELDS:
        raise ValueError("work 필드를 확인하세요")
    if any(not isinstance(work.get(key), str) or not work[key].strip() for key in ("goal", "next")):
        raise ValueError("work.goal·next는 비어 있지 않은 문자열이어야 합니다")
    if any(not isinstance(work.get(key), list) or any(not isinstance(item, str) for item in work[key]) for key in ("done", "remaining")):
        raise ValueError("work.done·remaining은 문자열 배열이어야 합니다")


def expect(value, revision):
    if revision != (value["revision"] if value else "missing"):
        raise ValueError("낡은 revision입니다; 현재 기록을 읽고 변경을 합치세요")


def save(root, issue, expected_revision, payload, record_checks=False, checked_fingerprint=None):
    path = issue_path(root, issue)
    if not isinstance(payload, dict) or set(payload) - {"work", "checks"} or not isinstance(payload.get("work", {}), dict):
        raise ValueError("입력에는 work 부분 갱신과 선택적인 checks만 허용합니다")
    if record_checks != ("checks" in payload) or record_checks != bool(checked_fingerprint):
        raise ValueError("checks는 --record-checks와 --checked-fingerprint를 함께 지정해야 합니다")
    with locked(root):
        current = snapshot(root)
        old = record(root, issue, current)
        expect(old, expected_revision)
        numbers = re.findall(r"#([0-9]+)(?=[/-]|$)", current["branch"])
        if current["branch"] == "HEAD" or any(int(number) != issue for number in numbers) or (old and old["branch"] != current["branch"]):
            raise ValueError("현재 브랜치와 이슈·기존 기록 브랜치가 일치하지 않습니다")
        work = merge(old["work"] if old else {}, payload.get("work", {}))
        validate_work(work)
        now = datetime.now(timezone.utc).isoformat(timespec="seconds")
        code_state = {key: current[key] for key in ("head", "fingerprint")}
        verification = old.get("verification") if old else None
        if record_checks:
            if checked_fingerprint != current["fingerprint"]:
                raise ValueError("검사 전 코드와 현재 코드가 다릅니다; 검증 결과를 연결하지 않았습니다")
            checks = payload["checks"]
            if not isinstance(checks, list) or not checks or any(not isinstance(check, dict) or not {"command", "result"} <= set(check) <= {"command", "result", "log"}
                    or any(not isinstance(item, str) or not item.strip() for item in check.values()) for check in checks):
                raise ValueError("checks에는 방금 실행한 command·result 문자열을 기록하세요")
            verification = {"code_state": code_state, "recorded_at": now, "checks": checks}
        value = {"schema": 1, "issue": issue, "repository": current["repository"], "branch": current["branch"],
                 "revision": uuid.uuid4().hex, "updated_at": now, "code_state": code_state, "work": work, "verification": verification}
        data = (json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode()
        if len(data) > LIMIT:
            raise ValueError("기록은 8KiB 이하여야 합니다; 내용을 자동으로 자르지 않습니다")
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".write-", delete=False) as handle:
                temporary = handle.name
                handle.write(data)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, path)
        finally:
            if temporary and os.path.exists(temporary):
                os.unlink(temporary)
        return {"revision": value["revision"], "record": value}


def remove(root, issue, expected_revision):
    with locked(root):
        current = snapshot(root)
        value = record(root, issue, current)
        if value is None:
            raise ValueError("삭제할 기록이 없습니다")
        expect(value, expected_revision)
        issue_path(root, issue).unlink()
    return {"issue": issue, "removed": True, "revision": "missing"}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    for action in ("snapshot", "load", "save", "remove"):
        command = commands.add_parser(action)
        command.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
        if action != "snapshot":
            command.add_argument("--issue", type=int, required=True)
        if action in ("save", "remove"):
            command.add_argument("--expected-revision", required=True)
        if action == "save":
            command.add_argument("--input", required=True)
            command.add_argument("--record-checks", action="store_true")
            command.add_argument("--checked-fingerprint")
    arguments = parser.parse_args(argv)
    try:
        if arguments.action == "snapshot":
            result = snapshot(arguments.root)
        elif arguments.action == "load":
            result = load(arguments.root, arguments.issue)
        elif arguments.action == "remove":
            result = remove(arguments.root, arguments.issue, arguments.expected_revision)
        else:
            payload = decode(sys.stdin.buffer.read(LIMIT + 1)) if arguments.input == "-" else read_json(arguments.input)
            result = save(arguments.root, arguments.issue, arguments.expected_revision, payload, arguments.record_checks, arguments.checked_fingerprint)
        print(json.dumps(result, ensure_ascii=False))
        return 0
    except (OSError, ValueError, TypeError, subprocess.SubprocessError) as exc:
        print(json.dumps({"error": str(exc)}, ensure_ascii=False))
        return 1


if __name__ == "__main__":
    sys.exit(main())
