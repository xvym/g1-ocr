"""Rebuild the pixel resource from pinned upstream graphics; never reads labels.

Development only: Python 3 + Pillow. The Java application reads the checked-in TSV.
"""
import hashlib
import io
import pathlib
import re
import unicodedata
import urllib.request

from PIL import Image

COMMIT = "953f41b34108621b2bf13c3b1e53abfc9c3e5aec"
BASE = f"https://raw.githubusercontent.com/Narishma-gb/pokegreen/{COMMIT}/"
ROOT = pathlib.Path(__file__).resolve().parents[1]
FILES = ["constants/charmap.asm", "gfx/font/font.png", "gfx/font/font_extra.png",
         "gfx/font/font_battle_extra.png"]
data = {p: urllib.request.urlopen(BASE + p).read() for p in FILES}
BLUE_COMMIT = "69cd1814203dd24533c46af383d7cff5591aea90"
BLUE_BORDER = "pokeblue/gfx/font/font_extra.png"
data[BLUE_BORDER] = urllib.request.urlopen(
    f"https://raw.githubusercontent.com/Narishma-gb/pokeblue/{BLUE_COMMIT}/gfx/font/font_extra.png").read()
charmap = data[FILES[0]].decode("utf-8")
mapping = re.findall(r'charmap "([^"]+)",\s*\$([0-9a-f]+)', charmap)


def tile(path, index):
    image = Image.open(io.BytesIO(data[path])).convert("L")
    x, y = index % (image.width // 8) * 8, index // (image.width // 8) * 8
    return sum(1 << (dy * 8 + dx) for dy in range(8) for dx in range(8)
               if image.getpixel((x + dx, y + dy)) < 128)


glyphs = {}
font_mapping = re.findall(r'charmap "([^"]+)",\s*\$([0-9a-f]+)',
                          charmap.split('; Actual characters (from gfx/font/font.png)')[1])
extra_mapping = re.findall(r'charmap "([^"]+)",\s*\$([0-9a-f]+)',
                           charmap.split('; Actual characters (from gfx/font/font_extra.png)')[1]
                           .split('; Actual characters (from gfx/font/font_battle_extra.png)')[0])
for token, code in font_mapping + extra_mapping:
    code = int(code, 16)
    if code >= 0x80:
        glyphs[token] = tile(FILES[1], code - 0x80)
    elif 0x60 <= code <= 0x7f:
        glyphs[token] = tile(FILES[2], code - 0x60)

# Explicit charmap aliases above share one tile. Compose marks exactly as displayed
# by the game: a full mark tile above the base tile, never a linguistic guess.
entries = {}
for token, pixels in glyphs.items():
    role = "text"
    if token in "┌─┐│└┘":
        role = "border"
    elif token in "▷▶▼゜゛":
        role = "control"
    text = {"　": " ", "⋯": "…"}.get(token, token)
    text = text.translate(str.maketrans("０１２３４５６７８９", "0123456789"))
    entries.setdefault((role, pixels, 0), []).append(text)
for token, code in mapping:
    parts = unicodedata.normalize("NFD", token)
    if len(parts) == 2 and parts[1] in "\u3099\u309a" and parts[0] in glyphs:
        key = ("text", glyphs[parts[0]], glyphs["゛" if parts[1] == "\u3099" else "゜"])
        if token not in entries.setdefault(key, []):
            entries[key].append(token)
entries[("text", tile(FILES[3], 0x6e - 0x62), 0)] = ["<Lv>"]
for token, code in extra_mapping:
    if token in "┌┐└┘":
        entries[("border", tile(BLUE_BORDER, int(code, 16) - 0x60), 0)] = [token]
target = ROOT / "src/main/resources/font.tsv"
target.parent.mkdir(parents=True, exist_ok=True)
lines = ["# role\tbase_8x8_lsb_xy\tmark_8x8_lsb_xy\tUnicode_candidates"]
lines += [f"{role}\t{base:016x}\t{mark:016x}\t{'|'.join(tokens)}"
          for (role, base, mark), tokens in entries.items()]
target.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
for path, blob in data.items():
    print(hashlib.sha256(blob).hexdigest(), path)
print(hashlib.sha256(target.read_bytes()).hexdigest(), "font.tsv")
