"""Gộp 2 lớp aura (_0 sau lưng, _1 trước mặt) thành 1 lớp để nút "Hào quang đặc biệt" của client bật là hiện, tắt là ẩn.

Client 2.4.x: ô "[x] Hào quang đặc biệt" chỉ vẽ lớp _0 của nhân vật mình, ô "[ ]" chỉ vẽ lớp _1 -> aura chỉ có lớp _1
(phần lớn aura thẻ radar) hiện khi TẮT, ẩn khi BẬT. Ảnh gộp lưu thành aura_<id+1000>_0 (đủ cả 2 lớp) và aura_<id+1000>_1
(ảnh trống 1x1); server gửi id + 1000 (Player.getAura) nên client tải ảnh mới, không vướng ảnh cũ đã lưu trong máy.

Chạy: python3 -I tools/aura_merge.py data/img_by_name  (in ra "tên n_frame" cho bảng img_by_name)
"""
import os
import sys

from PIL import Image

OFFSET = 1000
# số khung đoán sai (khung chạm mép nhau), đã xem tận mắt
OVERRIDE = {'aura_10_0': 4, 'aura_15_0': 4}


def frames_of(img, n):
    fh = img.height // n
    return [img.crop((0, i * fh, img.width, (i + 1) * fh)) for i in range(n)]


def guess_n(img):
    """Đoán số khung của dải ảnh dọc.

    Ưu tiên: số khung lớn nhất mà ranh giới các khung là hàng trong suốt và các khung liền nhau giống nhau;
    không có thì lấy số khung lớn nhất có độ giống gần tốt nhất (khung chia sai bị lệch nên khác hẳn nhau).
    """
    a = img.convert('RGBA').split()[3]
    rows = [sum(a.crop((0, y, a.width, y + 1)).tobytes()) for y in range(img.height)]
    avg = sum(rows) / len(rows) or 1
    cand = {}
    for n in range(2, 33):
        fh = img.height // n
        if fh < img.width * 0.4 or fh > img.width * 3:
            continue
        fr = [list(a.crop((0, i * fh, a.width, (i + 1) * fh)).tobytes()) for i in range(n)]
        tot = sum(sum(f) for f in fr) / n
        if tot <= 0:
            continue
        diff = sum(sum(abs(x - y) for x, y in zip(fr[i], fr[i + 1])) for i in range(n - 1)) / (n - 1) / tot
        edge = sum(rows[min(img.height - 1, k * fh + o)] for k in range(1, n) for o in (-1, 0, 1)) / (3 * (n - 1)) / avg
        cand[n] = (diff, edge)
    if not cand:
        return 4 if img.height % 4 == 0 else 1
    clean = [n for n, (d, e) in cand.items() if e <= 0.05 and d <= 1.0]
    if clean:
        return max(clean)
    best = min(d for d, e in cand.values())
    return max(n for n, (d, e) in cand.items() if d <= best * 1.2)


def load(path):
    if not os.path.exists(path):
        return None
    img = Image.open(path).convert('RGBA')
    return None if img.width < 4 or img.height < 4 else img


def merge(root, known):
    out = {}
    ids = set()
    for f in os.listdir(os.path.join(root, 'x2')):
        p = f[:-4].split('_')
        if f.startswith('aura_') and f.endswith('.png') and len(p) == 3 and p[1].isdigit() and int(p[1]) < OFFSET:
            ids.add(int(p[1]))
    for aid in sorted(ids):
        nf = None
        for z in (1, 2, 3, 4):
            d = os.path.join(root, 'x%d' % z)
            b, f = load(os.path.join(d, 'aura_%d_0.png' % aid)), load(os.path.join(d, 'aura_%d_1.png' % aid))
            if b is None and f is None:
                continue
            if nf is None:  # số khung lấy theo ảnh x2 (giống mọi cỡ)
                b2, f2 = load(os.path.join(root, 'x2', 'aura_%d_0.png' % aid)), load(os.path.join(root, 'x2', 'aura_%d_1.png' % aid))
                nf = (known.get('aura_%d_0' % aid) or (guess_n(b2) if b2 is not None else 0),
                      known.get('aura_%d_1' % aid) or (guess_n(f2) if f2 is not None else 0))
            nb, nfr = nf
            fb = frames_of(b, nb) if b is not None and nb else []
            ff = frames_of(f, nfr) if f is not None and nfr else []
            n = max(len(fb), len(ff))
            w = max([x.width for x in fb + ff])
            h = max([x.height for x in fb + ff])
            sheet = Image.new('RGBA', (w, h * n), (0, 0, 0, 0))
            for i in range(n):
                for layer in (fb, ff):  # lớp sau trước, lớp trước đè lên; canh giữa, đáy khung như client (BOTTOM | HCENTER)
                    if layer:
                        fr = layer[i % len(layer)]
                        sheet.alpha_composite(fr, ((w - fr.width) // 2, i * h + h - fr.height))
            sheet.save(os.path.join(d, 'aura_%d_0.png' % (aid + OFFSET)), optimize=True)
            Image.new('RGBA', (1, 1), (0, 0, 0, 0)).save(os.path.join(d, 'aura_%d_1.png' % (aid + OFFSET)))
            out[aid] = n
    return out


if __name__ == '__main__':
    root = sys.argv[1]
    known = dict(OVERRIDE)
    if len(sys.argv) > 2:  # file "tên<TAB>n_frame" xuất từ bảng img_by_name
        for line in open(sys.argv[2], encoding='utf-8'):
            p = line.split()
            if len(p) == 2:
                known[p[0]] = int(p[1])
    for aid, n in merge(root, known).items():
        print('aura_%d_0 %d' % (aid + OFFSET, n))
        print('aura_%d_1 1' % (aid + OFFSET))
