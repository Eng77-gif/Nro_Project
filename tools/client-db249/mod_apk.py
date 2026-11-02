"""Đổi IP client Android DragonBoy249 (2.4.8 chính thức) và ký lại APK.

python3 mod_apk.py <DragonBoy249.apk> <ip> <port> <out.apk> <keystore.p12> <mật khẩu> [tên server]

- Cả 2 danh sách máy chủ trong classes.dex được thay bằng 1 server "<tên>:<ip>:<port>" (giữ đúng số byte,
  phần thừa đệm số 0 trước cổng: "0000014445" vẫn đọc là 14445).
- Đuôi phiên bản "|2.4.8" đổi thành "|2.4.9": server nhận 2.4.9 là client số lớn (long), xem Service.setClientType.
- Ký v1 (jarsigner) + v2 (apksign.py). Lần sau phải ký đúng keystore này mới cài đè được.
"""
import sys, os, re, zipfile, struct, subprocess, tempfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dexpatch, apksign

apk, ip, port, out, ks, pw = sys.argv[1:7]
name = sys.argv[7] if len(sys.argv) > 7 else 'NRO'
dex = zipfile.ZipFile(apk).read('classes.dex')

def strings(d):
    n, off = struct.unpack_from('<II', d, 0x38)
    for i in range(n):
        so, = struct.unpack_from('<I', d, off + 4 * i); p = so
        while d[p] & 0x80: p += 1
        p += 1; e = d.index(b'\0', p); yield d[p:e]

repl = {}
for s in strings(dex):
    txt = s.decode('utf-8', 'replace')
    if re.search(r'^[^,:]*:[^,:]+:\d+:\d:\d:\d,', txt) or s.startswith(b'|2.4.'):
        if s.startswith(b'|2.4.'):
            if len(s) == 6: repl[s] = '|2.4.9'
            continue
        head, tail = f'{name}:{ip}:', f'{port}:0:0:0,0,0'  # đuôi ',0,0' giữ như bản gốc
        pad = len(s) - len(head.encode()) - len(tail.encode())
        assert pad >= 0, 'chuỗi danh sách server quá ngắn'
        repl[s] = head + '0' * pad + tail
new, n = dexpatch.patch(dex, repl)
print('đã sửa', n, 'chuỗi')
with tempfile.TemporaryDirectory() as t:
    open(f'{t}/classes.dex', 'wb').write(new)
    e, cd, cnt = apksign.write_aligned(apk, None, {'classes.dex': new}, drop_sig=True)
    open(f'{t}/u.apk', 'wb').write(e + cd + apksign.eocd(cnt, len(cd), len(e)))
    alias = subprocess.run(['keytool', '-list', '-keystore', ks, '-storepass', pw], capture_output=True, text=True).stdout
    alias = re.search(r'^(\S+), ', alias, re.M).group(1)
    subprocess.run(['jarsigner', '-keystore', ks, '-storepass', pw, '-sigalg', 'SHA256withRSA', '-digestalg', 'SHA-256',
                    '-signedjar', f'{t}/v1.apk', f'{t}/u.apk', alias], check=True, capture_output=True)
    sys.argv = ['', 'v2', f'{t}/v1.apk', ks, pw, out]
    from cryptography.hazmat.primitives.serialization import pkcs12
    p = pkcs12.load_key_and_certificates(open(ks, 'rb').read(), pw.encode())
    e, cd, cnt = apksign.write_aligned(f'{t}/v1.apk', None)
    open(out, 'wb').write(apksign.v2_sign(e, cd, cnt, p[0], p[1]))
print('xong:', out)
