package nro.models.network.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import nro.models.network.Message;
import nro.models.network.Session;

/**
 * Tách gói tin client gửi lên: [lệnh 1 byte][độ dài 2 byte][dữ liệu], sau khi gửi khóa thì mọi byte XOR với khóa phiên
 * (bộ đếm riêng chiều đọc). Chỉ lấy gói khi đã đủ byte (không chặn luồng như kiểu cũ). Kiểm tra kích thước và tốc độ gói.
 */
public class NroDecoder extends ByteToMessageDecoder {

    private final Session session;
    private int curR;
    private long secStart;
    private int secCount, overSeconds;

    public NroDecoder(Session session) {
        this.session = session;
    }

    private int key(byte[] k, int idx, int b) {
        return (k[idx % k.length] & 0xFF ^ b & 0xFF) & 0xFF;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (in.readableBytes() >= 3) {
            int start = in.readerIndex();
            boolean enc = session.sentKey();
            byte[] k = session.getKey();
            int r = curR;
            int cmd = in.getByte(start) & 0xFF;
            int s1 = in.getByte(start + 1) & 0xFF, s2 = in.getByte(start + 2) & 0xFF;
            if (enc) {
                cmd = key(k, r++, cmd);
                s1 = key(k, r++, s1);
                s2 = key(k, r++, s2);
            }
            int size = s1 << 8 | s2;
            if (size > AntiDdos.MAX_FRAME) {
                AntiDdos.ban(session.getIP(), "gói tin " + size + " byte");
                in.skipBytes(in.readableBytes());
                ctx.close();
                return;
            }
            if (in.readableBytes() < 3 + size) {
                return; // chờ thêm dữ liệu
            }
            in.skipBytes(3);
            byte[] data = new byte[size];
            in.readBytes(data);
            if (enc) {
                for (int i = 0; i < size; i++) {
                    data[i] = (byte) key(k, r++, data[i]);
                }
            }
            curR = enc ? r % k.length : 0;
            if (!enc && (byte) cmd != -27) {
                AntiDdos.strike(session.getIP(), "không bắt tay đúng giao thức");
                ctx.close();
                return;
            }
            if (!rateOk()) {
                AntiDdos.ban(session.getIP(), "gửi quá " + AntiDdos.MAX_MSG_PER_SEC + " gói / giây");
                ctx.close();
                return;
            }
            out.add(new Message((byte) cmd, data));
        }
    }

    /** Quá ngưỡng 3 giây liên tiếp, hoặc gấp 4 lần ngưỡng trong 1 giây thì ngắt. */
    private boolean rateOk() {
        long now = System.currentTimeMillis();
        if (now - secStart >= 1000) {
            overSeconds = secCount > AntiDdos.MAX_MSG_PER_SEC ? overSeconds + 1 : 0;
            secStart = now;
            secCount = 0;
        }
        secCount++;
        return overSeconds < 3 && secCount <= AntiDdos.MAX_MSG_PER_SEC * 4;
    }
}
