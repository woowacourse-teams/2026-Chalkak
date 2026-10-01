#!/usr/bin/env python3
"""Shared harness snapshots. Python 3.10+, Git, macOS/Linux; no AI calls."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import plistlib
import re
from urllib.parse import unquote, urlsplit
import shutil
import subprocess
import sys
import tarfile
import tempfile

ENTRY = ("AGENTS.md", "CLAUDE.md")
PREFIXES = (".agents/skills/business-rules/", ".claude/skills/business-rules/", "docs/business-rules/")
REFERENCES = ("backend/docs/interviews/캘린더-기록-연월-조회-방식.md",)
REQUIRED = {*ENTRY, *REFERENCES, *(p + "SKILL.md" for p in PREFIXES[:2]), "docs/business-rules/README.md"}
MARKER = "<!-- chalkak-shared-harness:begin -->"
END = "<!-- chalkak-shared-harness:end -->"
LIMIT = 10_000_000


def digest(data):
    return hashlib.sha256(data).hexdigest()


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode()


def read_json(path):
    return json.loads(path.read_text())


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as stream:
        temporary = Path(stream.name)
        stream.write(data)
    os.replace(temporary, path)


def git(*args, cwd=None):
    env = dict(os.environ, GIT_TERMINAL_PROMPT="0", GIT_SSH_COMMAND="ssh -oBatchMode=yes -oConnectTimeout=10")
    result = subprocess.run(["git", *args], cwd=cwd, env=env, capture_output=True, timeout=60)
    if result.returncode:
        raise ValueError("Git 명령 실패: " + result.stderr.decode(errors="replace").strip())
    return result.stdout


def allowed(name):
    path = PurePosixPath(name)
    return (not path.is_absolute() and ".." not in path.parts and name == path.as_posix()
            and (name in ENTRY or name in REFERENCES or any(name.startswith(prefix) for prefix in PREFIXES)))


@contextmanager
def lock(root):
    root.mkdir(parents=True, exist_ok=True)
    import fcntl
    with (root / ".lock").open("a") as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            raise ValueError("다른 하네스 작업이 실행 중입니다") from exc
        yield


def verify(root):
    manifest = read_json(root / "manifest.json")
    if not isinstance(manifest, dict) or not isinstance(manifest.get("files"), dict):
        raise ValueError("잘못된 배포 manifest 형식")
    files = manifest["files"]
    if (manifest.get("schema") != 1 or not REQUIRED <= files.keys()
            or not all(isinstance(p, str) and allowed(p) for p in files)
            or not re.fullmatch(r"[0-9a-f]{40,64}", str(manifest.get("source_commit", "")))):
        raise ValueError("잘못된 배포 manifest 또는 필수 파일 누락")
    actual = {}
    size = 0
    for path in root.rglob("*"):
        if path.is_symlink():
            raise ValueError("배포 파일에 심볼릭 링크를 사용할 수 없습니다")
        if path.is_file() and path.relative_to(root).as_posix() != "manifest.json":
            data = path.read_bytes()
            size += len(data)
            actual[path.relative_to(root).as_posix()] = digest(data)
    if size > LIMIT or actual != files:
        raise ValueError("배포 파일의 해시·목록이 다릅니다. 로컬 수정 또는 불완전한 배포입니다")
    if manifest.get("version") != digest(json_bytes(files)):
        raise ValueError("배포 버전 해시가 다릅니다")
    for name in files:
        if not name.endswith(".md"):
            continue
        for link in re.findall(r"\[[^\]]*\]\(([^)]+)\)", (root / name).read_text()):
            url = urlsplit(link)
            if url.scheme or url.netloc or not url.path:
                continue
            target = (root / name).parent / unquote(url.path)
            if not target.resolve().is_relative_to(root.resolve()) or not target.is_file():
                raise ValueError("배포에 포함되지 않은 로컬 문서 참조: " + name + " → " + link)
    return manifest


def protect_snapshot(root):
    """Prevent accidental edits/replacement inside a validated version directory."""
    verify(root)
    for path in root.rglob("*"):
        path.chmod(0o555 if path.is_dir() else 0o444)
    root.chmod(0o555)


def export(source, output):
    source = source.resolve()
    if output.exists():
        raise ValueError("export 대상은 존재하지 않는 새 디렉터리여야 합니다")
    source_commit = git("rev-parse", "HEAD", cwd=source).decode().strip()
    paths = [source / name for name in git("ls-tree", "-r", "--name-only", "-z", source_commit, "--", *ENTRY, *PREFIXES, *REFERENCES, cwd=source).decode().split("\0") if name]
    # A publication must come from a reviewed commit, never silently include work in progress.
    dirty = git("status", "--porcelain", "--", *ENTRY, *PREFIXES, *REFERENCES, cwd=source)
    if dirty.strip():
        raise ValueError("공통 하네스에 미커밋 변경이 있습니다. 검토·커밋 후 export하세요")
    with tempfile.TemporaryDirectory(dir=output.parent) as temporary:
        stage = Path(temporary) / "package"
        stage.mkdir()
        files = {}
        for path in sorted(paths):
            if path.is_symlink() or not path.resolve().is_relative_to(source):
                raise ValueError("공통 하네스 원본에 심볼릭 링크를 사용할 수 없습니다")
            name = path.relative_to(source).as_posix()
            data = git("show", source_commit + ":" + name, cwd=source)
            write(stage / name, data)
            files[name] = digest(data)
        manifest = {"schema": 1, "source_commit": source_commit,
                    "version": digest(json_bytes(files)), "files": files}
        write(stage / "manifest.json", json_bytes(manifest))
        verify(stage)
        shutil.move(stage, output)
    return manifest


def sync(cache, remote=None, branch=None):
    with lock(cache):
        config_path = cache / "config.json"
        config = read_json(config_path) if config_path.exists() else {}
        if config and ((remote and remote != config["remote"]) or (branch and branch != config["branch"])):
            raise ValueError("다른 배포 원본에는 새 cache 경로를 사용하세요")
        config = config or {"remote": remote, "branch": branch}
        if not config.get("remote") or not config.get("branch"):
            raise ValueError("최초 sync에는 --remote와 --branch가 필요합니다")
        git("check-ref-format", "refs/heads/" + config["branch"])
        write(config_path, json_bytes(config))
        try:
            with tempfile.TemporaryDirectory(dir=cache) as temporary:
                checkout = Path(temporary) / "repository"
                git("clone", "--quiet", "--depth", "1", "--single-branch", "--branch", config["branch"], "--", config["remote"], str(checkout))
                release = git("rev-parse", "HEAD", cwd=checkout).decode().strip()
                archive = git("archive", "HEAD", cwd=checkout)
                stage = Path(temporary) / "package"
                stage.mkdir()
                total = 0
                with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
                    for item in tar:
                        if item.isdir():
                            continue
                        if not item.isfile() or (item.name != "manifest.json" and not allowed(item.name)):
                            raise ValueError("배포 브랜치에 허용되지 않은 파일이 있습니다: " + item.name)
                        total += item.size
                        if total > LIMIT:
                            raise ValueError("배포 크기 한도 초과")
                        write(stage / item.name, tar.extractfile(item).read())
                manifest = verify(stage)
                versions = cache / "versions"
                versions.mkdir(exist_ok=True)
                destination = versions / manifest["version"]
                if destination.exists():
                    verify(destination)
                else:
                    shutil.move(stage, destination)
                protect_snapshot(destination)
                result = {"version": manifest["version"], "release_commit": release,
                          "checked_at": datetime.now(timezone.utc).isoformat(), "ok": True}
                write(cache / "latest.json", json_bytes(result))
                write(cache / "check.json", json_bytes(result))
                return result
        except (OSError, ValueError, subprocess.SubprocessError) as exc:
            write(cache / "check.json", json_bytes({"ok": False, "checked_at": datetime.now(timezone.utc).isoformat(), "error": str(exc)}))
            raise


def local_path(project, name):
    path = project / name
    for parent in [path, *path.parents]:
        if parent == project:
            break
        if parent.is_symlink():
            raise ValueError("설치 경로가 심볼릭 링크입니다: " + str(parent))
    return path


def instruction(name):
    return ("\n\n" + MARKER + "\n## Chalkak 공통 하네스\n"
            "- 서비스 정책 관련 작업의 시작·재개 전에 `python3 .chalkak-harness/manage.py status --project . --check`를 저장소 루트에서 실행한다.\n"
            "- `.chalkak-harness/active/" + name + "`와 해당 버전의 `business-rules` 스킬을 읽는다. 관련 규칙의 실제 읽기·적용 내용과 버전 변경 영향을 짧게 알린다.\n"
            "- 배포본의 문서 경로는 저장소 원본 대신 `.chalkak-harness/active/docs/business-rules/`를 사용한다. 설치된 스킬·참조 문서는 배포본 경로를 기준으로 읽는다.\n"
            "- 원격 조회 실패를 최신으로 표시하지 않는다. `status`는 내려받기만 하며 적용 버전을 바꾸지 않는다. 새 버전 적용은 작업을 정리하고 `apply`한 뒤 새 AI 세션에서 시작한다.\n"
            "- 배포본은 읽기 전용이다. 플랫폼 고유 지침은 유지하며 정책 충돌은 백엔드 확인 대상으로 알린다.\n" + END + "\n").encode()


def activate(project, cache=None):
    project = project.resolve()
    if (project / ".chalkak-harness/automatic.json").exists():
        raise ValueError("자동 모드에서는 새 세션이 버전을 선택합니다. install/apply를 반복하지 마세요")
    if Path(git("rev-parse", "--show-toplevel", cwd=project).decode().strip()).resolve() != project:
        raise ValueError("--project는 Git 저장소 최상위 경로여야 합니다")
    local = local_path(project, ".chalkak-harness")
    with lock(local):
        state_path = local / "install.json"
        state = read_json(state_path) if state_path.exists() else None
        if state and cache and str(cache) != state["cache"]:
            raise ValueError("기존 설치의 cache와 다릅니다")
        cache = Path(state["cache"]) if state else cache
        if cache is None:
            raise ValueError("최초 install에는 --cache가 필요합니다")
        latest = read_json(cache / "latest.json")
        package = cache / "versions" / latest["version"]
        manifest = verify(package)
        entries = {name: local_path(project, name) for name in ENTRY}
        for prefix in PREFIXES[:2]:
            local_path(project, str(PurePosixPath(prefix).parent))
        links = {p.rstrip("/"): project / p.rstrip("/") for p in PREFIXES[:2]}
        if state:
            verify(local / "active")
            for name, path in entries.items():
                if path.read_bytes().count(instruction(name)) != 1:
                    raise ValueError("관리 중인 진입 지침이 수정되었습니다: " + name)
            for name, path in links.items():
                if not path.is_symlink() or os.readlink(path) != state["links"][name]:
                    raise ValueError("관리 중인 스킬 연결이 수정되었습니다: " + name)
        else:
            for name, path in entries.items():
                if path.exists() and (MARKER.encode() in path.read_bytes() or END.encode() in path.read_bytes()):
                    raise ValueError("기존 설치 흔적을 먼저 확인하세요: " + name)
            for name in links:
                path = local_path(project, name)
                if path.exists():
                    raise ValueError("기존 business-rules 스킬을 보존하기 위해 설치를 중단합니다: " + name)
        versions = local / "versions"
        versions.mkdir(exist_ok=True)
        version = manifest["version"]
        destination = versions / version
        if destination.exists():
            verify(destination)
        else:
            with tempfile.TemporaryDirectory(dir=local) as temporary:
                staged = Path(temporary) / "package"
                shutil.copytree(package, staged)
                verify(staged)
                # macOS requires a writable root when relocating a copied read-only directory.
                staged.chmod(0o700)
                shutil.move(staged, destination)
        protect_snapshot(destination)
        pointer = local / "active.next"
        pointer.unlink(missing_ok=True)
        pointer.symlink_to(Path("versions") / version, target_is_directory=True)
        if not state:
            if (local / "active").exists() or (local / "active").is_symlink():
                raise ValueError("설치 기록 없는 active 연결이 있습니다. 기존 설치를 확인하세요")
            state = {"cache": str(cache), "links": {}, "created_entries": []}
            backups = {name: path.read_bytes() if path.exists() else None for name, path in entries.items()}
            made_links = []
            try:
                for name, path in entries.items():
                    if backups[name] is None:
                        state["created_entries"].append(name)
                    write(path, (backups[name] or b"") + instruction(name))
                for name, path in links.items():
                    path.parent.mkdir(parents=True, exist_ok=True)
                    target = os.path.relpath(local / "active" / name, path.parent)
                    path.symlink_to(target, target_is_directory=True)
                    made_links.append(path)
                    state["links"][name] = target
                write(local / "manage.py", Path(__file__).read_bytes())
                os.replace(pointer, local / "active")
                write(state_path, json_bytes(state))
            except OSError:
                (local / "active").unlink(missing_ok=True)
                state_path.unlink(missing_ok=True)
                for path in made_links:
                    path.unlink()
                for name, path in entries.items():
                    if backups[name] is None:
                        path.unlink(missing_ok=True)
                    else:
                        write(path, backups[name])
                raise
        else:
            os.replace(pointer, local / "active")
        return {"installed": version, "source_commit": manifest["source_commit"],
                "notice": "AI 세션을 새로 시작하세요. 설치 파일은 로컬 전용이며 커밋하지 마세요."}


def status(project, check=False):
    if (project / ".chalkak-harness/automatic.json").exists():
        import automatic
        return automatic.status(project, check)
    local = local_path(project, ".chalkak-harness")
    state = read_json(local / "install.json")
    for name in ENTRY:
        if local_path(project, name).read_bytes().count(instruction(name)) != 1:
            raise ValueError("관리 중인 진입 지침이 수정되었습니다: " + name)
    for name, target in state["links"].items():
        local_path(project, str(PurePosixPath(name).parent))
        path = project / name
        if not path.is_symlink() or os.readlink(path) != target:
            raise ValueError("관리 중인 스킬 연결이 수정되었습니다: " + name)
    cache = Path(state["cache"])
    attempt_error = None
    if check:
        try:
            sync(cache)
        except (OSError, ValueError, subprocess.SubprocessError) as exc:
            # A lock/config failure can happen before sync writes check.json.
            attempt_error = {"ok": False, "checked_at": datetime.now(timezone.utc).isoformat(), "error": str(exc)}
    installed = verify(local / "active")
    latest = read_json(cache / "latest.json")
    available = verify(cache / "versions" / latest["version"])
    changed = [name for name in sorted(installed["files"].keys() | available["files"].keys())
               if installed["files"].get(name) != available["files"].get(name)]
    return {"installed": installed["version"], "available": available["version"],
            "source_commit": installed["source_commit"], "remote_check": attempt_error or read_json(cache / "check.json"),
            "available_path": str(cache / "versions" / latest["version"]),
            "changed_files": changed, "notice": "변경 파일은 정책 의미 변경·서버 배포의 증거가 아닙니다. 관련 문서를 대조하세요."}


def uninstall(project):
    if (project / ".chalkak-harness/automatic.json").exists():
        import automatic
        return automatic.uninstall(project)
    local = local_path(project, ".chalkak-harness")
    with lock(local):
        state = read_json(local / "install.json")
        verify(local / "active")
        entries = {name: local_path(project, name) for name in ENTRY}
        for name, path in entries.items():
            if path.read_bytes().count(instruction(name)) != 1:
                raise ValueError("진입 지침의 설치 구간이 수정되어 자동 제거할 수 없습니다")
        for name, target in state["links"].items():
            local_path(project, str(PurePosixPath(name).parent))
            path = project / name
            if not path.is_symlink() or os.readlink(path) != target:
                raise ValueError("스킬 연결이 수정되어 자동 제거할 수 없습니다")
        for name, path in entries.items():
            remaining = path.read_bytes().replace(instruction(name), b"", 1)
            if not remaining and name in state["created_entries"]:
                path.unlink()
            else:
                write(path, remaining)
        for name in state["links"]:
            (project / name).unlink()
        # Keep downloaded snapshots for comparison/recovery; they are no longer connected to AI.
        (local / "active").unlink()
        (local / "install.json").unlink()
    return {"removed": True, "notice": "공유 cache와 로컬 versions는 보존했습니다. 진입 지침의 기존 내용도 보존했습니다."}


def login_plist(cache, output):
    if output.exists():
        raise ValueError("기존 plist를 덮어쓰지 않습니다")
    read_json(cache / "config.json")
    helper = cache / "manage.py"
    write(helper, Path(__file__).read_bytes())
    label = "com.chalkak.harness." + digest(str(cache).encode())[:12]
    value = {"Label": label, "ProgramArguments": [sys.executable, str(helper), "sync", "--cache", str(cache)],
             "RunAtLoad": True, "StandardOutPath": str(cache / "login.log"), "StandardErrorPath": str(cache / "login-error.log")}
    write(output, plistlib.dumps(value))
    return {"plist": str(output), "label": label, "notice": "plist만 생성했습니다. launchctl 등록은 사용법에 따라 별도로 실행하세요."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    exp = sub.add_parser("export")
    exp.add_argument("--source", type=Path, required=True)
    exp.add_argument("--output", type=Path, required=True)
    syn = sub.add_parser("sync")
    syn.add_argument("--cache", type=Path, required=True)
    syn.add_argument("--remote")
    syn.add_argument("--branch")
    for command in ("install", "apply", "status", "uninstall"):
        child = sub.add_parser(command)
        child.add_argument("--project", type=Path, required=True)
        if command == "install":
            child.add_argument("--cache", type=Path, required=True)
        if command == "status":
            child.add_argument("--check", action="store_true")
    login = sub.add_parser("login-plist")
    login.add_argument("--cache", type=Path, required=True)
    login.add_argument("--output", type=Path, required=True)
    setup = sub.add_parser("setup", help="macOS 자동 다운로드와 Codex·Claude 세션 연결을 한 번에 설치")
    setup.add_argument("--project", type=Path, default=Path.cwd())
    session = sub.add_parser("session", help="설치된 SessionStart hook 전용")
    session.add_argument("--provider", choices=("codex", "claude"), required=True)
    session.add_argument("--runtime", type=Path, required=True)
    args = parser.parse_args()
    for key, value in vars(args).items():
        if isinstance(value, Path):
            setattr(args, key, value.expanduser().resolve())
    try:
        if args.command == "setup":
            import automatic
            result = automatic.setup(args.project)
        elif args.command == "session":
            import automatic
            result = automatic.session(args.runtime, args.provider, json.load(sys.stdin))
        elif args.command == "export":
            result = export(args.source, args.output)
        elif args.command == "sync":
            result = sync(args.cache, args.remote, args.branch)
        elif args.command in ("install", "apply"):
            result = activate(args.project, getattr(args, "cache", None))
        elif args.command == "status":
            result = status(args.project, args.check)
        elif args.command == "uninstall":
            result = uninstall(args.project)
        else:
            result = login_plist(args.cache, args.output)
        print(json_bytes(result).decode(), end="")
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as exc:
        if args.command == "session":
            print(json_bytes({"continue": False, "stopReason": "Chalkak 정책 연결 실패: " + str(exc),
                              "systemMessage": "Chalkak 정책 연결 실패. 설치와 hook 설정을 확인하세요: " + str(exc),
                              "hookSpecificOutput": {"hookEventName": "SessionStart", "additionalContext":
                                  "Chalkak 공통 정책 연결에 실패했습니다. 정책 의존 작업 전에 설치를 확인하고 최신 정책을 읽었다고 표시하지 마세요. 오류: " + str(exc)}}).decode())
            return 0
        print("하네스 작업 실패: " + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
