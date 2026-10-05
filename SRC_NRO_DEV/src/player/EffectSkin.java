package player;
import item.Item;
import mob.Mob;
import services.ItemService;
import player.Service.PlayerService;
import services.Service;
import player.Service.InventoryService;
import map.Service.MapService;
import utils.Util;
import java.util.ArrayList;
import java.util.List;
import lombok.Setter;
import services.EffectSkillService;
import skill.Skill;

public class EffectSkin {

    public static final String[] textOdo = new String[]{
        "Hôi quá, tránh xa ta ra", "Biến đi", "Trời ơi đồ ở dơ",
        "Thúi quá", "Mùi gì hôi quá"
    };

    private static final String[] textThoBulma = new String[]{
        "Wow, sexy quá"
    };

    private static final String[] textBuffSD = new String[]{
        "Wow! Sexy quá",
    };

 

    @Setter
    private Player player;

    public EffectSkin(Player player) {
        this.player = player;
        this.xHPKI = 1;
        this.xDame = 1;
    }

    public long lastTimeAttack;
    public long lastTimeVoHinh;
    private long lastTimeOdo;
    private long lastTimeltdb;
    private long lastTimeThoBulma;
    private long lastTimeMaPhongBa;
    private long lastTimeXenHutHpKi;

    public long lastTimeAddTimeTrainArmor;
    public long lastTimeSubTimeTrainArmor;

    public boolean isVoHinh;

    public long lastTimeXHPKI;
    public int xHPKI;

    public long lastTimeXDame;
    public int xDame;

    public long lastTimeUpdateCTHT;

    private long lastTimeTanHinh;
    private long lastTimeLamCham;
    private long lastTimeHoaDa;
    private long lastTimeHalloween;
    public long lastTimeXChuong;
    public boolean isXChuong;
    public boolean isXDame;
    private long lastTimeTokuda;

    public void update() {
        updateVoHinh();
        if (!this.player.isDie() && this.player.zone != null && !MapService.gI().isMapOffline(this.player.zone.map.mapId)) {
            updateOdo();
            updateThoBulma();
            updateMaPhongBa();
            updateXenHutXungQuanh();
            updateTanHinh();
            updateHoaDa();
            updateLamCham();
            updateXChuong();
        }
        if (!this.player.isBoss && !this.player.isPet && !player.isNewPet) {
            updateTrainArmor();
        }
        if (xHPKI != 1 && Util.canDoWithTime(lastTimeXHPKI, 1800000)) {
            xHPKI = 1;
            Service.gI().point(player);
        }
        if (xDame != 1 && Util.canDoWithTime(lastTimeXDame, 1800000)) {
            xDame = 1;
            Service.gI().point(player);
        }
        updateCTHaiTac();
    }

    private void updateCTHaiTac() {
        if (this.player.setClothes != null && this.player.setClothes.ctHaiTac != -1
                && this.player.zone != null
                && Util.canDoWithTime(lastTimeUpdateCTHT, 5000)) {
            int count = 0;
            int[] cts = new int[9];
            cts[this.player.setClothes.ctHaiTac - 618] = this.player.setClothes.ctHaiTac;
            List<Player> players = new ArrayList<>();
            players.add(player);
            try {
                for (Player pl : player.zone.getNotBosses()) {
                    if (!player.equals(pl) && pl.setClothes.ctHaiTac != -1 && Util.getDistance(player, pl) <= 300) {
                        cts[pl.setClothes.ctHaiTac - 618] = pl.setClothes.ctHaiTac;
                        players.add(pl);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            for (int i = 0; i < cts.length; i++) {
                if (cts[i] != 0) {
                    count++;
                }
            }
            for (Player pl : players) {
                Item ct = pl.inventory.itemsBody.get(5);
                if (ct.isNotNullItem() && ct.template.id >= 618 && ct.template.id <= 626) {
                    for (Item.ItemOption io : ct.itemOptions) {
                        if (io.optionTemplate.id == 147
                                || io.optionTemplate.id == 77
                                || io.optionTemplate.id == 103) {
                            io.param = count * 3;
                        }
                    }
                }
                if (!pl.isPet && !pl.isNewPet && Util.canDoWithTime(lastTimeUpdateCTHT, 5000)) {
                    InventoryService.gI().sendItemBody(pl);
                }
                pl.effectSkin.lastTimeUpdateCTHT = System.currentTimeMillis();
            }
        }
    }

    private void updateXenHutXungQuanh() {
        try {
            if (this.player.nPoint != null && (player.nPoint.hp < player.nPoint.hpMax || player.nPoint.mp < player.nPoint.mpMax)) {
                int param = this.player.nPoint.tlHutHpMpXQ;
                if (param > 0) {
                    if (!this.player.isDie() && Util.canDoWithTime(lastTimeXenHutHpKi, 5000)) {
                        int hpHut = 0;
                        int mpHut = 0;
                        List<Player> players = new ArrayList<>();
                        List<Player> playersMap = this.player.zone.getNotBosses();
                        for (Player pl : playersMap) {
                            if (!this.player.equals(pl) && !pl.isBoss && !pl.isDie()
                                    && Util.getDistance(this.player, pl) <= 200) {
                                players.add(pl);
                            }

                        }
                        for (Mob mob : this.player.zone.mobs) {
                            if (mob.point.gethp() > 1) {
                                if (Util.getDistance(this.player, mob) <= 200) {
                                    int subHp = mob.point.getHpFull() * param / 100;
                                    if (subHp >= mob.point.gethp()) {
                                        subHp = mob.point.gethp() - 1;
                                    }
                                    hpHut += subHp;
                                    mob.injured(null, subHp, false);
                                }
                            }
                        }
                        for (Player pl : players) {
                            int subHp = (int) ((long) pl.nPoint.hpMax * param / 100);
                            int subMp = (int) ((long) pl.nPoint.mpMax * param / 100);
                            if (subHp >= pl.nPoint.hp) {
                                subHp = pl.nPoint.hp - 1;
                            }
                            if (subMp >= pl.nPoint.mp) {
                                subMp = pl.nPoint.mp - 1;
                            }
                            hpHut += subHp;
                            mpHut += subMp;
                            PlayerService.gI().sendInfoHpMpMoney(pl);
                            Service.gI().Send_Info_NV(pl);
                            pl.injured(null, subHp, true, false);
                        }
                        this.player.nPoint.addHp(hpHut);
                        this.player.nPoint.addMp(mpHut);
                        PlayerService.gI().sendInfoHpMpMoney(this.player);
                        Service.gI().Send_Info_NV(this.player);
                        this.lastTimeXenHutHpKi = System.currentTimeMillis();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateOdo() {
        try {
            if (this.player.nPoint != null) {
                int param = this.player.nPoint.tlHpGiamODo;
                if (param > 0) {
                    if (Util.canDoWithTime(lastTimeOdo, 10000)) {
                        List<Player> playersMap = this.player.zone.getNotBosses();
                        for (int i = playersMap.size() - 1; i >= 0; i--) {
                            Player pl = playersMap.get(i);
                            if (pl != null && pl.nPoint != null && !this.player.equals(pl) && !pl.isBoss && !pl.isDie()
                                    && Util.getDistance(this.player, pl) <= 200) {
                                int subHp = (int) ((long) pl.nPoint.hpMax * param / 100);
                                if (subHp >= pl.nPoint.hp) {
                                    subHp = pl.nPoint.hp - 1;
                                }
                                Service.gI().chat(pl, textOdo[Util.nextInt(0, textOdo.length - 1)]);
                                PlayerService.gI().sendInfoHpMpMoney(pl);
                                Service.gI().Send_Info_NV(pl);
                                pl.injured(null, subHp, true, false);
                            }

                        }
                        this.lastTimeOdo = System.currentTimeMillis();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

  

    private void updateThoBulma() {
        try {
            if (this.player.nPoint != null && this.player.nPoint.tlSexyDame > 0) {
                if (Util.canDoWithTime(lastTimeThoBulma, 10000)) {

                    List<Player> playersMap = this.player.zone.getNotBosses();
                    for (int i = playersMap.size() - 1; i >= 0; i--) {
                        Player pl = playersMap.get(i);
                        if (pl != null && pl.nPoint != null && !this.player.equals(pl) && !pl.isBoss && !pl.isDie()
                                && Util.getDistance(this.player, pl) <= 120) {
                            if (player.nPoint.isThoBulma) {
                                Service.gI().chat(pl, textThoBulma[Util.nextInt(0, textThoBulma.length - 1)]);
                            } else {
                                Service.gI().chat(pl, textBuffSD[Util.nextInt(0, textBuffSD.length - 1)]);
                            }
                            EffectSkillService.gI().setDameBuff(player, 11000, player.nPoint.tlSexyDame);
                        }
                    }
                    this.lastTimeThoBulma = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateTanHinh() {
        try {
            if (this.player.nPoint != null && this.player.nPoint.isTanHinh) {
                if (Util.canDoWithTime(lastTimeTanHinh, 5000)) {
                    EffectSkillService.gI().setIsTanHinh(player, 1500);
                    this.lastTimeTanHinh = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateHoaDa() {
        try {
            if (this.player.nPoint != null && this.player.nPoint.isHoaDa) {
                if (Util.canDoWithTime(lastTimeHoaDa, 30000)) {
                    List<Player> playersMap = this.player.zone.getNotBosses();
                    for (int i = playersMap.size() - 1; i >= 0; i--) {
                        Player pl = playersMap.get(i);
                        if (pl != null && pl.nPoint != null && !this.player.equals(pl) && !pl.isBoss && !pl.isDie()
                                && Util.getDistance(this.player, pl) <= 200) {
                            EffectSkillService.gI().setIsStone(pl, 6000);
                        }

                    }
                    this.lastTimeHoaDa = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateLamCham() {
        try {
            if (this.player.nPoint != null && this.player.nPoint.isLamCham) {
                if (Util.canDoWithTime(lastTimeLamCham, 10000)) {

                    List<Player> playersMap = this.player.zone.getNotBosses();
                    for (int i = playersMap.size() - 1; i >= 0; i--) {
                        Player pl = playersMap.get(i);
                        if (pl != null && pl.nPoint != null && !this.player.equals(pl) && !pl.isBoss && !pl.isDie()
                                && Util.getDistance(this.player, pl) <= 200) {
                            Service.gI().chat(pl, "Nặng quá!");
                            EffectSkillService.gI().setIsLamCham(pl, 5000);
                        }

                    }
                    this.lastTimeLamCham = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateXChuong() {
        try {
            if (this.player.nPoint != null && this.player.nPoint.xChuong != 0) {
                if (Util.canDoWithTime(lastTimeXChuong, 60000)) {
                    this.isXChuong = true;
                    this.lastTimeXChuong = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateMaPhongBa() {
        try {
            if (this.player.effectSkill != null && this.player.effectSkill.isBinh && this.player.effectSkill.playerUseMafuba != null) {
                if (Util.canDoWithTime(lastTimeMaPhongBa, 500) && this.player.effectSkill.playerUseMafuba.playerSkill != null) {
                    double param = this.player.effectSkill.playerUseMafuba.playerSkill.getSkillbyId(Skill.MA_PHONG_BA).point * (this.player.effectSkill.typeBinh == 0 ? 1 : 2);
                    int subHp = (int) ((long) this.player.effectSkill.playerUseMafuba.nPoint.hpMax * param / 100);
                    if (subHp >= this.player.nPoint.hp) {
                        subHp = Math.abs(this.player.nPoint.hp - 100);
                    }
                    PlayerService.gI().sendInfoHpMpMoney(this.player);
                    Service.gI().Send_Info_NV(this.player);
                    this.player.injured(this.player.effectSkill.playerUseMafuba, subHp, true, false);
                    this.lastTimeMaPhongBa = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private void updateTrainArmor() {
        // Đồng bộ cờ đang mặc giáp với trạng thái thực tế ở ô body slot 6.
        // Nếu không sync, lúc vừa tháo giáp ra khỏi body thì cờ vẫn còn "đang mặc"
        // khiến giáp bị cộng phút ở nhánh dưới thay vì phải trừ phút.
        syncWearingTrainArmor();

        if (Util.canDoWithTime(lastTimeAddTimeTrainArmor, 60000) && !Util.canDoWithTime(lastTimeAttack, 30000)) {
            Item trainArmor = this.player.inventory.trainArmor;
            if (this.player.nPoint.wearingTrainArmor && trainArmor != null) {
                for (Item.ItemOption io : trainArmor.itemOptions) {
                    if (io.optionTemplate.id == 9) {
                        if (io.param < 1000) {
                            io.param++;
                            InventoryService.gI().sendItemBody(player);
                        }
                        break;
                    }
                }
            }
            this.lastTimeAddTimeTrainArmor = System.currentTimeMillis();
        }
        if (Util.canDoWithTime(lastTimeSubTimeTrainArmor, 60000)) {
            // Giáp luyện tập nằm trong hành trang hoặc rương đồ sẽ bị trừ phút mỗi phút.
            // Lưu ý: hành trang/rương có ô trống xen kẽ nên phải duyệt hết danh sách,
            // không được break sớm khi gặp ô trống, nếu không giáp sẽ không bao giờ bị trừ.
            boolean subTime = subTimeTrainArmor(this.player.inventory.itemsBag)
                    | subTimeTrainArmor(this.player.inventory.itemsBox);
            this.lastTimeSubTimeTrainArmor = System.currentTimeMillis();
            if (subTime) {
                InventoryService.gI().sendItemBags(player);
                Service.gI().point(this.player);
            }
        }
    }

/**
     * Đồng bộ lại trạng thái "đang mặc giáp luyện tập" dựa trên dữ liệu thật ở body slot 6.
     *
     * {@link player.NPoint#setDameTrainArmor()} chỉ chạy khi tính lại chỉ số, nên nếu người chơi
     * vừa tháo/mặc giáp thì cờ {@code wearingTrainArmor} và {@code inventory.trainArmor} có thể
     * đã cũ. Kiểm tra lại theo body để nhánh cộng/trừ phút luôn đúng với thực tế.
     */
    private void syncWearingTrainArmor() {
        if (this.player.nPoint == null) {
            return;
        }
        List<Item> itemsBody = this.player.inventory.itemsBody;
        if (itemsBody == null || itemsBody.size() < 7) {
            return;
        }
        Item gtl = itemsBody.get(6);
        boolean wearing = gtl != null && gtl.isNotNullItem() && ItemService.gI().isTrainArmor(gtl);
        this.player.nPoint.wearingTrainArmor = wearing;
        if (wearing) {
            this.player.inventory.trainArmor = gtl;
        }
    }

    /**
     * Trừ 1 phút luyện tập cho mọi giáp luyện tập nằm trong danh sách.
     *
     * @return true nếu có ít nhất một giáp bị trừ phút (để mới cần đồng bộ lại client)
     */
    private boolean subTimeTrainArmor(List<Item> items) {
        boolean changed = false;
        if (items == null) {
            return false;
        }
        for (Item item : items) {
            if (!item.isNotNullItem()) {
                continue; // ô trống: bỏ qua ô này, tiếp tục duyệt các ô phía sau
            }
            if (!ItemService.gI().isTrainArmor(item)) {
                continue;
            }
            for (Item.ItemOption io : item.itemOptions) {
                if (io.optionTemplate.id == 9) {
                    if (io.param > 0) {
                        io.param--;
                        changed = true;
                    }
                    break;
                }
            }
        }
        return changed;
    }

    private void updateVoHinh() {
        if (this.player.nPoint != null && this.player.nPoint.wearingVoHinh) {
            if (Util.canDoWithTime(lastTimeAttack, 5000)) {
                isVoHinh = Util.canDoWithTime(lastTimeVoHinh, 5000);
            } else {
                isVoHinh = false;
            }
        } else {
            isVoHinh = false;
        }
    }

    public void dispose() {
        this.player = null;
    }
}
