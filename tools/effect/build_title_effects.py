"""Tạo hiệu ứng danh hiệu động (DataEffect + ImgEffect x1..x4) cho Đón Xuân (303) và Phá Cỗ Trung Thu (300, 301)."""
import struct, sys, math
from PIL import Image

REPO = sys.argv[1]
SRC511 = sys.argv[2]


def trim(im):
    bb = im.getbbox()
    return im.crop(bb) if bb else im


def pad4(im):
    w, h = im.size
    W, H = (w + 3) // 4 * 4, (h + 3) // 4 * 4
    o = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    o.alpha_composite(im, ((W - w) // 2, (H - h) // 2))
    return o


def pack(sprites):
    """Xếp sprite (x4) theo hàng, tọa độ chia hết cho 4, ảnh x1 <= 255."""
    x = y = rowh = 0
    maxw = 4 * 250
    pos = []
    for s in sprites:
        w, h = s.size
        if x + w > maxw:
            x, y, rowh = 0, y + rowh, 0
        pos.append((x, y))
        x += w
        rowh = max(rowh, h)
    W = max(p[0] + s.size[0] for p, s in zip(pos, sprites))
    H = max(p[1] + s.size[1] for p, s in zip(pos, sprites))
    assert W // 4 <= 255 and H // 4 <= 255, (W, H)
    atlas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    for p, s in zip(pos, sprites):
        atlas.alpha_composite(s, p)
    return atlas, pos


def write(eid, sprites, frames, trailer):
    atlas, pos = pack(sprites)
    b = bytearray([len(sprites)])
    for i, (p, s) in enumerate(zip(pos, sprites)):
        b += bytes([i, p[0] // 4, p[1] // 4, s.size[0] // 4, s.size[1] // 4])
    b += struct.pack('>h', len(frames))
    for parts in frames:
        b += bytes([len(parts)])
        for dx, dy, img in parts:
            b += struct.pack('>hhb', dx, dy, img)
    b += struct.pack('>h', len(frames))
    for i in range(len(frames)):
        b += struct.pack('>h', i)
    b += trailer
    open(f'{REPO}/data/effdata/DataEffect_{eid}', 'wb').write(bytes(b))
    for z in (1, 2, 3, 4):
        im = atlas if z == 4 else atlas.resize((atlas.width * z // 4, atlas.height * z // 4), Image.LANCZOS)
        im.save(f'{REPO}/data/effect/x{z}/ImgEffect_{eid}.png')
    print(eid, atlas.size, len(frames), 'frames', len(b), 'bytes')


def falling(n_frames, specs):
    """specs: (img, dx, y0, y1, phase, wobble) -> list theo frame của (dx, dy, img) cho vật rơi."""
    out = [[] for _ in range(n_frames)]
    for img, dx, y0, y1, phase, wob, size in specs:
        for f in range(n_frames):
            t = ((f + phase) % n_frames) / n_frames
            y = int(y0 + (y1 - y0) * t)
            x = int(dx + wob * math.sin(t * 2 * math.pi))
            out[f].append((x - size[0] // 8, y - size[1] // 8, img))  # size x4 -> x1
    return out


# ------------------------------------------------------------------ Đón Xuân (303) từ 511.png
src = Image.open(SRC511).convert('RGBA')
TEXT = [(414, 0, 532, 102), (340, 134, 458, 234), (272, 276, 388, 374), (388, 276, 506, 374),
        (272, 376, 388, 474), (388, 376, 506, 474), (0, 402, 118, 500), (132, 402, 252, 500)]
FLOWER = [(129, 26, 188, 82), (210, 31, 267, 86), (354, 24, 412, 85), (141, 86, 181, 122),
          (368, 90, 406, 127), (98, 58, 122, 82), (0, 0, 26, 22)]
texts = [pad4(trim(src.crop(r))) for r in TEXT]
flowers = [pad4(trim(src.crop(r).resize(((r[2] - r[0]) * 2 // 3, (r[3] - r[1]) * 2 // 3), Image.LANCZOS))) for r in FLOWER]
sprites = texts + flowers
N = 16
fall = falling(N, [
    (8 + 0, -26, -34, 6, 0, 3, flowers[0].size),
    (8 + 3, 24, -30, 8, 5, 3, flowers[3].size),
    (8 + 2, -6, -40, 4, 10, 4, flowers[2].size),
    (8 + 4, 12, -36, 6, 3, 2, flowers[4].size),
    (8 + 6, -18, -38, 2, 8, 3, flowers[6].size),
    (8 + 5, 30, -38, 4, 12, 2, flowers[5].size),
])
frames = []
for f in range(N):
    t = texts[(f // 2) % 8]
    w, h = t.size[0] // 4, t.size[1] // 4
    frames.append(fall[f] + [(-w // 2, -h + 1, (f // 2) % 8)])
write(303, sprites, frames, bytes([50, 50]))

# ------------------------------------------------------------------ Phá Cỗ Trung Thu (300, 301): bảng tên + đèn lồng / bánh rơi
lantern_sheet = Image.open(f'{REPO}/data/effect/x4/ImgEffect_514.png').convert('RGBA')
cells = []
for r in range(2):
    for c in range(4):
        cell = lantern_sheet.crop((c * 96, r * 96, c * 96 + 96, r * 96 + 96))
        if cell.getbbox():
            cells.append(pad4(trim(cell).resize((max(4, trim(cell).width * 2 // 3), max(4, trim(cell).height * 2 // 3)), Image.LANCZOS)))
for eid in (300, 301):
    plate_full = Image.open(f'{REPO}/tools/effect/orig/ImgEffect_{eid}_x4.png').convert('RGBA')
    plate = pad4(trim(plate_full))
    glow = []
    for k in range(4):  # bảng tên nhấp nháy sáng nhẹ
        p = plate.copy()
        if k:
            light = Image.new('RGBA', p.size, (255, 230, 120, 0))
            a = p.split()[3].point(lambda v: v * (k * 18) // 255)
            light.putalpha(a)
            p.alpha_composite(light)
        glow.append(p)
    sprites = glow + cells
    n0 = len(glow)
    fall = falling(N, [(n0 + (i % len(cells)), dx, -46, 8, ph, 3, cells[i % len(cells)].size)
                       for i, (dx, ph) in enumerate([(-34, 0), (34, 6), (-12, 11), (16, 3), (-40, 9), (42, 13)])])
    w, h = plate.size[0] // 4, plate.size[1] // 4
    frames = [fall[f] + [(-w // 2, -h + 1, [0, 1, 2, 3, 2, 1, 0, 0][f % 8])] for f in range(N)]
    write(eid, sprites, frames, bytes([50, 50]))
