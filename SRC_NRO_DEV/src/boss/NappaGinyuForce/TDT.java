package boss.NappaGinyuForce;
import boss.Boss;
import boss.BossID;
import consts.BossStatus;
import boss.BossesData;
import java.util.Random;
import map.ItemMap;
import player.Player;
import services.EffectSkillService;
import services.Service;
import services.TaskService;
import utils.Logger;
import utils.Util;

public class TDT extends Boss {

    private long st;

    private long lastBodyChangeTime;

    public TDT() throws Exception {
        super(BossID.TIEU_DOI_TRUONG, false, true, BossesData.TIEU_DOI_TRUONG);
    }

    private void bodyChangePlayerInMap() {
        if (this.zone != null) {
            for (Player pl : this.zone.getPlayers()) {
                if (Util.isTrue(5, 10) && pl.effectSkill != null && !pl.effectSkill.isBodyChangeTechnique) {
                    EffectSkillService.gI().setIsBodyChangeTechnique(pl);
                }
            }
        }
    }

    @Override
    public void moveTo(int x, int y) {
        if (this.currentLevel == 1) {
            return;
        }
        super.moveTo(x, y);
    }
@Override
public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
    if (this.isDie()) return 0;

    if (plAtt != null && plAtt.isPl()) {
        int hour = java.time.LocalTime.now().getHour();
        boolean isLimitTime =
                (hour >= 12 && hour < 14) ||
                (hour >= 19 && hour < 22);

        if (isLimitTime) {
            boolean isTask20 = plAtt.playerTask != null
                    && plAtt.playerTask.taskMain != null
                    && plAtt.playerTask.taskMain.id == 20;

            if (!isTask20) {
                Service.gI().sendThongBao(
                        plAtt,
                        "Trong khung giờ này, chỉ người đang làm nhiệm vụ 20 mới gây sát thương được boss!"
                );
                return 0;
            }
        }
    }

    // Né đòn 1%
    if (Util.isTrue(10, 1000)) {
        this.chat("Xí hụt");
        return 0;
    }

    // ✅ GIAO LẠI CORE XỬ LÝ CHẾT
    return super.injured(plAtt, damage, piercing, isMobAttack);
}



    @Override
    public void reward(Player plKill) {
        if (this.zone == null || this.zone.map == null) {
            return;
        }
        try {
            rewardItem(plKill);
        } catch (Exception e) {
            Logger.logException(TDT.class, e, "Lỗi rơi đồ khi hạ boss " + this.name);
        }
    }

    /** Rơi đồ + cộng nhiệm vụ. Tách riêng để lỗi 1 phần không làm hỏng phần còn lại. */
    private void rewardItem(Player plKill) {
    TaskService.gI().checkDoneTaskKillBoss(plKill, this);

    // Thả vàng mặc định
    Service.gI().dropItemMap(
        this.zone,
        new ItemMap(
            this.zone,
            190,
            Util.nextInt(20000, 30001),
            this.location.x,
            this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24),
            -1 // ai cũng nhặt được
        )
    );

    int rand = Util.nextInt(100); // 0 -> 99

    // ===== 50% rơi tất cả 381-384 (ai cũng nhặt, rải đều) =====
    if (rand < 5) {
        // ===== 5% rơi item 16 (chỉ người hạ boss) =====
        Service.gI().dropItemMap(
            this.zone,
            new ItemMap(
                this.zone,
                16,
                1,
                this.location.x,
                this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24),
                plKill.id
            )
        );
    }
    else if (rand < 15) {
        // ===== 15% rơi item 17 (chỉ người hạ boss) =====
        Service.gI().dropItemMap(
            this.zone,
            new ItemMap(
                this.zone,
                17,
                1,
                this.location.x,
                this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24),
                plKill.id
            )
        );
    }
    else if (rand < 25) {
        // ===== 25% rơi item 18 (chỉ người hạ boss) =====
        Service.gI().dropItemMap(
            this.zone,
            new ItemMap(
                this.zone,
                18,
                1,
                this.location.x,
                this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24),
                plKill.id
            )
        );
    }
    // ===== 25% rơi item 19 (chỉ người hạ boss) =====
    else if (rand < 75) {
        Service.gI().dropItemMap(
            this.zone,
            new ItemMap(
                this.zone,
                19,
                1,
                this.location.x,
                this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24),
                plKill.id
            )
        );
    }
    // ===== 25% rơi item 20 (chỉ người hạ boss) =====
    else {
        Service.gI().dropItemMap(
            this.zone,
            new ItemMap(
                this.zone,
                20,
                1,
                this.location.x,
                this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24),
                plKill.id
            )
        );
    }
}

    @Override
    protected void notifyJoinMap() {
        if (this.currentLevel == 1) {
            return;
        }
        super.notifyJoinMap();
    }

    @Override
    public void attack() {
        if (Util.canDoWithTime(lastBodyChangeTime, 10000)) {
            bodyChangePlayerInMap();
            this.chat("Úm ba la xì bùa");
            this.lastBodyChangeTime = System.currentTimeMillis();
        }
        super.attack();
    }

    @Override
    public void joinMap() {
        super.joinMap();
        st = System.currentTimeMillis();
    }

    @Override
    public void doneChatS() {
        this.changeStatus(BossStatus.AFK);
    }

    @Override
    public void autoLeaveMap() {
        if (Util.canDoWithTime(st, 90000000)) {
            this.leaveMapNew();
        }
        if (this.zone != null && this.zone.getNumOfPlayers() > 0) {
            st = System.currentTimeMillis();
        }
    }
}
