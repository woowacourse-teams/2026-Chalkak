"""찰칵 Android 화면 자동 확인 공용 함수 (adb + uiautomator).

역할: 확인 스크립트가 같이 쓰는 기기 조작. 바로 열기(DebugScreenActivity)로 화면 열기, 화면 노드 읽기,
      기다리기·누르기·밀기, 키보드 윗변, 캡처, 판정 결과 한 줄 남기기.
전제: 에뮬레이터 1대(adb devices), 바로 열기가 든 디버그 앱 설치(`./gradlew installDebug`).
      앱 삭제·데이터 지우기·connectedAndroidTest는 하지 않는다(로그인이 지워진다).
쓰는 법: 확인 스크립트 맨 위에서
    sys.path.insert(0, "<이 폴더>"); import chalkak_ui as ui
    ui.open_screen("upload-error"); n = ui.wait_text("영문, 숫자"); ui.screencap(out / "android-after-1.png")
좌표·크기는 모두 px(uiautomator bounds와 같음).
"""
import json
import os
import re
import subprocess
import time

ADB = os.environ.get("ADB") or os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
PKG = "com.stonefive.chalkak.debug"
DEBUG_ACTIVITY = PKG + "/com.stonefive.chalkak.debuglaunch.DebugScreenActivity"


def adb(*args):
    """adb 명령을 실행하고 표준 출력을 글자로 돌려준다."""
    return subprocess.run([ADB, *args], capture_output=True, text=True).stdout


def open_screen(name):
    """바로 열기 화면을 새로 띄운다(-S: 떠 있던 Activity를 멈추고 새로 시작해야 새 이름을 받는다)."""
    adb("shell", "am", "start", "-S", "-W", "-n", DEBUG_ACTIVITY, "--es", "screen", name)


def nodes():
    """지금 화면의 노드 목록. 각 노드: text, desc, cls, rid(resource-id=testTag), checked, focused, scrollable, l, t, r, b."""
    out = []
    for n in re.findall(r"<node [^>]*>", adb("exec-out", "uiautomator", "dump", "/dev/tty")):
        def g(k):
            m = re.search(r"\b" + k + r'="([^"]*)"', n)
            return m.group(1) if m else ""
        b = re.findall(r"\d+", g("bounds"))
        if len(b) != 4:
            continue
        out.append(dict(text=g("text"), desc=g("content-desc"), cls=g("class"), rid=g("resource-id"),
                        checked=g("checked") == "true", focused=g("focused") == "true",
                        scrollable=g("scrollable") == "true", l=int(b[0]), t=int(b[1]), r=int(b[2]), b=int(b[3])))
    return out


def find(ns, text=None, contains=None, rid=None, cls=None):
    """조건에 맞는 첫 노드. text는 정확히 같음, contains는 text나 desc에 포함, rid는 testTag 끝부분."""
    for n in ns:
        if text is not None and n["text"] != text:
            continue
        if contains is not None and contains not in n["text"] and contains not in n["desc"]:
            continue
        if rid is not None and not n["rid"].endswith(rid):
            continue
        if cls is not None and not n["cls"].endswith(cls):
            continue
        return n
    return None


def wait(pred, timeout=8.0, interval=0.25):
    """pred(nodes) 가 참이 될 때까지 기다린다. 돌려주는 값: (노드 목록, pred 결과) 또는 (마지막 노드 목록, None)."""
    end = time.time() + timeout
    ns = []
    while time.time() < end:
        ns = nodes()
        hit = pred(ns)
        if hit:
            return ns, hit
        time.sleep(interval)
    return ns, None


def wait_text(text, timeout=8.0):
    """글자가 정확히 같은 노드가 나올 때까지 기다려 그 노드를 돌려준다(없으면 None)."""
    return wait(lambda ns: find(ns, text=text), timeout)[1]


def wait_still(pred, reads=3, interval=0.15, timeout=6.0):
    """노드 위치가 reads번 연속 같을 때까지 기다린다(애니메이션 중에 누르면 포커스가 안 잡힌다)."""
    end = time.time() + timeout
    last, same = None, 0
    while time.time() < end:
        n = pred(nodes())
        box = n and (n["l"], n["t"], n["r"], n["b"])
        same = same + 1 if box and box == last else 0
        if same >= reads - 1:
            return n
        last = box
        time.sleep(interval)
    return None


def center(n):
    return (n["l"] + n["r"]) // 2, (n["t"] + n["b"]) // 2


def tap(n_or_xy):
    x, y = center(n_or_xy) if isinstance(n_or_xy, dict) else n_or_xy
    adb("shell", "input", "tap", str(x), str(y))


def swipe(x1, y1, x2, y2, ms=300):
    """손가락 끌기. 위로 올리기(아래 내용 보기): y1 > y2. 아래로 당기기(새로고침): y1 < y2."""
    adb("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))


def screen_size():
    m = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
    return int(m.group(1)), int(m.group(2))


def ime_top():
    """화면 키보드 윗변 y. 키보드가 안 떠 있으면 None."""
    if "mInputShown=true" not in adb("shell", "dumpsys", "input_method"):
        return None
    w = adb("shell", "dumpsys", "window", "InputMethod")
    f = re.search(r"frame=\[\d+,(\d+)\]", w)
    c = re.search(r"mGivenContentInsets=\[\d+,(\d+)\]", w)
    return int(f.group(1)) + int(c.group(1)) if f and c else None


class soft_keyboard:
    """키보드를 확인할 때만 화면 키보드를 켜고 끝나면 되돌린다.
    에뮬레이터는 Mac 키보드가 붙어 있으면(show_ime_with_hard_keyboard=0) 화면 키보드를 띄우지 않는다(#939 판단 불가 원인).
    쓰는 법: with ui.soft_keyboard(): ..."""

    KEY = ("secure", "show_ime_with_hard_keyboard")

    def __enter__(self):
        self.before = adb("shell", "settings", "get", *self.KEY).strip() or "0"
        adb("shell", "settings", "put", *self.KEY, "1")
        return self

    def __exit__(self, *exc):
        adb("shell", "settings", "put", *self.KEY, self.before)
        return False


def screencap(path):
    """지금 화면을 PNG로 저장한다. 로그인·계정 고르기 화면은 찍지 않는다(이메일이 보인다)."""
    png = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout
    with open(path, "wb") as fp:
        fp.write(png)
    return png


def record(path, **result):
    """판정 결과 한 줄을 JSONL에 덧붙이고 화면에도 한 줄 찍는다. ok(참/거짓)와 checks(항목별 참/거짓)를 넣는다."""
    with open(path, "a", encoding="utf-8") as fp:
        fp.write(json.dumps(result, ensure_ascii=False) + "\n")
    print(json.dumps({k: result.get(k) for k in ("name", "ok", "checks", "sec")}, ensure_ascii=False))
