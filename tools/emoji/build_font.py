#!/usr/bin/env python3
"""Собирает шрифт эмодзи для EmojiCompat из набора Microsoft Fluent Emoji (3D, лицензия MIT).

    python3 build_font.py <fluentui-emoji/assets> <out.ttf>

Получается CBDT-шрифт (цветные PNG внутри) с таблицей meta/Emji: её читает androidx.emoji2
(MetadataRepo). Каждое эмодзи лежит в шрифте под своим кодом из частной области U+F0001…,
а последовательности символов, которые ему соответствуют, описаны в метаданных.
"""
import io
import json
import shutil
import struct
import subprocess
import sys
from pathlib import Path

import flatbuffers
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import newTable
from fontTools.ttLib.tables.DefaultTable import DefaultTable
from PIL import Image

SIZE = 128          # сторона картинки в пикселях
PPEM = 109          # при каком кегле картинка рисуется 1:1 (как в Noto Color Emoji)
UPEM = 2048
ASCENT_PX = 101     # выше базовой линии; ниже остаётся SIZE - ASCENT_PX
PUA_START = 0xF0001
TONES = ["Default", "Light", "Medium-Light", "Medium", "Medium-Dark", "Dark"]
FE0F = 0xFE0F
PNGQUANT = shutil.which("pngquant")


def parse_seq(s):
    return [int(x, 16) for x in s.split()]


def collect(assets: Path):
    """[(последовательность с FE0F, путь к png)]"""
    out = []
    for d in sorted(p for p in assets.iterdir() if p.is_dir()):
        meta_file = d / "metadata.json"
        if not meta_file.exists():
            continue
        meta = json.loads(meta_file.read_text(encoding="utf-8"))
        tones = meta.get("unicodeSkintones")
        if tones:
            for i, tone in enumerate(TONES):
                if i >= len(tones):
                    break
                pngs = sorted((d / tone / "3D").glob("*.png"))
                if pngs:
                    out.append((parse_seq(tones[i]), pngs[0]))
        else:
            pngs = sorted((d / "3D").glob("*.png"))
            if pngs and meta.get("unicode"):
                out.append((parse_seq(meta["unicode"]), pngs[0]))
    return out


def png_bytes(path: Path) -> bytes:
    img = Image.open(path).convert("RGBA").resize((SIZE, SIZE), Image.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    full = buf.getvalue()
    if PNGQUANT:
        # Палитра на 256 цветов с прозрачностью: в 3–4 раза меньше, на глаз не отличить.
        r = subprocess.run([PNGQUANT, "--quality=65-95", "--speed=3", "--strip", "-"], input=full, capture_output=True)
        if r.returncode == 0 and r.stdout:
            return min(full, r.stdout, key=len)
    return full


def build_cbdt_cblc(images):
    """images: список PNG по порядку глифов 1..N. Возвращает (CBDT, CBLC) в бинарном виде."""
    n = len(images)
    cbdt = bytearray(struct.pack(">HH", 3, 0))
    offsets = []
    for data in images:
        offsets.append(len(cbdt))
        # format 17: smallGlyphMetrics + длина + PNG
        cbdt += struct.pack(">BBbbB", SIZE, SIZE, 0, ASCENT_PX, SIZE)
        cbdt += struct.pack(">I", len(data)) + data
    offsets.append(len(cbdt))

    first, last = 1, n
    image_data_offset = offsets[0]
    # IndexSubTableArray (1 запись) + IndexSubTable format 1
    sub = struct.pack(">HHI", 1, 17, image_data_offset)
    sub += b"".join(struct.pack(">I", o - image_data_offset) for o in offsets)
    array = struct.pack(">HHI", first, last, 8)  # подтаблица сразу после массива (8 байт)
    index_tables = array + sub

    header_size = 8
    size_record = 48
    index_offset = header_size + size_record
    line = struct.pack(">bbBbbbbbbbbb", ASCENT_PX, ASCENT_PX - SIZE, SIZE, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    record = struct.pack(">IIII", index_offset, len(index_tables), 1, 0) + line + line
    record += struct.pack(">HHBBBb", first, last, PPEM, PPEM, 32, 1)
    cblc = struct.pack(">HHI", 3, 0, 1) + record + index_tables
    return bytes(cbdt), cblc


def build_metadata(entries):
    """FlatBuffer MetadataList для androidx.emoji2. entries: [(id, codepoints, emojiStyle)]"""
    b = flatbuffers.Builder(1024 * 64)
    items = []
    for eid, cps, style in entries:
        b.StartVector(4, len(cps), 4)
        for cp in reversed(cps):
            b.PrependInt32(cp)
        vec = b.EndVector()
        b.StartObject(7)
        b.PrependInt32Slot(0, eid, 0)            # id
        b.PrependBoolSlot(1, style, False)       # emojiStyle
        b.PrependInt16Slot(2, 0, 0)              # sdkAdded
        b.PrependInt16Slot(3, 0, 0)              # compatAdded
        b.PrependInt16Slot(4, SIZE, 0)           # width
        b.PrependInt16Slot(5, SIZE, 0)           # height
        b.PrependUOffsetTRelativeSlot(6, vec, 0)  # codepoints
        items.append(b.EndObject())
    sha = b.CreateString("fluent-emoji-3d")
    b.StartVector(4, len(items), 4)
    for it in reversed(items):
        b.PrependUOffsetTRelative(it)
    lst = b.EndVector()
    b.StartObject(3)
    b.PrependInt32Slot(0, 1, 0)                  # version
    b.PrependUOffsetTRelativeSlot(1, lst, 0)     # list
    b.PrependUOffsetTRelativeSlot(2, sha, 0)     # sourceSha
    b.Finish(b.EndObject())
    return bytes(b.Output())


def build_meta_table(emji: bytes) -> bytes:
    header = struct.pack(">IIII", 1, 0, 0, 1)
    rec_size = 12
    data_offset = len(header) + rec_size
    rec = b"Emji" + struct.pack(">II", data_offset, len(emji))
    return header + rec + emji


def main():
    assets, out = Path(sys.argv[1]), Path(sys.argv[2])
    seen = set()
    emoji = []  # (codepoints без FE0F, emojiStyle, png)
    for seq, png in collect(assets):
        key = tuple(c for c in seq if c != FE0F)
        if not key or key in seen:
            continue
        seen.add(key)
        # Символ, который по умолчанию текстовый (например ❤ = 2764 FE0F), помечаем emojiStyle=false.
        style = not (len(key) == 1 and FE0F in seq)
        emoji.append((list(key), style, png))
    if not emoji:
        sys.exit("Не нашёл ни одного эмодзи в " + str(assets))

    images = [png_bytes(p) for _, _, p in emoji]
    glyph_order = [".notdef"] + [f"e{i}" for i in range(len(emoji))]
    cmap = {PUA_START + i: f"e{i}" for i in range(len(emoji))}

    fb = FontBuilder(UPEM, isTTF=True)
    fb.setupGlyphOrder(glyph_order)
    fb.setupCharacterMap(cmap)
    empty = TTGlyphPen(None).glyph()
    fb.setupGlyf({g: empty for g in glyph_order})
    adv = round(SIZE * UPEM / PPEM)
    fb.setupHorizontalMetrics({g: (adv, 0) for g in glyph_order})
    ascent = round(ASCENT_PX * UPEM / PPEM)
    descent = ascent - adv
    fb.setupHorizontalHeader(ascent=ascent, descent=descent)
    fb.setupNameTable({"familyName": "Ryzik Emoji", "styleName": "Regular"})
    fb.setupOS2(sTypoAscender=ascent, sTypoDescender=descent, usWinAscent=ascent, usWinDescent=-descent)
    fb.setupPost()
    font = fb.font

    cbdt, cblc = build_cbdt_cblc(images)
    for tag, data in (("CBDT", cbdt), ("CBLC", cblc)):
        t = DefaultTable(tag)
        t.data = data
        font[tag] = t
    meta = DefaultTable("meta")
    meta.data = build_meta_table(build_metadata([(PUA_START + i, k, s) for i, (k, s, _) in enumerate(emoji)]))
    font["meta"] = meta

    out.parent.mkdir(parents=True, exist_ok=True)
    font.save(str(out))
    print(f"{len(emoji)} эмодзи, {out.stat().st_size / 1e6:.1f} МБ → {out}")


if __name__ == "__main__":
    main()
