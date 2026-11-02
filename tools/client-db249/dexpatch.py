"""Sửa chuỗi trong classes.dex giữ nguyên số byte (cập nhật độ dài UTF-16, SHA-1, checksum)."""
import struct, sys, hashlib, zlib

def uleb(d, p):
    r = s = 0
    while True:
        b = d[p]; p += 1; r |= (b & 0x7f) << s; s += 7
        if b < 0x80:
            return r, p

def enc_uleb(v, n):
    out = bytearray()
    for i in range(n):
        b = v & 0x7f; v >>= 7
        out.append(b | (0x80 if i < n - 1 else 0))
    assert v == 0
    return bytes(out)

def patch(dex, repl):
    d = bytearray(dex)
    n, off = struct.unpack_from('<II', d, 0x38)
    done = 0
    for i in range(n):
        so, = struct.unpack_from('<I', d, off + i * 4)
        ln, p = uleb(d, so)
        e = d.index(b'\0', p)
        s = bytes(d[p:e])
        if s in repl:
            new = repl[s].encode('utf-8')
            assert len(new) == len(s), (len(new), len(s))
            u16 = len(repl[s].encode('utf-16-le')) // 2
            d[so:p] = enc_uleb(u16, p - so)
            d[p:e] = new
            done += 1
    d[12:32] = hashlib.sha1(bytes(d[32:])).digest()
    d[8:12] = struct.pack('<I', zlib.adler32(bytes(d[12:])) & 0xffffffff)
    return bytes(d), done
