#!/usr/bin/env python3
"""Writes the app's text from starbooru-web's Fluent catalogues.

tools/strings.txt and tools/strings.d/*.txt list, by group, the Fluent
messages the app shows. Each group becomes
app/src/main/res/values/strings_<group>.xml and its Italian twin in
values-it. A message's variables become positional arguments in the
order they first appear (listed in a comment above it); one that picks a
plural form becomes a <plurals> with that number as %N$d.

    python3 tools/strings.py [path to starbooru-web] [path to the reference app]

Text the web app doesn't have lives by hand in values/strings.xml and
values-it/strings.xml.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT.parent / "starbooru-web"
# The archived reference app, for the text only it had (the media cache).
REFERENCE = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT.parent / "StarBooru"
RES = ROOT / "app/src/main/res"
LANGS = {"en": "values", "it": "values-it"}


def load(path):
    """Message id to its raw value, for every message in a catalogue."""
    msgs, key, buf = {}, None, []

    def done():
        if key:
            msgs[key] = "\n".join(buf).strip()

    for line in path.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^([a-z][a-z0-9-]*)\s*=\s?(.*)$", line)
        if m:
            done()
            key, buf = m.group(1), [m.group(2)]
        elif key and (line.startswith(" ") or not line.strip() or line.strip() == "}"):
            buf.append(line.strip())
        else:
            done()
            key, buf = None, []
    done()
    return msgs


SELECT = re.compile(r"\{\s*\$([a-z_]+)\s*->(.*?)\n\s*\}", re.S)
VARIANT = re.compile(r"(\*?)\[([a-z0-9]+)\]\s*(.*)")
PLACEABLE = re.compile(r"\{\s*\$([a-z_]+)\s*\}")
QUANTITY = {"0": "zero", "one": "one", "other": "other", "few": "few", "many": "many", "zero": "zero"}


def parse(value):
    """(variants, selector): variants maps a plural quantity to its text."""
    m = SELECT.search(value)
    if not m:
        return {"": " ".join(value.split())}, None
    before, after = value[: m.start()], value[m.end():]
    variants = {}
    for line in m.group(2).strip().splitlines():
        v = VARIANT.match(line.strip())
        if v:
            variants[QUANTITY.get(v.group(2), v.group(2))] = " ".join((before + v.group(3) + after).split())
    return variants, m.group(1)


def variables(texts, selector):
    order = []
    if selector:
        order.append(selector)
    for t in texts:
        for name in PLACEABLE.findall(t):
            if name not in order:
                order.append(name)
    return order


def escape(text, order, selector):
    def arg(m):
        name = m.group(1)
        kind = "d" if name == selector else "s"
        return f"%{order.index(name) + 1}${kind}"

    text = text.replace("%", "%%") if order else text
    text = PLACEABLE.sub(arg, text)
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    if text[:1] in "@?":
        text = "\\" + text
    return text


def android_name(key):
    return key.replace("-", "_")


def main():
    # tools/strings.txt, then one file per area in tools/strings.d.
    parts = [ROOT / "tools/strings.txt", *sorted((ROOT / "tools/strings.d").glob("*.txt"))]
    spec = "\n".join(p.read_text(encoding="utf-8") for p in parts if p.exists())
    groups, group = {}, None
    for line in spec.splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        if line.startswith("[") and line.endswith("]"):
            group = line[1:-1]
            groups.setdefault(group, [])
        elif group:
            groups[group].extend(line.split())
    catalogues = {}
    for lang in LANGS:
        reference = REFERENCE / f"apps/app/locales/{lang}.ftl"
        # The web app's messages first, in its order, and its words where
        # both have one; then what only the reference has.
        merged = load(WEB / f"locales/{lang}.ftl")
        for key, value in (load(reference) if reference.exists() else {}).items():
            merged.setdefault(key, value)
        catalogues[lang] = merged
    problems = []
    # A message goes in the first group that asks for it: Android refuses a
    # name defined twice.
    taken = set()
    for group, patterns in groups.items():
        keys = []
        for p in patterns:
            found = [k for k in catalogues["en"] if re.fullmatch(p, k)]
            if not found:
                problems.append(f"{group}: no message matches {p}")
            keys.extend(k for k in found if k not in keys and k not in taken)
        taken.update(keys)
        for lang, folder in LANGS.items():
            out = ['<?xml version="1.0" encoding="utf-8"?>',
                   "<!-- Written by tools/strings.py from starbooru-web's locales; edit tools/strings.txt. -->",
                   "<resources>"]
            for key in keys:
                value = catalogues[lang].get(key)
                if value is None:
                    problems.append(f"{lang}: {key} is missing")
                    continue
                variants, selector = parse(value)
                order = variables(variants.values(), selector)
                name = android_name(key)
                if order:
                    out.append(f"    <!-- {', '.join(order)} -->")
                if selector:
                    out.append(f'    <plurals name="{name}">')
                    for q in ("zero", "one", "few", "many", "other"):
                        if q in variants:
                            out.append(f'        <item quantity="{q}">{escape(variants[q], order, selector)}</item>')
                    out.append("    </plurals>")
                else:
                    text = escape(variants[""], order, selector)
                    extra = ' formatted="false"' if not order and "%" in text else ""
                    out.append(f'    <string name="{name}"{extra}>{text}</string>')
            out.append("</resources>")
            path = RES / folder / f"strings_{group}.xml"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("\n".join(out) + "\n", encoding="utf-8")
    for p in problems:
        print(p, file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
