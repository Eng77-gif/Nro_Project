package nro.models.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import nro.models.player_system.Template.ItemOptionTemplate;
import nro.models.player_system.Template.ItemTemplate;
import nro.models.server.Manager;
import nro.models.utils.Logger;

/**
 * Tự tăng phiên bản dữ liệu item (vsItem) khi bảng item_template / item_option_template đổi.
 * Client chỉ tải lại danh sách item khi vsItem khác bản đã lưu trong máy; trước đây phải sửa tay số trong code, quên tăng
 * (thêm ván bay mới, xóa / dồn mã thú cưỡi...) thì client giữ danh sách cũ, gặp item mới trong hành trang là lỗi.
 * Mã băm và số phiên bản lưu trong bảng data_version.
 *
 * @author Việt
 */
public class DataVersionService {

    public static void syncItemVersion(Connection con) {
        try {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("create table if not exists data_version (name varchar(32) primary key, hash varchar(64) not null, ver int not null)");
            }
            String hash = itemHash();
            int ver = -1;
            String old = null;
            try (PreparedStatement ps = con.prepareStatement("select hash, ver from data_version where name = 'item'"); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    old = rs.getString(1);
                    ver = rs.getInt(2);
                }
            }
            if (old == null) {
                ver = next(DataGame.vsItem); // lần đầu: tăng 1 để mọi client tải lại
                try (PreparedStatement ps = con.prepareStatement("insert into data_version (name, hash, ver) values ('item', ?, ?)")) {
                    ps.setString(1, hash);
                    ps.setInt(2, ver);
                    ps.executeUpdate();
                }
                Logger.success("Phiên bản dữ liệu item: " + ver + "\n");
            } else if (!old.equals(hash)) {
                ver = next(ver);
                try (PreparedStatement ps = con.prepareStatement("update data_version set hash = ?, ver = ? where name = 'item'")) {
                    ps.setString(1, hash);
                    ps.setInt(2, ver);
                    ps.executeUpdate();
                }
                Logger.success("Dữ liệu item đã đổi, tăng phiên bản lên " + ver + " (client tải lại danh sách item)\n");
            }
            DataGame.vsItem = (byte) ver;
        } catch (Exception e) {
            Logger.error("Không đồng bộ được phiên bản item: " + e.getMessage() + "\n");
        }
    }

    /** Gọi sau khi sửa item trên panel (đã nạp lại vào bộ nhớ). */
    public static void resync() {
        try (Connection con = LocalManager.getConnection()) {
            syncItemVersion(con);
        } catch (Exception e) {
            Logger.error("Không đồng bộ được phiên bản item: " + e.getMessage() + "\n");
        }
    }

    private static int next(int v) {
        return v >= 120 || v < 1 ? 1 : v + 1;
    }

    private static String itemHash() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        StringBuilder sb = new StringBuilder();
        for (ItemTemplate t : Manager.ITEM_TEMPLATES) {
            sb.setLength(0);
            sb.append(t.id).append('|').append(t.type).append('|').append(t.gender).append('|').append(t.name).append('|')
                    .append(t.description).append('|').append(t.level).append('|').append(t.strRequire).append('|')
                    .append(t.iconID).append('|').append(t.part).append('|').append(t.isUpToUp).append('\n');
            md.update(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        for (ItemOptionTemplate o : Manager.ITEM_OPTION_TEMPLATES) {
            md.update((o.id + "|" + o.name + "|" + o.type + "\n").getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
