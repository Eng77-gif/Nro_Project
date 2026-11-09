package nro.models.network.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import nro.models.interfaces.ISessionAcceptHandler;
import nro.models.network.Message;
import nro.models.network.MySession;
import nro.models.network.Network;
import nro.models.network.Session;
import nro.models.network.SessionFactory;
import nro.models.network.SessionManager;
import nro.models.utils.Logger;

/**
 * Cổng game chạy trên Netty: vài luồng xử lý mọi kết nối (kiểu cũ tốn 2 luồng / người chơi nên dễ sập khi bị mở hàng
 * nghìn kết nối rác), có lớp chống DDoS (AntiDdos) trước khi tạo phiên. Gói tin của mỗi phiên vẫn được xử lý tuần tự
 * như cũ trên một nhóm luồng riêng, không chặn luồng mạng.
 *
 * @author Việt
 */
public final class NettyNetwork {

    private static EventLoopGroup boss, workers;
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(8, 512, 60, TimeUnit.SECONDS, new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r, "Xử lý gói tin");
                t.setDaemon(true);
                return t;
            }, (r, ex) -> new Thread(r, "Xử lý gói tin (dự phòng)").start());

    public static void start(int port, Class<?> sessionClass) throws Exception {
        ISessionAcceptHandler accept = Network.gI().getAcceptHandler();
        boss = new NioEventLoopGroup(1);
        workers = new NioEventLoopGroup(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));
        ServerBootstrap b = new ServerBootstrap()
                .group(boss, workers)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 256)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(256 * 1024, 4 * 1024 * 1024))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        String ip = ((InetSocketAddress) ch.remoteAddress()).getAddress().getHostAddress();
                        if (!AntiDdos.accept(ip)) {
                            ch.close();
                            return;
                        }
                        ch.closeFuture().addListener(f -> AntiDdos.release(ip));
                        Session s = (Session) SessionFactory.gI().cloneSession(sessionClass, (Channel) ch);
                        Handler h = new Handler(s, accept);
                        ch.pipeline().addLast(new IdleStateHandler(AntiDdos.LOGIN_IDLE_SECONDS, 0, 0),
                                new NroDecoder(s), h);
                        accept.sessionInit(s);
                        if (!s.isConnected()) {
                            ch.close();
                            return;
                        }
                        SessionManager.gI().putSession(s);
                        ch.eventLoop().schedule(() -> {
                            if (!s.sentKey()) {
                                AntiDdos.strike(ip, "không bắt tay trong " + AntiDdos.HANDSHAKE_SECONDS + " giây");
                                ch.close();
                            }
                        }, AntiDdos.HANDSHAKE_SECONDS, TimeUnit.SECONDS);
                    }
                });
        b.bind(port).sync();
        workers.scheduleAtFixedRate(AntiDdos::cleanup, 1, 1, TimeUnit.MINUTES);
        Logger.success("Server (Netty + chống DDoS) đang mở cổng " + port + "\n");
    }

    public static void shutdown() {
        if (boss != null) {
            boss.shutdownGracefully();
            workers.shutdownGracefully();
        }
    }

    /** Nhận gói đã tách, xử lý tuần tự theo phiên trên nhóm luồng riêng. */
    private static final class Handler extends ChannelInboundHandlerAdapter {

        private final Session session;
        private final ISessionAcceptHandler accept;
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        private boolean running, closed;

        Handler(Session session, ISessionAcceptHandler accept) {
            this.session = session;
            this.accept = accept;
        }

        private void submit(Runnable r) {
            synchronized (queue) {
                if (queue.size() > 2000) { // xử lý không kịp (bị dội gói): ngắt
                    session.getChannel().close();
                    return;
                }
                queue.add(r);
                if (running) {
                    return;
                }
                running = true;
            }
            POOL.execute(this::drain);
        }

        private void drain() {
            while (true) {
                Runnable r;
                synchronized (queue) {
                    r = queue.poll();
                    if (r == null) {
                        running = false;
                        return;
                    }
                }
                try {
                    r.run();
                } catch (Throwable e) {
                    Logger.error("Lỗi xử lý gói tin " + session.getIP() + ": " + e + "\n");
                }
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object o) {
            Message msg = (Message) o;
            submit(() -> {
                try {
                    if (msg.command == -27) {
                        session.sendKey();
                    } else if (session.getMessageHandler() != null) {
                        session.getMessageHandler().onMessage(session, msg);
                    }
                } catch (Exception e) {
                    session.getChannel().close();
                } finally {
                    msg.cleanup();
                }
            });
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            submit(() -> {
                if (closed) {
                    return;
                }
                closed = true;
                try {
                    accept.sessionDisconnect(session);
                } catch (Exception ignored) {
                }
                session.disconnect();
            });
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent && session instanceof MySession ms && ms.player == null) {
                ctx.close(); // chưa vào game mà im lặng quá lâu
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
