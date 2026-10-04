package mob;
import map.Zone;
import player.Player;
import utils.SkillUtil;
import services.Service;
import utils.Util;
import network.Message;

public final class MobMe extends Mob {

    private Player player;
    private final long lastTimeSpawn;
    private final int timeSurvive;

    public MobMe(Player player) {
        super();
        this.player = player;
        this.id = (int) player.id;
        int level = player.playerSkill.getSkillbyId(12).point;
        this.tempId = SkillUtil.getTempMobMe(level);
        this.point.maxHp = (int) Math.min(SkillUtil.getHPMobMe(player.nPoint.hpMax, level), 2_000_000_000);
        this.point.dame = (int) Math.min(SkillUtil.getHPMobMe(player.nPoint.getDameAttack(false), level), 2_000_000_000);
        this.point.hp = this.point.maxHp;
        this.zone = player.zone;
        this.lastTimeSpawn = System.currentTimeMillis();
        this.timeSurvive = SkillUtil.getTimeSurviveMobMe(level);
        spawn();
    }

    @Override
    public void update() {
        if (Util.canDoWithTime(lastTimeSpawn, timeSurvive) && this.player.setClothes.pikkoroDaimao != 5) {
            this.mobMeDie();
            this.dispose();
        }
    }

    public void attack(Player pl, Mob mob, boolean miss) {
        Message msg;
        try {
            if (pl != null) {
                int dame = !miss ? this.point.dame : 0;
                if ((pl.nPoint.hp > dame && pl.nPoint.hp > pl.nPoint.hpMax * 0.05) || this.player.setClothes.pikkoroDaimao == 5) {
                    int dameHit = pl.injured(this.player, dame, true, true);
                    msg = new Message(-95);
                    msg.writer().writeByte(2);
                    msg.writer().writeInt(this.id);
                    msg.writer().writeInt((int) pl.id);
                    msg.writer().writeInt(dameHit);
                    msg.writer().writeInt(pl.nPoint.hp);
                    Service.gI().sendMessAllPlayerInMap(this.player, msg);
                    msg.cleanup();
                }
            }

            if (mob != null && !mob.isDie()) {
                long damage = Math.min((long) this.point.dame, mob.point.gethp());
                // Sát thương phải đi qua Mob.injured() để chạy đủ luồng xử lý:
                // quái chết + rơi đồ + cộng tiềm năng/sức mạnh + TÍNH NHIỆM VỤ cho sư phụ.
                // Trước đây trừ HP trực tiếp nên nhiệm vụ không được tính cho sư phụ.
                mob.injured(this.player, damage, true);

                // Hiệu ứng "trứng đánh trúng quái" gửi cho client
                msg = new Message(-95);
                msg.writer().writeByte(3);
                msg.writer().writeInt(this.id);
                msg.writer().writeInt((int) mob.id);
                msg.writer().writeInt(mob.point.gethp());
                msg.writer().writeInt((int) damage);
                Service.gI().sendMessAllPlayerInMap(this.player, msg);
                msg.cleanup();
            }
        } catch (Exception e) {
        }
    }

    public void spawn() {
        Message msg;
        try {
            msg = new Message(-95);
            msg.writer().writeByte(0);//type
            msg.writer().writeInt((int) player.id);
            msg.writer().writeShort(this.tempId);
            msg.writer().writeInt(this.point.hp);// hp mob
            Service.gI().sendMessAllPlayerInMap(this.player, msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    public void goToMap(Zone zone) {
        if (zone != null) {
            this.removeMobInMap();
            this.zone = zone;
        }
    }

    private void removeMobInMap() {
        Message msg;
        try {
            msg = new Message(-95);
            msg.writer().writeByte(7);//type
            msg.writer().writeInt((int) player.id);
            Service.gI().sendMessAllPlayerInMap(this.player, msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    public void mobMeDie() {
        Message msg;
        try {
            msg = new Message(-95);
            msg.writer().writeByte(6);//type
            msg.writer().writeInt((int) player.id);
            Service.gI().sendMessAllPlayerInMap(this.player, msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    @Override
    public synchronized void injured(Player plAtt, long damage, boolean dieWhenHpFull) {
        Message msg;
        try {
            if (damage > point.maxHp / 20) {
                damage = point.maxHp / 20;
            }
            point.hp -= damage;
            msg = new Message(-95);
            msg.writer().writeByte(5);//type
            msg.writer().writeInt((int) plAtt.id);
            msg.writer().writeByte(plAtt.playerSkill.skillSelect.template.id); // id skill
            msg.writer().writeInt(id); //mob id
            msg.writer().writeInt((int) damage);
            msg.writer().writeInt(point.hp);
            Service.gI().sendMessAllPlayerInMap(this.player, msg);
            msg.cleanup();
            if (isDie()) {
                mobMeDie();
                dispose();
            }
        } catch (Exception e) {
        }
    }

    public void dispose() {
        player.mobMe = null;
        this.player = null;
    }
}
