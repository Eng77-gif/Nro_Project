"""Tạo hiệu ứng vệt bóng mờ (theo hướng chạy) từ atlas hiệu ứng bóng mờ có sẵn."""
import struct, sys, math, shutil
from PIL import Image
REPO = sys.argv[1]  # python3 bong_mo_trail.py <thư mục repo>
BASES = [508, 509, 513, 514, 518, 519]
FIRST = 1201

def parse(eid):
    b = open(f'{REPO}/data/effdata/DataEffect_{eid}', 'rb').read()
    n = b[0]; imgs = []
    for i in range(n):
        imgs.append(tuple(b[1 + 5 * i:6 + 5 * i]))
    p = 1 + 5 * n
    nf = struct.unpack('>h', b[p:p + 2])[0]; p += 2
    for f in range(nf):
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

N, P = 24, 9
for k, base in enumerate(BASES):
    imgs, trailer = parse(base)
    # ảnh nhỏ dần theo tuổi hạt: chọn tối đa 3 cỡ trong các ảnh vừa phải
    atlas = Image.open(f'{REPO}/data/effect/x4/ImgEffect_{base}.png').convert('RGBA')
    def ink(i):
        _, x0, y0, w, h = imgs[i]
        a = atlas.crop((x0 * 4, y0 * 4, (x0 + w) * 4, (y0 + h) * 4)).split()[3]
        return sum(1 for v in a.getdata() if v > 128)
    def solid(i):  # ảnh 1 vật (không phải chùm hạt): phần đặc chiếm nhiều diện tích khung
        return ink(i) / max(1, imgs[i][3] * imgs[i][4] * 16) > 0.25
    cand = sorted([i for i in range(len(imgs)) if solid(i) and imgs[i][3] <= 40 and imgs[i][4] <= 40], key=ink) or sorted(range(len(imgs)), key=ink)
    big, mid, small = cand[-1], cand[len(cand) // 2], cand[0]
    for d, eid in ((1, FIRST + 2 * k), (-1, FIRST + 2 * k + 1)):
        frames = []
        for f in range(N):
            parts = []
            for i in range(P):
                a = (f - i * N // P) % N
                if a >= N - 2:
                    continue
                im = big if a < N // 3 else (mid if a < 2 * N // 3 else small)
                w, h = imgs[im][3], imgs[im][4]
                cx = -10 - a * 2.6 + 3 * math.sin((a + i) * 0.7)
                cy = -(12 + (i * 11) % 34) + a * 0.5
                if d < 0:
                    cx = -cx
                parts.append((int(cx - w / 2), int(cy - h / 2), imgs[im][0]))
            frames.append(parts)
        write(eid, imgs, frames, trailer)
        for z in (1, 2, 3, 4):
            shutil.copy(f'{REPO}/data/effect/x{z}/ImgEffect_{base}.png', f'{REPO}/data/effect/x{z}/ImgEffect_{eid}.png')
        print(base, '->', eid, 'imgs', big, mid, small)
