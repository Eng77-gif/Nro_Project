package nro.models.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import nro.models.utils.Logger;

/**
 * Đợt 52: gán đúng part cho item mặc vào không hiện (lấy từ source V10), sửa giao diện Xe nước mía (trước đó dùng part quán phở anh 2).
 * Thêm part / cờ đeo lưng mới; ảnh đi kèm nằm trong data/icon. Chạy lại không sao (insert ignore, update có điều kiện).
 * Nếu id part / flag_bag mới đã có dữ liệu khác (database đã sửa tay) thì bỏ qua toàn bộ và báo lỗi, không ghi đè.
 */
public final class Dot52Migration {

    private Dot52Migration() {
    }

    private static final String[] SQL = {
        "insert into part (id, TYPE, DATA) select 2349, 0, '[[2955,-20,-51],[2955,0,0],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2349)",
        "insert into part (id, TYPE, DATA) select 2350, 1, '[[2955,0,0],[13255,-24,-69],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2350)",
        "insert into part (id, TYPE, DATA) select 2351, 2, '[[2955,0,0],[13254,-25,-75],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2351)",
        "update npc_template set head=2349, body=2350, leg=2351 where id=84 and head=1758",
        "update item_template set head=882, body=883, leg=884 where id=892 and head<0",
        "update item_template set head=885, body=886, leg=887 where id=893 and head<0",
        "update item_template set head=891, body=892, leg=893 where id=908 and head<0",
        "update item_template set head=894, body=895, leg=896 where id=909 and head<0",
        "update item_template set head=897, body=898, leg=899 where id=910 and head<0",
        "update item_template set head=925, body=926, leg=927 where id=916 and head<0",
        "update item_template set head=928, body=929, leg=930 where id=917 and head<0",
        "update item_template set head=931, body=932, leg=933 where id=918 and head<0",
        "update item_template set head=934, body=935, leg=936 where id=919 and head<0",
        "update item_template set head=718, body=719, leg=720 where id=936 and head<0",
        "update item_template set head=966, body=967, leg=968 where id=942 and head<0",
        "update item_template set head=969, body=970, leg=971 where id=943 and head<0",
        "update item_template set head=972, body=973, leg=974 where id=944 and head<0",
        "update item_template set head=997, body=998, leg=999 where id=957 and head<0",
        "update item_template set head=1000, body=1001, leg=1002 where id=958 and head<0",
        "update item_template set head=1003, body=1004, leg=1005 where id=959 and head<0",
        "update item_template set head=1050, body=1051, leg=1052 where id=967 and head<0",
        "update item_template set head=1053, body=1054, leg=1055 where id=989 and head<0",
        "update item_template set head=1056, body=1057, leg=1059 where id=990 and head<0",
        "update item_template set head=1060, body=1061, leg=1062 where id=991 and head<0",
        "update item_template set head=1074, body=1075, leg=1076 where id=1008 and head<0",
        "update item_template set head=1183, body=1184, leg=1185 where id=1188 and head<0",
        "update item_template set head=1201, body=1202, leg=1203 where id=1202 and head<0",
        "update item_template set head=1201, body=1202, leg=1203 where id=1203 and head<0",
        "update item_template set head=1227, body=1228, leg=1229 where id=1224 and head<0",
        "update item_template set head=1233, body=1234, leg=1235 where id=1225 and head<0",
        "update item_template set head=1230, body=1231, leg=1232 where id=1226 and head<0",
        "update item_template set head=1299, body=1300, leg=1301 where id=1318 and head<0",
        "update item_template set head=1302, body=1303, leg=1304 where id=1347 and head<0",
        "update item_template set head=1347, body=1348, leg=1349 where id=1435 and head<0",
        "update item_template set head=1398, body=1399, leg=1400 where id=1482 and head<0",
        "update item_template set head=1401, body=1402, leg=1403 where id=1497 and head<0",
        "update item_template set head=1572, body=1573, leg=1574 where id=1686 and head<0",
        "update item_template set head=371, body=372, leg=373 where id=1723 and head<0",
        "update item_template set head=573, body=574, leg=575 where id=1982 and head<0",
        "update item_template set head=802, body=803, leg=804 where id=1992 and head<0",
        "update item_template set part=57 where id=1776 and part<0",
        "insert ignore into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (162, '13807, 13808, 13809, 13810, 13811, 13812, 13813, 13814, 13815, 13816, 13817, 13818, 13819, 13820, 13821, 13822, 13823, 13824, 13825, 13826, 13827, 13828, 13829, 13830, 13831, 13832, 13833, 13834, 13835, 13836, 13837, 13838, 13839, 13840, 13841, 13842, 138', 'Hỏa tiêm thương', -1, -1, 13852)",
        "update item_template set part=162 where id=2092 and part<0",
        "insert into part (id, TYPE, DATA) select 2352, 0, '[[14788,-37,117],[14791,-46,118],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2352)",
        "insert into part (id, TYPE, DATA) select 2353, 1, '[[14683,-12,-14],[14684,-17,-19],[14685,-18,-20],[14686,-19,-22],[14687,-19,-17],[14688,-14,-18],[14689,-18,-18],[14690,-4,-24],[14691,-4,-22],[14692,-11,-21],[14693,-8,-18],[14694,-22,-16],[14695,-17,-29],[14696,-14,-20],[14697,-19,-20],[14698,-18,-19],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2353)",
        "insert into part (id, TYPE, DATA) select 2354, 2, '[[14699,1,-1],[14700,-9,-7],[14701,-4,-8],[14702,-4,-11],[14703,-6,-9],[14704,-6,-12],[14705,-3,-9],[14706,-4,-9],[14707,-4,-8],[14708,-11,-10],[14709,-4,-15],[14710,-7,-16],[14711,2,-10],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2354)",
        "update item_template set head=2352, body=2353, leg=2354 where id=1790 and head<0",
        "insert into part (id, TYPE, DATA) select 2355, 0, '[[14865,-1,-4],[14866,-2,-6],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2355)",
        "insert into part (id, TYPE, DATA) select 2356, 1, '[[14867,1,-3],[14868,0,-4],[14869,-1,-6],[14870,-1,-6],[14871,-1,-6],[14872,1,-6],[14873,0,-6],[14874,-2,-5],[14875,0,-3],[14876,-4,-2],[14877,-1,-6],[14878,-3,-6],[14879,-2,-7],[14880,-2,-7],[14881,-4,-7],[14882,1,-4],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2356)",
        "insert into part (id, TYPE, DATA) select 2357, 2, '[[14883,6,6],[14884,-1,-2],[14885,0,-1],[14886,1,-3],[14887,-1,-4],[14888,1,-4],[14889,1,-4],[14890,-2,0],[14891,1,0],[14892,-2,-5],[14893,0,-1],[14894,-2,0],[14895,-1,-3],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2357)",
        "update item_template set head=2355, body=2356, leg=2357 where id=1797 and head<0",
        "insert ignore into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (163, '15394, 15395, 15396, 15397, 15398, 15399, 15400, 15401, 15402, 15403, 15404, 15405, 15406, 15407, 15408, 15409, 15410', 'Bông cúc', -1, -1, 15411)",
        "update item_template set part=163 where id=1850 and part<0",
        "insert ignore into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (164, '15329, 15330', 'Đeo lưng bạch tuộc Xanh', -1, -1, 15331)",
        "update item_template set part=164 where id=1844 and part<0",
        "update item_template set part=164 where id=1882 and part<0",
        "update item_template set part=164 where id=1883 and part<0",
        "insert ignore into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (165, '15332, 15333', 'Đeo lưng bạch tuộc Hồng', -1, -1, 15334)",
        "update item_template set part=165 where id=1842 and part<0",
        "insert ignore into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (166, '15335, 15336', 'Đeo lưng bạch tuộc Vàng', -1, -1, 15337)",
        "update item_template set part=166 where id=1843 and part<0",
        "insert ignore into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (167, '32279, 32280, 32281, 32282, 32283, 32284, 32285, 32286, 32287, 32288, 32289, 32290, 32291, 32292, 32293, 32294, 32295, 32296, 32297, 32298\r\n', 'Cờ Quả Cầu Pokemon', -1, -1, 15685)",
        "update item_template set part=167 where id=1875 and part<0",
        "insert into part (id, TYPE, DATA) select 2358, 0, '[[15692,1,-10],[15693,2,-10],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2358)",
        "insert into part (id, TYPE, DATA) select 2359, 1, '[[15694,0,-7],[15695,-2,-11],[15696,0,-10],[15697,0,-11],[15698,1,-10],[15699,1,-11],[15700,0,-10],[15701,-3,-14],[15702,-3,-10],[15703,-7,-12],[15704,0,-8],[15705,0,-12],[15706,-2,-16],[15707,3,-11],[15708,-4,-11],[15709,0,-11],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2359)",
        "insert into part (id, TYPE, DATA) select 2360, 2, '[[15710,3,1],[15711,-3,-7],[15712,1,-4],[15713,2,-7],[15714,-1,-5],[15715,0,-5],[15716,-1,-5],[15717,-5,-4],[15718,-2,-4],[15719,-2,-6],[15720,2,-6],[15721,0,-2],[15722,-3,-5],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2360)",
        "update item_template set head=2358, body=2359, leg=2360 where id=1879 and head<0",
        "insert into part (id, TYPE, DATA) select 2361, 0, '[[15725,-5,-8],[15726,-5,-9],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2361)",
        "insert into part (id, TYPE, DATA) select 2362, 1, '[[15727,-1,-8],[15728,-2,-11],[15729,0,-13],[15730,-2,-12],[15731,1,-12],[15732,-1,-11],[15733,3,-12],[15734,0,-15],[15735,-2,-12],[15736,-4,-13],[15737,-5,-11],[15738,-3,-12],[15739,-1,-14],[15740,1,-11],[15741,-6,-14],[15742,-1,-11],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2362)",
        "insert into part (id, TYPE, DATA) select 2363, 2, '[[15743,5,1],[15744,2,-8],[15745,0,-7],[15746,1,-7],[15747,0,-6],[15748,0,-7],[15749,1,-7],[15750,-1,-4],[15751,-2,-5],[15752,-4,-9],[15753,1,-3],[15754,0,2],[15755,-2,0],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2363)",
        "update item_template set head=2361, body=2362, leg=2363 where id=1880 and head<0",
        "insert ignore into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (168, '15845, 15846, 15847, 15848, 15849, 15850, 15851, 15852, 15853, 15854, 15855, 15856, 15857, 15858, 15859', 'Thùng rác', -1, -1, 15860)",
        "update item_template set part=168 where id=1891 and part<0",
        "insert into part (id, TYPE, DATA) select 2364, 0, '[[14188,-2,-8],[14191,-5,-9],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2364)",
        "insert into part (id, TYPE, DATA) select 2365, 1, '[[14157,-13,-4],[14158,-9,-8],[14159,-8,-11],[14160,4,-11],[14161,6,-8],[14162,4,-9],[14163,5,-9],[14164,-7,-6],[14165,-1,-5],[14166,-9,-6],[14167,-4,-9],[14168,-5,-8],[14169,3,-8],[14170,-3,-13],[14171,-9,-10],[14172,-12,-7],[2954,0,0]]' from dual where not exists (select 1 from part where id = 2365)",
        "insert into part (id, TYPE, DATA) select 2366, 2, '[[14173,4,2],[14174,-2,-8],[14175,1,-5],[14176,-6,-5],[14177,-7,-6],[14178,-6,-5],[14179,-7,-7],[14180,0,-3],[14181,-2,-4],[32278,2,-5],[14183,-9,-5],[14184,-7,-1],[14185,-2,-4],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2366)",
        "update item_template set head=2364, body=2365, leg=2366 where id=1709 and head<0",
        "insert into part (id, TYPE, DATA) select 2367, 0, '[[14151,-2,-8],[14154,-5,-9],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2367)",
        "insert into part (id, TYPE, DATA) select 2368, 1, '[[14157,-13,-4],[14158,-9,-8],[14159,-8,-11],[14160,4,-11],[14161,6,-8],[14162,4,-9],[14163,5,-9],[14164,-7,-6],[14165,-1,-5],[14166,-9,-6],[14167,-4,-9],[14168,-5,-8],[14169,3,-8],[14170,-3,-13],[14171,-9,-10],[14172,-12,-7],[2954,0,0]]' from dual where not exists (select 1 from part where id = 2368)",
        "insert into part (id, TYPE, DATA) select 2369, 2, '[[14173,4,2],[14174,-2,-8],[14175,1,-5],[14176,-6,-5],[14177,-7,-6],[14178,-6,-5],[14179,-7,-7],[14180,0,-3],[14181,-2,-4],[32278,2,-5],[14183,-9,-5],[14184,-7,-1],[14185,-2,-4],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2369)",
        "update item_template set head=2367, body=2368, leg=2369 where id=1708 and head<0",
        "insert into part (id, TYPE, DATA) select 2370, 0, '[[15813,3,5],[15814,4,5],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2370)",
        "insert into part (id, TYPE, DATA) select 2371, 1, '[[15815,0,0],[15816,0,0],[15817,0,0],[15818,0,0],[15819,0,0],[15820,0,0],[15821,0,0],[15822,1,-1],[15823,0,0],[15824,0,0],[15825,0,0],[15826,0,0],[15827,0,-2],[15828,0,0],[15829,0,0],[15830,0,0],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2371)",
        "insert into part (id, TYPE, DATA) select 2372, 2, '[[15831,2,3],[15832,0,0],[15833,0,0],[15834,0,0],[15835,0,0],[15836,0,0],[15837,0,0],[15838,0,0],[15839,0,0],[15840,0,0],[15841,0,0],[15842,0,1],[15843,0,0],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2372)",
        "update item_template set head=2370, body=2371, leg=2372 where id=1889 and head<0",
        "insert ignore into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (169, '15804, 15805, 15806, 15807, 15808, 15809', 'flag_bag', -1, -1, 0)",
        "update item_template set part=169 where id=1888 and part<0",
        "insert ignore into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (170, '16140, 16141, 16142, 16143', 'flag_bag', -1, -1, 0)",
        "update item_template set part=170 where id=1925 and part<0",
        "insert into part (id, TYPE, DATA) select 2373, 0, '[[16145,3,1],[16146,3,1],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2373)",
        "insert into part (id, TYPE, DATA) select 2374, 1, '[[16147,-2,1],[16148,-3,-1],[16149,-5,-3],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[16150,0,0],[16151,-1,-1],[16152,-7,-2],[16153,-5,0],[16154,-3,-2],[16155,-1,-1],[16156,-1,-3],[16157,0,-2],[16158,-4,-1],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2374)",
        "insert into part (id, TYPE, DATA) select 2375, 2, '[[16159,7,7],[16160,-3,1],[16161,-1,-1],[16162,-1,-10],[16163,-1,-9],[16164,1,-9],[16165,-1,-8],[16166,-1,2],[16167,-3,0],[16168,0,-1],[16169,1,-2],[16170,0,1],[16171,-1,0],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2375)",
        "update item_template set head=2373, body=2374, leg=2375 where id=1926 and head<0",
        "insert ignore into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (171, '16133, 16134, 16135, 16136', 'flag_bag', -1, -1, 0)",
        "update item_template set part=171 where id=1923 and part<0",
        "insert into part (id, TYPE, DATA) select 2376, 0, '[[16188,0,-10],[16189,0,-12],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2376)",
        "insert into part (id, TYPE, DATA) select 2377, 1, '[[16190,-16,-9],[16191,-16,-11],[16192,-18,-11],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[16193,-17,-14],[16194,-14,-12],[16195,-15,-13],[16196,-8,-12],[16197,-10,-13],[16198,-3,-13],[16199,-9,-11],[16200,-13,-22],[16201,-25,-16],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2377)",
        "insert into part (id, TYPE, DATA) select 2378, 2, '[[16202,3,3],[16203,-3,-7],[16204,1,-2],[16205,-18,-17],[16206,-11,-17],[16207,-17,-18],[16208,-18,-18],[16209,-3,-3],[16210,0,-3],[16211,-2,-8],[16212,-13,-5],[16213,-18,-3],[16214,-5,-6],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2378)",
        "update item_template set head=2376, body=2377, leg=2378 where id=1931 and head<0",
        "insert ignore into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (172, '16327', 'flag_bag', -1, -1, 0)",
        "update item_template set part=172 where id=1941 and part<0",
        "insert ignore into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (173, '16400, 16401, 16402, 16403', 'flag_bag', -1, -1, 0)",
        "update item_template set part=173 where id=1951 and part<0"
    };

    public static void run(Connection con) {
        try (Statement st = con.createStatement()) {
            // kiểm tra trùng id trước
            for (String s : SQL) {
                if (s.startsWith("insert into part") || s.startsWith("insert ignore into flag_bag")) {
                    String table = s.contains(" part ") ? "part" : "flag_bag";
                    String rest = s.contains("values (") ? s.substring(s.indexOf("values (") + 8) : s.substring(s.indexOf("select ") + 7);
                    String id = rest.substring(0, rest.indexOf(','));
                    String data = rest.substring(rest.indexOf('\'') + 1, rest.indexOf("', ") > 0 ? rest.indexOf("', ") : rest.indexOf("' from dual") > 0 ? rest.indexOf("' from dual") : rest.lastIndexOf('\''));
                    try (ResultSet rs = st.executeQuery("select " + (table.equals("part") ? "DATA" : "icon_data") + " from " + table + " where id = " + id)) {
                        if (rs.next() && !rs.getString(1).replace("\\n", "\n").equals(data.replace("\\'", "'").replace("\\\\", "\\").replace("\\n", "\n"))) {
                            Logger.error("Đợt 52: " + table + " id " + id + " đã có dữ liệu khác, bỏ qua sửa part item\n");
                            return;
                        }
                    }
                }
            }
            int n = 0;
            for (String s : SQL) {
                n += st.executeUpdate(s);
            }
            if (n > 0) {
                Logger.success("Đợt 52: đã gán part cho item / Xe nước mía (" + n + " dòng)\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 52: lỗi cập nhật part: " + e.getMessage() + "\n");
        }
    }
}
