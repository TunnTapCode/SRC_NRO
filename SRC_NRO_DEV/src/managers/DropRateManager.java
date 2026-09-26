package managers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import database.DatabaseManager;
import server.Manager;
import utils.Logger;
import utils.Util;

/**
 * Quản lý TOÀN BỘ tỉ lệ rơi đồ của quái (Mob.getItemMobReward).
 *
 * - Mỗi tỉ lệ là 1 Rule (key, nhóm, nhãn, mục tiêu, % mặc định).
 * - Giá trị chỉnh được từ Admin > Vận hành > Tỉ Lệ Rơi Đồ (GET/PUT /api/drop-rates).
 * - Tỉ lệ được lưu vào DropRate.properties nên giữ nguyên sau khi restart server.
 * - Mob gọi {@link #roll(String)} để quyết định rơi hay không.
 * - Hệ số nhân chung dùng chung với Manager.RATE_DROP_ITEM (100 = giữ nguyên).
 */
public final class DropRateManager {

    public static final String CONFIG_FILE = "DropRate.properties";

    /** Mẫu số chung của xúc xắc = 1e9 ⇒ độ chính xác tới 0.0000001% */
    private static final long ROLL_TOTAL = 1_000_000_000L;
    private static final double PERCENT_TO_ROLL = ROLL_TOTAL / 100.0;

    public static class Rule {

        public final String key;
        public final String group;
        public final String label;
        public final String target;
        public final int[] itemIds;
        public final double defaultPercent;
        public double percent;

        Rule(String key, String group, String label, String target, int[] itemIds, double defaultPercent) {
            this.key = key;
            this.group = group;
            this.label = label;
            this.target = target;
            this.itemIds = itemIds;
            this.defaultPercent = defaultPercent;
            this.percent = defaultPercent;
        }
    }

    private static final List<Rule> RULES = new ArrayList<>();
    private static final Map<String, Rule> INDEX = new LinkedHashMap<>();
    private static boolean loaded = false;

    private DropRateManager() {
    }

    private static void add(String key, String group, String label, String target, int[] itemIds,
            double defaultPercent) {
        Rule rule = new Rule(key, group, label, target, itemIds, defaultPercent);
        RULES.add(rule);
        INDEX.put(key, rule);
    }

    /** Khai báo đầy đủ các tỉ lệ rơi đồ đang dùng trong Mob.getItemMobReward */
    private static void initRules() {
        if (!RULES.isEmpty()) {
            return;
        }
        int[] GOLD_LOW = { 76, 188, 189 };
        int[] GOLD_MID = { 188, 189, 190 };
        int[] GOLD_COLD = { 189, 190 };
        int[] STONES = { 220, 221, 222, 223, 224 };
        int[] STARS = { 441, 442, 443 };
        int[] SET_ITEMS = { 233, 241, 237, 245, 253, 249, 265, 261, 257, 277, 273, 269, 281 };

        // ── Vàng theo bản đồ ──
        add("gold_map_3planets", "Vàng theo bản đồ", "Map 3 hành tinh", "Mọi quái", GOLD_LOW, 10);
        add("gold_map_nappa", "Vàng theo bản đồ", "Map Nappa", "Mọi quái", GOLD_MID, 10);
        add("gold_map_cold", "Vàng theo bản đồ", "Map Cold", "Mọi quái", GOLD_COLD, 10);
        add("gold_map_tuonglai", "Vàng theo bản đồ", "Map Tương lai", "Mọi quái", GOLD_MID, 10);
        add("gold_map_phoban", "Vàng theo bản đồ", "Map Phó bản", "Mọi quái", GOLD_MID, 1);

        // ── Vật phẩm dùng chung (mọi bản đồ) ──
        add("item_220_224_any_map", "Vật phẩm dùng chung", "Mọi bản đồ", "Ngẫu nhiên 1 trong 5", STONES, 0.5);
        add("item_18_any_map", "Vật phẩm dùng chung", "Mọi bản đồ", "", new int[] { 18 }, 0.25);
        add("item_19_20_any_map", "Vật phẩm dùng chung", "Mọi bản đồ", "Ngẫu nhiên 1 trong 2", new int[] { 19, 20 }, 0.4);
        add("gem_77_any_map", "Vật phẩm dùng chung", "Mọi bản đồ", "Hiếm", new int[] { 77 }, 0.0001);

        // ── Vật phẩm theo bản đồ ──
        add("item_441_443_map_3planets", "Vật phẩm theo bản đồ", "Map 3 hành tinh", "Ngẫu nhiên 1 trong 3", STARS, 1);
        add("item_441_443_map_nappa", "Vật phẩm theo bản đồ", "Map Nappa", "Ngẫu nhiên 1 trong 3", STARS, 1.5);
        add("item_441_443_map_tuonglai", "Vật phẩm theo bản đồ", "Map Tương lai", "Ngẫu nhiên 1 trong 3", STARS, 2);
        add("item_441_443_map_cold", "Vật phẩm theo bản đồ", "Map Cold", "Ngẫu nhiên 1 trong 3", STARS, 2.5);
        add("item_220_224_map_cold", "Vật phẩm theo bản đồ", "Map Cold", "Kèm option", STONES, 1);
        add("item_225_map_doanhtrai", "Vật phẩm theo bản đồ", "Map Doanh trại", "Kèm option 74", new int[] { 225 }, 10);
        add("item_set_star_map_tuonglai", "Vật phẩm theo bản đồ", "Map Tương lai",
                "Ngẫu nhiên 1 trong 13 món + random 1-3 sao · có thông báo chat", SET_ITEMS, 100.0 / 14000);
        add("item_set_star_map_cold", "Vật phẩm theo bản đồ", "Map Cold",
                "Ngẫu nhiên 1 trong 13 món + random 1-3 sao · có thông báo chat", SET_ITEMS, 100.0 / 18000);

        // ── Máy dò & Porata ──
        add("item_380_use_maydo", "Máy dò & Porata", "Map 58-65", "Chỉ khi bật máy dò", new int[] { 380 }, 10);
        add("item_933_map_porata", "Máy dò & Porata", "Map Porata", "Được ưu tiên nhất", new int[] { 933 }, 10);
        add("item_934_map_porata", "Máy dò & Porata", "Map Porata",
                "Chỉ khi không rơi mảnh vỡ · tối đa 150 mảnh/ngày", new int[] { 934 }, 5);
        add("item_935_map_porata", "Máy dò & Porata", "Map Porata", "Chỉ khi không rơi 2 loại trên",
                new int[] { 935 }, 0.2);
    }

    /** Cache tên + icon vật phẩm lấy từ bảng item_template */
    private static final Map<Integer, String> ITEM_NAMES = new HashMap<>();
    private static final Map<Integer, Integer> ITEM_ICONS = new HashMap<>();

    private static synchronized void loadItemNames() {
        if (!ITEM_NAMES.isEmpty()) {
            return;
        }
        Set<Integer> ids = new LinkedHashSet<>();
        for (Rule rule : RULES) {
            for (int id : rule.itemIds) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        StringBuilder in = new StringBuilder();
        for (int id : ids) {
            if (in.length() > 0) {
                in.append(',');
            }
            in.append(id);
        }
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, name, icon_id FROM item_template WHERE id IN (" + in + ")")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int id = resultSet.getInt("id");
                    ITEM_NAMES.put(id, String.valueOf(resultSet.getString("name")));
                    ITEM_ICONS.put(id, resultSet.getInt("icon_id"));
                }
            }
        } catch (Exception e) {
            Logger.error("Khong lay duoc ten vat pham tu item_template: " + e.getMessage() + "\n");
        }
    }

    /** Icon của vật phẩm đầu tiên trong rule (-1 nếu không có) */
    private static int resolveIconId(Rule rule) {
        loadItemNames();
        for (int id : rule.itemIds) {
            Integer icon = ITEM_ICONS.get(id);
            if (icon != null && icon > 0) {
                return icon;
            }
        }
        return -1;
    }

    /** Gom tên vật phẩm của rule (bỏ trùng) — ví dụ [76,188,189] → "Vàng" */
    private static String resolveItemNames(Rule rule) {
        loadItemNames();
        Set<String> names = new LinkedHashSet<>();
        for (int id : rule.itemIds) {
            String name = ITEM_NAMES.get(id);
            names.add(name != null && !name.isEmpty() ? name : ("#" + id));
        }
        return String.join(", ", names);
    }

    /** Nạp tỉ lệ từ DropRate.properties (chưa có file thì tự sinh với giá trị mặc định) */
    public static synchronized void load() {
        initRules();
        loaded = true;
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            save();
            return;
        }
        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream(file);
                java.io.InputStreamReader reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            Logger.error("Khong doc duoc " + CONFIG_FILE + ": " + e.getMessage() + "\n");
            return;
        }
        for (Rule rule : RULES) {
            Object value = properties.get("drop." + rule.key);
            if (value == null) {
                continue;
            }
            try {
                rule.percent = clampPercent(Double.parseDouble(String.valueOf(value).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        Object global = properties.get("drop.globalMultiplier");
        if (global != null) {
            try {
                int v = (int) Math.round(Double.parseDouble(String.valueOf(global).trim()));
                if (v >= 0) {
                    Manager.RATE_DROP_ITEM = v;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        Logger.success("Loaded " + RULES.size() + " drop rate(s) from " + CONFIG_FILE + "\n");
    }

    /** Ghi toàn bộ tỉ lệ (kèm hệ số nhân chung) ra DropRate.properties */
    public static synchronized void save() {
        initRules();
        StringBuilder sb = new StringBuilder();
        sb.append("# Ti le roi do (%) - chinh trong Admin > Van hanh > Ti Le Roi Do\n");
        sb.append("# Ti le thuc te = gia tri ben duoi x he so nhan chung / 100\n");
        sb.append("# File tu sinh, co the sua tay roi restart server.\n\n");
        sb.append("drop.globalMultiplier=").append(Manager.RATE_DROP_ITEM).append("\n\n");
        String currentGroup = null;
        for (Rule rule : RULES) {
            if (!rule.group.equals(currentGroup)) {
                currentGroup = rule.group;
                sb.append("# ===== ").append(currentGroup).append(" =====\n");
            }
            sb.append("# ").append(rule.label).append(" | ").append(rule.target)
                    .append(" | mac dinh ").append(fmt(rule.defaultPercent)).append("%\n");
            sb.append("drop.").append(rule.key).append('=').append(fmt(rule.percent)).append("\n");
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
            Logger.success("Saved drop rates to " + CONFIG_FILE + "\n");
        } catch (IOException e) {
            Logger.error("Khong ghi duoc " + CONFIG_FILE + ": " + e.getMessage() + "\n");
        }
    }

    /** Mob gọi hàm này để quyết định rơi / không rơi (đã nhân hệ số nhân chung) */
    public static boolean roll(String key) {
        ensureLoaded();
        Rule rule = INDEX.get(key);
        if (rule == null) {
            return false;
        }
        double effective = rule.percent * Manager.RATE_DROP_ITEM / 100.0;
        if (effective <= 0) {
            return false;
        }
        if (effective >= 100) {
            return true;
        }
        return Util.isTrue(Math.round(effective * PERCENT_TO_ROLL), ROLL_TOTAL);
    }

    public static double percent(String key) {
        ensureLoaded();
        Rule rule = INDEX.get(key);
        return rule == null ? 0 : rule.percent;
    }

    /** @return false nếu key không tồn tại */
    public static boolean setPercent(String key, double value) {
        ensureLoaded();
        Rule rule = INDEX.get(key);
        if (rule == null) {
            return false;
        }
        rule.percent = clampPercent(value);
        return true;
    }

    public static synchronized void resetDefaults() {
        initRules();
        for (Rule rule : RULES) {
            rule.percent = rule.defaultPercent;
        }
    }

    public static List<Rule> rules() {
        ensureLoaded();
        return RULES;
    }

    public static Rule rule(String key) {
        ensureLoaded();
        return INDEX.get(key);
    }

    /** Dữ liệu cho GET /api/drop-rates */
    public static List<Map<String, Object>> forApi() {
        ensureLoaded();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Rule rule : RULES) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", rule.key);
            row.put("group", rule.group);
            row.put("label", rule.label);
            row.put("target", rule.target);
            row.put("itemNames", resolveItemNames(rule));
            row.put("itemIds", rule.itemIds);
            row.put("iconId", resolveIconId(rule));
            row.put("percent", rule.percent);
            row.put("defaultPercent", rule.defaultPercent);
            row.put("oneIn", oneIn(rule.percent));
            rows.add(row);
        }
        return rows;
    }

    private static void ensureLoaded() {
        initRules();
        if (!loaded) {
            load();
        }
    }

    private static double clampPercent(double value) {
        if (Double.isNaN(value) || value < 0) {
            return 0;
        }
        return Math.min(value, 100);
    }

    /** "1/200" | "100%" | "—" (tỉ lệ 0) */
    private static String oneIn(double percent) {
        if (percent <= 0) {
            return "—";
        }
        if (percent >= 100) {
            return "100%";
        }
        double oneIn = 100.0 / percent;
        if (oneIn >= 1000) {
            return "1/" + String.format("%,.0f", oneIn);
        }
        return ("1/" + String.format("%,.2f", oneIn)).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** Định dạng số gọn: 10 / 0.5 / 0.0071429 */
    private static String fmt(double value) {
        return String.format(java.util.Locale.US, "%.7f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
