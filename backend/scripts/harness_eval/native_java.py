"""Claude의 고정 Java 검사 전용 macOS 격리. 일반 shell 실행 도구를 제공하지 않는다."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import selectors
import signal
import socket
import subprocess
import sys
import time

try:
    from . import java_fixture
except ImportError:
    import java_fixture

SANDBOX = Path("/usr/bin/sandbox-exec")
LIMIT = 1_000_000
TIMEOUT = 30


def runtime(repo):
    path = Path(repo) / ".eval/runtime/config.json"
    config = json.loads(path.read_text())
    if not config.get("available"):
        raise ValueError("격리 Java 실행 의존성이 준비되지 않았습니다")
    return Path(config["java_home"]).resolve()


def profile(repo):
    repo = Path(repo).resolve()
    home = runtime(repo)
    reads = [home, Path("/System"), Path("/usr/lib"), Path("/usr/share"),
             Path("/private/var/db/timezone"), repo / "backend/src",
             repo / ".eval/runtime", repo / "backend/build/fixture-classes"]
    writes = [repo / "backend/build/fixture-classes", repo / ".eval/java-tmp"]
    quote = lambda p: json.dumps(str(p))
    subpaths = lambda paths: " ".join("(subpath " + quote(p) + ")" for p in paths)
    executables = [home / "bin/java", home / "bin/javac"]
    literals = lambda paths: " ".join("(literal " + quote(p) + ")" for p in paths)
    return "\n".join([
        "(version 1)", "(deny default)", "(import \"dyld-support.sb\")", "(deny network*)",
        "(allow process-fork)", "(allow process-exec " + literals(executables) + ")",
        "(allow signal (target self))", "(allow sysctl-read)",
        "(allow process-info* (target self))", "(allow file-read-metadata)",
        "(allow file-map-executable " + subpaths([home, Path("/System"), Path("/usr/lib")]) + ")",
        "(allow file-read* " + subpaths(reads) + " " + literals([
            Path("/"), repo / "backend", repo / "backend/scripts/FixtureTestLauncher.java", Path("/dev/random"),
            Path("/dev/urandom"), Path("/dev/null"), Path("/dev/zero"),
            Path("/private/etc/localtime"), Path("/private/etc/hosts"),
        ]) + ")",
        "(allow file-write* " + subpaths(writes) + " (literal \"/dev/null\"))",
    ])


def fingerprint(repo):
    data = Path(repo) / ".eval/runtime/config.json"
    return hashlib.sha256(data.read_bytes() + profile(repo).encode()).hexdigest()


def environment(repo, home):
    return {"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "JAVA_HOME": str(home),
            "HOME": str(Path(repo)), "TMPDIR": str(Path(repo) / ".eval/java-tmp")}


def execute(repo, command, deadline, output):
    """시간과 출력 상한을 넘으면 자식 그룹까지 종료한다. 인증 환경변수를 전달하지 않는다."""
    if sys.platform != "darwin" or not SANDBOX.is_file():
        raise ValueError("macOS sandbox-exec 격리를 사용할 수 없습니다")
    home = runtime(repo)
    if not command or Path(command[0]).resolve() not in {home / "bin/java", home / "bin/javac"}:
        raise ValueError("고정된 Java 검사 실행 파일만 허용합니다")
    process = subprocess.Popen([str(SANDBOX), "-p", profile(repo), *command],
                               cwd=Path(repo) / "backend", env=environment(repo, home),
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    try:
        with selectors.DefaultSelector() as selector:
            selector.register(process.stdout, selectors.EVENT_READ)
            while selector.get_map():
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise ValueError("Java 검사 시간 상한을 넘었습니다")
                for key, _ in selector.select(min(remaining, 0.2)):
                    data = os.read(key.fd, 65536)
                    if not data:
                        selector.unregister(key.fileobj)
                    else:
                        output.extend(data)
                        if len(output) > LIMIT:
                            raise ValueError("Java 검사 출력 상한을 넘었습니다")
        return process.wait(timeout=max(0.1, deadline - time.monotonic()))
    finally:
        # javac/JUnit이 자식을 남겼을 때도 실행 중인 그룹을 보존하지 않는다.
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait()
        process.stdout.close()


PROBE = r'''import java.nio.file.*;
import java.net.*;
public class JavaSandboxProbe {
    public static void main(String[] args) throws Exception {
        int blocked = 0;
        try { Files.readString(Path.of(args[0])); } catch (java.io.IOException expected) { blocked++; }
        try { Files.writeString(Path.of(args[1]), "escape"); } catch (java.io.IOException expected) { blocked++; }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", Integer.parseInt(args[2])), 1000);
        } catch (java.io.IOException expected) { blocked++; }
        try { new ProcessBuilder("/bin/sh", "-c", "exit 0").start().waitFor(); }
        catch (java.io.IOException expected) { blocked++; }
        if (args[3].equals(System.getenv("HOME")) && System.getenv("JAVA_TOOL_OPTIONS") == null) blocked++;
        try { Files.readString(Path.of(args[3], ".git/config")); }
        catch (java.io.IOException expected) { blocked++; }
        try { Files.writeString(Path.of(args[3], "backend/src/main/java/JavaSandboxEscape.java"), "escape"); }
        catch (java.io.IOException expected) { blocked++; }
        System.out.println("JAVA_SANDBOX_BLOCKED=" + blocked);
        if (blocked != 7) System.exit(1);
    }
}
'''


def preflight(repo, output):
    repo, output = Path(repo).resolve(), Path(output).resolve()
    if output.is_relative_to(repo):
        raise ValueError("격리 판정 기록은 작업 자료 밖에 보관해야 합니다")
    output.mkdir(parents=True, exist_ok=True)
    commands = java_fixture.commands(repo / "backend")
    home = runtime(repo)
    source = output / "JavaSandboxProbe.java"
    source.write_text(PROBE)
    classes = repo / "backend/build/fixture-classes"
    # 신뢰된 고정 probe만 컴파일한다. 평가받는 소스는 이 호스트 명령에 포함하지 않는다.
    subprocess.run([str(home / "bin/javac"), "-proc:none", "-d", str(classes), str(source)],
                   check=True, capture_output=True, timeout=10, env=environment(repo, home))
    canary, escape = output / "outside-canary.txt", output / "outside-write.txt"
    canary.write_text("outside fixture; must not be readable")
    captured = bytearray()
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        listener.listen()
        code = execute(repo, [commands[1][0], "-XX:-UsePerfData", "-cp", str(classes),
                             "JavaSandboxProbe", str(canary), str(escape), str(listener.getsockname()[1]), str(repo)],
                       time.monotonic() + 15, captured)
    (output / "java-preflight.log").write_bytes(captured)
    if code or b"JAVA_SANDBOX_BLOCKED=7" not in captured or escape.exists():
        raise ValueError("Java 격리 사전 검사 실패: 외부 읽기/쓰기/네트워크/명령/환경 차단을 확인할 수 없습니다")
    grant = {"repo": str(repo), "fingerprint": fingerprint(repo), "status": "PASS"}
    (output / "java-preflight.json").write_text(json.dumps(grant, indent=2) + "\n")


def check(repo, output):
    repo, output = Path(repo).resolve(), Path(output).resolve()
    grant = output / "java-preflight.json"
    if output.is_relative_to(repo) or grant.is_symlink():
        raise ValueError("작업 자료 밖의 사전 검사 기록이 필요합니다")
    data = json.loads(grant.read_text())
    if data != {"repo": str(repo), "fingerprint": fingerprint(repo), "status": "PASS"}:
        raise ValueError("현재 Java 격리의 사전 검사 통과 기록이 필요합니다")
    output_bytes = bytearray()
    deadline = time.monotonic() + TIMEOUT
    code = java_fixture.check(repo / "backend", lambda command: execute(repo, command, deadline, output_bytes))
    return {"exit_code": code, "output": output_bytes.decode("utf-8", errors="replace")}
