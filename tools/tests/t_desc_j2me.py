# Kiểm tra trên server đang chạy: python3 tools/tests/t_desc_j2me.py <thư mục chứa nroc.py> (đăng nhập tài khoản 1/1 bằng client J2ME và Unity, so gói hành trang)
import sys, time, struct
sys.path.insert(0, sys.argv[1])
from nroc import C, utf
def bag(tc, plat):
    c = C(); c.send(-27); time.sleep(0.5)
    c.send(-29, bytes([2, tc, 2, 0]) + struct.pack('>ii', 800, 480) + bytes([0, 1]) + utf(plat))
    time.sleep(0.3); c.send(-29, bytes([0]) + utf('1') + utf('1') + utf('2.4.9') + bytes([0])); time.sleep(5)
    c.send(-28, bytes([13])); time.sleep(2)
    got = {cc: len(d) for t, cc, d in c.msgs if cc in (-36, -37)}
    opt = [d for t, cc, d in c.msgs if cc == -28 and d[:1] == b'\x08' and d[2:3] == b'\x00']
    c.so.close()
    return got, (len(opt[0]) if opt else None)
a = bag(1, 'J2ME|2.4.0'); time.sleep(25)
c = bag(4, 'Android|2.4.7|long')
print('j2me', a, '\nunity-long', c)
d = a[0].get(-36, 0) - c[0].get(-36, 0)
assert c[0].get(-36, 0) > 0 and d > 0 and d % 6 == 0, 'J2ME phải có thêm dòng mô tả (6 byte / món), chênh %d' % d
print('PASS: J2ME thêm %d dòng mô tả' % (d // 6))
