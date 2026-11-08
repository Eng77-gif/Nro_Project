package nro.models.network;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import nro.models.interfaces.IMessage;

public class Message implements IMessage {

    public byte command;
    private ByteArrayOutputStream os;
    private DataOutputStream dos;
    private ByteArrayInputStream is;
    private DataInputStream dis;

    public Message(int command) {
        this((byte) command);
    }

    public Message(byte command) {
        this.command = command;
        this.os = new ByteArrayOutputStream();
        this.tee = new Tee(this.os);
        this.dos = new DataOutputStream(this.tee);
    }

    /**
     * Bản dữ liệu cho client J2ME 2.4.x (Dragonboy247...): client này đọc HP / KI / sát thương... dạng long và chỉ số
     * món đồ dạng (short id, int param), client cũ đọc int và (byte id, short param). Bản này chỉ được tạo khi gói có
     * trường ghi riêng (writeNum, writeItemOption...); các lệnh ghi khác đi vào cả hai bản.
     */
    private ByteArrayOutputStream osLong;
    /** Bản riêng cho client J2ME thật (loại 1): như osLong + dòng mô tả item (client Unity / APK số lớn đã đọc mô tả qua lệnh 12). */
    private ByteArrayOutputStream osJ2me;
    private Tee tee;

    private static final class Tee extends java.io.OutputStream {

        private final java.io.OutputStream a;
        private java.io.OutputStream b;
        private java.io.OutputStream c;

        Tee(java.io.OutputStream a) {
            this.a = a;
        }

        @Override
        public void write(int v) throws IOException {
            a.write(v);
            if (b != null) {
                b.write(v);
            }
            if (c != null) {
                c.write(v);
            }
        }

        @Override
        public void write(byte[] buf, int off, int len) throws IOException {
            a.write(buf, off, len);
            if (b != null) {
                b.write(buf, off, len);
            }
            if (c != null) {
                c.write(buf, off, len);
            }
        }
    }

    private ByteArrayOutputStream longStream() throws IOException {
        if (this.osLong == null) {
            this.osLong = new ByteArrayOutputStream();
            this.osLong.write(this.os.toByteArray());
            this.tee.b = this.osLong;
        }
        return this.osLong;
    }

    private ByteArrayOutputStream j2meStream() throws IOException {
        ByteArrayOutputStream l = longStream();
        if (this.osJ2me == null) {
            this.osJ2me = new ByteArrayOutputStream();
            this.osJ2me.write(l.toByteArray());
            this.tee.c = this.osJ2me;
        }
        return this.osJ2me;
    }

    private static void put(ByteArrayOutputStream o, long v, int size) {
        for (int s = (size - 1) * 8; s >= 0; s -= 8) {
            o.write((byte) (v >>> s));
        }
    }

    /** Ghi riêng cho 2 loại client: client cũ nhận oldV (oldSize byte), client J2ME nhận newV (newSize byte). */
    public void writeDual(long oldV, int oldSize, long newV, int newSize) throws IOException {
        ByteArrayOutputStream l = longStream();
        put(this.os, oldV, oldSize);
        put(l, newV, newSize);
        if (this.osJ2me != null) {
            put(this.osJ2me, newV, newSize);
        }
    }

    /** Chỉ ghi cho client J2ME (client cũ không có trường này). */
    public void writeOnlyLong(long v, int size) throws IOException {
        put(longStream(), v, size);
        if (this.osJ2me != null) {
            put(this.osJ2me, v, size);
        }
    }

    /** Chỉ ghi chuỗi cho client J2ME. */
    public void writeUTFOnlyLong(String s) throws IOException {
        new DataOutputStream(longStream()).writeUTF(s);
        if (this.osJ2me != null) {
            new DataOutputStream(this.osJ2me).writeUTF(s);
        }
    }

    /** Số lớn: client cũ nhận int (4 byte), client J2ME nhận long (8 byte). */
    public void writeNum(long v) throws IOException {
        writeDual(Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v)), 4, v, 8);
    }

    /** Số dòng chỉ số của món đồ; client J2ME có thêm 1 dòng mô tả item (ghi bằng writeDescOption sau các dòng). */
    public void writeOptionCount(int n, int templateId) throws IOException {
        if (nro.models.data.ItemData.descOption(templateId) < 0) {
            writeDual(n, 1, n, 1);
            return;
        }
        ByteArrayOutputStream j = j2meStream();
        put(this.os, n, 1);
        put(this.osLong, n, 1);
        put(j, n + 1, 1);
    }

    /** Dòng mô tả item cho client J2ME (gọi sau vòng ghi các dòng chỉ số, đi cặp với writeOptionCount). */
    public void writeDescOption(int templateId) throws IOException {
        int d = nro.models.data.ItemData.descOption(templateId);
        if (d >= 0) {
            ByteArrayOutputStream j = j2meStream();
            put(j, d, 2);
            put(j, 0, 4);
        }
    }

    /** 1 dòng chỉ số món đồ: (byte id, short param) cho client cũ, (short id, int param) cho J2ME 2.4.x. */
    public void writeItemOption(int id, int param) throws IOException {
        int cid = id, cparam = param;
        if (param > 32767 && (id == 6 || id == 7)) { // HP+# / KI+# lớn: hiện bằng HP+#K / KI+#K cho client APK / PC
            cid = id == 6 ? 22 : 23;
            cparam = param / 1000;
        }
        writeDual(nro.models.item.OptionIdMap.toClient(cid), 1, id, 2); // client byte: id >= 255 dời sang ô trống
        // client APK / PC đọc param 2 byte có dấu: số > 32767 bị cắt thành số âm làm client lỗi khi vẽ hành trang
        // (Pet Xên Max HP +150.000, Giáp +35.000...). Gửi tối đa 32767, server và client J2ME vẫn giữ số thật.
        writeDual(Math.max(-32768, Math.min(32767, cparam)), 2, param, 4);
    }

    /** Dữ liệu gửi cho phiên: client J2ME lấy bản riêng (nếu gói có trường ghi riêng). */
    public byte[] getData(boolean longNumbers) {
        return getData(longNumbers, false);
    }

    public byte[] getData(boolean longNumbers, boolean j2me) {
        if (j2me && this.osJ2me != null) {
            return this.osJ2me.toByteArray();
        }
        return longNumbers && this.osLong != null ? this.osLong.toByteArray() : this.os.toByteArray();
    }

    public Message(byte command, byte[] data) {
        this.command = command;
        this.is = new ByteArrayInputStream(data);
        this.dis = new DataInputStream(this.is);
    }

    @Override
    public DataOutputStream writer() {
        return this.dos;
    }

    @Override
    public DataInputStream reader() {
        return this.dis;
    }

    @Override
    public byte[] getData() {
        return this.os.toByteArray();
    }

    @Override
    public void cleanup() {
        try {
            if (this.is != null) {
                this.is.close();
            }
            if (this.os != null) {
                this.os.close();
            }
            if (this.dis != null) {
                this.dis.close();
            }
            if (this.dos != null) {
                this.dos.close();
            }
            if (this.osLong != null) {
                this.osLong.close();
            }
        } catch (IOException iOException) {
        }
    }

    @Override
    public void dispose() {
        this.cleanup();
        this.dis = null;
        this.is = null;
        this.dos = null;
        this.os = null;
    }

    @Override
    public int read() throws IOException {
        return this.reader().read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return this.reader().read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return this.reader().read(b, off, len);
    }

    @Override
    public boolean readBoolean() throws IOException {
        return this.reader().readBoolean();
    }

    @Override
    public byte readByte() throws IOException {
        return this.reader().readByte();
    }

    @Override
    public short readShort() throws IOException {
        return this.reader().readShort();
    }

    @Override
    public int readInt() throws IOException {
        return this.reader().readInt();
    }

    @Override
    public long readLong() throws IOException {
        return this.reader().readLong();
    }

    @Override
    public float readFloat() throws IOException {
        return this.reader().readFloat();
    }

    @Override
    public double readDouble() throws IOException {
        return this.reader().readDouble();
    }

    @Override
    public char readChar() throws IOException {
        return this.reader().readChar();
    }

    @Override
    public String readUTF() throws IOException {
        return this.reader().readUTF();
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        this.reader().readFully(b);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        this.reader().readFully(b, off, len);
    }

    @Override
    public int readUnsignedByte() throws IOException {
        return this.reader().readUnsignedByte();
    }

    @Override
    public int readUnsignedShort() throws IOException {
        return this.reader().readUnsignedShort();
    }

    @Override
    public void write(byte[] b) throws IOException {
        this.writer().write(b);
    }

    @Override
    public void write(int b) throws IOException {
        this.writer().write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        this.writer().write(b, off, len);
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        this.writer().writeBoolean(v);
    }

    @Override
    public void writeByte(int v) throws IOException {
        this.writer().writeByte(v);
    }

    @Override
    public void writeBytes(String s) throws IOException {
        this.writer().writeBytes(s);
    }

    @Override
    public void writeChar(int v) throws IOException {
        this.writer().writeChar(v);
    }

    @Override
    public void writeChars(String s) throws IOException {
        this.writer().writeChars(s);
    }

    @Override
    public void writeDouble(double v) throws IOException {
        this.writer().writeDouble(v);
    }

    @Override
    public void writeFloat(float v) throws IOException {
        this.writer().writeFloat(v);
    }

    @Override
    public void writeInt(int v) throws IOException {
        this.writer().writeInt(v);
    }

    @Override
    public void writeLong(long v) throws IOException {
        this.writer().writeLong(v);
    }

    @Override
    public void writeShort(int v) throws IOException {
        this.writer().writeShort(v);
    }

    @Override
    public void writeUTF(String str) throws IOException {
        this.writer().writeUTF(str);
    }

    @Override
    public BufferedImage readImage() throws IOException {
        int size = this.readInt();
        byte[] dataImage = new byte[size];
        this.read(dataImage);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(dataImage));
        return image;
    }

    @Override
    public void writeImage(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write((RenderedImage) image, format, baos);
        byte[] dataImage = baos.toByteArray();
        this.writeInt(dataImage.length);
        this.write(dataImage);
    }
}
