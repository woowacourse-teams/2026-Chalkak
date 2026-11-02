"""실제 JDK/JUnit을 사용하는 작은 오프라인 Java 평가 자료 준비."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import re

HERE = Path(__file__).resolve().parent

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
        result = subprocess.run(["/usr/libexec/java_home", "-v", "21"], capture_output=True, text=True)
        if result.returncode == 0:
            java_home = result.stdout.strip()
    if not java_home:
        javac = shutil.which("javac")
        java_home = str(Path(javac).resolve().parents[1]) if javac else None
    if not java_home or not all((Path(java_home) / "bin" / tool).is_file() for tool in ("java", "javac")):
        missing.append("JDK")
    else:
        version = subprocess.run([str(Path(java_home) / "bin/javac"), "-version"],
                                 capture_output=True, text=True, timeout=10)
        match = re.search(r"javac (\d+)", version.stdout + version.stderr)
        if version.returncode or not match or int(match[1]) < 21:
            missing.append("JDK 21 이상 (프로젝트 JavaLayout 검사기 실행)")
        java_home = str(Path(java_home).resolve())
    manifest = {"available": not missing, "missing": missing, "java_home": java_home, "jars": []}
    if not missing:
        (runtime / "jars").mkdir()
        for artifact in artifacts:
            target = runtime / "jars" / artifact.name
            shutil.copyfile(artifact, target)
            manifest["jars"].append({"name": artifact.name, "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
    shutil.copyfile(HERE.parent / "CheckJavaLayout.java", runtime / "CheckJavaLayout.java")
    shutil.copyfile(HERE / "java_fixture.py", runtime / "java_fixture.py")
    (runtime / "config.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    return manifest


def commands(backend):
    """두 도구가 같은 실제 컴파일·레이아웃·JUnit 명령을 사용한다."""
    backend = Path(backend).resolve()
    runtime = backend.parent / ".eval/runtime"
    config = json.loads((runtime / "config.json").read_text())
    if not config["available"]:
        raise ValueError("검사 불가: " + ", ".join(config["missing"]))
    jars = []
    for artifact in config["jars"]:
        path = runtime / "jars" / artifact["name"]
        if (path.is_symlink() or path.parent != runtime / "jars"
                or hashlib.sha256(path.read_bytes()).hexdigest() != artifact["sha256"]):
            raise ValueError("실행 의존성이 변경되었습니다")
        jars.append(str(path))
    classes = backend / "build/fixture-classes"
    scratch = backend.parent / ".eval/java-tmp"
    for path in (runtime, classes, scratch, backend / "src"):
        if any(parent.is_symlink() for parent in (path, *path.parents)):
            raise ValueError("Java 작업 경로의 심볼릭 링크는 허용하지 않습니다")
    # 이전 실행의 삭제/이름 변경된 테스트 class가 다음 JUnit 실행에 섞이지 않는다.
    if classes.exists():
        shutil.rmtree(classes)
    for path in (classes, scratch):
        path.mkdir(parents=True, exist_ok=True)
    sources = sorted(backend.glob("src/main/java/**/*.java")) + sorted(backend.glob("src/test/java/**/*.java"))
    if any(any(parent.is_symlink() for parent in (p, *p.parents)) for p in sources):
        raise ValueError("Java 소스의 심볼릭 링크는 허용하지 않습니다")
    sources += [backend / "scripts/FixtureTestLauncher.java", runtime / "CheckJavaLayout.java"]
    java_bin = Path(config["java_home"]) / "bin"
    classpath = os.pathsep.join(jars)
    options = ["-XX:-UsePerfData", "-Xmx256m", "-Djava.io.tmpdir=" + str(scratch)]
    return [
        [str(java_bin / "javac"), *["-J" + value for value in options], "-proc:none",
         "-encoding", "UTF-8", "-cp", classpath, "-d", str(classes), *map(str, sources)],
        [str(java_bin / "java"), *options, "-cp", str(classes), "CheckJavaLayout",
         str(backend / "src/main/java"), str(backend / "src/test/java")],
        [str(java_bin / "java"), *options, "-cp", str(classes) + os.pathsep + classpath,
         "FixtureTestLauncher"],
    ]


def check(backend, runner=None):
    runner = runner or (lambda command: subprocess.run(command, check=False).returncode)
    for command in commands(backend):
        code = runner(command)
        if code:
            return code
    return 0
