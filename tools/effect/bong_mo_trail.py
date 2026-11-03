"""Hiệu ứng "bóng mờ": vật rơi lại ngang lưng nhân vật trên đường chạy, nằm yên rồi mờ dần, nhỏ dần đều tay.

python3 bong_mo_trail.py <thư mục repo>

Atlas mới cho mỗi bóng mờ: 1 vật lấy từ hiệu ứng gốc, LEVELS bản (bản sau nhỏ hơn và trong hơn). Server gửi lệnh 113
tại các điểm nhân vật đi qua; mỗi bóng mờ có VARIANTS bản (mã FIRST + VARIANTS*k + v), bản v bỏ v*SKIP nhịp đầu để
điểm đi qua trước mờ trước, vệt liền mạch không giật.
"""
import struct, sys
from PIL import Image

REPO = sys.argv[1]
BASES = [508, 509, 513, 514, 518, 519]  # tuyết rơi, NRO, sao may mắn, trung thu, hoa hồng đỏ, hoa hồng băng
FIRST, VARIANTS, SKIP = 1221, 8, 4
LEVELS, HOLD, STEP = 8, 16, 3       # 8 mức mờ, giữ nguyên 16 nhịp, mỗi mức 3 nhịp
BACK_Y = -26                        # tâm vật ngang lưng (px x1 so với chân)


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
    obj = crop(cand[-1])
    obj = obj.crop(obj.getbbox())
    sprites = []
    for lv in range(LEVELS):
        sc, al = 1 - 0.07 * lv, 1 - 0.115 * lv
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
            rise = (f + v * SKIP) // 8  # bay lên rất nhẹ khi mờ dần
            frames.append([(-w // 2, BACK_Y - h // 2 - rise, lv)])
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
