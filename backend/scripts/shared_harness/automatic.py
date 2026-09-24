"""One-time macOS setup and immutable per-session policy bindings. No AI calls."""
from __future__ import annotations

from contextlib import contextmanager
import os
from pathlib import Path
import plistlib
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time

import manage as core

REMOTE = "git@github.com:woowacourse-teams/2026-Chalkak.git"
BRANCH = "harness/shared"
LABEL = "com.chalkak.shared-harness.auto"
BEGIN = "<!-- chalkak-automatic-harness:begin -->"
END = "<!-- chalkak-automatic-harness:end -->"
PROVIDERS = ("codex", "claude")


def runtime(home):
    return home / ".local/share/chalkak-harness/automatic"


@contextmanager
def lock(root):
    """Briefly queue simultaneous Codex/Claude startup instead of failing immediately."""
    import fcntl
    root.mkdir(parents=True, exist_ok=True)
    with (root / ".automatic.lock").open("a") as stream:
        deadline = time.monotonic() + 20
        while True:
            try:
                fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    raise ValueError("다른 하네스 세션 준비가 지연되고 있습니다. 잠시 후 새 세션을 시작하세요")
                time.sleep(0.05)
        yield


def identity(project):
    project = Path(core.git("rev-parse", "--show-toplevel", cwd=project).decode().strip()).resolve()
    common = core.git("rev-parse", "--git-common-dir", cwd=project).decode().strip()
    return project, str((project / common).resolve())


def entry():
    return ("\n\n" + BEGIN + "\n## Chalkak 공통 정책 자동 갱신\n"
            "- SessionStart hook이 알려준 이 세션의 고정 버전 경로에서 공통 지침과 business-rules 스킬, 관련 도메인 규칙을 읽는다.\n"
            "- 다른 세션의 버전이나 active/latest 경로를 대신 읽지 않는다. 읽은 규칙과 이번 작업의 적용 내용을 짧게 설명한다.\n"
            "- 고정 버전 안내가 없으면 자동 연결 미확인으로 알리고 정책 의존 작업 전에 설치·hook 신뢰 상태를 확인한다. 최신 확인 실패도 숨기지 않는다.\n"
            "- 공통 배포본은 읽기 전용이다. 정책 변경·충돌은 백엔드와 확인하고 문서 게시와 서버 배포를 구분한다.\n"
            + END + "\n").encode()


def skill():
    return ("---\nname: business-rules\ndescription: 서비스 정책 관련 Android 작업에서 세션에 고정된 공통 규칙을 확인하고 적용한다.\n---\n"
            "# 세션에 고정된 공통 비즈니스 규칙\n\n"
            "SessionStart hook의 `Chalkak 공통 정책` 안내에 있는 고정 버전 경로를 사용한다. "
            "그 경로의 해당 도구용 business-rules/SKILL.md와 필요한 도메인 문서를 읽는다. "
            "프로젝트의 active, cache의 latest 또는 다른 세션의 버전으로 바꾸지 않는다. "
            "세션 안내가 없으면 연결 미확인으로 알리고 추측하지 않는다.\n").encode()


def skill_targets(project):
    return [(project / prefix.rstrip("/"), project / ".chalkak-harness/skills" / provider / "SKILL.md")
            for prefix, provider in zip(core.PREFIXES[:2], PROVIDERS)]


def verify_skills(project):
    for link, stub in skill_targets(project):
        if not link.is_symlink() or os.readlink(link) != os.path.relpath(stub.parent, link.parent):
            raise ValueError("자동 정책 스킬 연결이 수정되었습니다: " + str(link))
        core.local_path(project, str(stub.relative_to(project)))
        if stub.read_bytes() != skill():
            raise ValueError("자동 정책 스킬이 수정되었습니다: " + str(stub))


class FileChanges:
    """Restore only explicitly touched files if setup fails; preserve user data."""
    def __init__(self):
        self.saved = {}

    def remember(self, path):
        if path in self.saved:
            return
        if path.is_symlink():
            self.saved[path] = ("link", os.readlink(path), None)
        elif path.is_file():
            self.saved[path] = ("file", path.read_bytes(), path.stat().st_mode & 0o777)
        elif path.exists():
            raise ValueError("파일 대신 디렉터리가 있습니다: " + str(path))
        else:
            self.saved[path] = ("missing", None, None)

    def write(self, path, data):
        self.remember(path)
        if path.is_symlink():
            raise ValueError("설정 파일 심볼릭 링크는 자동 수정하지 않습니다: " + str(path))
        core.write(path, data)

    def rollback(self):
        for path, (kind, data, mode) in reversed(list(self.saved.items())):
            path.unlink(missing_ok=True)
            if kind == "file":
                core.write(path, data)
                path.chmod(mode)
            elif kind == "link":
                path.parent.mkdir(parents=True, exist_ok=True)
                path.symlink_to(data)


def registry(root):
    path = root / "projects.json"
    value = core.read_json(path) if path.exists() else {"schema": 1, "repositories": {}}
    if value.get("schema") != 1 or not isinstance(value.get("repositories"), dict):
        raise ValueError("자동 설치 등록 정보가 올바르지 않습니다")
    return value


def exclude_block():
    return ("\n# chalkak-automatic-harness:begin\n/.chalkak-harness/\n"
            "/.agents/skills/business-rules\n/.claude/skills/business-rules\n"
            "/AGENTS.md\n/CLAUDE.md\n# chalkak-automatic-harness:end\n").encode()


def configure_exclude(project, changes):
    value = core.git("rev-parse", "--git-path", "info/exclude", cwd=project).decode().strip()
    path = (project / value).resolve()
    original = path.read_bytes() if path.exists() else b""
    if b"# chalkak-automatic-harness:begin" in original:
        if original.count(exclude_block()) != 1:
            raise ValueError("관리 중인 Git 제외 구간이 수정되었습니다")
    else:
        changes.write(path, original + exclude_block())
    return str(path)


def install_project(project, root, changes):
    local = core.local_path(project, ".chalkak-harness")
    state_path = local / "automatic.json"
    if state_path.exists():
        state = core.read_json(state_path)
        if state["runtime"] != str(root):
            raise ValueError("다른 자동 갱신 도구가 관리 중인 프로젝트입니다")
        for name in core.ENTRY:
            if core.local_path(project, name).read_bytes().count(entry()) != 1:
                raise ValueError("자동 진입 지침이 수정되었습니다: " + name)
        verify_skills(project)
        return
    old_state = local / "install.json"
    if old_state.exists():
        previous = core.read_json(old_state)
        # Validate all legacy entry blocks and links before migrating.
        core.status(project)
        for name in [*core.ENTRY, *previous["links"], ".chalkak-harness/install.json", ".chalkak-harness/active"]:
            changes.remember(project / name)
        core.uninstall(project)
    if (local / "active").exists() or (local / "active").is_symlink():
        raise ValueError("기존 active 연결의 설치 정보를 확인하세요")
    entries = {name: core.local_path(project, name) for name in core.ENTRY}
    targets = [core.local_path(project, p.rstrip("/")) for p in core.PREFIXES[:2]]
    for target in targets:
        if target.exists():
            raise ValueError("기존 business-rules 스킬을 보존하기 위해 중단합니다: " + str(target))
    created = []
    for name, path in entries.items():
        data = path.read_bytes() if path.exists() else b""
        if BEGIN.encode() in data or core.MARKER.encode() in data:
            raise ValueError("기존 하네스 설치 구간을 확인하세요")
        if not path.exists():
            created.append(name)
        changes.write(path, data + entry())
    for target, stub in skill_targets(project):
        core.local_path(project, str(stub.relative_to(project)))
        changes.write(stub, skill())
        changes.remember(target)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.symlink_to(os.path.relpath(stub.parent, target.parent), target_is_directory=True)
    changes.write(state_path, core.json_bytes({"runtime": str(root), "created_entries": created}))


def hook_definition(root, provider):
    command = shlex.join([sys.executable, str(root / "manage.py"), "session", "--provider", provider, "--runtime", str(root)])
    return {"matcher": "startup|resume|clear|compact", "hooks": [
        {"type": "command", "command": command, "timeout": 90}
    ]}


def config_path(home, provider):
    return home / (".codex/hooks.json" if provider == "codex" else ".claude/settings.json")


def configure_hooks(home, root, changes):
    owned = root / "hooks-owned.json"
    previous = core.read_json(owned) if owned.exists() else {}
    definitions = {}
    for provider in PROVIDERS:
        path = config_path(home, provider)
        config = core.read_json(path) if path.exists() else {}
        if not isinstance(config, dict) or not isinstance(config.get("hooks", {}), dict):
            raise ValueError("기존 hook 설정 형식이 올바르지 않습니다: " + str(path))
        groups = config.setdefault("hooks", {}).setdefault("SessionStart", [])
        if not isinstance(groups, list):
            raise ValueError("SessionStart 설정은 배열이어야 합니다")
        old = previous.get(provider)
        if old and groups.count(old) != 1:
            raise ValueError("관리 중인 hook이 수정되었습니다. 기존 설정을 먼저 확인하세요")
        if old:
            groups.remove(old)
        definition = hook_definition(root, provider)
        if definition not in groups:
            groups.append(definition)
        definitions[provider] = definition
        changes.write(path, core.json_bytes(config))
    changes.write(owned, core.json_bytes(definitions))


def label(root):
    return LABEL + "." + core.digest(str(root).encode())[:12]


def agent_path(home):
    return home / "Library/LaunchAgents" / (label(runtime(home)) + ".plist")


def agent_definition(root):
    return {"Label": label(root), "ProgramArguments": [sys.executable, str(root / "manage.py"), "sync", "--cache", str(root / "cache")],
            "RunAtLoad": True, "StartInterval": 300,
            "StandardOutPath": str(root / "download.log"), "StandardErrorPath": str(root / "download-error.log")}


def register_agent(path):
    target = f"gui/{os.getuid()}"
    name = plistlib.loads(path.read_bytes())["Label"]
    loaded = subprocess.run(["launchctl", "print", target + "/" + name], capture_output=True, timeout=10)
    if loaded.returncode == 0:
        return False
    subprocess.run(["launchctl", "bootstrap", target, str(path)], check=True, capture_output=True, timeout=15)
    return True


def unregister_agent(path):
    name = plistlib.loads(path.read_bytes())["Label"]
    loaded = subprocess.run(["launchctl", "print", f"gui/{os.getuid()}/{name}"], capture_output=True, timeout=10)
    if loaded.returncode != 0:
        return False
    subprocess.run(["launchctl", "bootout", f"gui/{os.getuid()}", str(path)], check=True, capture_output=True, timeout=15)
    return True


def previous_agent(home, root):
    """Only replace an owned, unchanged definition; migrate the original installer."""
    path = agent_path(home)
    owned = root / "agent-owned.json"
    if not path.exists():
        if owned.exists():
            raise ValueError("관리 중인 자동 실행 설정이 삭제되었습니다")
        return None
    current = plistlib.loads(path.read_bytes())
    if owned.exists():
        if current != core.read_json(owned):
            raise ValueError("관리 중인 자동 실행 설정이 수정되었습니다")
    else:
        # v1 had no ownership file. Require its complete known definition and
        # the matching recorded Codex hook, not just our Label.
        definitions = core.read_json(root / "hooks-owned.json")
        arguments = shlex.split(definitions["codex"]["hooks"][0]["command"])
        expected_command = [str(root / "manage.py"), "session", "--provider", "codex", "--runtime", str(root)]
        if arguments[1:] != expected_command:
            raise ValueError("기존 자동 실행 설정의 소유권을 확인할 수 없습니다")
        legacy = agent_definition(root)
        legacy["ProgramArguments"][0] = arguments[0]
        if current != legacy:
            raise ValueError("같은 이름의 다른 자동 실행 설정이 있습니다")
    return current


def setup(project, home=None, remote=REMOTE, branch=BRANCH):
    if sys.platform != "darwin":
        raise ValueError("자동 설치는 현재 macOS만 지원합니다")
    home = (home or Path.home()).resolve()
    root = runtime(home)
    project, common = identity(project)
    # Resolve/check managed paths before writing user-level settings.
    core.local_path(project, ".chalkak-harness")
    with lock(root):
        data = registry(root)
        old_agent = previous_agent(home, root)
        core.sync(root / "cache", remote, branch)
        changes = FileChanges()
        started = False
        stopped = False
        try:
            install_project(project, root, changes)
            for name in ("manage.py", "automatic.py"):
                source = Path(__file__).with_name(name)
                changes.write(root / name, source.read_bytes())
            configure_hooks(home, root, changes)
            item = data["repositories"].setdefault(common, {"projects": []})
            item["exclude_path"] = configure_exclude(project, changes)
            if str(project) not in item["projects"]:
                item["projects"].append(str(project))
            changes.write(root / "projects.json", core.json_bytes(data))
            path = agent_path(home)
            definition = agent_definition(root)
            if old_agent is not None and old_agent != definition:
                stopped = unregister_agent(path)
            changes.write(path, plistlib.dumps(definition))
            changes.write(root / "agent-owned.json", core.json_bytes(definition))
            started = register_agent(path)
        except Exception:
            if started:
                subprocess.run(["launchctl", "bootout", f"gui/{os.getuid()}", str(agent_path(home))], capture_output=True, timeout=15)
            changes.rollback()
            if stopped:
                register_agent(agent_path(home))
            raise
    return {"installed": True, "mode": "automatic", "project": str(project), "runtime": str(root), "interval_seconds": 300,
            "notice": "Codex hook을 최초 신뢰 승인하고 새 세션을 시작하세요. Claude 설정도 확인하세요. 앱별 실제 hook 실행을 확인해야 합니다. 설치 파일은 커밋하지 마세요."}


def copy_snapshot(project, package, manifest):
    versions = core.local_path(project, ".chalkak-harness/versions")
    versions.mkdir(parents=True, exist_ok=True)
    target = versions / manifest["version"]
    if target.exists():
        core.verify(target)
    else:
        with tempfile.TemporaryDirectory(dir=versions) as temporary:
            staged = Path(temporary) / "package"
            shutil.copytree(package, staged)
            core.verify(staged)
            staged.chmod(0o700)
            shutil.move(staged, target)
        core.protect_snapshot(target)
    return target


def context(provider, target, manifest, remote_check, resumed):
    agent_file = "AGENTS.md" if provider == "codex" else "CLAUDE.md"
    skill_path = ".agents/skills/business-rules/SKILL.md" if provider == "codex" else ".claude/skills/business-rules/SKILL.md"
    warning = ""
    if not remote_check.get("ok"):
        warning = "최신 정책 확인 실패. 마지막 정상 버전을 사용하며 최신이라고 표현하지 마세요. 오류: " + str(remote_check.get("error", "확인 기록 없음")) + "\n"
    message = ("Chalkak 공통 정책 — 이 세션의 고정 버전\n" + warning
               + "버전: " + manifest["version"] + "\n원본 커밋: " + manifest["source_commit"]
               + "\n고정 경로: " + str(target)
               + "\n관련 작업 전에 다음 공통 지침과 스킬을 실제로 읽으세요: " + str(target / agent_file) + ", " + str(target / skill_path)
               + "\n정책 목차: " + str(target / "docs/business-rules/README.md")
               + "\n배포본의 active 또는 원본 docs 상대 경로 안내보다 이 고정 경로를 우선합니다. 이 세션에서는 sync/apply로 버전을 바꾸지 않습니다."
               + " 읽은 규칙과 적용 내용을 짧게 알리고 공통 파일은 수정하지 마세요. 문서 게시와 API 서버 배포는 별개입니다."
               + (" 기존 세션의 고정 버전을 유지했습니다. 새 정책은 새 세션에서 사용하세요." if resumed else " 새 세션의 정책 버전을 선택했습니다."))
    result = {"hookSpecificOutput": {"hookEventName": "SessionStart", "additionalContext": message}}
    if warning:
        result["systemMessage"] = warning.strip()
    return result


def session(root, provider, event):
    if provider not in PROVIDERS:
        raise ValueError("지원하지 않는 AI 도구입니다")
    cwd = event.get("cwd")
    if not isinstance(cwd, str) or not Path(cwd).is_dir():
        return {}
    try:
        project, common = identity(Path(cwd))
    except (OSError, ValueError, subprocess.SubprocessError):
        return {}
    data = registry(root)
    if common not in data["repositories"]:
        return {}  # No side effects or warnings in unrelated repositories.
    session_id = event.get("session_id")
    if not isinstance(session_id, str) or not session_id or len(session_id) > 512:
        raise ValueError("세션 ID가 없어 정책 버전을 고정할 수 없습니다")
    with lock(root):
        data = registry(root)
        changes = FileChanges()
        try:
            install_project(project, root, changes)
            item = data["repositories"][common]
            if str(project) not in item["projects"]:
                item["projects"].append(str(project))
                changes.write(root / "projects.json", core.json_bytes(data))
            local = core.local_path(project, ".chalkak-harness")
            key = core.digest((provider + "\0" + session_id).encode())
            pin = core.local_path(project, ".chalkak-harness/sessions/" + key + ".json")
            cache = root / "cache"
            if pin.exists():
                binding = core.read_json(pin)
                if not re.fullmatch(r"[0-9a-f]{64}", str(binding.get("version", ""))):
                    raise ValueError("세션 버전 기록이 올바르지 않습니다")
                target = local / "versions" / binding["version"]
                core.local_path(project, str(target.relative_to(project)))
                manifest = core.verify(target)
                if manifest["version"] != binding["version"]:
                    raise ValueError("세션 버전과 문서가 일치하지 않습니다")
                return context(provider, target, manifest, core.read_json(cache / "check.json"), True)
            if event.get("source") in ("resume", "compact"):
                message = ("이 세션은 설치 전에 시작되어 공통 정책의 고정 버전이 없습니다. 기존 대화는 계속할 수 있습니다. "
                           "정책 의존 작업은 새 세션에서 시작하세요. 최신 정책 연결 성공으로 표시하거나 "
                           "active/latest 또는 다른 세션의 문서를 대신 사용하지 마세요.")
                return {"systemMessage": message, "hookSpecificOutput": {
                    "hookEventName": "SessionStart", "additionalContext": message}}
            if event.get("source") not in ("startup", "clear"):
                raise ValueError("설치 전에 시작한 세션의 규칙은 자동 교체하지 않습니다. 새 세션을 시작하세요")
            try:
                check = core.sync(cache)
            except (OSError, ValueError, subprocess.SubprocessError) as exc:
                check = {"ok": False, "error": str(exc)}
            latest = core.read_json(cache / "latest.json")
            package = cache / "versions" / latest["version"]
            manifest = core.verify(package)
            target = copy_snapshot(project, package, manifest)
            changes.write(pin, core.json_bytes({"provider": provider, "session_id": session_id, "version": manifest["version"], "source_commit": manifest["source_commit"]}))
            return context(provider, target, manifest, check, False)
        except Exception:
            changes.rollback()
            raise


def status(project, check=False):
    project, common = identity(project)
    local = core.local_path(project, ".chalkak-harness")
    state = core.read_json(local / "automatic.json")
    root = Path(state["runtime"])
    install_project(project, root, FileChanges())  # Existing automatic installation: validate only.
    error = None
    if check:
        try:
            core.sync(root / "cache")
        except (OSError, ValueError, subprocess.SubprocessError) as exc:
            error = {"ok": False, "error": str(exc)}
    result = {"mode": "automatic", "available": core.read_json(root / "cache/latest.json")["version"],
              "remote_check": error or core.read_json(root / "cache/check.json"),
              "sessions": [core.read_json(p) for p in sorted((local / "sessions").glob("*.json"))],
              "notice": "새 세션이 최신 버전을 선택합니다. 기존 세션에는 apply하지 않습니다."}
    return result


def remove_project(project, changes):
    local = core.local_path(project, ".chalkak-harness")
    state = core.read_json(local / "automatic.json")
    for name in core.ENTRY:
        path = core.local_path(project, name)
        contents = path.read_bytes()
        if contents.count(entry()) != 1:
            raise ValueError("관리 중인 진입 지침이 수정되어 제거하지 않습니다")
        changes.write(path, contents.replace(entry(), b"", 1))
        if not path.read_bytes() and name in state["created_entries"]:
            path.unlink()
    verify_skills(project)
    for link, stub in skill_targets(project):
        changes.remember(link)
        link.unlink()
    changes.remember(local / "automatic.json")
    (local / "automatic.json").unlink()


def uninstall(project, home=None):
    home = (home or Path.home()).resolve()
    project, common = identity(project)
    root = Path(core.read_json(project / ".chalkak-harness/automatic.json")["runtime"])
    if root != runtime(home):
        raise ValueError("현재 사용자의 자동 설치가 아닙니다")
    with lock(root):
        data = registry(root)
        item = data["repositories"].pop(common)
        changes = FileChanges()
        stopped = False
        try:
            exclude = Path(item["exclude_path"])
            contents = exclude.read_bytes()
            if contents.count(exclude_block()) != 1:
                raise ValueError("관리 중인 Git 제외 구간이 수정되어 제거하지 않습니다")
            changes.write(exclude, contents.replace(exclude_block(), b"", 1))
            for name in item["projects"]:
                p = Path(name)
                if (p / ".chalkak-harness/automatic.json").exists():
                    remove_project(p, changes)
            if not data["repositories"]:
                definitions = core.read_json(root / "hooks-owned.json")
                for provider, definition in definitions.items():
                    path = config_path(home, provider)
                    config = core.read_json(path)
                    groups = config.get("hooks", {}).get("SessionStart", [])
                    if groups.count(definition) != 1:
                        raise ValueError("관리 중인 hook이 수정되어 제거하지 않습니다")
                    groups.remove(definition)
                    changes.write(path, core.json_bytes(config))
                path = agent_path(home)
                previous_agent(home, root)
                stopped = unregister_agent(path)
                changes.remember(path)
                path.unlink(missing_ok=True)
                changes.remember(root / "hooks-owned.json")
                (root / "hooks-owned.json").unlink()
                changes.remember(root / "agent-owned.json")
                (root / "agent-owned.json").unlink(missing_ok=True)
            changes.write(root / "projects.json", core.json_bytes(data))
        except Exception:
            changes.rollback()
            if stopped:
                register_agent(agent_path(home))
            raise
    return {"removed": True, "notice": "등록된 해당 저장소와 worktree의 자동 연결을 제거했습니다. 버전·세션 기록과 cache는 보존합니다."}
