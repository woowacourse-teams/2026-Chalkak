"""Android Preview 스냅샷(Roborazzi)을 찍고, 수정 전·후를 비교한다. 기기가 필요 없다(JVM, Robolectric).

찍기:  python3 preview_snapshots.py record <저장 폴더> [패키지,패키지...]
       client/android에서 recordRoborazziDebug를 돌려 @Preview마다 PNG 한 장을 <저장 폴더>에 둔다.
       패키지 기본값: com.stonefive.chalkak.feature (모든 화면). 바꾼 화면의 패키지만 주면 빠르다(전시 화면 16장 약 20초).
비교:  python3 preview_snapshots.py diff <수정 전 폴더> <수정 후 폴더> <출력 폴더>
       같은 이름끼리 비교해 바뀜·잡음·새로 생김·사라짐·그대로로 나눈다. 결과 요약은 <출력 폴더>/summary.json.
       바뀐 것은 판정용 [수정 전 | 차이(빨강) | 수정 후] 한 장(<이름>_compare.png)과 바뀐 곳 상자(boxes, 원본 px)를,
       새로 생긴 것은 <이름>_new.png를 만든다. PR에 올리는 그림은 이것이 아니라 client-pr/scripts/screen-sheet.py가 만든다.
       한 가지 색뿐인 그림(빈 그림)은 경고한다. Preview가 첫 화면에서 안 보이게 시작하면 빈 그림이 된다
       (등장 애니메이션, 비동기 이미지 로딩, 찍는 범위 밖의 시트). 그 Preview를 고친다(화면 자동 확인 문서).
차이 기준: 픽셀 채널 차이가 8을 넘는 곳이 있으면 비교한다. 그다음 둘 중 하나라도 걸리면 `바뀜`, 아니면 `잡음`이다.
       1) 1/4로 줄이고 흐리게 한 뒤 차이 16 초과(눈에 보이는 변화). 사진 디코딩 잡음은 여기서 0이 된다(#988 4장).
       2) 사진 밖(UI)에서 원본 해상도 차이가 20px 이상. 줄이면 사라지는 글자·선·아이콘 1px 이동을 살린다(#948).
          사진 자리는 거의 모든 픽셀에 색 변화가 있는 큰 덩어리(가로·세로 120px 이상)로 찾는다.
          한계: 사진 위에 얹힌 글자(하트 수, 사인)의 1px 이동은 사진 자리로 보고 잡음으로 뺀다.
필요한 것: Pillow, numpy, scipy (python3 -m pip install --user pillow numpy scipy). 사용자 site-packages라 python3 -I 없이 실행한다.
"""
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageFilter
from scipy import ndimage

FINE = 8          # 원본 해상도에서 다르다고 보는 채널 차이
COARSE_K = 4      # 줄이는 배율
COARSE_THR = 16   # 줄이고 흐린 뒤 다르다고 보는 채널 차이
UI_MIN_PX = 20    # 사진 밖에서 이만큼 달라야 바뀜(#988 잡음은 사진 밖 0–6px)


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


def _coarse(im):
    small = im.resize((max(1, im.width // COARSE_K), max(1, im.height // COARSE_K)), Image.BOX)
    return np.asarray(small.filter(ImageFilter.GaussianBlur(1)), np.int16)


def _lum(a):
    a = a.astype(np.float32)
    return a[..., 0] * 0.299 + a[..., 1] * 0.587 + a[..., 2] * 0.114


def photo_mask(im, min_side=120):
    """사진 자리(참). UI는 단색 바탕이라 색 변화가 글자 가장자리에만 있고, 사진은 거의 모든 픽셀에 있다."""
    lum = _lum(np.asarray(im))
    varies = ndimage.maximum_filter(lum, 3) - ndimage.minimum_filter(lum, 3) > 0
    dense = ndimage.uniform_filter(varies.astype(np.float32), 15) > 0.8
    dense = ndimage.binary_fill_holes(ndimage.binary_closing(dense, iterations=3))
    lab, _ = ndimage.label(dense)
    keep = np.zeros_like(dense)
    for i, sl in enumerate(ndimage.find_objects(lab), 1):
        if sl[0].stop - sl[0].start >= min_side and sl[1].stop - sl[1].start >= min_side:
            keep |= lab == i
    return ndimage.binary_dilation(keep, iterations=4) if keep.any() else keep


def _boxes(mask, scale=1, gap=40, min_px=1):
    """참인 곳을 gap(px) 거리로 묶은 상자 목록 [왼, 위, 오른, 아래](원본 px)."""
    lab, _ = ndimage.label(ndimage.binary_dilation(mask, iterations=max(1, gap // scale)))
    out = []
    for sl in ndimage.find_objects(lab):
        sub = mask[sl]
        if sub.sum() < min_px:
            continue
        ys, xs = np.nonzero(sub)
        out.append([int((sl[1].start + xs.min()) * scale), int((sl[0].start + ys.min()) * scale),
                    int((sl[1].start + xs.max() + 1) * scale), int((sl[0].start + ys.max() + 1) * scale)])
    return sorted(out, key=lambda r: (r[1], r[0]))


def classify(a, b):
    """같은 크기의 두 그림을 비교한다. 돌려줌: ("same"|"noise"|"changed", 정보 dict)."""
    fine = np.abs(np.asarray(a, np.int16) - np.asarray(b, np.int16)).max(axis=2) > FINE
    if not fine.any():
        return "same", {}
    coarse = np.abs(_coarse(a) - _coarse(b)).max(axis=2) > COARSE_THR
    boxes = _boxes(coarse, COARSE_K, min_px=2)   # 줄인 그림에서 2칸(원본 약 32px) 이상 묶인 곳만
    if boxes:
        return "changed", dict(pixels=int(fine.sum()), boxes=boxes)
    ui = fine & ~(photo_mask(a) | photo_mask(b))
    if ui.sum() >= UI_MIN_PX:
        return "changed", dict(pixels=int(fine.sum()), small=True, boxes=_boxes(ui, 1, gap=12, min_px=4))
    return "noise", dict(pixels=int(fine.sum()), ui_pixels=int(ui.sum()))


def change_boxes(a_path, b_path):
    """두 그림 파일의 바뀐 곳 상자. 크기가 다르면 빈 목록(화면 전체가 바뀜). screen-sheet.py가 쓴다."""
    a, b = Image.open(a_path).convert("RGB"), Image.open(b_path).convert("RGB")
    if a.size != b.size:
        return []
    kind, info = classify(a, b)
    return info.get("boxes", []) if kind == "changed" else []


def diff(a_dir, b_dir, out):
    a_dir, b_dir, out = Path(a_dir), Path(b_dir), Path(out)
    out.mkdir(parents=True, exist_ok=True)
    A = {f.name for f in a_dir.glob("*.png")}
    B = {f.name for f in b_dir.glob("*.png")}
    res = {"changed": [], "noise": [], "new": sorted(B - A), "removed": sorted(A - B), "same": [],
           "blank_after": [f for f in sorted(B) if is_blank(b_dir / f)]}
    for f in sorted(A & B):
        a = Image.open(a_dir / f).convert("RGB")
        b = Image.open(b_dir / f).convert("RGB")
        info = {}
        if a.size == b.size:
            kind, info = classify(a, b)
            if kind == "same":
                res["same"].append(f)
                continue
            if kind == "noise":
                res["noise"].append(dict(name=f, **info))
                continue
            d = ImageChops.difference(a, b).convert("L").point(lambda v: 255 if v > FINE else 0)
        else:
            w, h = max(a.width, b.width), max(a.height, b.height)
            a2 = Image.new("RGB", (w, h), "white")
            a2.paste(a)
            b2 = Image.new("RGB", (w, h), "white")
            b2.paste(b)
            a, b = a2, b2
            d = ImageChops.difference(a, b).convert("L").point(lambda v: 255 if v > FINE else 0)
            info = dict(pixels="size_changed", boxes=[])
        res["changed"].append(dict(name=f, **info, compare=f"{f[:-4]}_compare.png"))
        mid = Image.blend(a, Image.new("RGB", a.size, "white"), 0.7)
        mid.paste(Image.new("RGB", a.size, (255, 0, 0)), mask=d)
        c = Image.new("RGB", (a.width * 3 + 40, a.height), "gray")
        for k, im in enumerate([a, mid, b]):
            c.paste(im, (k * (a.width + 20), 0))
        c.save(out / f"{f[:-4]}_compare.png")
    for f in res["new"]:
        Image.open(b_dir / f).save(out / f"{f[:-4]}_new.png")
    (out / "summary.json").write_text(json.dumps(res, ensure_ascii=False, indent=1), encoding="utf-8")
    short = dict(res, same=len(res["same"]), noise=[x["name"] for x in res["noise"]],
                 changed=[dict(name=x["name"], small=x.get("small", False), boxes=len(x["boxes"])) for x in res["changed"]])
    print(json.dumps(short, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "record":
        record(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "com.stonefive.chalkak.feature")
    elif len(sys.argv) == 5 and sys.argv[1] == "diff":
        diff(*sys.argv[2:5])
    else:
        sys.exit(__doc__)
