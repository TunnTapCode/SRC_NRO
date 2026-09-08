package managers;
import database.DatabaseManager;
import database.DatabaseResultSet;
import player.GiftCodeSystem;
import player.Player;
import map.Service.NpcService;
import services.Service;
import java.util.ArrayList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import player.Service.InventoryService;

public class GiftCodeManager {

    public String name;
    public final ArrayList<GiftCodeSystem> listGiftCode = new ArrayList<>();

    private static GiftCodeManager instance;

    public static GiftCodeManager gI() {
        if (instance == null) {
            instance = new GiftCodeManager();
        }
        return instance;
    }

    public GiftCodeSystem checkUseGiftCode(Player player, String code) {
        for (GiftCodeSystem giftCode : listGiftCode) {
            if (giftCode.code.equals(code)) {
                if (giftCode.countLeft <= 0) {
                    Service.gI().sendThongBaoOK(player, "Giftcode đã hết");
                    return null;
                } else if (giftCode.isUsedGiftCode(player)) {
                    Service.gI().sendThongBaoOK(player, "Bạn đã nhập giftcode này rồi");
                    return null;
                }
                if (InventoryService.gI().getCountEmptyBag(player) < giftCode.detail.size()) {
                    Service.gI().sendThongBaoOK(player, "Cần tối thiểu " + giftCode.detail.size() + " ô hành trang trống");
                    return null;
                }
                giftCode.countLeft -= 1;
                player.giftCode.add(code);
                updateGiftCode(giftCode);
                return giftCode;
            }
        }
        return null;
    }

    public void updateGiftCode(GiftCodeSystem giftcode) {
        try {
            DatabaseManager.executeUpdate("update giftcode set count_left = ? where id = ?", giftcode.countLeft, giftcode.id);
        } catch (Exception e) {
        }
    }

    public boolean createGiftCode(String code, int count, int[] itemIds, int[] quantities) throws Exception {
        if (itemIds.length == 0 || itemIds.length != quantities.length) {
            return false;
        }
        for (GiftCodeSystem giftCode : listGiftCode) {
            if (giftCode.code.equalsIgnoreCase(code)) {
                return false;
            }
        }

        JSONArray detail = new JSONArray();
        for (int i = 0; i < itemIds.length; i++) {
            JSONObject item = new JSONObject();
            item.put("id", itemIds[i]);
            item.put("quantity", quantities[i]);
            item.put("options", new JSONArray());
            detail.add(item);
        }

        DatabaseManager.executeUpdate(
                "insert into giftcode (code, count_left, detail) values (?, ?, ?)",
                code, count, detail.toJSONString());

        DatabaseResultSet rs = DatabaseManager.executeQuery(
                "select * from giftcode where code = ? order by id desc limit 1", code);
        if (!rs.next()) {
            return false;
        }

        GiftCodeSystem giftCode = new GiftCodeSystem();
        giftCode.id = rs.getInt("id");
        giftCode.code = rs.getString("code");
        giftCode.countLeft = rs.getInt("count_left");
        giftCode.datecreate = rs.getTimestamp("datecreate");
        giftCode.dateexpired = rs.getTimestamp("expired");
        for (int i = 0; i < itemIds.length; i++) {
            giftCode.detail.put(itemIds[i], quantities[i]);
            giftCode.option.put(itemIds[i], new ArrayList<>());
        }
        listGiftCode.add(giftCode);
        return true;
    }

    public void checkInfomationGiftCode(Player p) {
        StringBuilder sb = new StringBuilder();
        for (GiftCodeSystem giftCode : listGiftCode) {
            sb.append("Code: ").append(giftCode.code).append(", Số lượng còn lại: ").append(giftCode.countLeft).append("\b")
                    .append("Ngày tạo: ")
                    .append(giftCode.datecreate).append(", Ngày hết hạn: ").append(giftCode.dateexpired)
                    .append("\n");
        }
        sb.deleteCharAt(sb.length() - 1);
        NpcService.gI().createTutorial(p, 5073, sb.toString());
    }

}