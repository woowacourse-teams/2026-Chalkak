"""Validate AI findings against a unified diff before constructing a GitHub review."""

import ast
import json
from pathlib import PurePosixPath
import re


HUNK = re.compile(r"^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@")
MAX_FINDINGS = 5


def git_path(value):
    if value.startswith('"'):
        value = ast.literal_eval(value)
        try:
            value = value.encode("latin1").decode("utf-8")
        except UnicodeEncodeError:
            pass
    if value == "/dev/null":
        return None
    value = value[2:] if value.startswith(("a/", "b/")) else value
    if not value or PurePosixPath(value).is_absolute() or ".." in PurePosixPath(value).parts:
        raise ValueError("올바르지 않은 diff 파일 경로")
    return value


def diff_locations(diff):
    """Only added/deleted lines are anchors; use the new path for renamed files."""
    locations = set()
    files = set()
    old_path = path = None
    old_line = new_line = 0
    old_left = new_left = 0
    for text in diff.splitlines():
        if text.startswith("diff --git "):
            if old_left or new_left:
                raise ValueError("잘린 diff hunk")
            old_path = path = None
        elif old_left or new_left:
            if text.startswith("\\ No newline"):
                continue
            if text.startswith("+"):
                locations.add((path, "RIGHT", new_line))
                new_line += 1
                new_left -= 1
            elif text.startswith("-"):
                locations.add((path, "LEFT", old_line))
                old_line += 1
                old_left -= 1
            elif text.startswith(" "):
                old_line += 1
                new_line += 1
                old_left -= 1
                new_left -= 1
            else:
                raise ValueError("올바르지 않은 diff hunk")
            if old_left < 0 or new_left < 0:
                raise ValueError("diff 줄 수 불일치")
        elif text.startswith("--- "):
            old_path = git_path(text[4:])
        elif text.startswith("+++ "):
            path = git_path(text[4:]) or old_path
            files.add(path)
        elif match := HUNK.match(text):
            if path is None:
                raise ValueError("hunk에 파일 경로가 없습니다")
            old_line, old_count, new_line, new_count = match.groups()
            old_line, new_line = int(old_line), int(new_line)
            old_left = int(old_count) if old_count is not None else 1
            new_left = int(new_count) if new_count is not None else 1
    if old_left or new_left:
        raise ValueError("잘린 diff hunk")
    return locations, files


def nonempty(value, field, limit=6000):
    if not isinstance(value, str) or not value.strip() or len(value) > limit:
        raise ValueError(f"리뷰 {field} 형식 오류")
    # Remote markers belong exclusively to the watcher, never model output.
    if "<!--" in value:
        raise ValueError("리뷰에 숨겨진 HTML 표시는 허용하지 않습니다")
    return value.strip()


def make_payload(output, diff, changed_paths, head_sha, review_marker):
    """Fail the whole result on malformed/invalid anchors; never fall back to body."""
    data = json.loads(output)
    if not isinstance(data, dict) or set(data) != {"findings", "limitations"}:
        raise ValueError("리뷰는 findings·limitations JSON 객체여야 합니다")
    findings = data["findings"]
    limitations = data["limitations"]
    if not isinstance(findings, list) or len(findings) > MAX_FINDINGS:
        raise ValueError("리뷰는 최대 5개 항목이어야 합니다")
    if not isinstance(limitations, list) or len(limitations) > 5:
        raise ValueError("검토 한계는 최대 5개여야 합니다")
    locations, _ = diff_locations(diff)
    comments, common = [], []
    seen = set()
    for finding in findings:
        fields = {"severity", "title", "condition", "impact", "suggestion", "location", "unanchored_reason"}
        if not isinstance(finding, dict) or set(finding) != fields:
            raise ValueError("리뷰 항목 필드가 올바르지 않습니다")
        if finding["severity"] not in {"수정 필수", "확인 필요"}:
            raise ValueError("리뷰 중요도가 올바르지 않습니다")
        title = nonempty(finding["title"], "title", 200)
        parts = [nonempty(finding[field], field) for field in ("condition", "impact", "suggestion")]
        body = f"**[{finding['severity']}] {title}**\n\n" + "\n\n".join(parts)
        if body in seen:
            raise ValueError("중복된 리뷰 항목")
        seen.add(body)
        location = finding["location"]
        if location is None:
            reason = nonempty(finding["unanchored_reason"], "unanchored_reason", 1000)
            common.append(body + "\n\n코드 위치를 지정하지 못한 이유: " + reason)
            continue
        if not isinstance(location, dict) or set(location) != {"path", "line", "side"}:
            raise ValueError("리뷰 위치 형식 오류")
        path, line, side = location["path"], location["line"], location["side"]
        if (not isinstance(path, str) or path not in changed_paths
                or type(line) is not int or line <= 0 or not isinstance(side, str)
                or (path, side, line) not in locations):
            raise ValueError(f"실제 변경 줄이 아닌 리뷰 위치: {location}")
        if finding["unanchored_reason"] is not None:
            raise ValueError("코드 댓글에는 위치 미지정 사유를 넣지 않습니다")
        comments.append({"path": path, "line": line, "side": side, "body": body})
    body = (f"코드 댓글 {len(comments)}건 · 공통 문제 {len(common)}건"
            if findings else "발견된 문제 없음")
    if common:
        body += "\n\n" + "\n\n---\n\n".join(common)
    if limitations:
        body += "\n\n검토 한계:\n" + "\n".join("- " + nonempty(item, "limitations", 1000) for item in limitations)
    body += "\n\n" + review_marker
    if len(body) > 50_000 or any(len(item["body"]) > 50_000 for item in comments):
        raise ValueError("리뷰가 게시 제한에 비해 너무 깁니다")
    return {"event": "COMMENT", "commit_id": head_sha, "body": body, "comments": comments}
