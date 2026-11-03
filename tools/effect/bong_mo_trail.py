"""Hiệu ứng "bóng mờ" (bóng bước chân): mỗi vật rơi lại trên mặt đất, nằm yên rồi nhỏ dần và biến mất.

python3 bong_mo_trail.py <thư mục repo>

Server gửi lệnh 113 (hiệu ứng cố định trên map) tại các điểm nhân vật vừa đi qua. Mỗi bóng mờ có 4 bản
(mã FIRST + 4k + v): bản v bỏ qua v * SKIP nhịp đầu, dùng cho điểm đi qua lâu hơn để vệt mờ dần từ cũ đến mới.
"""
import struct, sys, shutil
from PIL import Image

REPO = sys.argv[1]
BASES = [508, 509, 513, 514, 518, 519]  # tuyết rơi, NRO, sao may mắn, trung thu, hoa hồng đỏ, hoa hồng băng
FIRST = 1221
HOLD, MID, SMALL, SKIP = 22, 12, 10, 8  # số nhịp: to, vừa, nhỏ; mỗi bản sau bỏ 8 nhịp


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


def write(eid, imgs, frames, trailer):
    b = bytearray([len(imgs)])
    for im in imgs:
        b += bytes(im)
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


for k, base in enumerate(BASES):
    imgs, trailer = parse(base)
    atlas = Image.open(f'{REPO}/data/effect/x4/ImgEffect_{base}.png').convert('RGBA')

    def ink(i):
        _, x0, y0, w, h = imgs[i]
        a = atlas.crop((x0 * 4, y0 * 4, (x0 + w) * 4, (y0 + h) * 4)).split()[3]
        return sum(1 for v in a.getdata() if v > 128)

    def solid(i):  # ảnh 1 vật, không phải chùm hạt
        return ink(i) / max(1, imgs[i][3] * imgs[i][4] * 16) > 0.25

    cand = sorted([i for i in range(len(imgs)) if solid(i) and imgs[i][3] <= 40 and imgs[i][4] <= 40], key=ink) or sorted(range(len(imgs)), key=ink)
    big, mid, small = cand[-1], cand[len(cand) // 2], cand[0]
    life = [big] * HOLD + [mid] * MID + [small] * SMALL
    for v in range(4):
        frames = []
        for f, im in enumerate(life[v * SKIP:]):
            w, h = imgs[im][3], imgs[im][4]
            bob = 1 if f < 4 and f % 2 else 0  # chạm đất nảy nhẹ
            frames.append([(-w // 2, -h - bob, imgs[im][0])])
        frames.append([])  # khung cuối trống
        eid = FIRST + 4 * k + v
        write(eid, imgs, frames, trailer)
        for z in (1, 2, 3, 4):
            shutil.copy(f'{REPO}/data/effect/x{z}/ImgEffect_{base}.png', f'{REPO}/data/effect/x{z}/ImgEffect_{eid}.png')
    print(base, '->', FIRST + 4 * k, '..', FIRST + 4 * k + 3)
