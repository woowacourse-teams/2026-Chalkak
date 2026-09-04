"""기존 Claude 로그인으로 제한된 문서 사례를 실행한다. 준비 단계는 모델을 호출하지 않는다."""

from __future__ import annotations

import json
import os
from pathlib import Path
import re
import subprocess
import sys


HERE = Path(__file__).resolve().parent
MCP_NAMES = ["mcp__fixture__" + name for name in ("read", "list", "write", "git_read", "work_state")]
REQUIRED_FLAGS = ("--tools", "--allowedTools", "--disallowedTools", "--permission-mode", "--setting-sources",
                  "--settings", "--strict-mcp-config", "--mcp-config", "--no-session-persistence",
                  "--output-format", "--verbose", "--no-chrome")


def dump(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def inspect_material(repo, config_home):
    """현재 팀의 정적 스킬만 지원하며 실행 가능한 확장은 조용히 제거하지 않고 거부한다."""
    names = []
    for path in sorted((repo / "backend/.claude/skills").glob("*/SKILL.md")):
        text = path.read_text(encoding="utf-8")
        lines = text.splitlines()
        try:
            stop = lines.index("---", 1)
        except ValueError:
            raise ValueError("Claude 스킬의 frontmatter를 확인할 수 없습니다: " + str(path)) from None
        # 런타임 YAML 파서를 새로 만들지 않는다. name/description 한 줄 메타데이터만 지원한다.
        headers = lines[1:stop]
        if (not lines or lines[0] != "---" or len(headers) != 2
                or not re.fullmatch(r"name: [a-z0-9]+(?:-[a-z0-9]+)*", headers[0])
                or not re.fullmatch(r"description: [^>|].+", headers[1])
                or re.search(r"!\s*`", text)):
            raise ValueError("Claude 문서 평가는 name/description의 정적 스킬만 지원합니다: " + str(path))
        name = headers[0].removeprefix("name: ")
        if name != path.parent.name:
            raise ValueError("Claude 스킬 이름과 디렉터리가 일치하지 않습니다: " + str(path))
        if (config_home / "skills" / name / "SKILL.md").exists() or (config_home / "commands" / (name + ".md")).exists():
            raise ValueError("개인 스킬이 프로젝트 스킬을 가릴 수 있어 평가하지 않습니다: " + name)
        names.append(name)
    if not names:
        raise ValueError("평가할 Claude 프로젝트 스킬이 없습니다")
    instructions = [repo / "backend/CLAUDE.md", config_home / "CLAUDE.md"]
    instructions += list((repo / "backend/.claude/rules").rglob("*.md"))
    instructions += list((config_home / "rules").rglob("*.md"))
    for path in instructions:
        if path.exists() and re.search(r"(?<!\S)@[^\s]+", path.read_text(encoding="utf-8")):
            raise ValueError("평가 자료 밖을 자동으로 읽을 수 있는 @import는 이 실행기가 지원하지 않습니다: " + str(path))
    settings = json.loads((repo / "backend/.claude/settings.json").read_text())
    if not isinstance(settings, dict) or set(settings) - {"permissions", "sandbox"}:
        raise ValueError("이 실행기는 permissions·sandbox 외의 프로젝트 실행 설정을 지원하지 않습니다")
    return names


def probe_helper(repo, output):
    """실제 stdio 서버가 자료를 읽고 외부·Git 내부·지침 수정을 거부하는지 모델 없이 확인한다."""
    requests = [
        {"jsonrpc": "2.0", "id": 1, "method": "initialize"},
        {"jsonrpc": "2.0", "id": 2, "method": "tools/call", "params": {"name": "read", "arguments": {"path": "backend/CLAUDE.md"}}},
        {"jsonrpc": "2.0", "id": 3, "method": "tools/call", "params": {"name": "read", "arguments": {"path": str(output / "criteria.json")}}},
        {"jsonrpc": "2.0", "id": 4, "method": "tools/call", "params": {"name": "read", "arguments": {"path": ".git/config"}}},
        {"jsonrpc": "2.0", "id": 5, "method": "tools/call", "params": {"name": "write", "arguments": {"path": "backend/CLAUDE.md", "content": "changed"}}},
    ]
    command = [sys.executable, str(HERE / "fixture_tools.py"), str(repo), str(output / "claude-preflight-tools.jsonl")]
    result = subprocess.run(command, input="\n".join(json.dumps(item) for item in requests) + "\n",
                            capture_output=True, text=True, timeout=10)
    try:
        responses = [json.loads(line) for line in result.stdout.splitlines()]
        ok = (result.returncode == 0 and len(responses) == 5
              and "serverInfo" in responses[0]["result"]
              and not responses[1]["result"].get("isError")
              and all(response["result"].get("isError") is True for response in responses[2:]))
    except (ValueError, KeyError, TypeError, AttributeError):
        ok = False
    if not ok:
        raise ValueError("Claude fixture MCP 사전 검사 실패")


def build_command(binary, repo, output, config_home=None):
    """명령·메타데이터만 반환한다. 호출자는 명시적 평가 요청 때에만 이 명령을 실행한다."""
    config_home = Path(config_home or os.environ.get("CLAUDE_CONFIG_DIR", Path.home() / ".claude"))
    help_result = subprocess.run([binary, "--help"], capture_output=True, text=True, timeout=10)
    if help_result.returncode or any(flag not in help_result.stdout for flag in REQUIRED_FLAGS) or "dontAsk" not in help_result.stdout:
        raise ValueError("설치된 Claude CLI에서 필요한 제한 실행 옵션을 확인할 수 없습니다")
    version = subprocess.run([binary, "--version"], capture_output=True, text=True, timeout=10)
    if version.returncode:
        raise ValueError("Claude CLI 버전을 확인할 수 없습니다")
    names = inspect_material(repo, config_home)
    probe_helper(repo, output)
    allowed = ["Skill(" + name + ")" for name in names] + MCP_NAMES
    settings = {
        "permissions": {"allow": allowed, "deny": ["Bash", "Read", "Write", "Edit", "Agent", "Task", "WebFetch", "WebSearch"]},
        "autoMemoryEnabled": False,
        "disableClaudeAiConnectors": True,
        "env": {"ENABLE_CLAUDEAI_MCP_SERVERS": "false", "CLAUDE_CODE_SYNC_SKILLS": "false"},
    }
    # 인증·개인 도구 설정은 복사하지 않는다. 기존 모델 선호만 보존한다.
    user_settings = config_home / "settings.json"
    if user_settings.exists():
        preference = json.loads(user_settings.read_text())
        if not isinstance(preference, dict):
            raise ValueError("Claude 개인 모델 설정을 확인할 수 없습니다")
        for key in ("model", "effortLevel"):
            if key in preference:
                if not isinstance(preference[key], str):
                    raise ValueError("Claude 모델 설정 형식이 올바르지 않습니다: " + key)
                settings[key] = preference[key]
    mcp = {"mcpServers": {"fixture": {"type": "stdio", "command": sys.executable,
           "args": [str(HERE / "fixture_tools.py"), str(repo), str(output / "fixture-tools.jsonl")]}}}
    settings_path, mcp_path = output / "claude-settings.json", output / "claude-mcp.json"
    dump(settings_path, settings)
    dump(mcp_path, mcp)
    command = [binary, "-p", "--output-format", "stream-json", "--verbose", "--no-session-persistence",
               "--tools", "Skill", "--allowedTools", ",".join(allowed), "--disallowedTools", "Bash,Read,Write,Edit,Agent,Task,WebFetch,WebSearch",
               "--permission-mode", "dontAsk", "--setting-sources", "project", "--settings", str(settings_path),
               "--strict-mcp-config", "--mcp-config", str(mcp_path), "--no-chrome"]
    metadata = {"cli_version": version.stdout.strip(), "mode": "native print; Skill + 임시 문서 MCP; 개인 도구 설정 제외, 기존 로그인·관리자 정책 유지",
                "model_settings": {key: settings[key] for key in ("model", "effortLevel") if key in settings},
                "limitations": "일반 Markdown·고정 작업 기록 사례만 지원; 네이티브 Bash/파일 도구·동적 스킬·추가 에이전트 평가는 포함하지 않음"}
    return command, metadata


def collect_events(output):
    events, errors, messages, usage = [], [], [], []
    for number, line in enumerate((output / "events.jsonl").read_text().splitlines(), 1):
        try:
            event = json.loads(line)
            if not isinstance(event, dict):
                raise ValueError("이벤트 object 필요")
            events.append(event)
        except ValueError:
            errors.append(f"Claude 이벤트 {number}행을 해석할 수 없습니다")
    for event in events:
        if event.get("type") == "assistant":
            message = event.get("message", {})
            blocks = message.get("content", []) if isinstance(message, dict) else []
            if isinstance(blocks, list):
                texts = [block["text"] for block in blocks if isinstance(block, dict)
                         and block.get("type") == "text" and isinstance(block.get("text"), str)]
                if texts:
                    messages.append("\n".join(texts))
    results = [event for event in events if event.get("type") == "result"]
    models = [event.get("model") for event in events if event.get("type") == "system" and event.get("subtype") == "init"]
    for result in results:
        usage.append({"usage": result.get("usage"), "modelUsage": result.get("modelUsage"),
                      "total_cost_usd_estimate": result.get("total_cost_usd"), "models": models})
    if not messages and results and isinstance(results[-1].get("result"), str):
        messages.append(results[-1]["result"])
    (output / "answer.md").write_text("\n\n".join(messages), encoding="utf-8")
    complete = bool(results and results[-1].get("subtype") == "success" and not results[-1].get("is_error")
                    and any(message.strip() for message in messages) and not errors)
    return complete, errors, usage
