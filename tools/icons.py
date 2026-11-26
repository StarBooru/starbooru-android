#!/usr/bin/env python3
"""Turns the web app's icons and logo into Android vector drawables.

    python3 tools/icons.py [path to starbooru-web]

Reads src/components/icons.rs and src/components/brand_paths.rs from the web
app (../starbooru-web by default) and writes app/src/main/res/drawable/
ic_<name>.xml for every icon, plus ic_brand_mark.xml, ic_brand_mark_compact.xml
and ic_brand_wordmark.xml. The drawables are black, for Compose to tint.
Run it again when the web's icons change.
"""

import re
import sys
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable"
INK = "#FF000000"

# Names snake_case can't guess.
NAMES = {"GitHub": "github"}

SHAPE = re.compile(
    r'Icon::(\w+)\s*=>\s*\{?\s*Shape::(Fill|EvenOdd|Stroke)\(\s*'
    r'(?:"([\d.]+)"\s*,\s*)?"([^"]+)"\s*,?\s*\)'
)

# How many numbers each path command takes.
ARITY = {"M": 2, "L": 2, "T": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "A": 7, "Z": 0}
NUMBER = re.compile(r"[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?")


def fail(message):
    sys.exit(f"icons.py: {message}")


def plain(number):
    """A number as every Android parser reads it: no exponent, no bare dot."""
    if "e" in number or "E" in number:
        number = format(Decimal(number), "f")
    sign = "-" if number.startswith("-") else ""
    number = number.lstrip("+-")
    if number.startswith("."):
        number = "0" + number
    if number.endswith("."):
        number = number[:-1]
    return sign + number


def normalise(d):
    """SVG path data, each command spelled out and its numbers apart."""
    out = []
    i, n = 0, len(d)
    command = None

    def skip():
        nonlocal i
        while i < n and d[i] in " \t\r\n,":
            i += 1

    while True:
        skip()
        if i >= n:
            break
        if d[i].isalpha():
            command = d[i]
            i += 1
            if command.upper() not in ARITY:
                fail(f"unknown path command {command!r} in {d[:40]}…")
            if command.upper() == "Z":
                out.append(command)
                continue
        elif command is None or command.upper() == "Z":
            fail(f"a number without a command at {i} in {d[:40]}…")
        args = []
        for k in range(ARITY[command.upper()]):
            skip()
            if command.upper() == "A" and k in (3, 4):
                # An arc's flags are one digit each, with or without a gap.
                if i >= n or d[i] not in "01":
                    fail(f"an arc flag expected at {i} in {d[:40]}…")
                args.append(d[i])
                i += 1
            else:
                m = NUMBER.match(d, i)
                if not m:
                    fail(f"a number expected at {i}: {d[i:i + 20]!r}")
                args.append(plain(m.group()))
                i = m.end()
        out.append(command + " ".join(args))
        # More pairs after a move are lines.
        if command == "M":
            command = "L"
        elif command == "m":
            command = "l"
    if not out or out[0][0] not in "Mm":
        fail(f"path data must start with a move: {d[:40]}…")
    return "".join(out)


def snake(name):
    return NAMES.get(name) or re.sub(r"(?<=[a-z0-9])(?=[A-Z])", "_", name).lower()


def number_text(x):
    return f"{x:.2f}".rstrip("0").rstrip(".")


def vector(width_dp, height_dp, viewport_w, viewport_h, paths):
    lines = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{number_text(width_dp)}dp"',
        f'    android:height="{number_text(height_dp)}dp"',
        f'    android:viewportWidth="{viewport_w}"',
        f'    android:viewportHeight="{viewport_h}">',
    ]
    for attributes in paths:
        lines.append("    <path")
        lines.extend(f'        android:{k}="{v}"' for k, v in attributes[:-1])
        k, v = attributes[-1]
        lines.append(f'        android:{k}="{v}" />')
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def icon_path(kind, width, d):
    data = ("pathData", normalise(d))
    if kind == "Fill":
        return [("fillColor", INK), data]
    if kind == "EvenOdd":
        return [("fillColor", INK), ("fillType", "evenOdd"), data]
    return [
        ("strokeColor", INK),
        ("strokeWidth", width),
        ("strokeLineCap", "round"),
        ("strokeLineJoin", "round"),
        data,
    ]


def icons(web):
    source = (web / "src" / "components" / "icons.rs").read_text(encoding="utf-8")
    # Comments sit on lines of their own.
    code = "\n".join(line for line in source.splitlines() if not line.lstrip().startswith("//"))
    enum = re.search(r"pub enum Icon \{(.*?)\n\}", code, re.S)
    if not enum:
        fail("no `pub enum Icon` in icons.rs")
    variants = re.findall(r"^\s*(\w+),\s*$", enum.group(1), re.M)
    shapes = {}
    for name, kind, width, d in SHAPE.findall(code):
        if name in shapes:
            fail(f"Icon::{name} has two shapes")
        if (kind == "Stroke") != bool(width):
            fail(f"Icon::{name}: only a stroke has a width")
        shapes[name] = (kind, width, d)
    missing = [v for v in variants if v not in shapes]
    extra = [s for s in shapes if s not in variants]
    if missing or extra:
        fail(f"icons without a shape: {missing}; shapes without an icon: {extra}")
    written = []
    for name in variants:
        kind, width, d = shapes[name]
        file = f"ic_{snake(name)}.xml"
        (OUT / file).write_text(vector(24, 24, 24, 24, [icon_path(kind, width, d)]), encoding="utf-8")
        written.append(file)
    return written


def brand(web):
    source = (web / "src" / "components" / "brand_paths.rs").read_text(encoding="utf-8")
    text = dict(re.findall(r'pub const (\w+): &str = "([^"]+)";', source))
    size = {k: int(v) for k, v in re.findall(r"pub const (\w+): u32 = (\d+);", source)}
    try:
        width = size["WIDTH"]
        cuts = [
            ("ic_brand_mark.xml", size["FULL_HEIGHT"], [text["FULL_CROWN"], text["FULL_STAR"]]),
            ("ic_brand_mark_compact.xml", size["COMPACT_HEIGHT"], [text["COMPACT_CROWN"], text["COMPACT_STAR"]]),
        ]
        wordmark = (size["WORDMARK_WIDTH"], size["WORDMARK_HEIGHT"], text["WORDMARK"])
    except KeyError as missing:
        fail(f"brand_paths.rs has no {missing}")
    written = []
    # The mark 48 dp tall, the wordmark 24, before Compose sizes them.
    for file, height, ds in cuts:
        paths = [[("fillColor", INK), ("pathData", normalise(d))] for d in ds]
        (OUT / file).write_text(vector(48 * width / height, 48, width, height, paths), encoding="utf-8")
        written.append(file)
    w, h, d = wordmark
    paths = [[("fillColor", INK), ("pathData", normalise(d))]]
    (OUT / "ic_brand_wordmark.xml").write_text(vector(24 * w / h, 24, w, h, paths), encoding="utf-8")
    written.append("ic_brand_wordmark.xml")
    return written


def main():
    web = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT.parent / "starbooru-web"
    if not (web / "src" / "components" / "icons.rs").is_file():
        fail(f"no starbooru-web at {web}")
    OUT.mkdir(parents=True, exist_ok=True)
    written = icons(web)
    logo = brand(web)
    print(f"{len(written)} icons and {len(logo)} logo drawables in {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
