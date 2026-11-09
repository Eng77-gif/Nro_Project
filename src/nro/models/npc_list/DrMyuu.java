package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.map.service.ChangeMapService;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.DrMyuuService;
import nro.models.services.TaskService;

/**
 * Dr. Myuu: ngoài các map (xuất hiện ngẫu nhiên) đưa người chơi vào Phòng thí nghiệm Myuu đánh Baby; trong phòng thí nghiệm
 * (không có lối ra) đưa người chơi về nhà.
 *
 * @author Việt
 */
public class DrMyuu extends Npc {

    public DrMyuu(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            if (TaskService.gI().checkDoneTaskTalkNpc(player, this)) {
                return;
            }
            if (this.mapId == DrMyuuService.LAB) {
                this.createOtherMenu(player, ConstNpc.BASE_MENU, "Baby đang ở trong phòng thí nghiệm này.\nHạ được nó thì quay lại đây, ta đưa ngươi về.",
                        "Về nhà", "Ở lại");
            } else {
                this.createOtherMenu(player, ConstNpc.BASE_MENU,
                        "Năm 740, ta tìm thấy các kí sinh trùng của King Tuffle,\nsau đó ta đã nghiên cứu và chế tạo kí sinh trùng Baby.\nBaby có khả năng bám vào cơ thể người khác,\nkiểm soát sức mạnh của họ và làm việc theo ý của ta.\nTuy nhiên ta đã mất kiểm soát nó hoàn toàn...\nNgươi có thể giúp ta chế ngự nó không ?",
                        "Đồng ý", "Từ chối");
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player) && player.idMark.isBaseMenu() && select == 0) {
            if (this.mapId == DrMyuuService.LAB) {
                ChangeMapService.gI().changeMapBySpaceShip(player, 21 + Math.max(0, Math.min(2, player.gender)), -1, 250);
            } else {
                ChangeMapService.gI().changeMapBySpaceShip(player, DrMyuuService.LAB, -1, 300);
            }
        }
    }
}
