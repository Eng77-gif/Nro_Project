import nro.models.network.Message;
import nro.models.player_system.Template;
import nro.models.server.Manager;

/**
 * Tự kiểm tra dòng mô tả item cho client J2ME (chạy: xem tools/tests/run.sh).
 * Client J2ME (loại 1) nhận thêm 1 dòng (id mô tả, 0); client số lớn khác và client cũ không đổi.
 */
public class DescOptionCheck {

    public static void main(String[] a) throws Exception {
        for (int i = 0; i < 3; i++) {
            Template.ItemOptionTemplate o = new Template.ItemOptionTemplate();
            o.id = i;
            o.name = "opt" + i;
            Manager.ITEM_OPTION_TEMPLATES.add(o);
        }
        Template.ItemTemplate noDesc = new Template.ItemTemplate();
        noDesc.id = 0;
        noDesc.description = "";
        Template.ItemTemplate withDesc = new Template.ItemTemplate();
        withDesc.id = 1;
        withDesc.description = "Dùng để mở #hộp";
        Manager.ITEM_TEMPLATES.add(noDesc);
        Manager.ITEM_TEMPLATES.add(withDesc);

        check(0, 0, 0);   // item không mô tả: 3 bản giống nhau
        check(1, 1, 6);   // item có mô tả: J2ME thêm 1 dòng (2 byte id + 4 byte param)
        if (nro.models.data.ItemData.descOption(1) != 3) {
            throw new AssertionError("id dòng mô tả phải nằm sau 3 dòng thật, được " + nro.models.data.ItemData.descOption(1));
        }
        System.out.println("PASS");
    }

    private static void check(int templateId, int extraCount, int extraBytes) throws Exception {
        Message m = new Message(1);
        m.writer().writeByte(7);
        m.writeOptionCount(1, templateId);
        m.writeItemOption(2, 5);
        m.writeDescOption(templateId);
        m.writer().writeByte(9);
        byte[] old = m.getData(false), lng = m.getData(true, false), j2me = m.getData(true, true);
        if (old[1] != 1 || lng[1] != 1 || j2me[1] != 1 + extraCount) {
            throw new AssertionError("số dòng sai: " + old[1] + " " + lng[1] + " " + j2me[1]);
        }
        if (j2me.length - lng.length != extraBytes || lng[lng.length - 1] != 9 || j2me[j2me.length - 1] != 9) {
            throw new AssertionError("độ dài / byte cuối sai: long " + lng.length + ", j2me " + j2me.length);
        }
    }
}
