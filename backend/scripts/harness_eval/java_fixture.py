"""실제 JDK/JUnit을 사용하는 작은 오프라인 Java 평가 자료 준비."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

DEPENDENCIES = (
    ("org.junit.jupiter", "junit-jupiter-api", "5.12.2"),
    ("org.junit.jupiter", "junit-jupiter-engine", "5.12.2"),
    ("org.junit.jupiter", "junit-jupiter-params", "5.12.2"),
    ("org.junit.platform", "junit-platform-commons", "1.12.2"),
    ("org.junit.platform", "junit-platform-engine", "1.12.2"),
    ("org.junit.platform", "junit-platform-launcher", "1.12.2"),
    ("org.opentest4j", "opentest4j", "1.3.0"),
    ("org.apiguardian", "apiguardian-api", "1.1.2"),
    ("org.assertj", "assertj-core", "3.27.3"),
)


def prepare(repo):
    runtime = repo / ".eval/runtime"
    runtime.mkdir(parents=True, exist_ok=True)
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
    missing, artifacts = [], []
    for group, name, version in DEPENDENCIES:
        matches = sorted((cache / group / name / version).glob(f"*/{name}-{version}.jar"))
        if len(matches) != 1 or matches[0].is_symlink():
            missing.append(f"{name}:{version}")
        else:
            artifacts.append(matches[0])
    java_home = os.environ.get("JAVA_HOME")
    if not java_home and Path("/usr/libexec/java_home").exists():
        result = subprocess.run(["/usr/libexec/java_home", "-v", "17"], capture_output=True, text=True)
        if result.returncode == 0:
            java_home = result.stdout.strip()
    if not java_home:
        javac = shutil.which("javac")
        java_home = str(Path(javac).resolve().parents[1]) if javac else None
    if not java_home or not all((Path(java_home) / "bin" / tool).is_file() for tool in ("java", "javac")):
        missing.append("JDK")
    manifest = {"available": not missing, "missing": missing, "java_home": java_home, "jars": []}
    if not missing:
        (runtime / "jars").mkdir()
        for artifact in artifacts:
            target = runtime / "jars" / artifact.name
            shutil.copyfile(artifact, target)
            manifest["jars"].append({"name": artifact.name, "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
    (runtime / "config.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    return manifest
