#!/usr/bin/env python3
"""Publish a validated shared bundle using an isolated Git index; intended for CI."""
from __future__ import annotations

import argparse
import json
import sys
import os
from pathlib import Path
import subprocess
import tempfile

import manage

BRANCH = "harness/shared"
SOURCE_BRANCH = "be/develop"


def git(source, *args, index=None, input_data=None):
    env = dict(os.environ, GIT_TERMINAL_PROMPT="0")
    if index:
        env["GIT_INDEX_FILE"] = str(index)
    result = subprocess.run(["git", *args], cwd=source, env=env, input=input_data,
                            capture_output=True, timeout=60)
    if result.returncode:
        raise ValueError("게시 Git 명령 실패: " + result.stderr.decode(errors="replace").strip())
    return result.stdout


def ancestor(source, older, newer):
    result = subprocess.run(["git", "merge-base", "--is-ancestor", older, newer], cwd=source,
                            capture_output=True, timeout=60)
    if result.returncode not in (0, 1):
        raise ValueError("배포 원본의 Git 이력을 확인할 수 없습니다. 전체 이력을 fetch하세요")
    return result.returncode == 0


def publish(source):
    source = source.resolve()
    head = git(source, "rev-parse", "HEAD").decode().strip()
    # Both refs are fixed. Only integrated source commits can be published.
    git(source, "fetch", "--no-tags", "origin", "refs/heads/" + SOURCE_BRANCH)
    source_tip = git(source, "rev-parse", "FETCH_HEAD").decode().strip()
    if not ancestor(source, head, source_tip):
        raise ValueError(SOURCE_BRANCH + "에 포함되지 않은 커밋은 게시할 수 없습니다")
    destination = "refs/heads/" + BRANCH
    existing = git(source, "ls-remote", "--heads", "origin", destination).decode().strip()
    parent = None
    previous = None
    if existing:
        git(source, "fetch", "--no-tags", "origin", destination)
        parent = git(source, "rev-parse", "FETCH_HEAD").decode().strip()
        previous = json.loads(git(source, "show", parent + ":manifest.json"))
        previous_source = previous["source_commit"]
        if previous_source == head:
            return {"published": False, "reason": "같은 원본 커밋이 이미 게시됨", "release_commit": parent}
        if ancestor(source, head, previous_source):
            return {"published": False, "reason": "더 최신 원본이 이미 게시됨", "release_commit": parent}
        if not ancestor(source, previous_source, head):
            raise ValueError("배포 원본 이력이 갈라졌습니다. 자동 덮어쓰기를 중단합니다")
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        package = root / "package"
        manifest = manage.export(source, package)
        index = root / "index"
        # No checkout, reset, clean or branch switch in the developer's source tree.
        git(source, "read-tree", "--empty", index=index)
        for path in sorted(package.rglob("*")):
            if not path.is_file():
                continue
            name = path.relative_to(package).as_posix()
            blob = git(source, "hash-object", "-w", "--stdin", input_data=path.read_bytes()).decode().strip()
            git(source, "update-index", "--add", "--cacheinfo", "100644", blob, name, index=index)
        tree = git(source, "write-tree", index=index).decode().strip()
        args = ["-c", "user.name=github-actions[bot]", "-c", "user.email=41898282+github-actions[bot]@users.noreply.github.com",
                "-c", "commit.gpgsign=false", "commit-tree", tree]
        if parent:
            args.extend(["-p", parent])
        args.extend(["-m", "chore: 공통 하네스 배포 " + head[:12]])
        release = git(source, *args).decode().strip()
        # Regular fast-forward push only. Concurrent updates fail; no retry/force overwrite.
        git(source, "push", "origin", release + ":" + destination)
        return {"published": True, "source_commit": head, "version": manifest["version"],
                "release_commit": release, "branch": BRANCH}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = publish(args.source)
        print(manage.json_bytes(result).decode(), end="")
        summary = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary:
            with open(summary, "a", encoding="utf-8") as stream:
                stream.write("## 공통 하네스 게시\n\n```json\n" + manage.json_bytes(result).decode() + "```\n")
        return 0
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as exc:
        print("공통 하네스 게시 실패: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
