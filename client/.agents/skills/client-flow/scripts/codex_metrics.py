#!/usr/bin/env python3
"""Silent Codex Stop hook for opt-in, numeric client-flow metrics.

Codex's transcript is an unstable internal format. Unknown records are ignored,
and a missing flow invocation or usage record produces no upload.
"""

import argparse
import fcntl
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import tempfile

from collect_metrics import fingerprint, put, run, stamp

FLOW = re.compile(r"(?:^|\n)\s*/client-flow\s*#(\d+)\b")
PR = re.compile(r"https://github\.com/woowacourse-teams/2026-Chalkak/pull/(\d+)\b")
UUID = re.compile(r"[a-fA-F0-9-]{32,36}")
COMMAND = re.compile(r"\bcmd[\"']?\s*:\s*([\"'])(.*?)(?<!\\)\1", re.S)
CREATE_PR = re.compile(r"(?:^|[;&\n])\s*gh\s+pr\s+create\b")


def human_text(payload):
    if payload.get("type") != "message" or payload.get("role") != "user":
        return None
    meta = payload.get("internal_chat_message_metadata_passthrough") or {}
    if "user.text" not in (meta.get("content_item_kinds") or []):
        return None
    return "\n".join(c.get("text", "") for c in payload.get("content", [])
                     if c.get("type") == "input_text")


def usage_weight(usage):
    # Cached input is already included in input_tokens in Codex's records.
    inp = usage.get("input_tokens", 0)
    cached = usage.get("cached_input_tokens", 0)
    return (inp - cached) + cached * .1 + usage.get("cache_write_input_tokens", 0) * 1.25 + usage.get("output_tokens", 0) * 5


def creates_pr(payload):
    name = payload.get("name") or ""
    if name in ("create_pull_request", "createPullRequest"):
        return True
    command = payload.get("arguments") or payload.get("input") or ""
    if not isinstance(command, str):
        return False
    return any(CREATE_PR.search(match.group(2)) for match in COMMAND.finditer(command))


def related_sessions(path):
    """Yield the main transcript and descendants linked by parent_thread_id."""
    yield path
    # The hook path is ~/.codex/sessions/<year>/<month>/<day>/<rollout>.jsonl.
    if len(path.parents) < 4 or path.parents[3].name != "sessions":
        return
    children = {}
    for candidate in path.parents[3].glob("**/*.jsonl"):
        if candidate == path:
            continue
        try:
            with candidate.open(errors="ignore") as stream:
                first = json.loads(stream.readline())
        except (OSError, ValueError):
            continue
        if first.get("type") != "session_meta":
            continue
        meta = first.get("payload") or {}
        parent = meta.get("parent_thread_id")
        if parent:
            children.setdefault(parent, []).append((meta.get("id"), candidate))
    try:
        with path.open(errors="ignore") as stream:
            root_id = (json.loads(stream.readline()).get("payload") or {}).get("id")
    except (OSError, ValueError):
        return
    if not root_id:
        return
    pending = [root_id]
    seen = {path}
    while pending:
        for child_id, candidate in children.get(pending.pop(), []):
            if candidate in seen:
                continue
            seen.add(candidate)
            yield candidate
            if child_id:
                pending.append(child_id)


def parse_session(path, issue_hint=None):
    records = []
    seen_responses = set()
    flow = None
    issue = issue_hint
    pr_time = None
    pr_number = None
    create_calls = set()
    for transcript in related_sessions(path):
        is_main = transcript == path
        try:
            lines = transcript.open(errors="ignore")
        except OSError:
            continue
        with lines:
            for line in lines:
                try:
                    row = json.loads(line)
                except ValueError:
                    continue
                at = row.get("timestamp")
                if not at:
                    continue
                kind = row.get("type")
                payload = row.get("payload") or {}
                if kind == "response_item":
                    text = human_text(payload)
                    if text is not None:
                        if is_main and flow is None:
                            match = FLOW.search(text)
                            if match:
                                flow, issue = at, int(match.group(1))
                        records.append((at, is_main, 0))
                    elif is_main and payload.get("type") in ("function_call", "custom_tool_call"):
                        if creates_pr(payload):
                            create_calls.add(payload.get("call_id"))
                        records.append((at, False, 0))
                    elif is_main and payload.get("type") in ("function_call_output", "custom_tool_call_output") and pr_time is None:
                        output = payload.get("output") or ""
                        match = PR.search(output if isinstance(output, str) and payload.get("call_id") in create_calls else "")
                        if match:
                            pr_time, pr_number = at, int(match.group(1))
                    else:
                        records.append((at, False, 0))
                elif kind == "token_usage_record":
                    rid = payload.get("response_id")
                    usage = payload.get("usage")
                    if rid and rid not in seen_responses and isinstance(usage, dict):
                        seen_responses.add(rid)
                        records.append((at, False, usage_weight(usage)))
    if flow is None or issue is None or not seen_responses:
        return None
    records.sort(key=lambda item: item[0])

    def part(start, end):
        items = [item for item in records if item[0] >= start and (end is None or item[0] < end)]
        elapsed = 0
        for before, after in zip(items, items[1:]):
            gap = (stamp(after[0]) - stamp(before[0])).total_seconds()
            if 0 <= gap <= 600 and not after[1]:
                elapsed += gap
        return {"ai_min": round(elapsed / 60, 1),
                "weighted_tokens": round(sum(item[2] for item in items)),
                "calls": sum(item[2] > 0 for item in items),
                "humans": sum(item[1] for item in items if item[0] != start)}

    return {"issue": issue, "pr": pr_number,
            "pre": part(flow, pr_time), "post": part(pr_time, None) if pr_time else None}


def collect(hook, home, remote_override=None):
    path = Path(hook.get("transcript_path", ""))
    cwd = Path(hook.get("cwd", ""))
    sid = hook.get("session_id", "")
    if not path.is_file() or not cwd.is_dir() or not UUID.fullmatch(sid):
        return
    branch = run("git", "-C", str(cwd), "branch", "--show-current").stdout.decode().strip()
    match = re.search(r"#(\d+)-", branch)
    result = parse_session(path, int(match.group(1)) if match else None)
    if result is None:
        return
    home.mkdir(parents=True, exist_ok=True)
    identity = home / "anonymous_id"
    if not identity.exists():
        try:
            with identity.open("x") as handle:
                handle.write(secrets.token_hex(16))
        except FileExistsError:
            pass
    anon = identity.read_text().strip()
    result.update(schema=1, anonymous_id=anon, session_id=sid, fingerprint=fingerprint(cwd))
    outbox = home / "outbox"
    outbox.mkdir(exist_ok=True)
    queued = outbox / f"{anon}-{sid}.json"
    with tempfile.NamedTemporaryFile(mode="w", dir=outbox, delete=False) as temp:
        json.dump(result, temp, separators=(",", ":"))
        name = temp.name
    Path(name).replace(queued)
    remote = remote_override or run("git", "-C", str(cwd), "remote", "get-url", "origin").stdout.decode().strip()
    if not remote:
        return
    with (home / "push.lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        for item in sorted(outbox.glob("*.json")):
            try:
                data = json.loads(item.read_text())
                relative = f"{data['anonymous_id']}/{data['session_id']}.json"
                if put(remote, relative, item.read_bytes()):
                    item.unlink()
                else:
                    break
            except (OSError, ValueError, KeyError, subprocess.SubprocessError):
                break


def main():
    if run("git", "config", "--global", "--get", "chalkak.metrics").stdout.decode().strip() != "on":
        return
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path)
    args = parser.parse_args()
    if args.input is None:
        # Stop waits only for the small input copy. Upload continues independently.
        raw = sys.stdin.buffer.read()
        try:
            json.loads(raw)
        except ValueError:
            return
        home = Path(os.environ.get("CHALKAK_METRICS_HOME", Path.home() / ".config/chalkak/metrics"))
        home.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(dir=home, prefix="codex-hook-", delete=False) as temp:
            temp.write(raw)
            name = temp.name
        subprocess.Popen([sys.executable, __file__, "--input", name], stdin=subprocess.DEVNULL,
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
        return
    try:
        hook = json.loads(args.input.read_text())
        collect(hook, Path(os.environ.get("CHALKAK_METRICS_HOME", Path.home() / ".config/chalkak/metrics")),
                os.environ.get("CHALKAK_METRICS_REMOTE"))
    finally:
        args.input.unlink(missing_ok=True)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # A metrics failure must never interrupt or print into the conversation.
        pass
