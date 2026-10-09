#!/usr/bin/env python3
"""하네스 변경의 필수 자동 검사. 실제 AI·외부 네트워크는 호출하지 않는다."""

from pathlib import Path
import subprocess
import sys

BACKEND = Path(__file__).resolve().parents[1]


def commands(python=sys.executable):
    return [
        [python, "scripts/check_harness.py"],
        [python, "-B", "-m", "unittest", "discover", "-s", "scripts", "-p", "*_test.py"],
        [python, "-B", "-m", "unittest", "discover", "-s", "scripts/harness_eval", "-p", "*_test.py"],
        [python, "-B", "-m", "unittest", "discover", "-s", "scripts/shared_harness", "-p", "test_*.py"],
        [python, "-B", "-m", "unittest", "discover", "-s", "scripts/pr-review", "-p", "*_test.py"],
        [python, "scripts/harness_eval/run.py", "prepare", "--platform", "both"],
    ]


def main():
    failed = []
    for command in commands():
        print("실행: " + " ".join(command), flush=True)
        result = subprocess.run(command, cwd=BACKEND, check=False)
        if result.returncode:
            failed.append((command, result.returncode))
    if failed:
        for command, code in failed:
            print(f"검증 미완료 (exit {code}): {' '.join(command)}", file=sys.stderr)
        return 1
    print("하네스 자동 검사 전체 통과. 동작 변경의 실제 AI 평가는 별도 필수 절차입니다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
