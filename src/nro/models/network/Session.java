package nro.models.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import nro.models.interfaces.IKeySessionHandler;
import nro.models.interfaces.IMessageHandler;
import nro.models.interfaces.IMessageSendCollect;
import nro.models.interfaces.ISession;
import nro.models.utils.StringUtil;

public class Session implements ISession {

    private static ISession instance;
    private static int ID_INIT;

    private final int id = ID_INIT++;
    private final Socket socket;
    /** Kết nối qua Netty (null = kiểu cũ: 2 luồng đọc / ghi mỗi người chơi) */
    private final io.netty.channel.Channel channel;
    private IMessageSendCollect nettyCollect;
    private IMessageHandler nettyHandler;
    private final String ip;
    private byte[] KEYS = "NRO".getBytes();
    private boolean sentKey;
    private boolean connected;

    private Sender sender;
    private Collector collector;
    private Thread tSender;
    private Thread tCollector;

    private IKeySessionHandler keyHandler;

    public static ISession gI() throws Exception {
        if (instance == null) {
            throw new Exception("Instance has not been initialized!");
        }
        return instance;
    }

    public Session(io.netty.channel.Channel channel) {
        this.socket = null;
        this.channel = channel;
        this.connected = true;
        this.ip = ((InetSocketAddress) channel.remoteAddress()).getAddress().getHostAddress();
    }

    public io.netty.channel.Channel getChannel() {
        return this.channel;
    }

    public IMessageHandler getMessageHandler() {
        return this.nettyHandler;
    }

    public Session(Socket socket) {
        this.channel = null;
        this.socket = socket;
        this.connected = true;

        try {
            this.socket.setSendBufferSize(0x100000);
            this.socket.setReceiveBufferSize(0x100000);
        } catch (SocketException ignored) {
        }

        this.ip = ((InetSocketAddress) socket.getRemoteSocketAddress()).getAddress().getHostAddress();

        this.sender = new Sender(this, socket);
        this.collector = new Collector(this, socket);

        this.tSender = new Thread(this.sender, "Sender - IP : " + ip);
        this.tCollector = new Thread(this.collector, "Collector - IP : " + ip);
    }

    @Override
    public ISession start() {
        if (this.channel == null) {
            this.tSender.start();
            this.tCollector.start();
        }
        return this;
    }

    @Override
    public ISession startSend() {
        if (this.channel == null && !this.tSender.isAlive() && this.tSender.getState() == Thread.State.NEW) {
            this.tSender.start();
        }
        return this;
    }

    @Override
    public ISession startCollect() {
        if (this.channel == null) {
            this.tCollector.start();
        }
        return this;
    }

    @Override
    public ISession setSendCollect(IMessageSendCollect collect) {
        if (this.channel != null) {
            this.nettyCollect = collect;
            return this;
        }
        this.sender.setSend(collect);
        this.collector.setCollect(collect);
        return this;
    }

    @Override
    public ISession setMessageHandler(IMessageHandler handler) {
        if (this.channel != null) {
            this.nettyHandler = handler;
            return this;
        }
        this.collector.setMessageHandler(handler);
        return this;
    }

    /** Netty: mã hóa ngay trên luồng gọi (giữ đúng thứ tự khóa XOR) rồi đẩy vào hàng đợi ghi của kênh. */
    private void nettyWrite(Message msg) throws Exception {
        if (!this.channel.isActive() || this.nettyCollect == null) {
            return;
        }
        synchronized (this) {
            io.netty.buffer.ByteBuf buf = this.channel.alloc().buffer(256);
            try (java.io.DataOutputStream dos = new java.io.DataOutputStream(new io.netty.buffer.ByteBufOutputStream(buf))) {
                this.nettyCollect.doSendMessage(this, dos, msg);
            } catch (Exception e) {
                buf.release();
                throw e;
            }
            io.netty.channel.ChannelOutboundBuffer out = this.channel.unsafe().outboundBuffer();
            if (out != null && out.totalPendingWriteBytes() > nro.models.network.netty.AntiDdos.MAX_PENDING_WRITE) {
                buf.release();
                this.channel.close(); // máy khách không nhận nổi (mạng treo / cố ý không đọc): ngắt để không ăn RAM
                return;
            }
            this.channel.writeAndFlush(buf, this.channel.voidPromise());
        }
    }

    @Override
    public ISession setKeyHandler(IKeySessionHandler handler) {
        this.keyHandler = handler;
        return this;
    }

    @Override
    public void sendMessage(Message msg) {
        if (this.isConnected() && msg != null) {
            if (this.channel != null) {
                try {
                    nettyWrite(msg);
                } catch (Exception ignored) {
                }
                return;
            }
            this.sender.sendMessage(msg);
        }
    }

    @Override
    public void doSendMessage(Message msg) throws Exception {
        if (this.channel != null) {
            nettyWrite(msg);
            return;
        }
        this.sender.doSendMessage(msg);
    }

    @Override
    public void sendKey() throws Exception {
        if (this.keyHandler == null) {
            throw new Exception("Key handler has not been initialized!");
        }
        if (Network.gI().isRandomKey()) {
            this.KEYS = StringUtil.randomText(7).getBytes();
        }
        this.keyHandler.sendKey(this);
    }

    @Override
    public void setSentKey(boolean sent) {
        this.sentKey = sent;
    }

    @Override
    public boolean sentKey() {
        return this.sentKey;
    }

    @Override
    public boolean isConnected() {
        return this.connected;
    }

    @Override
    public long getID() {
        return this.id;
    }

    @Override
    public String getIP() {
        return this.ip;
    }

    @Override
    public byte[] getKey() {
        return this.KEYS;
    }

    @Override
    public int getNumMessages() {
        if (this.channel != null) {
            return this.isConnected() ? 0 : -1;
        }
        return this.isConnected() ? this.sender.getNumMessages() : -1;
    }

    @Override
    public void disconnect() {
        this.connected = false;
        this.sentKey = false;

        if (this.sender != null) {
            this.sender.close();
        }
        if (this.collector != null) {
            this.collector.close();
        }

        if (this.channel != null) {
            this.channel.close();
        } else {
            try {
                this.socket.close();
            } catch (IOException ignored) {
            }
        }

        this.dispose();
    }

    @Override
    public void dispose() {
        if (this.sender != null) {
            this.sender.dispose();
        }
        if (this.collector != null) {
            this.collector.dispose();
        }

        if (this.tSender != null && this.tSender.isAlive()) {
            this.tSender.interrupt();
        }
        if (this.tCollector != null && this.tCollector.isAlive()) {
            this.tCollector.interrupt();
        }

        SessionManager.gI().removeSession(this);
    }
}
