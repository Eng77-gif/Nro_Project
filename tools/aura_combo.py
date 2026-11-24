"""Ghép hào quang sức mạnh (sau lưng) + hào quang thẻ radar / thưởng top (đè lên) thành 1 aura, vì mỗi nhân vật chỉ gửi được 1 aura
và client chỉ tự vẽ hào quang cấp khi không có aura nào.

  aura_<2000+id>_0 : hào quang cấp 60 - 90 tỷ của client (icon 7598) + aura id
  aura_<3000+id>_0 : hào quang 90 tỷ (aura 5, khói trắng) + aura id
  aura_<...>_1     : ảnh trống 1x1 (như aura gộp của aura_merge.py)

Chạy: python3 -I tools/aura_combo.py data  (in ra "tên n_frame" để thêm vào data/img_by_name/aura_merged.txt)
"""
import math
import os
import sys

from PIL import Image

IDS = [0, 1, 2, 3, 4, 120, 121, 122, 123, 124, 125]  # aura thẻ radar (RadarService.auraFor) + thưởng top Quy Lão (TOP_AURA)
RAW_FRAMES = {123: 8, 124: 8, 125: 8}  # aura chưa gộp (bảng img_by_name)


def frames(img, n):
    fh = img.height // n
    return [img.crop((0, i * fh, img.width, (i + 1) * fh)) for i in range(n)]


def load(p):
    return Image.open(p).convert('RGBA') if os.path.exists(p) else None


def card_layers(root, z, aid, merged_n):
    """Các khung của aura thẻ (lớp sau rồi lớp trước), mỗi phần tử là 1 danh sách khung."""
    d = os.path.join(root, 'img_by_name', 'x%d' % z)
    m = load(os.path.join(d, 'aura_%d_0.png' % (aid + 1000)))
    if m is not None and aid + 1000 in merged_n:
        return [frames(m, merged_n[aid + 1000])]
    out = []
    for layer in (0, 1):
        im = load(os.path.join(d, 'aura_%d_%d.png' % (aid, layer)))
        if im is not None and im.width >= 4:
            out.append(frames(im, RAW_FRAMES.get(aid, 4)))
    return out


def sheet(layers):
    n = 1
    for L in layers:
        n = n * len(L) // math.gcd(n, len(L))
    w = max(f.width for L in layers for f in L)
    h = max(f.height for L in layers for f in L)
    out = Image.new('RGBA', (w, h * n), (0, 0, 0, 0))
    for i in range(n):
        for L in layers:  # canh giữa, đáy khung như client (BOTTOM | HCENTER)
            fr = L[i % len(L)]
            out.alpha_composite(fr, ((w - fr.width) // 2, i * h + h - fr.height))
    return out, n


def main(root):
    merged_n = {}
    for line in open(os.path.join(root, 'img_by_name', 'aura_merged.txt'), encoding='utf-8'):
        p = line.split()
        if len(p) == 2 and p[0].endswith('_0'):
            merged_n[int(p[0].split('_')[1])] = int(p[1])
    res = {}
    for z in (1, 2, 3, 4):
        d = os.path.join(root, 'img_by_name', 'x%d' % z)
        powers = {2000: frames(load(os.path.join(root, 'icon', 'x%d' % z, '7598.png')), 4),
                  3000: frames(load(os.path.join(d, 'aura_1005_0.png')), merged_n[1005])}
        for aid in IDS:
            card = card_layers(root, z, aid, merged_n)
            if not card:
                continue
            for off, pw in powers.items():
                img, n = sheet([pw] + card)
                img.save(os.path.join(d, 'aura_%d_0.png' % (off + aid)), optimize=True)
                Image.new('RGBA', (1, 1), (0, 0, 0, 0)).save(os.path.join(d, 'aura_%d_1.png' % (off + aid)))
                res[off + aid] = n
    for aid in sorted(res):
        print('aura_%d_0 %d' % (aid, res[aid]))
        print('aura_%d_1 1' % aid)


if __name__ == '__main__':
    main(sys.argv[1])
