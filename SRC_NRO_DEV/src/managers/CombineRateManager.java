package managers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.DoubleSupplier;
import combine.CheTaoCuonSachCu;
import combine.CheTaoDuiDuc;
import combine.CheTaoTrangBiThienSu;
import combine.CombineSystem;
import combine.CuongHoaLoSaoPhaLe;
import combine.DanhBongSaoPhaLe;
import combine.DoiSachTuyetKy;
import combine.LamPhepNhapDa;
import combine.NangCapBongTai;
import combine.NangCapDeBlackGoku;
import combine.NangCapDeTuBlackGokuRose;
import combine.NangCapItemCap2;
import combine.NangCapKichHoat;
import combine.NangCapSachTuyetKy;
import combine.NangCapSaoPhaLe;
import combine.NangChiSoBongTai;
import combine.TaoDaHematite;
import combine.TaoDaMai;
import server.Manager;
import utils.Logger;

/**
 * Quản lý TOÀN BỘ tỉ lệ đập / nâng cấp đồ của server.
 *
 * - Mỗi tỉ lệ là 1 Rule (key, nhóm, tên, tỉ lệ gốc đọc trực tiếp từ hằng số trong class).
 * - Tỉ lệ gốc KHÔNG bị nhân bản: luôn đọc từ hằng số/hàm gốc, nên cấu hình sẵn trong code vẫn là mốc.
 * - rate(key, configured) trả tỉ lệ dùng thực tế: ưu tiên giá trị admin đặt, không thì lấy tỉ lệ gốc,
 *   sau đó nhân hệ số chung Manager.RATE_COMBINE / 100 và kẹp trong [0, 100].
 * - Giá trị admin đặt lưu ở CombineRate.properties nên giữ nguyên sau khi restart server.
 * - Admin > Vận hành > Tỉ Lệ Đập Đồ (GET/PUT /api/combine-rates).
 */
public final class CombineRateManager {

    public static final String CONFIG_FILE = "CombineRate.properties";

    public static class Rule {

        public final String key;
        public final String group;
        public final String name;
        public final DoubleSupplier base;

        Rule(String key, String group, String name, DoubleSupplier base) {
            this.key = key;
            this.group = group;
            this.name = name;
            this.base = base;
        }

        /** Tỉ lệ gốc đang config trong code */
        public double baseRate() {
            try {
                return base.getAsDouble();
            } catch (Exception e) {
                return 0;
            }
        }
    }

    private static final List<Rule> RULES = new ArrayList<>();
    private static final Map<String, Rule> INDEX = new LinkedHashMap<>();
    /** key -> tỉ lệ (%) do admin đặt; không có thì dùng tỉ lệ gốc trong code */
    private static final Map<String, Double> OVERRIDE = new LinkedHashMap<>();
    private static boolean loaded = false;

    private CombineRateManager() {
    }

    private static void add(String key, String group, String name, DoubleSupplier base) {
        Rule rule = new Rule(key, group, name, base);
        RULES.add(rule);
        INDEX.put(key, rule);
    }

    private static void initRules() {
        if (!RULES.isEmpty()) {
            return;
        }
        // ── Nâng cấp trang bị (bảng tỉ lệ theo cấp nằm trong CombineSystem) ──
        for (int level = 0; level <= 12; level++) {
            final int lv = level;
            add("nangcap_vatpham_" + level, "Nâng cấp trang bị",
                    "Nâng cấp đồ: " + level + "★ → " + (level + 1) + "★",
                    () -> CombineSystem.getTileNangCapDo(lv));
        }

        // ── Pha lê hóa trang bị ──
        for (int star = 0; star <= 12; star++) {
            final int st = star;
            add("phalehoa_" + star, "Pha lê hóa trang bị",
                    "Pha lê hóa: " + star + "★ → " + (star + 1) + "★",
                    () -> CombineSystem.getRatioPhaLeHoa(st));
        }

        // ── Sao pha lê & đá ──
        add("nangcap_sao_phale", "Sao pha lê & đá", "Nâng cấp sao pha lê",
                () -> NangCapSaoPhaLe.RATIO_NANG_CAP);
        add("danh_bong_sao_phale", "Sao pha lê & đá", "Đánh bóng sao pha lê",
                () -> DanhBongSaoPhaLe.RATIO_NANG_CAP);
        add("cuong_hoa_lo_sao_phale", "Sao pha lê & đá", "Cường hóa lô sao pha lê",
                () -> CuongHoaLoSaoPhaLe.SUCCESS_RATE);
        add("tao_da_hematite", "Sao pha lê & đá", "Tạo đá hematite", () -> TaoDaHematite.RATIO_TAO_DA);
        add("tao_dui_duc", "Sao pha lê & đá", "Tạo dùi đục", () -> CheTaoDuiDuc.COMBINE_SUCCESS_RATE);
        add("tao_da_mai", "Sao pha lê & đá", "Tạo đá mài", () -> TaoDaMai.RATIO_TAO_DA);

        // ── Đồ, phụ kiện & đệ tử ──
        add("lam_phep_nhap_da", "Đồ & phụ kiện", "Làm phép nhập đá", () -> LamPhepNhapDa.RATIO_TAO_DA);
        add("nang_cap_bong_tai", "Đồ & phụ kiện", "Nâng cấp bông tai Porata",
                () -> NangCapBongTai.RATIO_BONG_TAI);
        add("nang_chi_so_bong_tai", "Đồ & phụ kiện", "Nâng chỉ số bông tai",
                () -> NangChiSoBongTai.RATIO_NANG_CAP);
        add("nang_cap_item_cap_2", "Đồ & phụ kiện", "Tạo item cấp 2", () -> NangCapItemCap2.RATIO_TAO_DA);
        add("nang_de_black_goku", "Đồ & phụ kiện", "Tạo đệ Black Goku",
                () -> NangCapDeBlackGoku.SUCCESS_RATIO);
        add("nang_de_tu_rose", "Đồ & phụ kiện", "Nâng cấp đệ tử Black Goku Rose",
                () -> NangCapDeTuBlackGokuRose.SUCCESS_RATIO_MIN);
        add("che_tao_thien_su", "Đồ & phụ kiện", "Chế tạo trang bị thiên sứ",
                () -> CheTaoTrangBiThienSu.BASE_SUCCESS_RATE);
        add("dap_set_kich_hoat_nhe", "Đồ & phụ kiện", "Đập set kích hoạt — 2 option nhẹ",
                () -> NangCapKichHoat.RATE_LIGHT);
        add("dap_set_kich_hoat_vua", "Đồ & phụ kiện", "Đập set kích hoạt — 2 option vừa",
                () -> NangCapKichHoat.RATE_MEDIUM);

        // ── Sách ──
        add("nang_cap_sach_tuyet_ky", "Sách", "Nâng cấp sách tuyệt kỹ",
                () -> NangCapSachTuyetKy.SUCCESS_RATE_PERCENT);
        add("che_tao_cuon_sach_cu", "Sách", "Tạo cuốn sách cứu", () -> CheTaoCuonSachCu.SUCCESS_RATE_PERCENT);
        add("doi_sach_tuyet_ky", "Sách", "Đổi sách tuyệt kỹ", () -> DoiSachTuyetKy.SUCCESS_RATE_PERCENT);
    }

    /**
     * Tỉ lệ dùng thực tế cho key.
     * - Nếu admin đã đặt tỉ lệ riêng cho key này → dùng luôn giá trị đó.
     * - Ngược lại → lấy tỉ lệ gốc trong code, nhân hệ số chung Manager.RATE_COMBINE / 100.
     * Kết quả luôn kẹp trong [0, 100].
     */
    public static float rate(String key, double configured) {
        ensureLoaded();
        Double override = OVERRIDE.get(key);
        if (override != null) {
            return (float) clamp(override);
        }
        return (float) clamp(configured * Manager.RATE_COMBINE / 100.0);
    }

    /** @return tỉ lệ admin đã đặt, hoặc -1 nếu chưa đặt (đang dùng tỉ lệ gốc trong code) */
    public static double overridePercent(String key) {
        ensureLoaded();
        Double value = OVERRIDE.get(key);
        return value == null ? -1 : value;
    }

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
            Object value = properties.get("combine." + rule.key);
            if (value == null) {
                continue;
            }
            try {
                OVERRIDE.put(rule.key, clamp(Double.parseDouble(String.valueOf(value).trim())));
            } catch (NumberFormatException ignored) {
            }
        }
        Object global = properties.get("combine.globalMultiplier");
        if (global != null) {
            try {
                int v = (int) Math.round(Double.parseDouble(String.valueOf(global).trim()));
                if (v >= 0) {
                    Manager.RATE_COMBINE = v;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        Logger.success("Loaded " + OVERRIDE.size() + " combine rate override(s) from " + CONFIG_FILE + "\n");
    }

    public static synchronized void save() {
        initRules();
        StringBuilder sb = new StringBuilder();
        sb.append("# Ti le dap do (%) - chinh trong Admin > Van hanh > Ti Le Dap Do\n");
        sb.append("# Bo trong: chi cac muc admin da chinh, con lai dung ti le goc trong code.\n");
        sb.append("# File tu sinh, co the sua tay roi restart server.\n\n");
        sb.append("combine.globalMultiplier=").append(Manager.RATE_COMBINE).append("\n\n");
        for (Map.Entry<String, Double> entry : OVERRIDE.entrySet()) {
            Rule rule = INDEX.get(entry.getKey());
            sb.append("# ").append(rule != null ? rule.name : entry.getKey())
                    .append(" | goc ").append(rule != null ? fmt(rule.baseRate()) : "?")
                    .append("%\n");
            sb.append("combine.").append(entry.getKey()).append('=').append(fmt(entry.getValue())).append("\n");
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
            Logger.success("Saved combine rates to " + CONFIG_FILE + "\n");
        } catch (IOException e) {
            Logger.error("Khong ghi duoc " + CONFIG_FILE + ": " + e.getMessage() + "\n");
        }
    }

    /** @return false nếu key không tồn tại. value = -1 xóa override (về tỉ lệ gốc) */
    public static boolean setPercent(String key, double value) {
        ensureLoaded();
        if (!INDEX.containsKey(key)) {
            return false;
        }
        if (value < 0) {
            OVERRIDE.remove(key);
        } else {
            OVERRIDE.put(key, clamp(value));
        }
        return true;
    }

    public static synchronized void resetOverrides() {
        initRules();
        OVERRIDE.clear();
    }

    public static List<Rule> rules() {
        ensureLoaded();
        return RULES;
    }

    public static Rule rule(String key) {
        ensureLoaded();
        return INDEX.get(key);
    }

    /** Dữ liệu cho GET /api/combine-rates */
    public static List<Map<String, Object>> forApi() {
        ensureLoaded();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Rule rule : RULES) {
            double base = rule.baseRate();
            double override = overridePercent(rule.key);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", rule.key);
            row.put("group", rule.group);
            row.put("name", rule.name);
            row.put("base", base);
            row.put("overridden", override >= 0);
            // Giá trị đang dùng thực tế trong game
            row.put("value", (double) rate(rule.key, base));
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

    private static double clamp(double value) {
        if (Double.isNaN(value) || value < 0) {
            return 0;
        }
        return Math.min(value, 100);
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.US, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
