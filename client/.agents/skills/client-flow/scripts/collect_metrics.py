#!/usr/bin/env python3
"""Collect numeric client-flow metrics without changing the working tree."""

import argparse
import datetime as dt
import fcntl
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import tempfile

BRANCH = "refs/heads/client/metrics"
ISSUE = re.compile(r"#(\d+)(?:-|\b)")
SKIP = ("시험 조건을 풀어요",)


def run(*args, timeout=20, check=False, env=None, input=None):
    return subprocess.run(args, input=input, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                          timeout=timeout, check=check, env=env)


def typed(d):
    if d.get("type") != "user" or d.get("isSidechain") or d.get("isMeta") or d.get("isCompactSummary"):
        return None
    c = (d.get("message") or {}).get("content")
    if isinstance(c, list):
        if any(isinstance(b, dict) and b.get("type") == "tool_result" for b in c):
            return None
        texts = [b.get("text", "") for b in c if isinstance(b, dict) and b.get("type") == "text"]
    elif isinstance(c, str):
        texts = [c]
    else:
        return None
    return next((t.strip() for t in texts if t.strip() and not t.lstrip().startswith(("<", "[Request interrupted"))), None)


def flow_issue(d):
    if d.get("type") != "user" or d.get("isSidechain"):
        return None
    content = (d.get("message") or {}).get("content")
    if not isinstance(content, str):
        return None
    command = re.search(r"<command-name>\s*/client-flow\s*</command-name>(?:\s*<command-args>\s*#(\d+))?", content)
    if command:
        return command.group(1) or ""
    direct = re.search(r"(?m)^\s*/client-flow(?:\s+#(\d+))?(?:\s|$)", content)
    if direct:
        return direct.group(1) or ""
    # A handoff can ask to proceed with the skill in the middle of a sentence.
    requested = re.search(r"/client-flow\s+로\s+진행해", content)
    return "" if requested else None


def human_event(d):
    if d.get("type") != "user" or d.get("isSidechain"):
        return False
    c = (d.get("message") or {}).get("content")
    if isinstance(c, str):
        return not c.startswith("<")
    if isinstance(c, list):
        return (any(isinstance(b, dict) and b.get("type") == "text" and not b.get("text", "").startswith("<") for b in c)
                and not any(isinstance(b, dict) and b.get("type") == "tool_result" for b in c))
    return False


def weight(usage):
    return (usage.get("input_tokens", 0) + usage.get("cache_creation_input_tokens", 0) * 1.25
            + usage.get("cache_read_input_tokens", 0) * .1 + usage.get("output_tokens", 0) * 5)


def stamp(value):
    return dt.datetime.fromisoformat(value.replace("Z", "+00:00"))


def human_count(rows, start, end):
    ask = set()
    items = []
    for d in rows:
        t = d.get("timestamp")
        if not t or t < start or (end and t > end):
            continue
        c = (d.get("message") or {}).get("content")
        if d.get("type") == "assistant" and isinstance(c, list):
            ask.update(b.get("id") for b in c if isinstance(b, dict) and b.get("type") == "tool_use" and b.get("name") == "AskUserQuestion")
        if d.get("type") == "user" and isinstance(c, list):
            for b in c:
                if isinstance(b, dict) and b.get("type") == "tool_result" and b.get("tool_use_id") in ask and not b.get("is_error"):
                    items.append((t, "[question]", True))
        x = typed(d)
        if x:
            items.append((t, x, False))
    count = 0
    for i, (t, x, question) in enumerate(items):
        if question:
            count += 1
            continue
        if t == start or x == "재개" or any(s in x for s in SKIP):
            continue
        nxt = next((y for _, y, q in items[i + 1:] if not q), None)
        if nxt is not None and (nxt == x or nxt.startswith(x)):
            continue
        count += 1
    return count


def parse_session(fp, issue_hint=None):
    main = []
    events = []
    seen = set()
    flow = None
    issue = issue_hint
    pr_time = None
    pr_number = None
    paths = [fp] + list(Path(str(fp)[:-6], "subagents").glob("*.jsonl"))
    for path in paths:
        is_main = path == fp
        try:
            lines = path.open(errors="ignore")
        except OSError:
            continue
        with lines:
            for line in lines:
                try:
                    d = json.loads(line)
                except (ValueError, TypeError):
                    continue
                t = d.get("timestamp")
                ty = d.get("type")
                if is_main:
                    main.append(d)
                    if ty == "pr-link" and pr_time is None:
                        pr_time = t
                        pr_number = d.get("prNumber")
                    if flow is None and ty == "user":
                        invoked_issue = flow_issue(d)
                        if invoked_issue is not None:
                            flow = t
                            issue = int(invoked_issue) if invoked_issue else issue
                if not t or ty not in ("user", "assistant"):
                    continue
                tok = 0
                if ty == "assistant":
                    msg = d.get("message") or {}
                    mid = msg.get("id")
                    if mid and mid not in seen:
                        seen.add(mid)
                        tok = weight(msg.get("usage") or {})
                events.append((t, is_main and human_event(d), tok))
    if not flow or not issue:
        return None
    events.sort(key=lambda x: x[0])
    def part(start, end):
        subset = [x for x in events if x[0] >= start and (end is None or x[0] <= end)]
        ai = 0.0
        for a, b in zip(subset, subset[1:]):
            gap = (stamp(b[0]) - stamp(a[0])).total_seconds()
            if 0 <= gap <= 600 and not b[1]:
                ai += gap
        return {"ai_min": round(ai / 60, 1), "weighted_tokens": round(sum(x[2] for x in subset)),
                "calls": sum(1 for x in subset if x[2]), "humans": human_count(main, start, end)}
    return {"issue": int(issue), "pr": int(pr_number) if pr_number else None,
            "pre": part(flow, pr_time), "post": part(pr_time, None) if pr_time else None}


def fingerprint(cwd):
    top = run("git", "-C", str(cwd), "rev-parse", "--show-toplevel", check=True).stdout.decode().strip()
    tree = run("git", "-C", top, "ls-tree", "-r", "HEAD", "--", "client/.agents", "client/.claude",
               "client/.codex", "client/AGENTS.md", "client/CLAUDE.md", check=True).stdout
    return run("git", "hash-object", "--stdin", input=tree, check=True).stdout.decode().strip()


def put(remote, relpath, payload):
    with tempfile.TemporaryDirectory(prefix="chalkak-metrics-git-") as temp:
        gitdir = Path(temp, "repo.git")
        run("git", "init", "--bare", "-q", str(gitdir), check=True)
        cmd = ("git", f"--git-dir={gitdir}")
        blob = run(*cmd, "hash-object", "-w", "--stdin", input=payload, check=True).stdout.decode().strip()
        for _ in range(4):
            run(*cmd, "fetch", "-q", remote, f"+{BRANCH}:{BRANCH}")
            base = run(*cmd, "rev-parse", "-q", "--verify", BRANCH)
            with tempfile.NamedTemporaryFile(dir=temp) as idx:
                env = dict(os.environ, GIT_INDEX_FILE=idx.name)
                if base.returncode == 0:
                    run(*cmd, "read-tree", base.stdout.decode().strip(), check=True, env=env)
                else:
                    run(*cmd, "read-tree", "--empty", check=True, env=env)
                run(*cmd, "update-index", "--add", "--cacheinfo", f"100644,{blob},{relpath}", check=True, env=env)
                tree = run(*cmd, "write-tree", check=True, env=env).stdout.decode().strip()
            args = (*cmd, "commit-tree", tree)
            if base.returncode == 0:
                args += ("-p", base.stdout.decode().strip())
            commit = run(*args, "-m", "chore: collect client metrics", check=True).stdout.decode().strip()
            pushed = run(*cmd, "push", "-q", remote, f"{commit}:{BRANCH}")
            if pushed.returncode == 0:
                return True
    return False


def collect(hook, home, remote_override=None):
    fp = Path(hook.get("transcript_path", ""))
    cwd = Path(hook.get("cwd", ""))
    if not fp.is_file() or not cwd.is_dir():
        return
    issue_hint = None
    branch = run("git", "-C", str(cwd), "branch", "--show-current").stdout.decode().strip()
    m = ISSUE.search(branch)
    if m:
        issue_hint = int(m.group(1))
    result = parse_session(fp, issue_hint)
    if result is None:
        return
    home.mkdir(parents=True, exist_ok=True)
    identity = home / "anonymous_id"
    if not identity.exists():
        try:
            with identity.open("x") as f:
                f.write(secrets.token_hex(16))
        except FileExistsError:
            pass
    anon = identity.read_text().strip()
    sid = hook.get("session_id") or fp.stem
    if not re.fullmatch(r"[a-fA-F0-9-]{32,36}", sid):
        return
    result.update(schema=1, anonymous_id=anon, session_id=sid, fingerprint=fingerprint(cwd))
    outbox = home / "outbox"
    outbox.mkdir(exist_ok=True)
    path = outbox / f"{anon}-{sid}.json"
    temp = path.with_suffix(".tmp")
    temp.write_text(json.dumps(result, separators=(",", ":")))
    temp.replace(path)
    remote = remote_override or run("git", "-C", str(cwd), "remote", "get-url", "origin").stdout.decode().strip()
    if not remote:
        return
    with (home / "push.lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        for queued in sorted(outbox.glob("*.json")):
            try:
                data = json.loads(queued.read_text())
                rel = f"{data['anonymous_id']}/{data['session_id']}.json"
                if put(remote, rel, queued.read_bytes()):
                    queued.unlink()
                else:
                    break
            except (OSError, ValueError, KeyError, subprocess.SubprocessError):
                break


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path, help="Claude Stop JSON input")
    args = parser.parse_args()
    enabled = run("git", "config", "--global", "--get", "chalkak.metrics").stdout.decode().strip()
    if enabled != "on":
        return
    try:
        hook = json.loads(args.input.read_text())
        collect(hook, Path(os.environ.get("CHALKAK_METRICS_HOME", Path.home() / ".config/chalkak/metrics")),
                os.environ.get("CHALKAK_METRICS_REMOTE"))
    finally:
        args.input.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
