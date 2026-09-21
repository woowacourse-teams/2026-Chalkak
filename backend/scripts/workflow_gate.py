#!/usr/bin/env python3
"""이전 이슈·PR의 실제 GitHub 상태를 읽는다. 종료·병합·개발은 실행하지 않는다.

0: 병합 및 이슈 종료 확인(완료 조건의 의미 대조는 별도)
1: 대기/불일치, 2: 병합했으나 이슈 열림(완료 조건 검토 후 종료 필요), 3: 조회 불가
"""
from __future__ import annotations

import argparse
from datetime import datetime
import json
import re
import subprocess
import sys
from urllib.parse import urlsplit


def positive(value):
    number = int(value)
    if number <= 0:
        raise argparse.ArgumentTypeError("양의 이슈·PR 번호가 필요합니다")
    return number


def read_api(repo, resource):
    result = subprocess.run(
        ["gh", "api", "--method", "GET", f"repos/{repo}/{resource}"],
        text=True, capture_output=True, timeout=30, check=True,
    )
    data = json.loads(result.stdout)
    if not isinstance(data, dict):
        raise ValueError("GitHub 응답은 객체여야 합니다")
    return data


def evaluate(repo, issue_number, pr_number, base, issue, pr):
    def blocked(reason):
        return {"status": "BLOCKED", "reason": reason}, 1

    for data, kind, number in ((issue, "issues", issue_number), (pr, "pull", pr_number)):
        if not isinstance(data, dict) or type(data.get("number")) is not int or data["number"] != number:
            return blocked("요청한 이슈·PR 번호와 응답이 다릅니다")
        url = urlsplit(data.get("html_url", ""))
        if url.scheme != "https" or url.netloc != "github.com" or url.path.lower() != f"/{repo}/{kind}/{number}".lower():
            return blocked("요청한 저장소·대상과 응답 URL이 다릅니다")
        if data.get("state") not in ("open", "closed") or not isinstance(data.get("body"), str):
            return blocked("상태·본문을 확인할 수 없습니다")
    if "pull_request" in issue:
        return blocked("작업 이슈 대신 PR 번호가 지정되었습니다")
    target = pr.get("base")
    if not isinstance(target, dict) or target.get("ref") != base or (target.get("repo") or {}).get("full_name", "").lower() != repo.lower():
        return blocked("PR이 합의한 저장소·통합 브랜치를 대상으로 하지 않습니다")
    if pr.get("merged") is not True or not pr.get("merged_at") or pr["state"] != "closed":
        return blocked("이전 PR이 병합되지 않았습니다; 닫힘만으로 병합을 인정하지 않습니다")
    try:
        merged_at = datetime.fromisoformat(pr["merged_at"].replace("Z", "+00:00"))
        if merged_at.tzinfo is None:
            return blocked("병합 시각의 시간대를 확인할 수 없습니다")
    except (ValueError, TypeError, AttributeError):
        return blocked("병합 시각을 확인할 수 없습니다")
    # 담당 이슈 링크는 기존 팀 양식의 지정 구역에서만 인정한다.
    # 코드 블록·주석은 근거로 사용하지 않으며 복잡한 양식은 수동 정정 후 재조회한다.
    body = re.sub(r"<!--.*?-->", "", pr["body"], flags=re.S)
    body = re.sub(r"(?ms)^\s*(`{3,}|~{3,}).*?^\s*\1\s*$", "", body)
    sections = re.findall(r"(?m)^## [^\n]*연관된 이슈[^\n]*\n(.*?)(?=^## |\Z)", body, re.S)
    if len(sections) != 1 or not re.search(rf"(?m)^- #{issue_number}[ \t]*$", sections[0]):
        return blocked("PR의 연관된 이슈 구역에 담당 이슈 연결이 없습니다")
    if issue["state"] == "closed" and issue.get("state_reason") != "completed":
        return blocked("이슈가 완료 사유로 종료되지 않았습니다")
    opened = issue["state"] == "open"
    return {
        "status": "MERGED_ISSUE_OPEN" if opened else "MERGED_ISSUE_CLOSED",
        "issue": issue["html_url"], "pr": pr["html_url"], "merged_at": pr["merged_at"],
        "issue_body": issue["body"], "pr_body": pr["body"],
        "completion_review_required": True,
        "reason": "실제 완료 조건·diff·검증 근거 대조는 별도입니다. 열린 이슈는 검토 후 종료·재조회하세요.",
    }, 2 if opened else 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, help="확인된 GitHub OWNER/REPO")
    parser.add_argument("--issue", required=True, type=positive)
    parser.add_argument("--pr", required=True, type=positive)
    parser.add_argument("--base", required=True)
    args = parser.parse_args(argv)
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repo) or not args.base.strip():
        parser.error("OWNER/REPO와 합의한 base가 필요합니다")
    try:
        issue = read_api(args.repo, f"issues/{args.issue}")
        pr = read_api(args.repo, f"pulls/{args.pr}")
        result, code = evaluate(args.repo, args.issue, args.pr, args.base, issue, pr)
    except (OSError, ValueError, TypeError, AttributeError, subprocess.SubprocessError):
        result, code = {"status": "UNAVAILABLE", "reason": "GitHub 상태 조회·해석 실패; 다음 작업을 시작하지 마세요."}, 3
    print(json.dumps(result, ensure_ascii=False))
    return code


if __name__ == "__main__":
    sys.exit(main())
