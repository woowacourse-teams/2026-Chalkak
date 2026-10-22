"""Pretendard를 랜딩페이지용 woff2로 줄인다.

iOS 앱 번들의 Pretendard(OFL)에서 KS X 1001 한글 2,350자, ASCII와
src 아래 소스에 실제로 쓰인 글자만 남긴다. 문구에 새 글자가 들어가면 다시 실행한다.

    python3 scripts/subset-fonts.py   # landing-web 디렉터리에서 실행, fonttools·brotli 필요
"""

from pathlib import Path

from fontTools import subset

ROOT = Path(__file__).resolve().parent.parent
FONT_SOURCE = ROOT.parent / "client/iOS/chalkak/chalkak/DesignSystem/Resources/Fonts"
OUTPUT = ROOT / "src/app/fonts"
WEIGHTS = {"Regular": 400, "SemiBold": 600, "Bold": 700}


def ks_x_1001_hangul() -> set[str]:
    chars = set()
    for lead in range(0xB0, 0xC9):
        for trail in range(0xA1, 0xFF):
            try:
                chars.add(bytes([lead, trail]).decode("euc-kr"))
            except UnicodeDecodeError:
                pass
    return chars


def source_chars() -> set[str]:
    chars = set()
    for path in (ROOT / "src").rglob("*"):
        if path.suffix in {".ts", ".tsx", ".css"}:
            chars.update(path.read_text(encoding="utf-8"))
    return chars


def main() -> None:
    text = ks_x_1001_hangul() | source_chars() | {chr(c) for c in range(0x20, 0x7F)}
    text |= set("·—–‘’“”…→←♥♡※")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, weight in WEIGHTS.items():
        options = subset.Options()
        options.flavor = "woff2"
        options.layout_features = ["*"]
        font = subset.load_font(str(FONT_SOURCE / f"Pretendard-{name}.otf"), options)
        subsetter = subset.Subsetter(options)
        subsetter.populate(text="".join(sorted(text)))
        subsetter.subset(font)
        out = OUTPUT / f"Pretendard-{weight}.woff2"
        subset.save_font(font, str(out), options)
        print(f"{out.relative_to(ROOT)}: {out.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
