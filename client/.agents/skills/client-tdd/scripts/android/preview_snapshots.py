"""Android Preview 스냅샷(Roborazzi)을 찍고, 수정 전·후를 비교한다. 기기가 필요 없다(JVM, Robolectric).

찍기:  python3 preview_snapshots.py record <저장 폴더> [패키지,패키지...]
       client/android에서 recordRoborazziDebug를 돌려 @Preview마다 PNG 한 장을 <저장 폴더>에 둔다.
       패키지 기본값: com.stonefive.chalkak.feature (모든 화면). 바꾼 화면의 패키지만 주면 빠르다(전시 화면 16장 약 20초).
비교:  python3 preview_snapshots.py diff <수정 전 폴더> <수정 후 폴더> <출력 폴더>
       같은 이름끼리 비교해 바뀜·새로 생김·사라짐·그대로로 나누고, 바뀐 것은 [수정 전 | 차이(빨강) | 수정 후] 한 장
       (<이름>_compare.png)으로, 새로 생긴 것은 <이름>_new.png로 만든다. 결과 요약은 <출력 폴더>/summary.json.
       한 가지 색뿐인 그림(빈 그림)은 경고한다. Preview가 첫 화면에서 안 보이게 시작하면 빈 그림이 된다
       (등장 애니메이션, 비동기 이미지 로딩, 찍는 범위 밖의 시트). 그 Preview를 고친다(화면 자동 확인 문서).
차이 기준: 픽셀 채널 차이가 8을 넘는 곳만 센다(렌더링 잡음 제외). 크기가 다르면 바뀜.
필요한 것: Pillow (python3 -m pip install --user pillow). 사용자 site-packages라 python3 -I 없이 실행한다.
"""
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageChops


def android_dir():
    top = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True).stdout.strip()
    return Path(top) / "client" / "android"


def record(out, packages):
    root = android_dir()
    shots = root / "app" / "build" / "outputs" / "roborazzi"
    shutil.rmtree(shots, ignore_errors=True)  # 다른 패키지의 옛 그림이 섞이지 않게
    cmd = ["./gradlew", ":app:recordRoborazziDebug", "-Pchalkak.previewSnapshots",
           f"-Pchalkak.previewPackages={packages}",
           "--tests", "com.github.takahirom.roborazzi.RoborazziPreviewParameterizedTests", "--console=plain", "-q"]
    r = subprocess.run(cmd, cwd=root)
    out = Path(out)
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)
    n = 0
    for f in sorted(shots.glob("*.png")) if shots.exists() else []:
        shutil.copy2(f, out / f.name)
        n += 1
    blanks = [f.name for f in sorted(out.glob("*.png")) if is_blank(f)]
    print(json.dumps(dict(exit=r.returncode, shots=n, blank=blanks, out=str(out)), ensure_ascii=False))
    sys.exit(r.returncode)


def is_blank(path):
    im = Image.open(path).convert("RGB").resize((64, 64))
    lo, hi = zip(*im.getextrema())
    return max(h - l for l, h in zip(lo, hi)) < 6


def diff(a_dir, b_dir, out):
    a_dir, b_dir, out = Path(a_dir), Path(b_dir), Path(out)
    out.mkdir(parents=True, exist_ok=True)
    A = {f.name for f in a_dir.glob("*.png")}
    B = {f.name for f in b_dir.glob("*.png")}
    res = {"changed": [], "new": sorted(B - A), "removed": sorted(A - B), "same": [],
           "blank_after": [f for f in sorted(B) if is_blank(b_dir / f)]}
    for f in sorted(A & B):
        a = Image.open(a_dir / f).convert("RGB")
        b = Image.open(b_dir / f).convert("RGB")
        if a.size == b.size:
            d = ImageChops.difference(a, b).convert("L").point(lambda v: 255 if v > 8 else 0)
            n = sum(d.histogram()[255:])
            if n == 0:
                res["same"].append(f)
                continue
        else:
            n = -1
            w, h = max(a.width, b.width), max(a.height, b.height)
            a2 = Image.new("RGB", (w, h), "white")
            a2.paste(a)
            b2 = Image.new("RGB", (w, h), "white")
            b2.paste(b)
            a, b = a2, b2
            d = ImageChops.difference(a, b).convert("L").point(lambda v: 255 if v > 8 else 0)
        res["changed"].append(dict(name=f, pixels=n if n >= 0 else "size_changed", compare=f"{f[:-4]}_compare.png"))
        mid = Image.blend(a, Image.new("RGB", a.size, "white"), 0.7)
        mid.paste(Image.new("RGB", a.size, (255, 0, 0)), mask=d)
        c = Image.new("RGB", (a.width * 3 + 40, a.height), "gray")
        for k, im in enumerate([a, mid, b]):
            c.paste(im, (k * (a.width + 20), 0))
        c.save(out / f"{f[:-4]}_compare.png")
    for f in res["new"]:
        Image.open(b_dir / f).save(out / f"{f[:-4]}_new.png")
    (out / "summary.json").write_text(json.dumps(res, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps({k: (len(v) if k == "same" else v) for k, v in res.items()}, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "record":
        record(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "com.stonefive.chalkak.feature")
    elif len(sys.argv) == 5 and sys.argv[1] == "diff":
        diff(*sys.argv[2:5])
    else:
        sys.exit(__doc__)
