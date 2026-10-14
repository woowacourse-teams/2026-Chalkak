from pathlib import Path
import sys
text = Path("README.md").read_text()
assert "JDK 25" in text, "JDK 25 안내 누락"
assert "./gradlew bootRun" in text, "기존 실행 명령 누락"
assert "macOS에서도 같은 실행 명령" in text, "사용자 메모 누락"
print("README 검증 통과")
