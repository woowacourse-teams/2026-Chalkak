#!/usr/bin/env python3
"""수정 전·후 캡처를 비교해 바뀐 곳마다 번호 테두리를 그린다.

역할
  같은 조건으로 찍은 전·후 이미지 두 장을 픽셀 단위로 비교해서, 바뀐 영역을 묶고
  전·후를 나란히 붙인 이미지 위에 같은 번호의 테두리를 그린다.
  AI는 번호마다 "의도한 변화"인지 "설명 못 함"인지 적고, 사람은 "설명 못 함"부터 본다.
  의도하지 않은 변화(예: #814의 탑바 아래 그림자 선)를 놓치지 않으려는 도구다.

입력
  before.png after.png   같은 기기·같은 화면·같은 데이터로 찍은 이미지. 크기가 다르면 비율이 같을 때만
                         after를 before 크기로 맞춘다.
  -o, --out              결과 이미지 경로 (기본: <after 이름>-marked.png)
  --ignore x,y,w,h       비교에서 뺄 영역. 여러 번 줄 수 있다. 서버에서 늦게 오는 사진처럼
                         찍을 때마다 달라지는 곳을 뺀다. 좌표는 before 이미지 픽셀 기준.
  --threshold N          채널 차이가 N 넘는 픽셀만 바뀐 것으로 본다 (기본 16).
                         mp4에서 뽑은 프레임은 압축 잡음이 있어 32 정도로 올린다.
  --gap N                이 픽셀 거리 안의 변화는 한 영역으로 묶는다 (기본 12).
  --min-pixels N         바뀐 픽셀이 N개보다 적은 영역은 잡음으로 버린다 (기본 3).

출력
  결과 이미지 1장과, 표준 출력의 JSON 한 개:
    {"out": 경로, "mode": "marked" | "side-by-side" | "same",
     "changed_ratio": 바뀐 영역 넓이 / 화면 넓이,
     "regions": [{"n": 1, "x": .., "y": .., "w": .., "h": .., "pixels": ..}, ...]}
  - marked: 번호 테두리를 그렸다.
  - side-by-side: 바뀐 영역이 화면 절반을 넘어 테두리 없이 나란히만 붙였다(전체 개편은 번호가 의미 없음).
  - same: 바뀐 곳이 없다. 이미지는 나란히 붙여 둔다.
  번호는 위에서 아래, 왼쪽에서 오른쪽 순서다.

전제
  python3 와 Pillow(PIL), numpy. 상태 표시줄 고정·애니메이션 끄기는 찍는 쪽(화면 확인 스킬)에서 한다.

예시
  python3 mark-changes.py before-1.png after-1.png -o compare-1.png
  python3 mark-changes.py b.png a.png --ignore 0,600,1179,900      # 사진 영역 제외
  ffmpeg -ss 1.20 -i before.mp4 -frames:v 1 b-1.20.png             # 움직임 버그는 같은 시점 프레임 쌍으로
"""

import argparse
import json
import os
import sys
from collections import deque

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HALF = 0.5          # 바뀐 영역이 이 비율을 넘으면 번호 없이 나란히만 둔다
CELL = 4            # 영역 묶기를 이 픽셀 단위 격자에서 한다(속도용)
LINE_REACH = 4      # 같은 줄 위의 조각은 gap의 이 배수 거리까지 한 영역으로 합친다
BOX = (230, 0, 126)        # 테두리 색: 사진 앱 화면에서 잘 안 나오는 진분홍
IGNORED = (120, 120, 120)  # 비교에서 뺀 영역 표시 색


def parse_box(text):
    try:
        x, y, w, h = (int(v) for v in text.split(","))
    except ValueError:
        raise argparse.ArgumentTypeError("--ignore 는 x,y,w,h 정수 네 개예요")
    return x, y, w, h


def load_pair(before_path, after_path):
    before = Image.open(before_path).convert("RGB")
    after = Image.open(after_path).convert("RGB")
    if before.size != after.size:
        bw, bh = before.size
        aw, ah = after.size
        if abs(bw / bh - aw / ah) > 0.01:
            sys.exit(f"전·후 이미지 비율이 달라요({bw}x{bh}, {aw}x{ah}). 같은 기기·방향으로 다시 찍어 주세요.")
        after = after.resize(before.size, Image.LANCZOS)
    return before, after


def changed_mask(before, after, threshold, ignores):
    a = np.asarray(before, dtype=np.int16)
    b = np.asarray(after, dtype=np.int16)
    mask = np.abs(a - b).max(axis=2) > threshold
    for x, y, w, h in ignores:
        mask[max(y, 0):y + h, max(x, 0):x + w] = False
    return mask


def find_regions(mask, gap, min_pixels):
    """바뀐 픽셀을 격자 칸으로 줄인 뒤, gap 안에 있는 칸끼리 이어서 영역 상자를 만든다."""
    h, w = mask.shape
    gh, gw = -(-h // CELL), -(-w // CELL)
    padded = np.zeros((gh * CELL, gw * CELL), dtype=bool)
    padded[:h, :w] = mask
    counts = padded.reshape(gh, CELL, gw, CELL).sum(axis=(1, 3))
    reach = max(1, -(-gap // CELL))

    seen = np.zeros_like(counts, dtype=bool)
    regions = []
    for sy, sx in zip(*np.nonzero(counts)):
        if seen[sy, sx]:
            continue
        queue = deque([(sy, sx)])
        seen[sy, sx] = True
        y0, x0, y1, x1, pixels = sy, sx, sy, sx, 0
        while queue:
            cy, cx = queue.popleft()
            pixels += int(counts[cy, cx])
            y0, x0, y1, x1 = min(y0, cy), min(x0, cx), max(y1, cy), max(x1, cx)
            ny0, ny1 = max(cy - reach, 0), min(cy + reach + 1, gh)
            nx0, nx1 = max(cx - reach, 0), min(cx + reach + 1, gw)
            window = counts[ny0:ny1, nx0:nx1]
            for dy, dx in zip(*np.nonzero(window)):
                ny, nx = ny0 + dy, nx0 + dx
                if not seen[ny, nx]:
                    seen[ny, nx] = True
                    queue.append((ny, nx))
        if pixels < min_pixels:
            continue
        # 격자 상자를 실제 바뀐 픽셀에 딱 맞게 줄인다.
        sub = mask[y0 * CELL:(y1 + 1) * CELL, x0 * CELL:(x1 + 1) * CELL]
        ys, xs = np.nonzero(sub)
        regions.append({
            "x": int(x0 * CELL + xs.min()), "y": int(y0 * CELL + ys.min()),
            "w": int(xs.max() - xs.min() + 1), "h": int(ys.max() - ys.min() + 1),
            "pixels": pixels,
        })
    regions = merge_collinear(regions, gap * LINE_REACH)
    regions.sort(key=lambda r: (r["y"] // 20, r["x"]))
    for i, r in enumerate(regions, 1):
        r["n"] = i
    return [{"n": r["n"], **{k: r[k] for k in ("x", "y", "w", "h", "pixels")}} for r in regions]


def merge_collinear(regions, reach):
    """같은 줄 위에 있는 조각을 하나로 합친다.

    어두운 선이 어두운 사진 위를 지나면 그 구간은 차이가 문턱보다 작아 선이 여러 조각으로 끊긴다.
    둘 다 폭이 thin 이하인 세로 조각이 가로 범위가 겹치고 세로 간격이 reach 이하면 합친다.
    가로 조각도 같은 방식이다. 얇지 않은 영역(버튼, 글자 덩어리)은 합치지 않는다.
    """
    thin = reach // LINE_REACH
    boxes = [dict(r) for r in regions]
    merged = True
    while merged:
        merged = False
        for i in range(len(boxes)):
            for j in range(i + 1, len(boxes)):
                a, b = boxes[i], boxes[j]
                x_overlap = a["x"] < b["x"] + b["w"] and b["x"] < a["x"] + a["w"]
                y_overlap = a["y"] < b["y"] + b["h"] and b["y"] < a["y"] + a["h"]
                y_gap = max(a["y"], b["y"]) - min(a["y"] + a["h"], b["y"] + b["h"])
                x_gap = max(a["x"], b["x"]) - min(a["x"] + a["w"], b["x"] + b["w"])
                vertical = a["w"] <= thin and b["w"] <= thin and x_overlap and y_gap <= reach
                horizontal = a["h"] <= thin and b["h"] <= thin and y_overlap and x_gap <= reach
                if vertical or horizontal:
                    x0, y0 = min(a["x"], b["x"]), min(a["y"], b["y"])
                    x1 = max(a["x"] + a["w"], b["x"] + b["w"])
                    y1 = max(a["y"] + a["h"], b["y"] + b["h"])
                    boxes[i] = {"x": x0, "y": y0, "w": x1 - x0, "h": y1 - y0,
                                "pixels": a["pixels"] + b["pixels"]}
                    del boxes[j]
                    merged = True
                    break
            if merged:
                break
    return boxes


def union_ratio(regions, size):
    w, h = size
    covered = np.zeros((h, w), dtype=bool)
    for r in regions:
        covered[r["y"]:r["y"] + r["h"], r["x"]:r["x"] + r["w"]] = True
    return float(covered.mean())


def font(size):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:  # Pillow 10.1 이전
        return ImageFont.load_default()


def draw_marks(image, regions, ignores, offset_x, scale):
    draw = ImageDraw.Draw(image)
    line = max(2, round(3 * scale))
    pad = max(4, round(6 * scale))
    label_font = font(max(14, round(22 * scale)))
    for x, y, w, h in ignores:
        draw.rectangle([offset_x + x, y, offset_x + x + w - 1, y + h - 1], outline=IGNORED, width=line)
    for r in regions:
        x0 = offset_x + r["x"] - pad
        y0 = r["y"] - pad
        x1 = offset_x + r["x"] + r["w"] + pad
        y1 = r["y"] + r["h"] + pad
        draw.rectangle([x0, y0, x1, y1], outline=BOX, width=line)
        text = str(r["n"])
        tb = draw.textbbox((0, 0), text, font=label_font)
        tw, th = tb[2] - tb[0], tb[3] - tb[1]
        bx0, by0 = x0, max(0, y0 - th - 2 * pad)
        draw.rectangle([bx0, by0, bx0 + tw + 2 * pad, by0 + th + 2 * pad], fill=BOX)
        draw.text((bx0 + pad - tb[0], by0 + pad - tb[1]), text, fill="white", font=label_font)


def side_by_side(before, after, gutter):
    w, h = before.size
    canvas = Image.new("RGB", (w * 2 + gutter, h), "white")
    canvas.paste(before, (0, 0))
    canvas.paste(after, (w + gutter, 0))
    return canvas


def main():
    parser = argparse.ArgumentParser(
        description="수정 전·후 캡처의 바뀐 곳에 번호 테두리를 그린다.",
        epilog="출력: 결과 이미지 1장 + 표준 출력 JSON {out, mode(marked|side-by-side|same), changed_ratio, regions}",
    )
    parser.add_argument("before", help="수정 전 이미지")
    parser.add_argument("after", help="수정 후 이미지(같은 기기·화면·데이터)")
    parser.add_argument("-o", "--out", help="결과 이미지 경로 (기본: <after>-marked.png)")
    parser.add_argument("--ignore", type=parse_box, action="append", default=[], metavar="x,y,w,h",
                        help="비교에서 뺄 영역. 여러 번 줄 수 있다(예: 늦게 오는 사진 영역)")
    parser.add_argument("--threshold", type=int, default=16, help="채널 차이가 이보다 커야 바뀐 것으로 봄. mp4 프레임은 32")
    parser.add_argument("--gap", type=int, default=12, help="이 거리 안의 변화는 한 영역으로 묶음")
    parser.add_argument("--min-pixels", type=int, default=3, help="바뀐 픽셀이 이보다 적은 영역은 잡음으로 버림")
    args = parser.parse_args()

    out = args.out or os.path.splitext(args.after)[0] + "-marked.png"
    before, after = load_pair(args.before, args.after)
    mask = changed_mask(before, after, args.threshold, args.ignore)
    regions = find_regions(mask, args.gap, args.min_pixels)
    ratio = union_ratio(regions, before.size)

    scale = before.size[0] / 400
    gutter = max(8, round(12 * scale))
    canvas = side_by_side(before, after, gutter)
    if not regions:
        mode = "same"
    elif ratio > HALF:
        mode = "side-by-side"
    else:
        mode = "marked"
        draw_marks(canvas, regions, args.ignore, 0, scale)
        draw_marks(canvas, regions, args.ignore, before.size[0] + gutter, scale)
    canvas.save(out)

    print(json.dumps({
        "out": out, "mode": mode, "changed_ratio": round(ratio, 4),
        "regions": regions if mode == "marked" else [],
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
