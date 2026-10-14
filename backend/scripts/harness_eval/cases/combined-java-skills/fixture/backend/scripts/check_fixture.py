"""실제 JDK/JUnit 검사. 네트워크·Gradle·캐시 변경 없이 임시 자료 안에서 실행한다."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import sys

backend = Path(__file__).resolve().parents[1]
runtime = backend.parent / ".eval/runtime"
config = json.loads((runtime / "config.json").read_text())
if not config["available"]:
    sys.exit("검사 불가: " + ", ".join(config["missing"]))
if sys.argv[1:] != ["test"]:
    sys.exit("사용법: python3 scripts/check_fixture.py test")
jars = []
for artifact in config["jars"]:
    path = runtime / "jars" / artifact["name"]
    if hashlib.sha256(path.read_bytes()).hexdigest() != artifact["sha256"]:
        sys.exit("실행 의존성이 변경되었습니다")
    jars.append(str(path))
classes = backend / "build/fixture-classes"
classes.mkdir(parents=True, exist_ok=True)
classpath = os.pathsep.join(jars)
sources = sorted(backend.glob("src/main/java/**/*.java")) + sorted(backend.glob("src/test/java/**/*.java"))
sources.append(backend / "scripts/FixtureTestLauncher.java")
java_bin = Path(config["java_home"]) / "bin"
compiled = subprocess.run([str(java_bin / "javac"), "-encoding", "UTF-8", "-cp", classpath,
                           "-d", str(classes), *map(str, sources)], check=False)
if compiled.returncode:
    sys.exit(compiled.returncode)
sys.exit(subprocess.run([str(java_bin / "java"), "-cp", str(classes) + os.pathsep + classpath,
                        "FixtureTestLauncher"], check=False).returncode)
