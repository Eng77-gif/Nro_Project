import struct,zlib,io,sys,glob,os
from PIL import Image
def clean(path):
    d=open(path,'rb').read(); out=bytearray(d[:8]); p=8
    while p<len(d):
        n,=struct.unpack('>I',d[p:p+4]); t=d[p+4:p+8]; body=d[p+8:p+8+n]; crc=d[p+8+n:p+12+n]
        ok=len(crc)==4 and zlib.crc32(t+body)&0xffffffff==struct.unpack('>I',crc)[0]
        if ok or t[:1].isupper(): out+=d[p:p+12+n]
        p+=12+n
        if t==b'IEND':break
    return bytes(out)
def nframes(path):
    im=Image.open(io.BytesIO(clean(path))).convert('RGBA'); a=im.split()[3]
    w,h=im.size; px=a.load()
    rows=[any(px[x,y]>8 for x in range(0,w,2)) for y in range(h)]
    # thử số frame n sao cho mỗi ô chiều cao h/n có nội dung, và đường chia rơi vào vùng trống nhiều nhất
    best=None
    for n in range(1,13):
        if h%n: continue
        fh=h//n
        if fh< w*0.4: continue
        empties=sum(1 for k in range(1,n) if not rows[k*fh] or not rows[k*fh-1])
        filled=all(any(rows[k*fh:(k+1)*fh]) for k in range(n))
        score=(filled, empties==n-1, n)
        if filled and empties==n-1: best=n
    return best,im.size
if __name__=='__main__':
    for f in sys.argv[1:]: print(os.path.basename(f), *nframes(f))
