"""PR 📸 칸에 올릴 Android 화면 확인 그림("변경점 한 장")을 만든다 (#1000).

역할: 화면 확인을 한 Android 서브에이전트가 쓴 설명 파일(JSON)을 받아, 수정 전·후 캡처를 둥근 폰 카드로 줄여
      항목 번호별 섹션에 나란히 놓고, 바뀐 곳에 올가미를 그리고, 캡션과 결과 카드를 붙여 JPEG로 저장한다.
      판정은 하지 않는다. 판정은 preview_snapshots.py diff와 확인 스크립트(checks/)가 이미 했다.
입력: 설명 파일 하나. 경로는 모두 설명 파일이 있는 폴더(보통 $EVID) 기준이다.
출력: <출력 이름>-1.jpg, -2.jpg ... (높이 4000px을 넘으면 섹션 경계에서 나눔, 한 장 2MB 이하)와
      originals/ 폴더(그림에 쓴 원본 캡처를 겹치지 않는 이름으로 복사, PR에 함께 올림).
      표준 출력에 JSON 한 줄: {"sheets": [...], "originals": [...], "sec": 걸린 초}
쓰는 법(레포 맨 위에서):
    python3 client/.agents/skills/client-pr/scripts/screen-sheet.py "$EVID/media/sheet-android.json" -o "$EVID/media/android-sheet"
전제: 맥(Apple SD Gothic Neo 글꼴). Pillow, numpy, scipy (python3 -m pip install --user pillow numpy scipy).

설명 파일 예 (항목 2개, 의도하지 않은 변경 0):
{
  "title": "#988 맨 위에서 끌면 헤더부터",
  "subtitle": "Android 화면 확인 · 바로 열기 display-latest 기기 캡처와 Preview 스냅샷",
  "results_file": "checks/results.jsonl",
  "sections": [
    {"no": 1, "title": "접힌 채 끌기", "note": "누른 채 700px 끈 상태",
     "pairs": [{"before": "media/android-before-1.png", "after": "media/android-after-1.png", "marks": "record",
                "before_caption": ["새로고침 표시가 당겨져요", "날짜 헤더는 안 나오고", "놓으면 새로고침 1번"],
                "after_caption": ["날짜 헤더가 펼쳐져요", "날짜 글자 윗변 93px", "놓아도 새로고침 0번"]}]},
    {"no": 3, "title": "새로고침 중", "note": "Preview",
     "pairs": [{"before": "media/preview-before/X.png", "after": "media/preview-after/X.png",
                "head": "최신 전시", "before_caption": ["첫 사진 위에 겹쳐 있었어요"],
                "after_caption": ["날짜 헤더 줄로 올라갔어요"]}]}
  ],
  "unintended": [],
  "results": {"auto": ["① 수정 전 실패 → 수정 후 통과", "③ Preview 16장 중 바뀜 1, 그대로 15"],
              "second": {"title": "잡음으로 뺀 것", "lines": ["Preview 4장 — 사진 안 픽셀만 달라짐", ["줄여 비교하면 바뀐 곳 0", true]]},
              "human": ["④ 이어서 당길 때 표시가 튀지 않는지", ["움직임이라 기기에서 직접 봐 주세요", true]]}
}

쌍(pair)의 칸
- before, after: 캡처 경로. head: 쌍 위의 작은 제목(플랫폼·화면 이름 등, 없어도 됨)
- before_caption, after_caption: [굵은 한 줄, 회색 줄...]. 해요체
- marks (올가미 자리, 생략하면 "auto". same이면 그리지 않음)
  - "auto": Preview 비교. preview_snapshots.py와 같은 기준으로 바뀐 곳을 찾는다. 자리를 옮긴 것은 각 카드에서 지금 자리 실선,
            다른 카드의 자리 점선. 그 자리에서 바뀐 것은 두 카드 모두 실선
  - "record": 기기 항목. results_file에서 shot이 이 캡처 파일 이름인 마지막 줄의 marks 자리에 실선. 칩(수정 전 실패 등)도 그 줄의 ok로 단다
  - {"before": [[왼,위,오른,아래]...], "after": [...], "was_before": [...], "was_after": [...]}: 직접 적은 자리(원본 px). was_*는 점선(원래 자리)
- same: true면 바뀌지 않아야 하는 쌍(기존 동작 지키기, Preview 그대로). 화살표 대신 '=', 이후 라벨 회색
- chips: {"before": ["수정 전 실패", "fail"], "after": ["수정 후 통과", "pass"]}로 직접 줄 수도 있다(record면 자동)

의도하지 않은 변경(unintended)의 칸: before, after, box([왼,위,오른,아래] 원본 px, "auto"면 첫 바뀐 곳),
  title(굵은 한 줄), seen(보이는 것), checked(확인한 것), cause(원인, 모르면 "모름"). 있으면 제목 아래 빨간 칩과 "먼저 봐 주세요" 섹션
"""
import argparse
import json
import shutil
import sys
import time
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "client-tdd" / "scripts" / "android"))
from preview_snapshots import change_boxes  # noqa: E402  같은 바뀜 기준을 쓰려고 판정 스크립트에서 가져온다

FONT = "/System/Library/Fonts/AppleSDGothicNeo.ttc"
BG = (245, 242, 237); INK = (30, 27, 25); GRAY = (125, 119, 113); LINE = (224, 218, 210)
ACCENT = (196, 82, 52); LOOP = (230, 0, 126); PASS = (46, 140, 87); FAIL = (200, 62, 50); NEUTRAL = (120, 114, 108)
W = 2000; M = 56; PW = 414; JOIN = 70; PAIRGAP = 90
PAIR_W = 2 * PW + JOIN            # 쌍 하나의 폭. 한 줄에 쌍 두 개
HALF_X = M + PAIR_W + PAIRGAP     # 오른쪽 쌍의 x
PAGE_MAX = 4000                   # 이보다 높으면 섹션 경계에서 나눈다
MAX_BYTES = 2 * 1024 * 1024
MIN_LOOP = 44                     # 올가미 최소 크기(원본 px). 작은 아이콘도 보이게


def font(size, weight="Regular"):
    return ImageFont.truetype(FONT, size, index={"Regular": 0, "Medium": 2, "SemiBold": 4, "Bold": 6}[weight])


def canvas(h=6000):
    im = Image.new("RGB", (W, h), BG)
    return im, ImageDraw.Draw(im)


def _grow(b):
    """작은 상자를 MIN_LOOP까지 키운다(가운데 기준)."""
    x0, y0, x1, y1 = b
    if x1 - x0 < MIN_LOOP:
        c = (x0 + x1) / 2; x0, x1 = c - MIN_LOOP / 2, c + MIN_LOOP / 2
    if y1 - y0 < MIN_LOOP:
        c = (y0 + y1) / 2; y0, y1 = c - MIN_LOOP / 2, c + MIN_LOOP / 2
    return x0, y0, x1, y1


def _dashed(d, r, color, width=3, dash=6, gap=5):
    x0, y0, x1, y1 = r
    for x in np.arange(x0, x1, dash + gap):
        d.line([(x, y0), (min(x + dash, x1), y0)], fill=color, width=width)
        d.line([(x, y1), (min(x + dash, x1), y1)], fill=color, width=width)
    for y in np.arange(y0, y1, dash + gap):
        d.line([(x0, y), (x0, min(y + dash, y1))], fill=color, width=width)
        d.line([(x1, y), (x1, min(y + dash, y1))], fill=color, width=width)


def phone(img, now=(), was=()):
    """둥근 폰 카드. now: 이 카드에 지금 있는 바뀐 곳(분홍 실선), was: 원래 자리(분홍 점선). 좌표는 원본 px."""
    s = PW / img.width; ph = round(img.height * s)
    p = img.resize((PW, ph), Image.LANCZOS); d = ImageDraw.Draw(p); pad = 8
    sc = lambda b: [b[0] * s - pad, b[1] * s - pad, b[2] * s + pad, b[3] * s + pad]
    for b in was:
        _dashed(d, sc(_grow(b)), LOOP)
    for b in now:
        d.rounded_rectangle(sc(_grow(b)), 12, outline=LOOP, width=5)
    mask = Image.new("L", p.size, 0); ImageDraw.Draw(mask).rounded_rectangle([0, 0, PW - 1, ph - 1], 30, fill=255)
    card = Image.new("RGB", p.size, BG); card.paste(p, (0, 0), mask)
    ImageDraw.Draw(card).rounded_rectangle([0, 0, PW - 1, ph - 1], 30, outline=LINE, width=2)
    return card


def chip(d, x, y, text, color):
    tw = d.textlength(text, font=font(22, "Bold"))
    d.rounded_rectangle([x, y, x + tw + 26, y + 38], 19, fill=color)
    d.text((x + 13, y + 5), text, font=font(22, "Bold"), fill="white")


def caption(d, x, y, lines, chip_=None, width=PW):
    if chip_:
        chip(d, x, y, chip_[0], {"pass": PASS, "fail": FAIL}.get(chip_[1], NEUTRAL)); y += 52
    if lines:
        y = _wrap(d, x, y, lines[0], font(32, "Bold"), INK, width, 46)
        for line in lines[1:]:
            y = _wrap(d, x, y, line, font(25), GRAY, width, 36)
    return y


def _wrap(d, x, y, text, f, color, width, step):
    """폭을 넘으면 어절 단위로 줄을 바꾼다."""
    line = ""
    for word in str(text).split(" "):
        t = (line + " " + word).strip()
        if line and d.textlength(t, font=f) > width:
            d.text((x, y), line, font=f, fill=color); y += step; line = word
        else:
            line = t
    d.text((x, y), line, font=f, fill=color)
    return y + step


def _find(template, region):
    """region 안에서 template과 가장 비슷한 자리의 (평균 채널 차이, 왼, 위). 못 넣으면 None."""
    th, tw = template.shape[:2]; rh, rw = region.shape[:2]
    if th < 4 or tw < 4 or th > rh or tw > rw:
        return None
    best = None
    for y in range(0, rh - th + 1, 2):
        for x in range(0, rw - tw + 1, 2):
            dv = float(np.abs(region[y:y + th, x:x + tw] - template).mean())
            if best is None or dv < best[0]:
                best = (dv, x, y)
    return best


def auto_marks(before, after, boxes, pad=12, same_thr=10.0):
    """Preview의 바뀐 곳 상자를 카드별 실선(지금 자리)·점선(원래 자리)으로 나눈다.

    한 상자의 이후 모습이 다른 상자의 이전 모습 안에 그대로 있으면 '옮겨짐'이다(#989 새로고침 표시가 사진 위 → 헤더 줄).
    그러면 이전 카드: 원래 자리 실선 + 새 자리 점선, 이후 카드: 새 자리 실선 + 원래 자리 점선.
    옮겨짐이 아닌 상자는 그 자리에서 바뀐 것이라 두 카드 모두 실선이다.
    돌려줌: (now_before, now_after, was_before, was_after)
    """
    a = np.asarray(before, np.int16); b = np.asarray(after, np.int16)
    H, W_ = a.shape[:2]
    clip = lambda r: (max(0, r[0] - pad), max(0, r[1] - pad), min(W_, r[2] + pad), min(H, r[3] + pad))
    moved, used = [], set()
    for j, bj in enumerate(boxes):          # bj: 이후에 무언가 나타난 자리 후보
        x0, y0, x1, y1 = bj
        tmpl = b[y0:y1, x0:x1]
        for i, bi in enumerate(boxes):      # bi: 이전에 그것이 있던 자리 후보
            if i == j or i in used or j in used:
                continue
            rx0, ry0, rx1, ry1 = clip(bi)
            hit = _find(tmpl, a[ry0:ry1, rx0:rx1])
            if hit and hit[0] < same_thr:
                moved.append((i, j)); used |= {i, j}
                break
    now_b, now_a, was_b, was_a = [], [], [], []
    for i, j in moved:
        now_b.append(boxes[i]); was_b.append(boxes[j])
        now_a.append(boxes[j]); was_a.append(boxes[i])
    for k, bk in enumerate(boxes):
        if k not in used:
            now_b.append(bk); now_a.append(bk)
    return now_b, now_a, was_b, was_a


class Spec:
    def __init__(self, path):
        self.path = Path(path); self.root = self.path.parent
        self.data = json.loads(self.path.read_text(encoding="utf-8"))
        rf = self.data.get("results_file")
        self.results = []
        if rf and (self.root / rf).exists():
            self.results = [json.loads(l) for l in (self.root / rf).read_text(encoding="utf-8").splitlines() if l.strip()]
        self.used = []   # 그림에 쓴 원본 (경로, 올릴 이름)

    def img(self, rel, upload_name):
        p = self.root / rel
        if not p.exists():
            sys.exit(f"캡처가 없어요: {p}")
        if all(p.resolve() != q.resolve() for q, _ in self.used):   # 같은 캡처를 여러 번 쓰면 한 번만 올린다
            self.used.append((p, upload_name))
        return Image.open(p).convert("RGB")

    def record_for(self, rel):
        name = Path(rel).name
        hits = [r for r in self.results if r.get("shot") == name]
        return hits[-1] if hits else None


def pair_block(spec, sec, k, pr):
    """쌍 하나(이전 · 화살표 또는 = · 이후 + 캡션)를 그려 돌려준다."""
    tag = f"android-{sec.get('no', 0)}-{k}"
    before = spec.img(pr["before"], f"{tag}-before{Path(pr['before']).suffix}")
    after = spec.img(pr["after"], f"{tag}-after{Path(pr['after']).suffix}")
    same = pr.get("same", False)
    marks = pr.get("marks", "auto")
    now_b = now_a = was_b = was_a = []
    chips = pr.get("chips", {})
    if marks == "record":
        rb, ra = spec.record_for(pr["before"]), spec.record_for(pr["after"])
        if rb is None or ra is None:
            sys.exit(f"results_file에 이 캡처의 판정 줄(shot)이 없어요: {pr['before'] if rb is None else pr['after']}")
        if not same:
            now_b, now_a = list((rb.get("marks") or {}).values()), list((ra.get("marks") or {}).values())
        chips = chips or {"before": (f"수정 전 {'통과' if rb['ok'] else '실패'}", "pass" if rb["ok"] else "fail"),
                          "after": (f"수정 후 {'통과' if ra['ok'] else '실패'}", "pass" if ra["ok"] else "fail")}
    elif marks == "auto":
        if not same:
            boxes = change_boxes(spec.root / pr["before"], spec.root / pr["after"])
            if before.size == after.size:
                now_b, now_a, was_b, was_a = auto_marks(before, after, boxes)
    elif isinstance(marks, dict):
        now_b, now_a = marks.get("before", []), marks.get("after", [])
        was_b, was_a = marks.get("was_before", []), marks.get("was_after", [])

    im, d = canvas(4000); y = 0
    if pr.get("head"):
        d.text((0, y), pr["head"], font=font(32, "Bold"), fill=INK); y += 50
    d.text((0, y), "이전", font=font(28, "Bold"), fill=GRAY)
    d.text((PW + JOIN, y), "이후", font=font(28, "Bold"), fill=NEUTRAL if same else ACCENT); y += 46
    c1, c2 = phone(before, now_b, was_b), phone(after, now_a, was_a)
    im.paste(c1, (0, y)); im.paste(c2, (PW + JOIN, y))
    cx, cy = PW + JOIN // 2, y + min(c1.height, c2.height) // 2
    if same:
        for dy in (-8, 8):
            d.line([(cx - 14, cy + dy), (cx + 14, cy + dy)], fill=NEUTRAL, width=5)
    else:
        d.polygon([(cx - 12, cy - 16), (cx - 12, cy + 16), (cx + 14, cy)], fill=ACCENT)
    y += max(c1.height, c2.height) + 26
    y = max(caption(d, 0, y, pr.get("before_caption", []), chips.get("before")),
            caption(d, PW + JOIN, y, pr.get("after_caption", []), chips.get("after")))
    return im.crop((0, 0, PAIR_W, y + 40))


def section_head(d, x0, x1, y, sec):
    no = sec.get("no")
    text = (f"{'①②③④⑤⑥⑦⑧⑨⑩'[no - 1]} " if isinstance(no, int) and 1 <= no <= 10 else "") + sec.get("title", "")
    d.text((x0, y), text, font=font(40, "Bold"), fill=ACCENT)
    if sec.get("note"):
        tw = d.textlength(text, font=font(40, "Bold"))
        d.text((x0 + tw + 18, y + 14), sec["note"], font=font(24), fill=GRAY)
    d.line([(x0, y + 62), (x1, y + 62)], fill=ACCENT, width=3)
    return y + 92


def section_rows(spec, sections):
    """섹션을 줄(블록)로 놓는다. 쌍 하나짜리 섹션 둘은 한 줄에 나란히, 쌍이 둘 이상이면 한 줄에 쌍 두 개씩."""
    units = []   # (섹션, 그 섹션의 쌍 그림 목록)
    for sec in sections:
        units.append((sec, [pair_block(spec, sec, k, pr) for k, pr in enumerate(sec["pairs"], 1)]))
    rows, i = [], 0
    while i < len(units):
        sec, pairs = units[i]
        if len(pairs) == 1 and i + 1 < len(units) and len(units[i + 1][1]) == 1:
            rows.append([(sec, pairs, M), (units[i + 1][0], units[i + 1][1], HALF_X)]); i += 2
        else:
            for j in range(0, len(pairs), 2):
                rows.append([(sec if j == 0 else dict(sec, title=sec.get("title", "") + " (이어서)"), pairs[j:j + 2], M)])
            i += 1
    blocks = []
    for row in rows:
        im, d = canvas(); y_end = 0
        for sec, pairs, x in row:
            y = section_head(d, x, x + PAIR_W if len(pairs) == 1 else W - M, 0, sec)
            for k, p in enumerate(pairs):
                im.paste(p, (x + k * (PAIR_W + PAIRGAP), y))
                y_end = max(y_end, y + p.height)
        blocks.append(im.crop((0, 0, W, y_end + 30)))
    return blocks


def unintended_block(spec, items):
    im, d = canvas(); y = section_head(d, M, W - M, 0, dict(title="먼저 봐 주세요", note="계획 항목으로 설명되지 않는 변화"))
    for k, it in enumerate(items, 1):
        before = spec.img(it["before"], f"android-unintended-{k}-before{Path(it['before']).suffix}")
        after = spec.img(it["after"], f"android-unintended-{k}-after{Path(it['after']).suffix}")
        box = it.get("box", "auto")
        if box == "auto":
            boxes = change_boxes(spec.root / it["before"], spec.root / it["after"])
            box = boxes[0] if boxes else [0, 0, before.width, before.height]
        x0, y0, x1, y1 = box; pad = 24
        crop = (max(0, x0 - pad), max(0, y0 - pad), min(before.width, x1 + pad), min(before.height, y1 + pad))
        cw = 560; ch = max(1, round(cw * (crop[3] - crop[1]) / (crop[2] - crop[0])))
        ch = min(ch, 700)
        d.text((M, y), "이전", font=font(28, "Bold"), fill=GRAY); d.text((M + cw + 30, y), "이후", font=font(28, "Bold"), fill=FAIL)
        y += 46
        for j, src in enumerate((before, after)):
            t = src.crop(crop).resize((cw, ch), Image.NEAREST if (crop[2] - crop[0]) * 3 < cw else Image.LANCZOS)
            x = M + j * (cw + 30)
            im.paste(t, (x, y)); d.rounded_rectangle([x, y, x + cw, y + ch], 8, outline=FAIL, width=3)
        tx = M + 2 * cw + 90; ty = y
        ty = _wrap(d, tx, ty, it.get("title", ""), font(32, "Bold"), INK, W - M - tx, 46)
        for label, key in (("보이는 것", "seen"), ("확인한 것", "checked"), ("원인", "cause")):
            ty = _wrap(d, tx, ty, f"{label}: {it.get(key) or ('모름' if key == 'cause' else '')}", font(25), GRAY, W - M - tx, 36)
        y = max(y + ch, ty) + 50
    return im.crop((0, 0, W, y))


def title_block(spec, n_unintended):
    im, d = canvas(800); y = 50
    y = _wrap(d, M, y, spec.data.get("title", "화면 확인"), font(66, "Bold"), INK, W - 2 * M, 96)
    if spec.data.get("subtitle"):
        y = _wrap(d, M, y, spec.data["subtitle"], font(27), GRAY, W - 2 * M, 44)
    y += 8
    if n_unintended:
        chip(d, M, y, f"의도하지 않은 변경 {n_unintended} — 먼저 봐 주세요", FAIL); y += 52
    return im.crop((0, 0, W, y + 36))


def _card_lines(d, x, y, lines, color, width):
    """결과 카드의 글머리표 줄. (글, True)면 점 없이 회색으로 앞 줄에 잇는다. 끝 y를 돌려준다."""
    for item in lines:
        text, cont = (item, False) if isinstance(item, str) else (item[0], bool(item[1]))
        if not cont:
            d.ellipse([x + 2, y + 12, x + 12, y + 22], fill=color)
        y = _wrap(d, x + 26, y, text, font(25), GRAY if cont else INK, width - 26, 42)
    return y


def results_block(spec, n_unintended):
    r = spec.data.get("results", {})
    auto = list(r.get("auto", []))
    if not n_unintended and not any("의도하지 않은 변경" in (x if isinstance(x, str) else x[0]) for x in auto):
        auto.append("의도하지 않은 변경 0")
    second = r.get("second") or {"title": "잡음으로 뺀 것", "lines": ["없음"]}
    cards = [("자동 확인", auto, PASS), (second["title"], second.get("lines", []) or ["없음"], NEUTRAL),
             ("사람이 볼 것", r.get("human", []) or ["없음"], ACCENT)]
    gap = 28; cw = (W - 2 * M - 2 * gap) // 3; inner = cw - 34 - 30
    _, probe = canvas(10)
    h = max(_card_lines(probe, 0, 86, lines, color, inner) for _, lines, color in cards) + 26
    im, d = canvas(); y = section_head(d, M, W - M, 0, dict(title="결과"))
    for k, (title, lines, color) in enumerate(cards):
        x = M + k * (cw + gap)
        d.rounded_rectangle([x, y, x + cw, y + h], 24, fill="white", outline=LINE, width=2)
        d.text((x + 34, y + 28), title, font=font(32, "Bold"), fill=INK)
        _card_lines(d, x + 34, y + 86, lines, color, inner)
    return im.crop((0, 0, W, y + h + 40))


def paginate(blocks, first_h, cont_h=110):
    """블록을 PAGE_MAX 높이 안에서 차례로 쌓아 장마다 블록 목록을 돌려준다. 블록 하나가 넘으면 그 블록만 한 장이다."""
    pages, cur, h = [], [], first_h
    for b in blocks:
        if cur and h + b.height > PAGE_MAX:
            pages.append(cur); cur, h = [], cont_h
        cur.append(b); h += b.height
    pages.append(cur)
    return pages


def save_jpeg(im, path):
    for q in (88, 82, 75, 68, 60):
        im.save(path, "JPEG", quality=q, optimize=True, progressive=True)
        if path.stat().st_size <= MAX_BYTES:
            return q
    sys.exit(f"2MB 아래로 줄이지 못했어요: {path} — 섹션을 나눠 주세요")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("spec"); ap.add_argument("-o", "--out", required=True, help="출력 이름(확장자 없이). 예: $EVID/media/android-sheet")
    a = ap.parse_args()
    t0 = time.time()
    spec = Spec(a.spec)
    unintended = spec.data.get("unintended", [])
    blocks = []
    if unintended:
        blocks.append(unintended_block(spec, unintended))
    blocks += section_rows(spec, spec.data.get("sections", []))
    blocks.append(results_block(spec, len(unintended)))
    title = title_block(spec, len(unintended))
    out = Path(a.out); out.parent.mkdir(parents=True, exist_ok=True)
    pages = paginate(blocks, title.height)
    sheets = []
    for k, page in enumerate(pages, 1):
        if k == 1:
            page = [title] + page
        else:   # 이어지는 장에는 작은 머리말
            im, d = canvas(120)
            d.text((M, 40), f"{spec.data.get('title', '화면 확인')} ({k}/{len(pages)})", font=font(32, "Bold"), fill=GRAY)
            page = [im.crop((0, 0, W, 110))] + page
        h = sum(b.height for b in page) + 30
        sheet = Image.new("RGB", (W, h), BG); y = 0
        for b in page:
            sheet.paste(b, (0, y)); y += b.height
        p = out.parent / f"{out.name}-{k}.jpg"
        q = save_jpeg(sheet, p)
        sheets.append(dict(file=str(p), size=sheet.size, bytes=p.stat().st_size, quality=q))
    od = out.parent / "originals"; od.mkdir(exist_ok=True)
    originals = []
    for src, name in spec.used:
        dst = od / name
        if str(src.resolve()) != str(dst.resolve()):
            shutil.copy2(src, dst)
        if str(dst) not in originals:
            originals.append(str(dst))
    print(json.dumps(dict(sheets=sheets, originals=originals, sec=round(time.time() - t0, 1)), ensure_ascii=False))


if __name__ == "__main__":
    main()
