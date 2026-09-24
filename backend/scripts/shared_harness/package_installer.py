"""Build one reviewable installer from an explicit committed revision; no network."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess


def package(source, commit, output):
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("검토한 전체 40자리 커밋 SHA가 필요합니다")

    def read(name):
        return subprocess.run(
            ["git", "show", f"{commit}:backend/scripts/shared_harness/{name}"],
            cwd=source, capture_output=True, check=True, timeout=30).stdout

    template = read("install.sh").decode()
    values = {"SOURCE_COMMIT": commit}
    for name in ("manage", "automatic"):
        values[name.upper() + "_SHA256"] = hashlib.sha256(read(name + ".py")).hexdigest()
    for key, value in values.items():
        marker = "@" + key + "@"
        if template.count(marker) != 1:
            raise ValueError("해당 커밋은 고정 버전 설치기 템플릿이 아닙니다: " + key)
        template = template.replace(marker, value)
    output.parent.mkdir(parents=True, exist_ok=True)
    # Never overwrite a source template or an already delivered installer.
    with output.open("x") as stream:
        stream.write(template)
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path.cwd())
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    values = package(args.source, args.source_commit, args.output)
    print("설치기 생성:", args.output)
    for key, value in values.items():
        print(key + "=" + value)


if __name__ == "__main__":
    main()
