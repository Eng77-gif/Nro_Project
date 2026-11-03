"""Hiệu ứng "bóng mờ": vật rơi lại dưới chân nhân vật trên đường chạy, nằm yên rồi mờ dần, nhỏ dần đều tay.

python3 bong_mo_trail.py <thư mục repo>

Atlas mới cho mỗi bóng mờ: 1 vật lấy từ hiệu ứng gốc, LEVELS bản (bản sau nhỏ hơn và trong hơn). Server gửi lệnh 113
tại các điểm nhân vật đi qua; mỗi bóng mờ có VARIANTS bản (mã FIRST + VARIANTS*k + v), bản v bỏ v*SKIP nhịp đầu để
điểm đi qua trước mờ trước, vệt liền mạch không giật.
"""
import struct, sys
from PIL import Image

REPO = sys.argv[1]
BASES = [508, 509, 513, 514, 518, 519]  # tuyết rơi, NRO, sao may mắn, trung thu, hoa hồng đỏ, hoa hồng băng
FIRST, VARIANTS, SKIP = 1221, 12, 3
LEVELS, HOLD, STEP = 10, 14, 3      # 10 mức mờ, giữ nguyên 14 nhịp, mỗi mức 3 nhịp
FOOT_Y = 0                          # đáy vật chạm đất tại chân (px x1)


def parse(eid):
    b = open(f'{REPO}/data/effdata/DataEffect_{eid}', 'rb').read()
    n = b[0]
    imgs = [tuple(b[1 + 5 * i:6 + 5 * i]) for i in range(n)]
    p = 1 + 5 * n
    nf = struct.unpack('>h', b[p:p + 2])[0]; p += 2
    for _ in range(nf):
        k = b[p]; p += 1 + 5 * k
    na = struct.unpack('>h', b[p:p + 2])[0]; p += 2 + 2 * na
    return imgs, b[p:]


def pad4(im):
    w, h = im.size
    o = Image.new('RGBA', ((w + 3) // 4 * 4, (h + 3) // 4 * 4), (0, 0, 0, 0))
    o.alpha_composite(im, ((o.width - w) // 2, (o.height - h) // 2))
    return o


def largest_part(im):
    """Giữ mảng hình lớn nhất (bỏ chấm lẻ cùng ô ảnh, vd bông tuyết kèm 1 chấm làm vật lơ lửng)."""
    w, h = im.size
    a = im.split()[3].load()
    seen, best = set(), []
    for y in range(h):
        for x in range(w):
            if a[x, y] > 40 and (x, y) not in seen:
                comp, st = [], [(x, y)]
                seen.add((x, y))
                while st:
                    cx, cy = st.pop()
                    comp.append((cx, cy))
                    for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                        if 0 <= nx < w and 0 <= ny < h and (nx, ny) not in seen and a[nx, ny] > 40:
                            seen.add((nx, ny)); st.append((nx, ny))
                if len(comp) > len(best):
                    best = comp
    xs, ys = [p[0] for p in best], [p[1] for p in best]
    box = (max(0, min(xs) - 2), max(0, min(ys) - 2), min(w, max(xs) + 3), min(h, max(ys) + 3))
    if len(best) < 0.6 * sum(1 for v in im.split()[3].getdata() if v > 40):
        return im.crop(im.getbbox())  # hình nhiều mảnh rời (hoa, chùm sao): giữ nguyên
    return im.crop(box)


for k, base in enumerate(BASES):
    imgs, trailer = parse(base)
    src = Image.open(f'{REPO}/data/effect/x4/ImgEffect_{base}.png').convert('RGBA')

    def crop(i):
        _, x0, y0, w, h = imgs[i]
        return src.crop((x0 * 4, y0 * 4, (x0 + w) * 4, (y0 + h) * 4))

    def ink(i):
        return sum(1 for v in crop(i).split()[3].getdata() if v > 128)

    def solid(i):
        return ink(i) / max(1, imgs[i][3] * imgs[i][4] * 16) > 0.25

    cand = sorted([i for i in range(len(imgs)) if solid(i) and imgs[i][3] <= 40 and imgs[i][4] <= 40], key=ink) or sorted(range(len(imgs)), key=ink)
    obj = largest_part(crop(cand[-1]))
    sprites = []
    for lv in range(LEVELS):
        sc, al = 1 - 0.055 * lv, 1 - 0.095 * lv
        im = obj.resize((max(4, int(obj.width * sc)), max(4, int(obj.height * sc))), Image.LANCZOS)
        a = im.split()[3].point(lambda v, al=al: int(v * al))
        im.putalpha(a)
        sprites.append(pad4(im))
    x, pos = 0, []
    for s in sprites:
        pos.append(x); x += s.width
    atlas = Image.new('RGBA', (x, max(s.height for s in sprites)), (0, 0, 0, 0))
    for p, s in zip(pos, sprites):
        atlas.alpha_composite(s, (p, 0))
    assert atlas.width // 4 <= 255 and atlas.height // 4 <= 255
    info = [(i, pos[i] // 4, 0, s.width // 4, s.height // 4) for i, s in enumerate(sprites)]
    life = [0] * HOLD + [lv for lv in range(1, LEVELS) for _ in range(STEP)]
    for v in range(VARIANTS):
        frames = []
        for f, lv in enumerate(life[v * SKIP:]):
            w, h = info[lv][3], info[lv][4]
            frames.append([(-w // 2, FOOT_Y - h + 1, lv)])  # nằm trên mặt đất, nhỏ dần về phía đáy
        frames.append([])
        eid = FIRST + VARIANTS * k + v
        b = bytearray([len(info)])
        for t in info:
            b += bytes(t)
        b += struct.pack('>h', len(frames))
        for parts in frames:
            b += bytes([len(parts)])
            for dx, dy, img in parts:
                b += struct.pack('>hhb', dx, dy, img)
        b += struct.pack('>h', len(frames)) + b''.join(struct.pack('>h', i) for i in range(len(frames))) + trailer
        open(f'{REPO}/data/effdata/DataEffect_{eid}', 'wb').write(bytes(b))
        for z in (1, 2, 3, 4):
            im = atlas if z == 4 else atlas.resize((atlas.width * z // 4, atlas.height * z // 4), Image.LANCZOS)
            im.save(f'{REPO}/data/effect/x{z}/ImgEffect_{eid}.png')
    print(base, '->', FIRST + VARIANTS * k, '..', FIRST + VARIANTS * k + VARIANTS - 1)
