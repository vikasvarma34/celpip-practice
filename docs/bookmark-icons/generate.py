"""Generate tiny, antialiased navy bookmark glyphs using only Python's standard library."""
from pathlib import Path
import base64
import math
import re
import struct
import zlib

ROOT = Path(__file__).resolve().parent
NAVY = (36, 59, 96)
SIZE, SCALE = 32, 4

def polygon(x, y, vertices):
    inside = False
    previous = vertices[-1]
    for current in vertices:
        ax, ay = previous
        bx, by = current
        if (ay > y) != (by > y) and x < (bx - ax) * (y - ay) / (by - ay) + ax:
            inside = not inside
        previous = current
    return inside

def glyph(name, x, y):
    if name == 'start':
        return polygon(x, y, [(9, 5), (27, 16), (9, 27)])
    if name == 'stop':
        return 7 <= x <= 25 and 7 <= y <= 25
    if name == 'restart':
        radius = math.hypot(x - 16, y - 16)
        angle = math.degrees(math.atan2(y - 16, x - 16)) % 360
        return (8 <= radius <= 12 and (angle >= 10 or angle <= 300) and not 300 < angle < 350) or polygon(x, y, [(25, 3), (28, 13), (18, 11)])
    return polygon(x, y, [(4, 8), (13, 8), (16, 11), (28, 11), (28, 26), (4, 26)])

def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)

def png(name):
    pixels = bytearray()
    for row in range(SIZE):
        pixels.append(0)
        for col in range(SIZE):
            hits = sum(glyph(name, col + (sx + .5) / SCALE, row + (sy + .5) / SCALE) for sy in range(SCALE) for sx in range(SCALE))
            pixels.extend((*NAVY, round(255 * hits / SCALE ** 2)))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', SIZE, SIZE, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(bytes(pixels))) + chunk(b'IEND', b'')

html_path = ROOT.parent / 'CELPIP-bookmarks.html'
html = html_path.read_text()
for name, title in [('start', 'Start%20Server'), ('stop', 'Stop%20Server'), ('restart', 'Restart%20Server'), ('files', 'Practice%20Files')]:
    data = png(name)
    (ROOT / f'{name}.png').write_bytes(data)
    icon = 'data:image/png;base64,' + base64.b64encode(data).decode()
    pattern = rf'(<A HREF="shortcuts://run-shortcut\?name=CELPIP%20{title}")(?: ICON="[^"]*")?'
    html, count = re.subn(pattern, lambda match: match[1] + f' ICON="{icon}"', html)
    assert count == 1
html_path.write_text(html)
