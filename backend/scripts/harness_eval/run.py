#!/usr/bin/env python3
"""작은 하네스 행동 평가 도구. Python 3.11+, 표준 라이브러리만 사용한다.

prepare는 AI를 실행하지 않는다. run은 명시적으로 요청한 실제 평가에만 사용한다.
종료: 0 준비/판정 통과, 1 행동 실패, 2 미실행·검사 불가·판정 대기.
"""

from __future__ import annotations

import argparse
import difflib
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import time
import tomllib
import uuid

try:
    from . import claude_adapter
except ImportError:
    import claude_adapter

HERE = Path(__file__).resolve().parent
BACKEND = HERE.parents[1]
COPY_PATHS = ("backend/AGENTS.md", "backend/CLAUDE.md", "backend/.agents/skills",
              "backend/.claude/skills", "backend/.claude/rules", "backend/.claude/settings.json",
              ".github/ISSUE_TEMPLATE", ".github/pull_request_template.md")
DISABLED = ("apps", "plugins", "remote_plugin", "hooks", "browser_use", "computer_use",
            "in_app_browser", "image_generation", "view_image", "multi_agent", "multi_agent_v2",
            "skill_mcp_dependency_install", "memories", "shell_snapshot", "goals")


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def within(root, relative):
    path = root / relative
    if Path(relative).is_absolute() or ".." in Path(relative).parts or path.is_symlink():
        raise ValueError(f"허용하지 않는 상대 경로: {relative}")
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError(f"작업 자료 밖의 경로: {relative}")
    return path


def copy_files(source, destination):
    if not source.exists():
        raise ValueError(f"필요한 작업 자료가 없습니다: {source}")
    paths = sorted(source.rglob("*")) if source.is_dir() else [source]
    for path in paths:
        if path.is_symlink():
            raise ValueError(f"평가 자료의 심볼릭 링크는 지원하지 않습니다: {path}")
        if path.is_file():
            target = destination / path.relative_to(source) if source.is_dir() else destination
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, target)


def git(repo, *args):
    env = {key: value for key, value in os.environ.items() if not key.startswith("GIT_")}
    env.update(GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_NOSYSTEM="1", GIT_OPTIONAL_LOCKS="0")
    command = ["git", "-c", "commit.gpgSign=false",
               "-c", "user.name=Harness Fixture", "-c", "user.email=fixture@example.invalid", *args]
    return subprocess.check_output(command, cwd=repo, env=env, text=True, stderr=subprocess.PIPE)


def snapshot(repo):
    files = {}
    for path in sorted(repo.rglob("*")):
        relative = path.relative_to(repo).as_posix()
        if relative == ".git" or relative.startswith(".git/"):
            continue
        if path.is_symlink():
            raise ValueError(f"실행 결과에 심볼릭 링크가 있습니다: {relative}")
        if path.is_file():
            if path.stat().st_size > 1_000_000:
                raise ValueError(f"작은 문서 사례의 파일 크기 초과: {relative}")
            files[relative] = path.read_bytes().decode("utf-8")
    return {"files": files, "refs": git(repo, "show-ref"),
            "head": git(repo, "symbolic-ref", "HEAD"), "config": (repo / ".git/config").read_text()}


def prepare(case, output, repo, source=BACKEND.parent):
    for relative in COPY_PATHS:
        copy_files(source / relative, repo / relative)
    copy_files(case / "fixture", repo)
    criteria = json.loads((case / "criteria.json").read_text())
    setup = criteria["setup"]
    for relative in setup.get("copy_source_paths", []):
        copy_files(within(source, relative), within(repo, relative))
    branch = setup.get("branch", "be/develop")
    if not isinstance(branch, str) or not re.fullmatch(r"[A-Za-z0-9._/#-]+", branch):
        raise ValueError("평가용 브랜치 이름이 올바르지 않습니다")
    git(repo, "init", "-b", branch)
    git(repo, "remote", "add", "origin", "https://example.invalid/team/fixture.git")
    git(repo, "add", ".")
    git(repo, "commit", "-qm", "fixture baseline")
    seed = setup.get("work_state")
    if seed:
        executable = repo / "backend/scripts/work_state.py"
        issue, work, checks = seed.get("issue"), seed.get("work"), seed.get("checks")
        if not executable.is_file() or not isinstance(issue, int) or issue <= 0 or not isinstance(work, dict) or not isinstance(checks, list):
            raise ValueError("작업 기록 평가 준비 값이 올바르지 않습니다")
        snapshot_result = subprocess.run([sys.executable, str(executable), "snapshot"], cwd=repo / "backend",
                                         capture_output=True, text=True, timeout=20)
        try:
            fingerprint = json.loads(snapshot_result.stdout)["fingerprint"]
        except (ValueError, KeyError, TypeError):
            raise ValueError("작업 기록 평가용 지문을 만들지 못했습니다") from None
        payload = json.dumps({"work": work, "checks": checks}, ensure_ascii=False)
        saved = subprocess.run([sys.executable, str(executable), "save", "--issue", str(issue),
                                "--expected-revision", "missing", "--input", "-", "--record-checks",
                                "--checked-fingerprint", fingerprint], cwd=repo / "backend", input=payload,
                               capture_output=True, text=True, timeout=20)
        if saved.returncode:
            raise ValueError("작업 기록 평가 자료를 만들지 못했습니다: " + saved.stdout[:500])
    for relative, content in setup["uncommitted_append"].items():
        with within(repo, relative).open("a", encoding="utf-8") as handle:
            handle.write(content)
    before = snapshot(repo)
    write_json(output / "before.json", before)
    write_json(output / "criteria.json", criteria)
    shutil.copyfile(case / "prompt.md", output / "prompt.md")
    digest = hashlib.sha256(json.dumps(before["files"], sort_keys=True).encode()).hexdigest()
    write_json(output / "manifest.json", {"case": criteria["id"], "input_sha256": digest,
                                         "source_revision": git(source, "rev-parse", "HEAD").strip()})
    return criteria, before


def compare(before, after, criteria):
    old, new = before["files"], after["files"]
    changed = sorted(path for path in old.keys() | new.keys() if old.get(path) != new.get(path))
    checks, errors = criteria["mechanical"], []
    for path in set(changed) - set(checks["allowed_changed_paths"]):
        errors.append(f"범위 밖 변경: {path}")
    for path in set(checks["required_changed_paths"]) - set(changed):
        errors.append(f"요청한 변경 없음: {path}")
    for kind in ("required_text", "preserved_text"):
        for path, snippets in checks[kind].items():
            for snippet in snippets:
                if snippet not in new.get(path, ""):
                    errors.append(f"{kind}: {path}: {snippet}")
    for key in ("refs", "head", "config"):
        if before[key] != after[key]:
            errors.append(f"승인하지 않은 Git 상태 변경: {key}")
    diff = "".join("".join(difflib.unified_diff(old.get(path, "").splitlines(True),
                    new.get(path, "").splitlines(True), fromfile="before/" + path,
                    tofile="after/" + path)) for path in changed)
    return sorted(errors), diff


def overrides(values):
    result = []
    for key, value in values.items():
        # JSON 문자열/숫자/불리언은 이 값들의 TOML 표현과 호환된다. shell을 거치지 않는다.
        result.extend(["-c", key + "=" + json.dumps(value, ensure_ascii=False)])
    return result


def codex_config(repo, protected):
    values = {"default_permissions": "harness-eval", "approval_policy": "never",
              "web_search": "disabled", "allow_login_shell": False,
              "shell_environment_policy.inherit": "none",
              "shell_environment_policy.set.PATH": "/Library/Developer/CommandLineTools/usr/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin",
              "permissions.harness-eval.network.enabled": False}
    grants = {":root": "deny", ":minimal": "read", ":tmpdir": "deny", ":slash_tmp": "deny",
              str(repo): "write", str(protected): "deny"}
    grants[str(Path(tempfile.gettempdir()).resolve())] = "deny"
    grants[str(Path("/tmp").resolve())] = "deny"
    # macOS의 /usr/bin/python3·git은 개발 도구를 호출하는 shim일 수 있다.
    # 실행 중인 Python과 설치된 명령의 런타임만 읽도록 추가한다.
    for runtime in (Path(sys.base_prefix), Path("/opt/homebrew"), Path("/usr/local"),
                    Path("/Library/Developer/CommandLineTools")):
        if runtime.is_dir() and runtime.resolve() not in (Path("/"), Path.home()):
            grants[str(runtime.resolve())] = "read"
    for feature in DISABLED:
        values["features." + feature] = False
    filesystem = "{" + ",".join(json.dumps(path) + "=" + json.dumps(access) for path, access in grants.items()) + "}"
    return overrides(values) + ["-c", "permissions.harness-eval.filesystem=" + filesystem, "-c", "mcp_servers={}"]


def execute(command, cwd, output, prompt=None, timeout=180):
    """네이티브 출력은 그대로 보관한다. timeout 시 자식 프로세스 그룹도 종료한다."""
    start = time.monotonic()
    with (output / "events.jsonl").open("w") as stdout, (output / "stderr.log").open("w") as stderr:
        process = subprocess.Popen(command, cwd=cwd, stdin=subprocess.PIPE,
                                   stdout=stdout, stderr=stderr, start_new_session=True)
        try:
            process.communicate(None if prompt is None else prompt.encode(), timeout=timeout)
            return process.returncode, round(time.monotonic() - start, 2)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
            return None, round(time.monotonic() - start, 2)
        except KeyboardInterrupt:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
            raise


def preflight(binary, repo, output, config):
    """모델을 호출하지 않고 실제 sandbox와 비활성 MCP를 확인한다."""
    if sys.platform != "darwin":
        return "현재 실행기는 macOS의 Codex sandbox만 검증합니다"
    # exec는 사용자 설정을 제외한다. 나머지 로컬 설정에 legacy sandbox가 있으면
    # sandbox -P의 사전 검사와 다른 권한이 선택될 수 있으므로 실행하지 않는다.
    directory = Path(os.environ.get("CODEX_HOME", Path.home() / ".codex"))
    sources = [Path("/etc/codex/config.toml"), Path("/etc/codex/managed_config.toml"),
               directory / "managed_config.toml"]
    sources.extend(parent / ".codex/config.toml" for parent in (repo / "backend", repo, *repo.parents))
    for path in sources:
        if path.exists():
            data = tomllib.loads(path.read_text())
            if path.name == "managed_config.toml" and data:
                return "관리형 설정의 유효 실행 권한 검증을 지원하지 않습니다"
            if "sandbox_mode" in data or "sandbox_workspace_write" in data or data.get("profile"):
                return "별도 설정의 legacy sandbox/프로필과 평가 권한이 충돌할 수 있어 실행하지 않습니다"
    managed = Path("/Library/Managed Preferences")
    for path in (managed / "com.openai.codex.plist", managed / Path.home().name / "com.openai.codex.plist"):
        if path.exists():
            with path.open("rb") as handle:
                if plistlib.load(handle):
                    return "관리형 macOS 설정의 유효 실행 권한 검증을 지원하지 않습니다"
    listing = subprocess.run([binary, "mcp", "list", "--json", *config], cwd=repo / "backend",
                             capture_output=True, text=True, timeout=20)
    try:
        servers = json.loads(listing.stdout)
        if listing.returncode or not isinstance(servers, list):
            return "MCP 목록을 확인하지 못했습니다"
        names = [server["name"] for server in servers]
        if any(not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9_-]+", name) for name in names):
            return "지원하지 않는 MCP 이름이 있어 실행하지 않습니다"
        disabled = overrides({f"mcp_servers.{name}.enabled": False for name in names})
        verified = subprocess.run([binary, "mcp", "list", "--json", *config, *disabled], cwd=repo / "backend",
                                  capture_output=True, text=True, timeout=20)
        remaining = json.loads(verified.stdout)
        if verified.returncode or not isinstance(remaining, list) or any(s.get("enabled", True) for s in remaining):
            return "활성 MCP가 없음을 확인하지 못했습니다"
    except (ValueError, AttributeError, KeyError, TypeError):
        return "MCP 설정을 확인하지 못했습니다"
    sentinel = output / "outside-sentinel"
    peer = repo.parent / "outside-sentinel"
    sentinel.write_text("outside evaluation workspace\n")
    peer.write_text("outside evaluation workspace\n")
    probe = '''import errno, json, pathlib, socket, sys
repo, port = pathlib.Path(sys.argv[1]), int(sys.argv[2])
outside = [pathlib.Path(path) for path in sys.argv[3:]]
def denied(action):
    try: action()
    except OSError as exc: return exc.errno in (errno.EACCES, errno.EPERM)
    return False
inside = repo / "probe.tmp"
inside.write_text("probe")
can_read_write = inside.read_text() == "probe"
inside.unlink()
read_blocked = [denied(path.read_text) for path in outside]
write_blocked = [denied(lambda: path.write_text("probe")) for path in outside]
with socket.socket() as connection:
    connection.settimeout(2)
    network_blocked = denied(lambda: connection.connect(("127.0.0.1", port)))
print(json.dumps([can_read_write, *read_blocked, *write_blocked, network_blocked]))
'''
    # 접속 거부/없는 파일을 차단 성공으로 오인하지 않도록 실제 파일과 listener를 쓴다.
    with socket.socket() as listener, tempfile.TemporaryDirectory(prefix="harness-outside-") as other:
        temp_sentinel = Path(other).resolve() / "outside-sentinel"
        temp_sentinel.write_text("outside evaluation workspace\n")
        listener.bind(("127.0.0.1", 0))
        listener.listen(1)
        command = [binary, "sandbox", "--include-managed-config", "-C", str(repo / "backend"),
                   "-P", "harness-eval", *config, *disabled, "--", str(Path(sys.executable).resolve()), "-c", probe,
                   str(repo), str(listener.getsockname()[1]), str(sentinel), str(peer), str(temp_sentinel)]
        result = subprocess.run(command, capture_output=True, text=True, timeout=20)
    (output / "preflight.log").write_text(result.stdout + result.stderr)
    try:
        if result.returncode == 0 and json.loads(result.stdout) == [True] * 8:
            # mcp list는 사용자 설정을 읽지만 exec는 제외한다. 비활성 서버도
            # transport가 필수이므로 실제 실행에는 인증 없는 비활성 정의를 준다.
            config.extend(disabled)
            config.extend(overrides({f"mcp_servers.{name}.command": "/usr/bin/false" for name in names}))
            return None
    except ValueError:
        pass
    return "읽기·쓰기·네트워크 격리 사전 검사 실패 (preflight.log 확인)"


def model_settings():
    """사용자 설정은 격리하되 기존 모델 선택만 유지한다. 인증 파일은 복사하지 않는다."""
    directory = Path(os.environ.get("CODEX_HOME", Path.home() / ".codex"))
    path = directory / "config.toml"
    data = tomllib.loads(path.read_text()) if path.exists() else {}
    if data.get("model_provider", "openai") != "openai":
        raise ValueError("사용자 지정 모델 공급자의 격리 실행은 지원하지 않습니다")
    return {key: data[key] for key in ("model", "model_reasoning_effort") if key in data}


def collect_events(output, platform="codex"):
    if platform == "claude":
        return claude_adapter.collect_events(output)
    events, errors = [], []
    for number, line in enumerate((output / "events.jsonl").read_text().splitlines(), 1):
        try:
            event = json.loads(line)
            if not isinstance(event, dict) or not isinstance(event.get("item", {}), dict):
                raise ValueError("이벤트는 객체여야 합니다")
            events.append(event)
            if event.get("item", {}).get("type") == "error":
                errors.append(f"실행 도구 오류 ({number}행): {event['item'].get('message', '')}")
        except ValueError:
            errors.append(f"이벤트 {number}행을 해석할 수 없습니다")
    completed = [event for event in events if event.get("type") == "turn.completed"]
    messages = [event.get("item", {}).get("text", "") for event in events
                if event.get("type") == "item.completed" and event.get("item", {}).get("type") == "agent_message"]
    (output / "answer.md").write_text("\n\n".join(messages))
    return bool(completed and any(messages) and not errors), errors, [e.get("usage") for e in completed]


def result_status(errors, complete, code, reviews=()):
    # 시작도 못 한 실행의 '요청한 수정 없음'을 하네스 행동 실패로 세지 않는다.
    violation = any(not error.startswith(("required_text:", "요청한 변경 없음:")) for error in errors)
    if violation or "FAIL" in reviews:
        return "FAIL"
    if code != 0 or not complete or "INCONCLUSIVE" in reviews:
        return "INCONCLUSIVE"
    if errors:
        return "FAIL"
    return "PASS" if reviews else "REVIEW_REQUIRED"


def run_case(case, platform, action, timeout, destination, actor_parent=None):
    destination.mkdir(parents=True)
    report = {"case": case.name, "platform": platform, "status": "NOT_RUN", "reason": "준비만 수행",
              "mechanical_errors": [], "model_sessions": 0}
    with tempfile.TemporaryDirectory(prefix="chalkak-harness-eval-", dir=actor_parent) as temporary:
        repo = Path(temporary).resolve() / "repo"
        repo.mkdir()
        criteria, before = prepare(case, destination, repo)
        if action == "prepare":
            report["reason"] = "작업 자료·판정 기준 분리 준비 확인; 임시 작업 폴더는 정리됨"
        else:
            binary = shutil.which(platform)
            if not binary:
                reason = f"{platform} CLI가 설치되어 있지 않습니다"
            elif platform == "claude":
                command, metadata = claude_adapter.build_command(binary, repo, destination)
                report.update(metadata)
                reason = None
            else:
                version = subprocess.run([binary, "--version"], capture_output=True, text=True, timeout=10)
                report["cli_version"] = version.stdout.strip()
                config = codex_config(repo, BACKEND.parent)
                settings = model_settings()
                report["model_settings"] = settings or {"model": "CLI 기본값 (사용량 기록과 별도)"}
                report["mode"] = "native exec; 개인 설정 제외, 전역 지침/관리자 정책은 유지"
                reason = preflight(binary, repo, destination, config)
                if reason is None:
                    command = [binary, "exec", "--ignore-user-config", "--strict-config", "--ephemeral",
                               "--json", "-C", str(repo / "backend"), *config, *overrides(settings), "-"]
            if reason:
                report.update(status="BLOCKED", reason=reason)
            else:
                write_json(destination / "command.json", command)
                report["model_sessions"] = 1
                report.update(status="INCONCLUSIVE", reason="실행 시작; 결과 수집 전")
                write_json(destination / "report.json", report)
                try:
                    code, elapsed = execute(command, repo / "backend", destination,
                                            (case / "prompt.md").read_text(), timeout)
                except KeyboardInterrupt:
                    report.update(reason="사용자가 실행을 중단함; 후속 사례도 실행하지 않음")
                    write_json(destination / "report.json", report)
                    raise
                report.update(exit_code=code, elapsed_seconds=elapsed)
                write_json(destination / "report.json", report)
                try:
                    after = snapshot(repo)
                    write_json(destination / "after.json", after)
                    errors, diff = compare(before, after, criteria)
                    (destination / "changes.diff").write_text(diff)
                except (OSError, ValueError, subprocess.SubprocessError) as exc:
                    errors = [f"문서 사례의 작업 결과 수집 실패: {exc}"]
                    (destination / "collection-error.txt").write_text(errors[0])
                complete, parse_errors, usage = collect_events(destination, platform)
                report.update(mechanical_errors=errors, event_errors=parse_errors, usage=usage)
                report["status"] = result_status(errors, complete, code)
                report["reason"] = "기록·답변·diff의 의미 및 금지된 시도를 별도 판정해야 합니다"
    write_json(destination / "report.json", report)
    review = {"attempts": {"status": "UNREVIEWED", "evidence": ""},
              **{item["id"]: {"status": "UNREVIEWED", "evidence": ""} for item in criteria["semantic"]}}
    write_json(destination / "review.json", review)
    return report


def grade(destination):
    report = json.loads((destination / "report.json").read_text())
    if report["status"] not in ("REVIEW_REQUIRED", "PASS", "FAIL", "INCONCLUSIVE"):
        raise ValueError("미실행·검사 불가 결과를 통과로 변경할 수 없습니다")
    criteria = json.loads((destination / "criteria.json").read_text())
    review = json.loads((destination / "review.json").read_text())
    keys = {"attempts"} | {item["id"] for item in criteria["semantic"]}
    if set(review) != keys or any(item.get("status") not in ("PASS", "FAIL", "INCONCLUSIVE")
                                  or not item.get("evidence", "").strip() for item in review.values()):
        raise ValueError("모든 판정 항목에 상태와 실제 기록의 근거를 작성해야 합니다")
    statuses = {item["status"] for item in review.values()}
    complete, event_errors, usage = collect_events(destination, report["platform"])
    report.update(event_errors=event_errors, usage=usage)
    # 캐시한 성공 여부를 신뢰하지 않고 필수 결과 자료로 다시 계산한다.
    try:
        before = json.loads((destination / "before.json").read_text())
        after = json.loads((destination / "after.json").read_text())
        errors, diff = compare(before, after, criteria)
        complete = complete and (destination / "changes.diff").read_text() == diff
        report["mechanical_errors"] = sorted(set(report["mechanical_errors"] + errors))
    except (OSError, ValueError, KeyError, TypeError):
        complete = False
    report["status"] = result_status(report["mechanical_errors"], complete, report.get("exit_code"), statuses)
    report["review"] = review
    report["reason"] = "사례별 기록 판정 완료"
    write_json(destination / "report.json", report)
    write_summary(destination.parent)
    return report


def write_summary(batch):
    lines = ["# 하네스 동작 검사 결과", "", "사례별 실제 실행과 기록 판정 상태입니다. 실행기 자체 테스트 결과와는 별도입니다.", "",
             "| 도구 | 사례 | 상태 | 실행 시간 | 기록 |", "| --- | --- | --- | --- | --- |"]
    for path in sorted(batch.glob("*/report.json")):
        report = json.loads(path.read_text())
        elapsed = report.get("elapsed_seconds")
        duration = "—" if elapsed is None else f"{elapsed}초"
        lines.append(f"| {report['platform']} | {report['case']} | {report['status']} | {duration} | [기록]({path.parent.name}/report.json) |")
    lines.extend(["", "- PASS: 해당 사례의 실행·변경·판정 근거를 확인해 통과", "- REVIEW_REQUIRED: 실행을 마쳤으나 내용 판정 대기",
                  "- FAIL: 요구 행동 또는 변경 범위 위반", "- INCONCLUSIVE: 실행 오류·중단·근거 부족으로 판정 불가",
                  "- NOT_RUN / BLOCKED: 미실행 / 실행 조건을 충족하지 못함", ""])
    (batch / "summary.md").write_text("\n".join(lines), encoding="utf-8")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("list", "prepare", "run", "grade"))
    parser.add_argument("--platform", choices=("codex", "claude", "both"), default="both")
    parser.add_argument("--case", action="append", choices=sorted(p.name for p in (HERE / "cases").iterdir() if p.is_dir()))
    parser.add_argument("--timeout", type=int, default=180, help="세션당 초, 최대 300")
    parser.add_argument("--result", type=Path, help="grade할 결과 폴더")
    args = parser.parse_args(argv)
    try:
        if args.action == "grade":
            if args.result is None:
                parser.error("grade에는 --result가 필요합니다")
            reports = [grade(args.result)]
            print(f"{reports[0]['platform']}/{reports[0]['case']}: {reports[0]['status']}")
        else:
            cases = [HERE / "cases" / name for name in args.case or sorted(p.name for p in (HERE / "cases").iterdir() if p.is_dir())]
            if args.action == "list":
                for case in cases:
                    print(case.name + ": " + json.loads((case / "criteria.json").read_text())["title"])
                return 0
            if not 1 <= args.timeout <= 300:
                parser.error("timeout은 1~300초여야 합니다")
            platforms = ("codex", "claude") if args.platform == "both" else (args.platform,)
            batch = BACKEND / "build/harness-eval" / (time.strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:8])
            print(f"{'AI 미실행 준비' if args.action == 'prepare' else '명시적 동작 평가'}: {len(cases)}개 사례 × {len(platforms)}개 도구", flush=True)
            reports = []
            try:
                for platform in platforms:
                    for case in cases:
                        destination = batch / (platform + "-" + case.name)
                        try:
                            actor_parent = None
                            if platform == "codex" and args.action == "run":
                                # 관리형 환경에서도 만들 수 있고 Git에서 제외되는 작업 영역을 쓴다.
                                # 실제 평가 저장소에는 이 하위 경로만 쓰기 권한을 부여한다.
                                actor_parent = BACKEND / "build/harness-eval/actors"
                                actor_parent.mkdir(parents=True, exist_ok=True)
                            report = run_case(case, platform, args.action, args.timeout, destination, actor_parent)
                        except (OSError, ValueError, subprocess.SubprocessError) as exc:
                            destination.mkdir(parents=True, exist_ok=True)
                            saved = destination / "report.json"
                            report = json.loads(saved.read_text()) if saved.exists() else {"case": case.name, "platform": platform, "model_sessions": 0}
                            report.update(status="INCONCLUSIVE" if report["model_sessions"] else "BLOCKED", reason=str(exc))
                            write_json(destination / "report.json", report)
                        reports.append(report)
                        write_summary(batch)
                        print(f"{platform}/{case.name}: {report['status']} — {report['reason']}", flush=True)
            except KeyboardInterrupt:
                write_summary(batch)
                print(f"중단됨. 후속 사례는 실행하지 않습니다. 결과: {batch}", file=sys.stderr)
                return 130
            print(f"결과: {batch}")
        if any(report["status"] == "FAIL" for report in reports):
            return 1
        if all(report["status"] == "PASS" for report in reports):
            return 0
        if args.action == "prepare" and all(report["status"] == "NOT_RUN" for report in reports):
            return 0
        return 2
    except (OSError, ValueError, subprocess.SubprocessError) as exc:
        print(f"검사 불가: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
