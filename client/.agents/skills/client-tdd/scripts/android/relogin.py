"""찰칵 Android 디버그 앱의 로그인이 풀렸을 때, 기기에 등록된 테스트 구글 계정으로 다시 로그인한다.

역할: 앱을 열어 홈이면 끝, 로그인 화면이면 "Google로 계속하기" → 계정 고르기 창에서 이름이 맞는 계정 → 홈까지 탭만 한다.
      비밀번호는 쓰지 않는다(계정은 사람이 기기 설정에 미리 등록). 이 과정은 캡처하지 않고 계정 창의 글자(이메일)는 출력하지 않는다.
사용: python3 relogin.py <계정 표시 이름> [--logout-first]
      --logout-first: 시험용. 설정 탭에서 먼저 로그아웃한 뒤 다시 로그인한다.
출력: JSON 한 줄 {"ok": 참/거짓, "step": 끝난 단계, "sec": 걸린 시간}
      step: already_home | home | no_google_button | account_not_in_picker | not_home_after_pick
"""
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import chalkak_ui as ui  # noqa: E402

HOME_TABS = {"홈", "전시", "기록", "설정"}


def is_home(ns):
    return HOME_TABS <= {n["text"] for n in ns}


def main():
    name = sys.argv[1]
    t0 = time.time()

    def done(ok, step):
        print(json.dumps(dict(ok=ok, step=step, sec=round(time.time() - t0, 1)), ensure_ascii=False))

    ui.adb("shell", "monkey", "-p", ui.PKG, "-c", "android.intent.category.LAUNCHER", "1")
    if "--logout-first" in sys.argv:
        s = ui.wait_text("설정", timeout=15)
        if not s:
            return done(False, "no_settings_tab")
        ui.tap(s)
        lo = ui.wait_text("로그아웃")
        if not lo:
            return done(False, "no_logout")
        ui.tap(lo)
        time.sleep(1.2)
        ns = ui.nodes()
        if ui.find(ns, text="취소"):
            # 확인 창 제목도 "로그아웃"이라 같은 글자 중 가장 아래(버튼)를 누른다
            cs = [n for n in ns if n["text"] in ("로그아웃", "확인")]
            if cs:
                ui.tap(max(cs, key=lambda n: n["t"]))
        time.sleep(1.5)

    ns, g = ui.wait(lambda ns: "home" if is_home(ns) else ui.find(ns, contains="Google로 계속"), timeout=15)
    if g == "home":
        return done(True, "already_home")
    if not g:
        return done(False, "no_google_button")
    ui.tap(g)
    acc = ui.wait(lambda ns: ui.find(ns, contains=name), timeout=15)[1]
    if not acc:
        return done(False, "account_not_in_picker")
    ui.tap(acc)
    end = time.time() + 20
    while time.time() < end:
        ns = ui.nodes()
        if is_home(ns):
            return done(True, "home")
        c = ui.find(ns, text="계속") or ui.find(ns, text="Continue")
        if c:
            ui.tap(c)
        time.sleep(0.8)
    return done(False, "not_home_after_pick")


if __name__ == "__main__":
    main()
