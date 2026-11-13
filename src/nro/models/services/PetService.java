package nro.models.services;

import nro.models.consts.ConstPlayer;
import nro.models.player.NewPet;
import nro.models.player.NPoint;
import nro.models.player.Pet;
import nro.models.player.Player;
import nro.models.map.service.ChangeMapService;
import nro.models.skill.Skill;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;

/**
 *
 * @author Việt
 *
 */
public class PetService {

    private static PetService instance;

    public static PetService gI() {
        if (instance == null) {
            instance = new PetService();
        }
        return instance;
    }

    public void createNormalPet(Player player, int gender, byte... limitPower) {
        new Thread(() -> {
            try {
                createNewPet(player, false, false, false, false, (byte) gender);
                if (limitPower != null && limitPower.length == 1) {
                    player.pet.nPoint.limitPower = limitPower[0];
                }
                Thread.sleep(1000);
                Service.gI().chatJustForMe(player, player.pet, "Xin hãy thu nhận tao làm đệ tử");
            } catch (Exception e) {
            }
        }).start();
    }

    public void createNormalPet(Player player, byte... limitPower) {
        new Thread(() -> {
            try {
                createNewPet(player, false, false, false, false);
                if (limitPower != null && limitPower.length == 1) {
                    player.pet.nPoint.limitPower = limitPower[0];
                }
                Thread.sleep(1000);
                Service.gI().chatJustForMe(player, player.pet, "Xin hãy thu nhận tao làm đệ tử");
            } catch (Exception e) {
            }
        }).start();
    }

    public void createMabuPet(Player player, int gender, byte... limitPower) {
        new Thread(() -> {
            try {
                createNewPet(player, true, false, false, false, (byte) gender);
                if (limitPower != null && limitPower.length == 1) {
                    player.pet.nPoint.limitPower = limitPower[0];
                }
                Thread.sleep(1000);
                Service.gI().chatJustForMe(player, player.pet, "Oa oa oa...");
            } catch (Exception e) {
            }
        }).start();
    }

    public void createMabuPet(Player player, byte... limitPower) {
        new Thread(() -> {
            try {
                createNewPet(player, true, false, false, false);
                if (limitPower != null && limitPower.length == 1) {
                    player.pet.nPoint.limitPower = limitPower[0];
                }
                Thread.sleep(1000);
                Service.gI().chatJustForMe(player, player.pet, "Oa oa oa...");
            } catch (Exception e) {
            }
        }).start();
    }

    public void createUubPet(Player player, byte... limitPower) {
        new Thread(() -> {
            try {
                createNewPet(player, false, true, false, false, (byte) player.gender);
                if (limitPower != null && limitPower.length == 1) {
                    player.pet.nPoint.limitPower = limitPower[0];
                }
                Thread.sleep(1000);
                Service.gI().chatJustForMe(player, player.pet, "Xin hãy thu nhận tao làm đệ tử");
            } catch (Exception e) {
            }
        }).start();
    }
    
    public void createJirenPet(Player player, byte... limitPower) {
        new Thread(() -> {
            try {
                createNewPet(player, false, false, false,true, (byte) player.gender);
                if (limitPower != null && limitPower.length == 1) {
                    player.pet.nPoint.limitPower = limitPower[0];
                }
                Thread.sleep(1000);
                Service.gI().chatJustForMe(player, player.pet, "Xin hãy thu nhận tao làm đệ tử");
            } catch (Exception e) {
            }
        }).start();
    }

    public void createKidBeerPet(Player player, byte... limitPower) {
        new Thread(() -> {
            try {
                createNewPet(player, false, false, true, false, (byte) player.gender);
                if (limitPower != null && limitPower.length == 1) {
                    player.pet.nPoint.limitPower = limitPower[0];
                }
                Thread.sleep(1000);
                Service.gI().chatJustForMe(player, player.pet, "Hãy hợp tác với ta, Kakarot!");
            } catch (Exception e) {
            }
        }).start();
    }

    /** Chiêu 1-4 (cấp 1) của đệ tử 2 theo hành tinh: Trái Đất, Namếc, Xayda; chiêu 5 mở ở 60 tỷ sức mạnh (Pet.updatePower). */
    static final byte[][] PET2_SKILLS = {
        {Skill.DRAGON, Skill.KAMEJOKO, Skill.TAI_TAO_NANG_LUONG, Skill.DE_TRUNG},
        {Skill.DEMON, Skill.ANTOMIC, Skill.TAI_TAO_NANG_LUONG, Skill.KHIEN_NANG_LUONG},
        {Skill.GALICK, Skill.MASENKO, Skill.KAIOKEN, Skill.DE_TRUNG}};
    /** Hình dạng theo hành tinh: Trái Đất Uub (2), Namếc Jiren (4), Xayda Kid Beer (3). */
    static final byte[] PET2_TYPE = {2, 4, 3};
    /** Chỉ số gốc tối đa của đệ tử 2 (= mốc cuối của đệ tử 1 +30%). */
    public static final int PET2_HPMP_MAX = 747_000, PET2_DAME_MAX = 33_800;

    public static boolean isPet2(Pet pet) {
        return pet != null && (pet.typePet == 2 || pet.typePet == 3 || pet.typePet == 4);
    }

    /** Thay đệ tử hiện tại bằng đệ tử 2 hành tinh ngẫu nhiên (Bình hút năng lượng / Đổi đệ tử 2): 40 tỷ, giữ sức mạnh nếu đã là đệ tử 2 cao hơn. */
    public void createPet2(Player player) {
        long power = 40_000_000_000L;
        byte limitPower = 0;
        long[] base = null; // hp, mp, dame, def, crit gốc của đệ mới
        if (player.pet != null) {
            NPoint o = player.pet.nPoint;
            limitPower = o.limitPower;
            if (isPet2(player.pet)) { // đổi lại đệ tử 2: giữ sức mạnh và chỉ số gốc
                power = Math.max(power, o.power);
                base = new long[]{o.hpg, o.mpg, o.dameg, o.defg, o.critg};
            } else { // từ đệ tử 1: chỉ số gốc +30%, tối đa HP/KI 747k, sức đánh 33800
                base = new long[]{Math.min(PET2_HPMP_MAX, o.hpg * 13 / 10), Math.min(PET2_HPMP_MAX, o.mpg * 13 / 10),
                    Math.min(PET2_DAME_MAX, o.dameg * 13 / 10), o.defg * 13 / 10, o.critg * 13 / 10};
            }
            if (player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
                player.pet.unFusion();
            }
            ChangeMapService.gI().exitMap(player.pet);
            player.pet.dispose();
            player.pet = null;
        }
        byte gender = (byte) Util.nextInt(0, 2);
        byte type = PET2_TYPE[gender];
        createNewPet(player, false, type == 2, type == 3, type == 4, gender);
        Pet pet = player.pet;
        pet.nPoint.power = power;
        pet.nPoint.limitPower = limitPower;
        if (base != null) {
            pet.nPoint.hpg = base[0];
            pet.nPoint.mpg = base[1];
            pet.nPoint.dameg = base[2];
            pet.nPoint.defg = base[3];
            pet.nPoint.critg = (int) base[4];
        }
        pet.playerSkill.skills.clear();
        for (byte id : PET2_SKILLS[gender]) {
            pet.playerSkill.skills.add(SkillUtil.createSkill(id, 1));
        }
        for (int i = 0; i < 3; i++) {
            pet.playerSkill.skills.add(SkillUtil.createEmptySkill());
        }
        pet.nPoint.calPoint();
        pet.nPoint.setFullHpMp();
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                Service.gI().chatJustForMe(player, player.pet, "Xin hãy thu nhận con làm đệ tử");
            } catch (Exception e) {
            }
        }).start();
    }

    /** Ảnh mặt đệ tử 2 (khung đầu tiên của part đầu Uub / Kid Beer / Jiren) cho hiệu ứng mở như Capsule kì bí. */
    public static short pet2HeadIcon(Pet pet) {
        return (short) (pet.typePet == 3 ? 12793 : pet.typePet == 4 ? 8063 : 11625);
    }

    public void changeNormalPet(Player player, int gender) {
        byte limitPower = player.pet.nPoint.limitPower;
        if (player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            player.pet.unFusion();
        }
        ChangeMapService.gI().exitMap(player.pet);
        player.pet.dispose();
        player.pet = null;
        createNormalPet(player, gender, limitPower);
    }

    public void changeNormalPet(Player player) {
        byte limitPower = player.pet.nPoint.limitPower;
        if (player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            player.pet.unFusion();
        }
        ChangeMapService.gI().exitMap(player.pet);
        player.pet.dispose();
        player.pet = null;
        createNormalPet(player, limitPower);
    }

    public void changeMabuPet(Player player) {
        byte limitPower = player.pet.nPoint.limitPower;
        if (player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            player.pet.unFusion();
        }
        ChangeMapService.gI().exitMap(player.pet);
        player.pet.dispose();
        player.pet = null;
        createMabuPet(player, limitPower);
    }

    public void changeUubPet(Player player) {
        byte limitPower = player.pet.nPoint.limitPower;
        if (player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            player.pet.unFusion();
        }
        ChangeMapService.gI().exitMap(player.pet);
        player.pet.dispose();
        player.pet = null;
        createUubPet(player, limitPower);
    }

    public void changeKidBeerPet(Player player) {
        byte limitPower = player.pet.nPoint.limitPower;
        if (player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            player.pet.unFusion();
        }
        ChangeMapService.gI().exitMap(player.pet);
        player.pet.dispose();
        player.pet = null;
        createKidBeerPet(player, limitPower);
    }
    
    public void changeJirenPet(Player player) {
        byte limitPower = player.pet.nPoint.limitPower;
        if (player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            player.pet.unFusion();
        }
        ChangeMapService.gI().exitMap(player.pet);
        player.pet.dispose();
        player.pet = null;
        createJirenPet(player, limitPower);
    }

    public void changeMabuPet(Player player, int gender) {
        byte limitPower = player.pet.nPoint.limitPower;
        if (player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            player.pet.unFusion();
        }
        ChangeMapService.gI().exitMap(player.pet);
        player.pet.dispose();
        player.pet = null;
        createMabuPet(player, gender, limitPower);
    }

    public void changeNamePet(Player player, String name) {
        try {
            if (!InventoryService.gI().isExistItemBag(player, 400)) {
                Service.gI().sendThongBao(player, "Bạn cần thẻ đặt tên đệ tử, mua tại Santa");
                return;
            } else if (Util.haveSpecialCharacter(name)) {
                Service.gI().sendThongBao(player, "Tên không được chứa ký tự đặc biệt");
                return;
            } else if (name.length() > 10) {
                Service.gI().sendThongBao(player, "Tên quá dài");
                return;
            }
            ChangeMapService.gI().exitMap(player.pet);
            player.pet.name = "$" + name.toLowerCase().trim();
            InventoryService.gI().subQuantityItemsBag(player, InventoryService.gI().findItemBag(player, 400), 1);
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    Service.gI().chatJustForMe(player, player.pet, "Cảm ơn sư phụ đã đặt cho con tên " + name);
                } catch (Exception e) {
                }
            }).start();
        } catch (Exception ex) {

        }
    }

    private int[] getDataPetNormal() {
        int[] petData = new int[5];
        petData[0] = Util.nextInt(40, 105) * 20; //hp
        petData[1] = Util.nextInt(40, 105) * 20; //mp
        petData[2] = Util.nextInt(20, 45); //dame
        petData[3] = Util.nextInt(9, 50); //def
        petData[4] = Util.nextInt(0, 2); //crit
        return petData;
    }

    private int[] getDataPetMabu() {
        int[] petData = new int[5];
        petData[0] = Util.nextInt(40, 105) * 20; //hp
        petData[1] = Util.nextInt(40, 105) * 20; //mp
        petData[2] = Util.nextInt(50, 120); //dame
        petData[3] = Util.nextInt(9, 50); //def
        petData[4] = Util.nextInt(0, 2); //crit
        return petData;
    }

    private int[] getDataPetUub() {
        int[] petData = new int[5];
        petData[0] = 650_000; // hp (đệ tử 2 theo video: HP/KI 650000, sức đánh 31200, giáp 2204, chí mạng 6) // hp
        petData[1] = 650_000; // mp // mp
        petData[2] = 31_200; // dame  // dame
        petData[3] = 551; // def gốc (NPoint: giáp = gốc x4 = 2204) //def
        petData[4] = 6; // crit //crit
        return petData;
    }

    private int[] getDataPetKidBeer() {
        int[] petData = new int[5];
        petData[0] = 650_000; // hp (đệ tử 2 theo video: HP/KI 650000, sức đánh 31200, giáp 2204, chí mạng 6) // hp
        petData[1] = 650_000; // mp // mp
        petData[2] = 31_200; // dame  // dame
        petData[3] = 551; // def gốc (NPoint: giáp = gốc x4 = 2204) //def
        petData[4] = 6; // crit //crit
        return petData;
    }
    
    private int[] getDataPetJiren() {
        int[] petData = new int[5];
        petData[0] = 650_000; // hp (đệ tử 2 theo video: HP/KI 650000, sức đánh 31200, giáp 2204, chí mạng 6) // hp
        petData[1] = 650_000; // mp // mp
        petData[2] = 31_200; // dame  // dame
        petData[3] = 551; // def gốc (NPoint: giáp = gốc x4 = 2204) //def
        petData[4] = 6; // crit //crit
        return petData;
    }

    private void createNewPet(Player player, boolean isMabu, boolean isUub, boolean isKidBeer, boolean isJiren, byte... gender) {
        int[] data;

        if (isMabu) {
            data = getDataPetMabu();
        } else if (isUub) {
            data = getDataPetUub();
        } else if (isKidBeer) {
            data = getDataPetKidBeer();
        } else if (isJiren) {
            data = getDataPetJiren();
        } else {
            data = getDataPetNormal();
        }

        Pet pet = new Pet(player);

        pet.name = "$" + (isMabu ? "Mabư" : isUub ? "Uub" : isKidBeer ? "Kid Beer" : isJiren ? "Kid Jiren" : "Đệ tử");

        pet.gender = (gender != null && gender.length != 0) ? gender[0] : (byte) Util.nextInt(0, 2);

        pet.id = player.isPl() ? -player.id : -Math.abs(player.id) - 100000;

        pet.nPoint.power = isUub ? 40000000000L : isMabu ? 1500000L : isKidBeer ? 40000000000L : isJiren ? 40000000000L : 2000L;

        pet.typePet = (byte) (isMabu ? 1 : isUub ? 2 : isKidBeer ? 3 : isJiren ? 4 : 0);

        pet.nPoint.stamina = 1000;
        pet.nPoint.maxStamina = 1000;
        pet.nPoint.hpg = data[0];
        pet.nPoint.mpg = data[1];
        pet.nPoint.dameg = data[2];
        pet.nPoint.defg = data[3];
        pet.nPoint.critg = data[4];

        int itemBodySize = 7;
        if (pet.typePet == 2 || pet.typePet == 3 || pet.typePet == 4) {
            itemBodySize = 9;
        }
        for (int i = 0; i < itemBodySize; i++) {
            pet.inventory.itemsBody.add(ItemService.gI().createItemNull());
        }

        pet.playerSkill.skills.add(SkillUtil.createSkill(Util.nextInt(0, 2) * 2, 1));
        for (int i = 0; i < 6; i++) {
            pet.playerSkill.skills.add(SkillUtil.createEmptySkill());
        }

        pet.nPoint.setFullHpMp();
        player.pet = pet;
    }

    public static void Pet2(Player pl, int h, int b, int l) {
        if (pl.newPet != null) {
            pl.newPet.dispose();
        }
        pl.newPet = new NewPet(pl, (short) h, (short) b, (short) l);
        pl.newPet.name = "$";
        pl.newPet.gender = pl.gender;
        pl.newPet.nPoint.tiemNang = 1;
        pl.newPet.nPoint.power = 1;
        pl.newPet.nPoint.limitPower = 1;
        pl.newPet.nPoint.hpg = 500000000;
        pl.newPet.nPoint.mpg = 500000000;
        pl.newPet.nPoint.hp = 500000000;
        pl.newPet.nPoint.mp = 500000000;
        pl.newPet.nPoint.dameg = 1;
        pl.newPet.nPoint.defg = 1;
        pl.newPet.nPoint.critg = 1;
        pl.newPet.nPoint.stamina = 1;
        pl.newPet.nPoint.setBasePoint();
        pl.newPet.nPoint.setFullHpMp();
    }
}
