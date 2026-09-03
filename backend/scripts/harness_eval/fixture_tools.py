#!/usr/bin/env python3
"""Claude 평가용 작은 stdio MCP. 임시 저장소의 문서와 고정 Git 조회만 제공한다."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys


LIMIT = 1_000_000
OPERATIONS = {
    "root": ["rev-parse", "--show-toplevel"],
    "status": ["status", "--short"],
    "diff": ["diff", "--no-ext-diff", "--no-textconv", "--"],
    "head-diff": ["diff", "--no-ext-diff", "--no-textconv", "HEAD", "--"],
    "branch": ["branch", "--show-current"],
    "branches": ["branch", "--list"],
    "remote": ["remote", "-v"],
    "history": ["log", "-5", "--oneline", "--no-show-signature"],
}


def schema(properties, required):
    return {"type": "object", "properties": properties, "required": required, "additionalProperties": False}


TOOLS = [
    {"name": "read", "description": "Read a UTF-8 file inside this repository. Paths are relative to the repository root, for example backend/README.md or .github/ISSUE_TEMPLATE/docs.md.",
     "inputSchema": schema({"path": {"type": "string"}}, ["path"])},
    {"name": "list", "description": "List files and directories inside this repository, excluding Git internals. Empty path lists the repository root.",
     "inputSchema": schema({"path": {"type": "string"}}, [])},
    {"name": "write", "description": "Write complete UTF-8 Markdown content under backend/. Harness instructions, hidden directories, and configuration files are protected.",
     "inputSchema": schema({"path": {"type": "string"}, "content": {"type": "string"}}, ["path", "content"])},
    {"name": "git_read", "description": "Inspect this repository with one fixed read-only Git operation. No shell, arbitrary arguments, commit, branch creation, push or network operations are available.",
     "inputSchema": schema({"operation": {"type": "string", "enum": list(OPERATIONS)}}, ["operation"])},
]


class FixtureTools:
    def __init__(self, root, audit):
        self.root = Path(root).resolve(strict=True)
        self.audit = Path(audit).resolve()
        if not (self.root / ".git").is_dir() or self.audit.is_relative_to(self.root):
            raise ValueError("독립 Git 저장소와 저장소 밖의 도구 기록 경로가 필요합니다")

    def path(self, relative):
        if not isinstance(relative, str):
            raise ValueError("path는 문자열이어야 합니다")
        parts = Path(relative).parts
        if Path(relative).is_absolute() or ".." in parts or any(part.lower() == ".git" for part in parts):
            raise ValueError("저장소 상대 경로만 허용하며 Git 내부 파일은 접근할 수 없습니다")
        target = self.root / relative
        current = self.root
        for part in parts:
            current = current / part
            if current.is_symlink():
                raise ValueError("심볼릭 링크를 통한 접근은 허용하지 않습니다")
        if not target.resolve().is_relative_to(self.root):
            raise ValueError("임시 저장소 밖의 경로는 접근할 수 없습니다")
        return target

    def invoke(self, name, arguments):
        declaration = next((tool for tool in TOOLS if tool["name"] == name), None)
        if declaration is None or not isinstance(arguments, dict):
            raise ValueError("지원하지 않는 도구 요청입니다")
        expected = declaration["inputSchema"]
        if set(arguments) - set(expected["properties"]) or set(expected["required"]) - set(arguments):
            raise ValueError("도구 인자가 올바르지 않습니다")
        if name == "git_read":
            operation = arguments["operation"]
            if not isinstance(operation, str) or operation not in OPERATIONS:
                raise ValueError("고정된 Git 조회만 허용합니다")
            executable = shutil.which("git")
            if not executable:
                raise ValueError("Git을 찾을 수 없습니다")
            environment = {"PATH": os.environ.get("PATH", ""), "LANG": "C.UTF-8", "GIT_CONFIG_GLOBAL": os.devnull,
                           "GIT_CONFIG_NOSYSTEM": "1", "GIT_OPTIONAL_LOCKS": "0", "GIT_TERMINAL_PROMPT": "0"}
            result = subprocess.run([executable, "--no-pager", *OPERATIONS[operation]], cwd=self.root,
                                    env=environment, capture_output=True, text=True, timeout=10)
            if result.returncode:
                raise ValueError("Git 조회 실패: " + result.stderr[:2000])
            return result.stdout[:LIMIT]
        target = self.path(arguments.get("path", ""))
        if name == "read":
            if not target.is_file() or target.stat().st_size > LIMIT:
                raise ValueError("작은 일반 텍스트 파일만 읽을 수 있습니다")
            return target.read_text(encoding="utf-8")
        if name == "list":
            if not target.is_dir():
                raise ValueError("디렉터리 경로가 필요합니다")
            return "\n".join(path.name + ("/" if path.is_dir() else "") for path in sorted(target.iterdir())
                             if path.name.lower() != ".git" and not path.is_symlink())
        relative = target.relative_to(self.root)
        if (not relative.parts or relative.parts[0] != "backend" or target.suffix.lower() != ".md"
                or any(part.startswith(".") for part in relative.parts)
                or target.name.lower() in ("agents.md", "claude.md", "claude.local.md")):
            raise ValueError("backend의 일반 Markdown 문서만 수정할 수 있습니다; 하네스·설정은 보호됩니다")
        content = arguments["content"]
        if not isinstance(content, str) or len(content.encode()) > LIMIT:
            raise ValueError("문서 내용은 1MB 이하 문자열이어야 합니다")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
        return "문서를 수정했습니다: " + relative.as_posix()

    def call(self, name, arguments):
        audit = {"tool": name, "arguments": {key: value for key, value in arguments.items() if key != "content"}
                 if isinstance(arguments, dict) else arguments}
        try:
            text = self.invoke(name, arguments)
            audit["ok"] = True
            result = {"content": [{"type": "text", "text": text}]}
        except (OSError, ValueError, UnicodeError, subprocess.SubprocessError) as exc:
            audit.update(ok=False, reason=str(exc))
            result = {"content": [{"type": "text", "text": str(exc)}], "isError": True}
        with self.audit.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(audit, ensure_ascii=False) + "\n")
        return result


def serve(root, audit, input_stream=sys.stdin, output_stream=sys.stdout):
    fixture = FixtureTools(root, audit)
    for line in input_stream:
        request_id = None
        try:
            if len(line.encode()) > LIMIT + 10000:
                raise ValueError("요청 크기 초과")
            request = json.loads(line)
            if not isinstance(request, dict):
                raise ValueError("JSON-RPC object가 필요합니다")
            if "id" not in request:
                continue
            request_id = request["id"]
            method = request.get("method")
            if method == "initialize":
                result = {"protocolVersion": "2024-11-05", "capabilities": {"tools": {}},
                          "serverInfo": {"name": "harness-fixture", "version": "1"}}
            elif method == "tools/list":
                result = {"tools": TOOLS}
            elif method == "tools/call":
                parameters = request.get("params", {})
                if not isinstance(parameters, dict):
                    raise ValueError("params object가 필요합니다")
                result = fixture.call(parameters.get("name"), parameters.get("arguments", {}))
            elif method == "ping":
                result = {}
            else:
                raise ValueError("지원하지 않는 MCP 메서드입니다")
            response = {"jsonrpc": "2.0", "id": request_id, "result": result}
        except (ValueError, TypeError) as exc:
            response = {"jsonrpc": "2.0", "id": request_id, "error": {"code": -32602, "message": str(exc)}}
        output_stream.write(json.dumps(response, ensure_ascii=False) + "\n")
        output_stream.flush()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path)
    parser.add_argument("audit", type=Path)
    options = parser.parse_args()
    serve(options.root, options.audit)
