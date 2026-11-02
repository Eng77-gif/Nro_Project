package nro.models.panel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nro.models.data.LocalManager;

/**
 * Quản lý database trên panel: danh sách bảng, cấu trúc, xem / tìm / sắp xếp / phân trang, sửa ô, thêm, xóa dòng,
 * xuất bảng ra SQL hoặc CSV. Tên bảng / cột chỉ nhận đúng tên có trong database (chống chèn SQL),
 * mọi giá trị đi qua PreparedStatement. Sửa / xóa ghi lại dòng cũ vào logs/panel_actions.log để khôi phục.
 *
 * @author Việt
 */
final class PanelDb {

    private PanelDb() {
    }

    static Object tables() throws Exception {
        return PanelApi.rows("select table_name name, table_rows `rows`, round((data_length + index_length) / 1024) kb, table_comment note "
                + "from information_schema.tables where table_schema = database() order by table_name");
    }

    /** Cột của bảng: name, type, nullable, key, default, extra. */
    static List<Map<String, Object>> columns(String table) throws Exception {
        checkTable(table);
        return PanelApi.rows("select column_name name, column_type type, is_nullable nullable, column_key `key`, column_default def, extra "
                + "from information_schema.columns where table_schema = database() and table_name = ? order by ordinal_position", table);
    }

    static void checkTable(String table) throws Exception {
        if (table == null || PanelApi.rows("select 1 from information_schema.tables where table_schema = database() and table_name = ?", table).isEmpty()) {
            throw new IllegalArgumentException("Không có bảng " + table);
        }
    }

    private static List<String> colNames(String table) throws Exception {
        List<String> l = new ArrayList<>();
        for (Map<String, Object> c : columns(table)) {
            l.add(String.valueOf(c.get("name")));
        }
        return l;
    }

    private static List<String> pk(String table) throws Exception {
        List<String> l = new ArrayList<>();
        for (Map<String, Object> c : columns(table)) {
            if ("PRI".equals(c.get("key"))) {
                l.add(String.valueOf(c.get("name")));
            }
        }
        return l;
    }

    private static String q(String col) {
        return "`" + col.replace("`", "") + "`";
    }

    static Object rows(String table, int page, int size, String search, String sort, boolean desc) throws Exception {
        List<String> cols = colNames(table);
        size = Math.max(10, Math.min(500, size));
        page = Math.max(0, page);
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            where.append(" where (");
            for (int i = 0; i < cols.size(); i++) {
                where.append(i == 0 ? "" : " or ").append("cast(").append(q(cols.get(i))).append(" as char) like ?");
                args.add("%" + search.trim() + "%");
            }
            where.append(")");
        }
        String order = sort != null && cols.contains(sort) ? " order by " + q(sort) + (desc ? " desc" : "") : "";
        long total = ((Number) PanelApi.rows("select count(*) n from " + q(table) + where, args.toArray()).get(0).get("n")).longValue();
        List<Object> a2 = new ArrayList<>(args);
        a2.add(size);
        a2.add(page * size);
        List<Map<String, Object>> data = PanelApi.rows("select * from " + q(table) + where + order + " limit ? offset ?", a2.toArray());
        for (Map<String, Object> r : data) {
            for (Map.Entry<String, Object> e : r.entrySet()) {
                if (e.getValue() instanceof java.sql.Timestamp || e.getValue() instanceof java.sql.Date) {
                    e.setValue(String.valueOf(e.getValue()));
                }
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("columns", columns(table));
        m.put("pk", pk(table));
        m.put("total", total);
        m.put("page", page);
        m.put("size", size);
        m.put("rows", data);
        return m;
    }

    /** Điều kiện tìm đúng 1 dòng: theo khóa chính, bảng không có khóa thì theo toàn bộ giá trị cũ. */
    @SuppressWarnings("unchecked")
    private static String match(String table, Map<String, Object> key, List<Object> args) throws Exception {
        List<String> cols = colNames(table);
        List<String> pk = pk(table);
        List<String> use = pk.isEmpty() ? new ArrayList<>(key.keySet()) : pk;
        StringBuilder w = new StringBuilder(" where ");
        int n = 0;
        for (String c : use) {
            if (!cols.contains(c)) {
                throw new IllegalArgumentException("Không có cột " + c);
            }
            Object v = key.get(c);
            w.append(n++ == 0 ? "" : " and ");
            if (v == null) {
                w.append(q(c)).append(" is null");
            } else {
                w.append(q(c)).append(" = ?");
                args.add(v);
            }
        }
        if (n == 0) {
            throw new IllegalArgumentException("Thiếu khóa để xác định dòng");
        }
        return w.toString();
    }

    private static String oldRow(String table, String where, List<Object> args) throws Exception {
        return PanelApi.GSON_PUBLIC.toJson(PanelApi.rows("select * from " + q(table) + where + " limit 1", args.toArray()));
    }

    @SuppressWarnings("unchecked")
    static Object update(Map<String, Object> b) throws Exception {
        String table = PanelApi.str(b.get("table"));
        checkTable(table);
        Map<String, Object> key = (Map<String, Object>) b.get("key");
        Map<String, Object> set = (Map<String, Object>) b.get("values");
        List<String> cols = colNames(table);
        if (set == null || set.isEmpty()) {
            throw new IllegalArgumentException("Không có gì để sửa");
        }
        List<Object> args = new ArrayList<>();
        StringBuilder s = new StringBuilder("update " + q(table) + " set ");
        int n = 0;
        for (Map.Entry<String, Object> e : set.entrySet()) {
            if (!cols.contains(e.getKey())) {
                throw new IllegalArgumentException("Không có cột " + e.getKey());
            }
            s.append(n++ == 0 ? "" : ", ").append(q(e.getKey())).append(" = ?");
            args.add(e.getValue());
        }
        List<Object> wa = new ArrayList<>();
        String where = match(table, key, wa);
        PanelServer.appendAudit("db/update " + table + " dòng cũ: " + oldRow(table, where, wa));
        args.addAll(wa);
        int c = PanelApi.update(s + where + " limit 1", args.toArray());
        return Map.of("ok", true, "note", c > 0 ? "Đã lưu" : "Không tìm thấy dòng để sửa (có thể đã bị đổi)");
    }

    @SuppressWarnings("unchecked")
    static Object insert(Map<String, Object> b) throws Exception {
        String table = PanelApi.str(b.get("table"));
        checkTable(table);
        Map<String, Object> vals = (Map<String, Object>) b.get("values");
        List<String> cols = colNames(table);
        List<String> use = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> e : vals.entrySet()) {
            if (!cols.contains(e.getKey())) {
                throw new IllegalArgumentException("Không có cột " + e.getKey());
            }
            if (e.getValue() == null || String.valueOf(e.getValue()).isEmpty()) {
                continue; // để database tự điền (auto_increment / mặc định)
            }
            use.add(q(e.getKey()));
            args.add(e.getValue());
        }
        if (use.isEmpty()) {
            throw new IllegalArgumentException("Nhập ít nhất 1 cột");
        }
        PanelApi.update("insert into " + q(table) + " (" + String.join(", ", use) + ") values (" + "?, ".repeat(use.size() - 1) + "?)", args.toArray());
        return Map.of("ok", true, "note", "Đã thêm dòng");
    }

    @SuppressWarnings("unchecked")
    static Object delete(Map<String, Object> b) throws Exception {
        String table = PanelApi.str(b.get("table"));
        checkTable(table);
        List<Object> wa = new ArrayList<>();
        String where = match(table, (Map<String, Object>) b.get("key"), wa);
        PanelServer.appendAudit("db/delete " + table + " dòng đã xóa: " + oldRow(table, where, wa));
        int c = PanelApi.update("delete from " + q(table) + where + " limit 1", wa.toArray());
        return Map.of("ok", true, "note", c > 0 ? "Đã xóa 1 dòng" : "Không tìm thấy dòng");
    }

    /** Xuất bảng: SQL (create + insert) hoặc CSV. */
    static Object export(String table, String format) throws Exception {
        checkTable(table);
        StringBuilder out = new StringBuilder();
        try (Connection con = LocalManager.getConnection()) {
            if ("csv".equals(format)) {
                try (PreparedStatement ps = con.prepareStatement("select * from " + q(table)); ResultSet rs = ps.executeQuery()) {
                    int n = rs.getMetaData().getColumnCount();
                    for (int i = 1; i <= n; i++) {
                        out.append(i > 1 ? "," : "").append(csv(rs.getMetaData().getColumnLabel(i)));
                    }
                    out.append("\n");
                    while (rs.next()) {
                        for (int i = 1; i <= n; i++) {
                            out.append(i > 1 ? "," : "").append(csv(rs.getString(i)));
                        }
                        out.append("\n");
                    }
                }
            } else {
                try (PreparedStatement ps = con.prepareStatement("show create table " + q(table)); ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        out.append("DROP TABLE IF EXISTS ").append(q(table)).append(";\n").append(rs.getString(2)).append(";\n\n");
                    }
                }
                try (PreparedStatement ps = con.prepareStatement("select * from " + q(table)); ResultSet rs = ps.executeQuery()) {
                    int n = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        out.append("INSERT INTO ").append(q(table)).append(" VALUES (");
                        for (int i = 1; i <= n; i++) {
                            String v = rs.getString(i);
                            out.append(i > 1 ? ", " : "").append(v == null ? "NULL" : "'" + v.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r") + "'");
                        }
                        out.append(");\n");
                    }
                }
            }
        }
        return Map.of("filename", table + ("csv".equals(format) ? ".csv" : ".sql"), "content", out.toString());
    }

    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
