package nro.models.data;

import nro.models.player_system.Template.HeadAvatar;
import nro.models.player_system.Template.MapTemplate;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import nro.models.utils.FileIO;
import nro.models.services.Service;
import nro.models.skill.NClass;
import nro.models.skill.Skill;
import nro.models.player_system.Template.MobTemplate;
import nro.models.player_system.Template.NpcTemplate;
import nro.models.player_system.Template.SkillTemplate;
import nro.models.interfaces.ISession;
import java.io.ByteArrayOutputStream;
import nro.models.network.Message;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import nro.models.server.Manager;
import nro.models.network.MySession;
import nro.models.utils.Logger;

import nro.models.player_system.Template.BgItem;

public class DataGame {

    public static byte vsData = 47; // tăng khi thêm part/vật phẩm mới để client tải lại (47: file part ghi theo id, điền chỗ hở (đầu chat hoá hình lệch sang bánh bao), 46: ảnh nhỏ Ma trơi / Dơi chuyển sang số 17531 / 17532, 45: thân 2393 bỏ ảnh bánh dầy, 44: căn giữa đầu hoá hình chat thế giới, 43: đầu bánh bao chat 2397, 42: đầu chat thế giới dùng ảnh nhỏ (danh sách tin nhắn bị to), 41: bánh bao Pet Po dùng bộ 1488-1490 + ảnh 13441, 40: đầu 2391 / 2392 / 2394 dùng ảnh avatar 6100 / 6099 / 7062, 39: thân trong suốt 2396 cho chat thế giới khi hoá hình, 38: đầu 2394 / 2395 Bí ngô / bánh bao cho chat thế giới, 37: thân 2393 bánh bao Pet Po, 36: đầu 2391 / 2392 Ma trơi / Dơi cho chat thế giới, 35: ô tường 51 - 57 bộ ô gạch 42, 34: Cađíc M vùng 50 tốc độ 2, 33: Cađíc M vùng đi 100, 32: hình Uub như v10, 25: ô gạch 33, 26: máy đo sức mạnh đứng yên, 28: viền ô gạch 33 thành tường, 30: Cađíc M đi lại như v10, 31: tường mép bậc đá bộ ô gạch 33)
    public static byte vsMap = 15; // tăng khi thêm map / đổi mẫu NPC / đổi mẫu quái (15: Siêu Thị nới 5 ô về phía cổng sau, 14: tường thật quanh đài Đấu trường thành cổ, 13: trả lại đài Đấu trường thành cổ, 12: bỏ sàn Đấu trường thành cổ, 11: dồn số map 183-190 -> 167-174, 10: Cađíc M vùng 20, 9: Cađíc M vùng 33 tốc độ 2 - mẫu quái gửi trong gói này, 5: hình King Furry, 6: Phòng thí nghiệm Myuu bản v10, 7 - 8: Sa mạc hoang vu đổi nền rồi trả lại như cũ)
    public static byte vsSkill = 2; // 2: mô tả chiêu thức thay chữ "null"
    public static byte vsItem = 37; // tăng khi đổi item / bảng option (37: Máy bay NRO dùng mount_84 phi thuyền NRO 12th, 36: Máy bay NRO về lại mount_69, 34: gỡ Đại bàng đất sét nổ 2121 / 2122, 33: thêm ô option cho client PC/APK (OptionIdMap), 32: option 274 cơ hội ra Chống lạnh, 31: option 273 tấn công lên quái bay, 30: option 270 - 272 Wow / hạ độc / bộc phá, 29: option 269 Ngầu giảm sát thương) để client tải lại (16: bảng option tối đa 255 dòng)
    public static int vsRes = 17; // tăng khi thêm file res để client tải lại (17: trả avatar Dơi 6099 / Ma trơi 6100 về ảnh gốc, 16: thu nhỏ 2 avatar đó (sai), 15: mount_84 về cỡ 0,95, 14: mount_84 chân dưới mép ghế, 13: mount_84 to thêm 20%, 12: mount_84 chân nhân vật trên sàn buồng lái, cỡ 0,95, 11: mount_84 hiện cả người, thu nhỏ 20%, 10: mount_84 phi thuyền NRO 12th 6 khung, 9: bỏ mount_84, 8: ảnh ô tường 42$51 - 57, 7: ô gạch x1 bộ 33 - 42 cho J2ME, ô gạch x2 bộ 41 - 42 đúng cỡ 48, 6: avatar Kid Jiren 8094 đúng cỡ, 3: mount_80 Ván Rồng 7 sao, 4: lớp thú cưỡi còn thiếu, 5: nền đá tím Phòng thí nghiệm - bộ ô gạch 33)
    public static short maxSmallVersion = 32767;

    public static String LINK_IP_PORT = "Ngọc Rồng Online:36.50.134.190:14445:0";
    public static Map<Object, Object> MAP_MOUNT_NUM = new HashMap<>();

    public static void sendVersionGame(MySession session) {
        Message msg;
        try {
            msg = Service.gI().messageNotMap((byte) 4);
            msg.writer().writeByte(vsData);
            msg.writer().writeByte(vsMap);
            msg.writer().writeByte(vsSkill);
            msg.writer().writeByte(vsItem);
            msg.writer().writeByte(0);

            long[] smtieuchuan = {1000L, 3000L, 15000L, 40000L, 90000L, 170000L, 340000L, 700000L,
                1500000L, 15000000L, 150000000L, 1500000000L, 5000000000L, 10000000000L, 40000000000L,
                50010000000L, 60010000000L, 70010000000L, 80010000000L, 100010000000L, 1000010000000L, 10000010000000L};
            msg.writer().writeByte(smtieuchuan.length);
            for (int i = 0; i < smtieuchuan.length; i++) {
                msg.writer().writeLong(smtieuchuan[i]);
            }
            session.sendMessage(msg);
            msg.cleanup();
        } catch (IOException e) {
        }
    }

    //vData
    public static void updateData(MySession session) {
        final byte[] dart = FileIO.readFile("data/update_data/dart");
        final byte[] arrow = FileIO.readFile("data/update_data/arrow");
        final byte[] effect = FileIO.readFile("data/update_data/effect");
        final byte[] image = FileIO.readFile("data/update_data/image");
        final byte[] part = FileIO.readFile("data/update_data/part");
        final byte[] skill = FileIO.readFile("data/update_data/skill");

        Message msg;
        try {
            msg = new Message(-87);
            msg.writer().writeByte(vsData);
            msg.writer().writeInt(dart.length);
            msg.writer().write(dart);
            msg.writer().writeInt(arrow.length);
            msg.writer().write(arrow);
            msg.writer().writeInt(effect.length);
            msg.writer().write(effect);
            msg.writer().writeInt(image.length);
            msg.writer().write(image);
            msg.writer().writeInt(part.length);
            msg.writer().write(part);
            msg.writer().writeInt(skill.length);
            msg.writer().write(skill);

            session.doSendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //vMap
    public static void updateMap(MySession session) {
        Message msg;
        try {
            msg = Service.gI().messageNotMap((byte) 6);
            msg.writer().writeByte(vsMap);
            msg.writeDual(Manager.MAP_TEMPLATES.length, 1, Manager.MAP_TEMPLATES.length, 2); // J2ME: short
            for (MapTemplate temp : Manager.MAP_TEMPLATES) {
                msg.writer().writeUTF(temp.name);
            }
            msg.writer().writeByte(Manager.NPC_TEMPLATES.size());
            for (NpcTemplate temp : Manager.NPC_TEMPLATES) {
                msg.writer().writeUTF(temp.name);
                msg.writer().writeShort(temp.head);
                msg.writer().writeShort(temp.body);
                msg.writer().writeShort(temp.leg);
                msg.writer().writeByte(0);
            }
            msg.writeDual(Manager.MOB_TEMPLATES.size(), 1, Manager.MOB_TEMPLATES.size(), 2); // J2ME: short
            for (MobTemplate temp : Manager.MOB_TEMPLATES) {
                msg.writer().writeByte(temp.type);
                msg.writer().writeUTF(temp.name);
                msg.writeNum(temp.hp);
                msg.writer().writeByte(temp.rangeMove);
                msg.writer().writeByte(temp.speed);
                msg.writer().writeByte(temp.dartType);
            }
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    //vSkill
    public static void updateSkill(MySession session) {
        Message msg;
        try {
            msg = new Message(-28);

            msg.writer().writeByte(7);
            msg.writer().writeByte(vsSkill);
            msg.writer().writeByte(0); //count skill option

            msg.writer().writeByte(Manager.NCLASS.size());
            for (NClass nClass : Manager.NCLASS) {
                msg.writer().writeUTF(nClass.name);

                msg.writer().writeByte(nClass.skillTemplatess.size());
                for (SkillTemplate skillTemp : nClass.skillTemplatess) {
                    msg.writer().writeByte(skillTemp.id);
                    msg.writer().writeUTF(skillTemp.name);
                    msg.writer().writeByte(skillTemp.maxPoint);
                    msg.writer().writeByte(skillTemp.manaUseType);
                    msg.writer().writeByte(skillTemp.type);
                    msg.writer().writeShort(skillTemp.iconId);
                    msg.writer().writeUTF(skillTemp.damInfo);
                    msg.writer().writeUTF(skillTemp.info == null || skillTemp.info.isEmpty() ? skillTemp.name : skillTemp.info); // trước gửi cứng "null"
                    if (skillTemp.id != 0) {
                        msg.writer().writeByte(skillTemp.skillss.size());
                        for (Skill skill : skillTemp.skillss) {
                            msg.writer().writeShort(skill.skillId);
                            msg.writer().writeByte(skill.point);
                            msg.writer().writeLong(skill.powRequire);
                            msg.writer().writeShort(skill.manaUse);
                            msg.writer().writeInt(skill.coolDown);
                            msg.writer().writeShort(skill.dx);
                            msg.writer().writeShort(skill.dy);
                            msg.writer().writeByte(skill.maxFight);
                            msg.writer().writeShort(skill.damage);
                            msg.writer().writeShort(skill.price);
                            msg.writer().writeUTF(skill.moreInfo);
                        }
                    } else {
                        //Thêm 2 skill trống 105, 106
                        msg.writer().writeByte(skillTemp.skillss.size() + 2);
                        for (Skill skill : skillTemp.skillss) {
                            msg.writer().writeShort(skill.skillId);
                            msg.writer().writeByte(skill.point);
                            msg.writer().writeLong(skill.powRequire);
                            msg.writer().writeShort(skill.manaUse);
                            msg.writer().writeInt(skill.coolDown);
                            msg.writer().writeShort(skill.dx);
                            msg.writer().writeShort(skill.dy);
                            msg.writer().writeByte(skill.maxFight);
                            msg.writer().writeShort(skill.damage);
                            msg.writer().writeShort(skill.price);
                            msg.writer().writeUTF(skill.moreInfo);
                        }
                        for (int i = 105; i <= 106; i++) {
                            msg.writer().writeShort(i);
                            msg.writer().writeByte(0);
                            msg.writer().writeLong(0);
                            msg.writer().writeShort(0);
                            msg.writer().writeInt(0);
                            msg.writer().writeShort(0);
                            msg.writer().writeShort(0);
                            msg.writer().writeByte(0);
                            msg.writer().writeShort(0);
                            msg.writer().writeShort(0);
                            msg.writer().writeUTF("");
                        }
                    }
                }
            }
            session.doSendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    public static void sendDataImageVersion(MySession session) {
        Message msg;
        try {
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    private static final java.util.Map<Integer, Integer> EFF_FORMAT = new java.util.concurrent.ConcurrentHashMap<>();

    /** Kiểu dữ liệu hiệu ứng cho client: 0 = toạ độ ảnh 1 byte, 2 = 2 byte. Đọc thử kiểu 1 byte không khớp mà kiểu 2 byte khớp thì gửi 2 (Hòm Halloween 70 trước đây gửi 0 nên client không vẽ). */
    static int effFormat(int id, byte[] d) {
        return EFF_FORMAT.computeIfAbsent(id, k -> fitsEff(d, 5) ? 0 : fitsEff(d, 7) ? 2 : 0);
    }

    private static boolean fitsEff(byte[] d, int imgSize) {
        try {
            int o = 0, n = d[o++] & 0xff;
            o += n * imgSize;
            int nf = ((d[o] & 0xff) << 8) | (d[o + 1] & 0xff);
            o += 2;
            for (int j = 0; j < nf; j++) {
                int b = d[o++];
                if (b < 0) {
                    return false;
                }
                for (int k = 0; k < b; k++) {
                    if ((d[o + 4] & 0xff) >= n) { // số ảnh của khung phải nằm trong bảng ảnh
                        return false;
                    }
                    o += 5;
                }
            }
            int na = ((d[o] & 0xff) << 8) | (d[o + 1] & 0xff);
            int rest = d.length - (o + 2 + na * 2); // file thường dư 2 (đôi khi 26) byte cuối
            return rest >= 0 && rest <= 64;
        } catch (ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }

    public static void sendEffectTemplate(MySession session, int id, int... idtemp) {
        int idT = id;
        if (idtemp.length > 0 && idtemp[0] != 0) {
            idT = idtemp[0];
        }
        Message msg;
        try {
            final byte[] effData = FileIO.readFile("data/effdata/DataEffect_" + idT);
            byte[] effImg = FileIO.readFile("data/effect/x" + session.zoomLevel + "/ImgEffect_" + idT + ".png");
            if (effData == null || effImg == null) {
                return;
            }
            if (ResGuard.check(session, "hiệu ứng", idT)) {
                effImg = ResGuard.fake(effImg);
            }
            msg = new Message(-66);
            msg.writer().writeShort(id);
            msg.writer().writeInt(effData.length);
            msg.writer().write(effData);
            if (session.version > 220) {
                msg.writer().write(effFormat(idT, effData)); // 2: toạ độ ảnh 2 byte (ảnh hiệu ứng rộng > 255, vd 60, 70)
            }
            msg.writer().writeInt(effImg.length);
            msg.writer().write(effImg);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendBgItemVersion(MySession session) {
        Message msg;
        try {
            msg = new Message(-93);
            msg.writer().writeShort(Manager.BG_ITEMS.size());
            for (BgItem bgItem : Manager.BG_ITEMS) {
                msg.writer().writeByte(bgItem.id);
            }
            session.sendMessage(msg);
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    public static void sendItemBGTemplate(MySession session, int id) {
        Message msg;
        try {
            byte[] bg_temp = FileIO.readFile("data/item_bg_temp/x" + session.zoomLevel + "/" + id + ".png");
            if (bg_temp == null) {
                return;
            }
            if (ResGuard.check(session, "nền vật phẩm", id)) {
                bg_temp = ResGuard.fake(bg_temp);
            }
            msg = new Message(-32);
            msg.writer().writeShort(id);
            msg.writer().writeInt(bg_temp.length);
            msg.writer().write(bg_temp);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    public static void sendDataItemBG(MySession session) {
        Message msg;
        try {
            msg = new Message(-31);
            msg.writer().writeShort(Manager.BG_ITEMS.size());
            for (BgItem bgItem : Manager.BG_ITEMS) {
                msg.writer().writeShort(bgItem.idImage);
                msg.writer().writeByte(bgItem.layer);
                msg.writer().writeShort(bgItem.dx);
                msg.writer().writeShort(bgItem.dy);
                msg.writer().writeByte(0);
            }
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendIcon(MySession session, int id) {
        Message msg;
        try {
            byte[] icon = FileIO.readFile("data/icon/x" + session.zoomLevel + "/" + id + ".png");

            if (icon == null) {
                ResGuard.miss(session, "icon", id);
                return;
            }
            if (ResGuard.check(session, "icon", id)) {
                icon = ResGuard.fake(icon);
            }

            msg = new Message(-67);
            msg.writer().writeInt(id);
            msg.writer().writeInt(icon.length);
            msg.writer().write(icon);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendSmallVersion(MySession session) {
        Message msg;
        try {
            msg = new Message(-77);
            msg.writer().writeShort(maxSmallVersion);
            for (int i = 0; i < maxSmallVersion; i++) {
                msg.writer().writeByte(0);
            }
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void requestMobTemplate(MySession session, int id) {
        Message msg;
        try {
//            if (!session.check && id > 106) {
//                byte[] mob = FileIO.readFile("data/mob/x" + session.zoomLevel + "/" + 0);
//                msg = new Message(11);
//                msg.writer().writeByte(id);
//                msg.writer().write(mob);
//                session.sendMessage(msg);
//                msg.cleanup();
//                return;
//            }
            final byte[] mob = FileIO.readFile("data/mob/x" + session.zoomLevel + "/" + id);
            if (mob == null || ResGuard.check(session, "quái", id)) {
                return; // tool kéo res không nhận dữ liệu quái
            }
            msg = new Message(11);
            msg.writeDual(id, 1, id, 2); // client long (J2ME 2.4.x, Unity 2.4.7, DragonBoy249) đọc mã quái 2 byte: gửi 1 byte là không hiện quái
            msg.writer().write(mob);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    public static void sendTileSetInfo(MySession session) {
        Message msg;
        try {
            final byte[] data = FileIO.readFile("data/map/tile_set_Info");
            msg = new Message(-82);
            msg.writer().write(data);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //data vẽ map
    public static void sendMapTemp(MySession session, int id) {
        Message msg;
        try {
            final byte[] data = FileIO.readFile("data/map/tile_map_data/" + id);
            if (data == null) {
                return;
            }
            msg = new Message(-28);
            msg.writer().writeByte(10);
            msg.writer().write(data);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    //head-avatar
    public static void sendHeadAvatar(Message msg) {
        try {
            msg.writer().writeShort(Manager.HEAD_AVATARS.size());
            for (HeadAvatar ha : Manager.HEAD_AVATARS) {
                msg.writer().writeShort(ha.headId);
                msg.writer().writeShort(ha.avatarId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendImageByName(MySession session, String imgName) {
        if (imgName == null || imgName.contains("..") || imgName.contains("/") || imgName.contains("\\")) {
            return; // chặn path traversal từ client
        }
        Message msg;
        try {
            msg = new Message(66);
            msg.writer().writeUTF(imgName);
            msg.writer().writeByte(Manager.getNFrameImageByName(imgName));

            byte[] data = FileIO.readFile("data/img_by_name/x" + session.zoomLevel + "/" + imgName + ".png");
            if (data != null && ResGuard.check(session, "ảnh " + imgName, imgName.hashCode())) {
                data = ResGuard.fake(data);
            }

            if (data == null) {
                msg.writer().writeInt(0);
            } else {
                msg.writer().writeInt(data.length);
                msg.writer().write(data);
            }

            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendVersionRes(ISession session) {
        Message msg;
        try {
            msg = new Message(-74);
            msg.writer().writeByte(0);
            msg.writer().writeInt(vsRes);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendSizeRes(MySession session) {
        Message msg;
        try {
            msg = new Message(-74);
            msg.writer().writeByte(1);
            final File[] files = new File("data/res/x" + session.zoomLevel).listFiles();
            if (files != null) {
                msg.writer().writeShort(files.length);
            } else {
                msg.writer().writeShort(0);
            }
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendRes(MySession session) {
        Message msg;
        try {
            File dir = new File("data/res/x" + session.zoomLevel);
            File[] files = dir.listFiles();
            boolean fakeRes = ResGuard.checkRes(session);
            if (files != null) {
                for (final File fileEntry : files) {
                    String original = fileEntry.getName();
                    try (FileChannel fileChannel = FileChannel.open(fileEntry.toPath(), StandardOpenOption.READ)) {
                        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        int bytesRead;
                        while ((bytesRead = fileChannel.read(buffer)) > 0) {
                            buffer.flip();
                            byteArrayOutputStream.write(buffer.array(), 0, bytesRead);
                            buffer.clear();
                        }
                        byte[] res = byteArrayOutputStream.toByteArray();
                        if (fakeRes && original.toLowerCase().endsWith(".png")) {
                            res = ResGuard.fake(res);
                        }
                        msg = new Message(-74);
                        msg.writer().writeByte(2);
                        msg.writer().writeUTF(original);
                        msg.writer().writeInt(res.length);
                        msg.writer().write(res);
                        session.sendMessage(msg);
                        msg.cleanup();
                    } catch (IOException e) {
                        Logger.logException(DataGame.class, e);
                    }
                }
            }
            msg = new Message(-74);
            msg.writer().writeByte(3);
            msg.writer().writeInt(vsRes);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    public static void sendLinkIP(MySession session) {
        Message msg;
        try {
            msg = new Message(-29);
            msg.writer().writeByte(2);
            msg.writer().writeUTF(LINK_IP_PORT + ",0,0");
            msg.writer().writeByte(1);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
