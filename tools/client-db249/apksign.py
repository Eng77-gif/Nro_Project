"""Đóng gói lại APK (thay classes.dex), căn lề 4 byte cho mục STORED, ký v1 (jarsigner bên ngoài) + v2."""
import sys, zipfile, zlib, struct, hashlib
from cryptography.hazmat.primitives.serialization import pkcs12, Encoding, PublicFormat
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding

def write_aligned(src, dst, replace=None, drop_sig=False):
    zin = zipfile.ZipFile(src)
    out = bytearray(); cd = bytearray(); n = 0
    for info in zin.infolist():
        name = info.filename
        if drop_sig and name.startswith('META-INF/') and name.split('/')[-1].upper().endswith(('.SF', '.RSA', '.DSA', '.EC', 'MANIFEST.MF')):
            continue
        data = replace[name] if replace and name in replace else zin.read(name)
        method = 0 if info.compress_type == 0 else 8
        if method == 8:
            c = zlib.compressobj(9, zlib.DEFLATED, -15); comp = c.compress(data) + c.flush()
        else:
            comp = data
        crc = zlib.crc32(data) & 0xffffffff
        fn = name.encode('utf-8')
        off = len(out)
        extra = b''
        if method == 0:
            align = 4096 if name.endswith('.so') else 4
            pad = (-(off + 30 + len(fn))) % align
            extra = b'\0' * pad
        dt, dd = 0x21, 0x5821  # 1980-01-01... fixed time
        out += struct.pack('<IHHHHHIIIHH', 0x04034b50, 20, 0x800, method, dd, dt, crc, len(comp), len(data), len(fn), len(extra)) + fn + extra + comp
        cd += struct.pack('<IHHHHHHIIIHHHHHII', 0x02014b50, 20, 20, 0x800, method, dd, dt, crc, len(comp), len(data), len(fn), 0, 0, 0, 0, 0, off) + fn
        n += 1
    return bytes(out), bytes(cd), n

def eocd(n, cdsize, cdoff):
    return struct.pack('<IHHHHIIH', 0x06054b50, 0, 0, n, n, cdsize, cdoff, 0)

def chunks_digest(parts):
    ds = []
    for p in parts:
        for i in range(0, len(p), 1 << 20):
            c = p[i:i + (1 << 20)]
            ds.append(hashlib.sha256(b'\xa5' + struct.pack('<I', len(c)) + c).digest())
    return hashlib.sha256(b'\x5a' + struct.pack('<I', len(ds)) + b''.join(ds)).digest()

def lp(b):
    return struct.pack('<I', len(b)) + b

def v2_sign(entries, cd, n, key, cert):
    eo = eocd(n, len(cd), len(entries))
    digest = chunks_digest([entries, cd, eo])  # EOCD có offset CD = offset của signing block (=len(entries))
    ALG = 0x0103  # RSASSA-PKCS1-v1_5 SHA-256
    der = cert.public_bytes(Encoding.DER)
    signed_data = lp(lp(struct.pack('<I', ALG) + lp(digest))) + lp(lp(der)) + lp(b'')
    sig = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    pub = cert.public_key().public_bytes(Encoding.DER, PublicFormat.SubjectPublicKeyInfo)
    signer = lp(signed_data) + lp(lp(struct.pack('<I', ALG) + lp(sig))) + lp(pub)
    v2 = lp(lp(signer))
    pair = struct.pack('<QI', 4 + len(v2), 0x7109871a) + v2
    size = len(pair) + 8 + 16
    block = struct.pack('<Q', size) + pair + struct.pack('<Q', size) + b'APK Sig Block 42'
    return entries + block + cd + eocd(n, len(cd), len(entries) + len(block))

if __name__ == '__main__':
    mode = sys.argv[1]
    if mode == 'unsigned':  # src dex dst
        e, cd, n = write_aligned(sys.argv[2], None, {'classes.dex': open(sys.argv[3], 'rb').read()}, drop_sig=True)
        open(sys.argv[4], 'wb').write(e + cd + eocd(n, len(cd), len(e)))
    else:  # v2 src p12 pass dst
        p = pkcs12.load_key_and_certificates(open(sys.argv[3], 'rb').read(), sys.argv[4].encode())
        e, cd, n = write_aligned(sys.argv[2], None)
        open(sys.argv[5], 'wb').write(v2_sign(e, cd, n, p[0], p[1]))
