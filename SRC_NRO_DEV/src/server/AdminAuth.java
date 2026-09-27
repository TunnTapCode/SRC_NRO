package server;

import com.sun.net.httpserver.HttpExchange;
import database.DatabaseManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import utils.Logger;

/**
 * Xác thực (authentication) và phân quyền (authorization) cho Admin Panel.
 *
 * - Chỉ tài khoản trong bảng `account` có `is_admin = 1`, `ban = 0` và
 *   `active <> 0` mới được đăng nhập trang quản trị.
 * - Phiên làm việc lưu trong bộ nhớ server và gắn với cookie HttpOnly,
 *   SameSite=Strict nên JavaScript không đọc được token.
 * - Mọi request thay đổi dữ liệu (POST/PUT/DELETE) bắt buộc gửi kèm
 *   header `X-CSRF-Token` khớp với phiên để chống CSRF.
 * - Form đăng nhập dùng nonce dùng một lần để chống login CSRF / replay.
 * - Chống dò mật khẩu: tạm khóa theo cặp (username + IP) sau nhiều lần sai.
 *
 * Cấu hình tùy chọn trong Config.properties (thiếu thì dùng mặc định an toàn):
 *   admin.auth.enabled      = true
 *   admin.session.minutes   = 120
 *   admin.cookie.secure     = false
 *   admin.login.maxattempts = 5
 *   admin.login.lockminutes = 5
 *   admin.trust.proxy       = false
 */
public final class AdminAuth {

    /** Tên cookie giữ phiên đăng nhập của Admin Panel. */
    public static final String COOKIE_NAME = "NRO_ADMIN_SESSION";
    /** Header chứa CSRF token cho các request thay đổi dữ liệu. */
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LOGIN_NONCES = new ConcurrentHashMap<>();
    private static final Map<String, Attempt> ATTEMPTS = new ConcurrentHashMap<>();
    /** Cửa sổ chống spam đăng ký theo IP (khóa: IP, giá trị: số lần + thời điểm hết cửa sổ). */
    private static final Map<String, RegisterWindow> REGISTER_WINDOWS = new ConcurrentHashMap<>();

    /** Nonce đăng nhập chỉ sống 5 phút và chỉ dùng được một lần. */
    private static final long NONCE_TTL_MS = 5 * 60 * 1000L;
    /** Số lần tạo tài khoản tối đa cho một IP trong một cửa sổ thời gian. */
    private static final int MAX_REGISTER_PER_WINDOW = 10;
    /** Độ dài cửa sổ chống spam đăng ký (10 phút). */
    private static final long REGISTER_WINDOW_MS = 10 * 60 * 1000L;
    /** Định dạng tên tài khoản: chữ thường + số, 4-20 ký tự (khớp contract game client). */
    private static final java.util.regex.Pattern REGISTER_USERNAME_PATTERN =
            java.util.regex.Pattern.compile("[a-z0-9]{4,20}");
    /** Định dạng email tối giản; chỉ kiểm tra khi người dùng có nhập email. */
    private static final java.util.regex.Pattern REGISTER_EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** Tuổi thọ tuyệt đối của một phiên, dù người dùng có hoạt động liên tục. */
    private static final long MAX_SESSION_MS = 24 * 60 * 60 * 1000L;

    private static final boolean ENABLED;
    private static final boolean COOKIE_SECURE;
    private static final boolean TRUST_PROXY;
    private static final long SESSION_TTL_MS;
    private static final int MAX_ATTEMPTS;
    private static final long LOCK_MS;

    static {
        Properties properties = loadConfig();
        ENABLED = readBoolean(properties, "admin.auth.enabled", true);
        COOKIE_SECURE = readBoolean(properties, "admin.cookie.secure", false);
        TRUST_PROXY = readBoolean(properties, "admin.trust.proxy", false);
        SESSION_TTL_MS = Math.max(5, readInt(properties, "admin.session.minutes", 120)) * 60_000L;
        MAX_ATTEMPTS = Math.max(1, readInt(properties, "admin.login.maxattempts", 5));
        LOCK_MS = Math.max(1, readInt(properties, "admin.login.lockminutes", 5)) * 60_000L;
    }

    private AdminAuth() {
    }

    /** Một phiên đăng nhập đang hoạt động. */
    public static final class Session {

        public final int accountId;
        public final String username;
        public final String csrfToken;

        private final String token;
        private final long createdAt;
        private volatile long expiresAt;
        private volatile long lastSeen;

        private Session(String token, int accountId, String username, String csrfToken) {
            this.token = token;
            this.accountId = accountId;
            this.username = username;
            this.csrfToken = csrfToken;
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = this.createdAt + SESSION_TTL_MS;
            this.lastSeen = this.createdAt;
        }

        private boolean isExpired(long now) {
            return now >= expiresAt || now - createdAt >= MAX_SESSION_MS;
        }

        private void touch(long now) {
            this.lastSeen = now;
            long hardLimit = createdAt + MAX_SESSION_MS;
            this.expiresAt = Math.min(now + SESSION_TTL_MS, hardLimit);
        }

        /** Số giây còn lại của phiên (dùng cho /api/auth/session). */
        public long remainingSeconds() {
            return Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000);
        }

        public long lastSeen() {
            return lastSeen;
        }

        String token() {
            return token;
        }
    }

    /** Bộ đếm đăng nhập sai theo từng cặp (username + IP). */
    private static final class Attempt {

        volatile int failures;
        volatile long lockedUntil;
    }

    /** Tài khoản đã xác thực hợp lệ. */
    public static final class Credentials {

        public final int accountId;
        public final String username;

        private Credentials(int accountId, String username) {
            this.accountId = accountId;
            this.username = username;
        }
    }

    /** Kết quả đăng nhập: credentials != null là thành công, ngược lại error là lý do. */
    public static final class AuthResult {

        public final Credentials credentials;
        public final String error;

        private AuthResult(Credentials credentials, String error) {
            this.credentials = credentials;
            this.error = error;
        }
    }

    /**
     * Kết quả đăng ký tài khoản người chơi.
     *
     * @param status mã HTTP gợi ý cho API (200/400/403/409/429/500)
     */
    public static final class RegisterResult {

        public final boolean success;
        public final int status;
        public final String message;

        private RegisterResult(boolean success, int status, String message) {
            this.success = success;
            this.status = status;
            this.message = message;
        }
    }

    /** Auth có đang được bật không (admin.auth.enabled). */
    public static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * Đọc phiên đăng nhập từ cookie của request.
     *
     * @return phiên còn hiệu lực, hoặc null nếu chưa đăng nhập / hết hạn.
     */
    public static Session getSession(HttpExchange exchange) {
        if (!ENABLED) {
            return null;
        }
        String token = readCookie(exchange, COOKIE_NAME);
        if (token == null || token.isEmpty()) {
            return null;
        }
        Session session = SESSIONS.get(token);
        if (session == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (session.isExpired(now)) {
            SESSIONS.remove(token);
            return null;
        }
        session.touch(now);
        return session;
    }

    /**
     * Như {@link #getSession} nhưng tự trả lỗi 401 JSON khi chưa đăng nhập.
     *
     * @return phiên hợp lệ, hoặc null (đã gửi response lỗi cho client).
     */
    public static Session requireSession(HttpExchange exchange) throws IOException {
        Session session = getSession(exchange);
        if (session == null) {
            sendError(exchange, 401, "Phien dang nhap khong hop le hoac da het han. Vui long dang nhap lai.");
        }
        return session;
    }

    /** Tạo phiên mới cho tài khoản admin và gắn cookie HttpOnly vào response. */
    public static Session createSession(HttpExchange exchange, int accountId, String username, String ip) {
        String token = randomHex(32);
        Session session = new Session(token, accountId, username, randomHex(32));
        SESSIONS.put(token, session);
        setCookie(exchange, token, SESSION_TTL_MS / 1000);
        Logger.success("Admin dang nhap: " + username + " (" + (ip == null ? "-" : ip) + ")\n");
        return session;
    }

    /** Kết thúc phiên hiện tại và xóa cookie ở trình duyệt. */
    public static void destroySession(HttpExchange exchange) {
        String token = readCookie(exchange, COOKIE_NAME);
        if (token != null && !token.isEmpty()) {
            Session removed = SESSIONS.remove(token);
            if (removed != null) {
                Logger.logln("Admin dang xuat: " + removed.username + "\n");
            }
        }
        setCookie(exchange, "", 0);
    }

    /**
     * Chống CSRF: các request thay đổi dữ liệu phải kèm header X-CSRF-Token
     * khớp với CSRF token của phiên.
     *
     * @return true nếu hợp lệ (hoặc là request chỉ đọc); false nếu đã gửi lỗi 403.
     */
    public static boolean requireCsrf(HttpExchange exchange, Session session) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        String provided = exchange.getRequestHeaders().getFirst(CSRF_HEADER);
        if (provided == null || !constantTimeEquals(session.csrfToken, provided)) {
            sendError(exchange, 403, "CSRF token khong hop le. Vui long tai lai trang.");
            return false;
        }
        return true;
    }
    /** Sinh nonce dùng một lần cho form đăng nhập (chống login CSRF / replay). */
    public static String createLoginNonce() {
        long now = System.currentTimeMillis();
        LOGIN_NONCES.entrySet().removeIf(entry -> now - entry.getValue() > NONCE_TTL_MS);
        String nonce = randomHex(24);
        LOGIN_NONCES.put(nonce, now);
        return nonce;
    }

    /** Kiểm tra và hủy nonce đăng nhập; mỗi nonce chỉ dùng được một lần. */
    public static boolean consumeLoginNonce(String nonce) {
        if (nonce == null || nonce.isEmpty()) {
            return false;
        }
        Long issuedAt = LOGIN_NONCES.remove(nonce);
        if (issuedAt == null) {
            return false;
        }
        return System.currentTimeMillis() - issuedAt <= NONCE_TTL_MS;
    }

    /**
     * Xác thực tài khoản đăng nhập Admin Panel.
     *
     * <p>Luồng kiểm tra: tài khoản tồn tại → mật khẩu khớp → có quyền admin
     * (`is_admin`) → không bị khóa (`ban`) → đang hoạt động (`active`).</p>
     *
     * @return kết quả chứa {@link Credentials} khi thành công, hoặc thông báo lỗi.
     */
    public static AuthResult authenticate(String rawUsername, String password, String ip) {
        if (!ENABLED) {
            // Chế độ dev: tắt xác thực hoàn toàn (admin.auth.enabled=false)
            return new AuthResult(new Credentials(0, "development"), null);
        }
        String username = rawUsername == null ? "" : rawUsername.trim().toLowerCase(Locale.ROOT);
        if (username.isEmpty() || password == null || password.isEmpty()) {
            return new AuthResult(null, "Vui long nhap day du tai khoan va mat khau.");
        }
        if (username.length() > 64 || password.length() > 200) {
            return new AuthResult(null, "Tai khoan hoac mat khau khong chinh xac.");
        }

        String attemptKey = username + "|" + (ip == null ? "-" : ip);
        long lockedFor = lockedSeconds(attemptKey);
        if (lockedFor > 0) {
            return new AuthResult(null,
                    "Ban da dang nhap sai qua nhieu lan. Vui long thu lai sau " + lockedFor + " giay.");
        }

        String sql = "SELECT id, password, is_admin, active, ban FROM account WHERE username = ? LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || !passwordMatches(resultSet.getString("password"), password)) {
                    registerFailure(attemptKey);
                    return new AuthResult(null, "Tai khoan hoac mat khau khong chinh xac.");
                }
                // ── Phân quyền: chỉ tài khoản admin được vào trang quản trị ──
                if (!resultSet.getBoolean("is_admin")) {
                    registerFailure(attemptKey);
                    return new AuthResult(null, "Tai khoan nay khong co quyen quan tri.");
                }
                if (resultSet.getBoolean("ban")) {
                    return new AuthResult(null, "Tai khoan dang bi khoa. Lien he Admin cap cao hon.");
                }
                if (resultSet.getInt("active") == 0) {
                    return new AuthResult(null, "Tai khoan chua duoc kich hoat.");
                }
                int accountId = resultSet.getInt("id");
                ATTEMPTS.remove(attemptKey);
                return new AuthResult(new Credentials(accountId, username), null);
            }
        } catch (Exception e) {
            Logger.logException(AdminAuth.class, e, "Loi xac thuc admin");
            return new AuthResult(null, "Khong ket noi duoc database. Kiem tra MySQL va Config.properties.");
        }
    }

    // ══════════════════════════════════════════════════════
    //  Đăng ký tài khoản người chơi (tích hợp trong trang login)
    // ══════════════════════════════════════════════════════

    /**
     * Tạo tài khoản người chơi mới từ trang Đăng ký của Admin Panel.
     *
     * <p>Cùng contract với trang đăng ký cũ: username {@code [a-z0-9]{4,20}},
     * mật khẩu 4-100 ký tự, email tùy chọn. Luôn luôn tạo tài khoản thường
     * ({@code is_admin = 0} theo mặc định của bảng) — không tự cấp quyền admin.</p>
     *
     * @param ip dùng để chống spam (giới hạn số lần tạo per IP)
     */
    public static RegisterResult registerAccount(String rawUsername, String password, String confirm,
            String email, String ip) {
        String username = rawUsername == null ? "" : rawUsername.trim().toLowerCase(Locale.ROOT);
        String mail = email == null ? "" : email.trim();

        if (!REGISTER_USERNAME_PATTERN.matcher(username).matches()) {
            return new RegisterResult(false, 400,
                    "Tai khoan chi gom chu thuong va so, dai tu 4 den 20 ky tu.");
        }
        if (password == null || password.length() < 4 || password.length() > 100) {
            return new RegisterResult(false, 400, "Mat khau phai dai tu 4 den 100 ky tu.");
        }
        if (!password.equals(confirm)) {
            return new RegisterResult(false, 400, "Mat khau nhap lai khong khop.");
        }
        if (mail.length() > 255 || (!mail.isEmpty() && !REGISTER_EMAIL_PATTERN.matcher(mail).matches())) {
            return new RegisterResult(false, 400, "Email khong hop le.");
        }
        if (!allowRegister(ip == null || ip.isEmpty() ? "-" : ip)) {
            return new RegisterResult(false, 429,
                    "Ban da tao qua nhieu tai khoan. Vui long thu lai sau it phut.");
        }

        String checkSql = "SELECT id FROM account WHERE username = ? LIMIT 1";
        String insertSql = "INSERT INTO account (username, password, email, token, xsrf_token, newpass)"
                + " VALUES (?, ?, ?, '', '', '')";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement check = connection.prepareStatement(checkSql)) {
            check.setString(1, username);
            try (ResultSet resultSet = check.executeQuery()) {
                if (resultSet.next()) {
                    return new RegisterResult(false, 409, "Tai khoan da ton tai.");
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setString(1, username);
                insert.setString(2, password);
                insert.setString(3, mail);
                insert.executeUpdate();
            }
            return new RegisterResult(true, 200, "Dang ky thanh cong. Ban co the dang nhap ngay.");
        } catch (Exception e) {
            Logger.logException(AdminAuth.class, e, "Loi dang ky tai khoan");
            return new RegisterResult(false, 500, "Khong the tao tai khoan. Hay kiem tra ket noi database.");
        }
    }

    /** Kiểm tra và cộng dồn số lần đăng ký của IP trong cửa sổ hiện tại; false = đã vượt hạn mức. */
    private static boolean allowRegister(String key) {
        long now = System.currentTimeMillis();
        REGISTER_WINDOWS.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        RegisterWindow window = REGISTER_WINDOWS.computeIfAbsent(key, ignored -> new RegisterWindow(now));
        return window.tryRegister(now);
    }

    /** Bộ đếm số lần đăng ký của một IP trong một cửa sổ thời gian. */
    private static final class RegisterWindow {

        private int count;
        private long resetAt;

        private RegisterWindow(long now) {
            this.resetAt = now + REGISTER_WINDOW_MS;
        }

        private synchronized boolean isExpired(long now) {
            return resetAt <= now;
        }

        private synchronized boolean tryRegister(long now) {
            if (resetAt <= now) {
                count = 0;
                resetAt = now + REGISTER_WINDOW_MS;
            }
            if (count >= MAX_REGISTER_PER_WINDOW) {
                return false;
            }
            count++;
            return true;
        }
    }

    /** IP của client; chỉ tin X-Forwarded-For khi bật admin.trust.proxy. */
    public static String clientIp(HttpExchange exchange) {
        if (TRUST_PROXY) {
            String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isEmpty()) {
                int comma = forwarded.indexOf(',');
                String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
        }
        try {
            return exchange.getRemoteAddress().getAddress().getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Số phiên admin đang hoạt động. */
    public static int activeSessionCount() {
        return SESSIONS.size();
    }

    /** Dọn phiên hết hạn, nonce cũ và bộ đếm sai đã hết thời gian khóa. */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, Session>> iterator = SESSIONS.entrySet().iterator(); iterator.hasNext();) {
            if (iterator.next().getValue().isExpired(now)) {
                iterator.remove();
            }
        }
        LOGIN_NONCES.entrySet().removeIf(entry -> now - entry.getValue() > NONCE_TTL_MS);
        ATTEMPTS.entrySet().removeIf(entry -> {
            Attempt attempt = entry.getValue();
            return attempt.lockedUntil > 0 && attempt.lockedUntil <= now;
        });
        REGISTER_WINDOWS.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }


    // ══════════════════════════════════════════════════════
    //  Cookie / response helpers
    // ══════════════════════════════════════════════════════

    /** Gắn cookie phiên; maxAgeSeconds = 0 nghĩa là xóa cookie. */
    private static void setCookie(HttpExchange exchange, String token, long maxAgeSeconds) {
        StringBuilder builder = new StringBuilder();
        builder.append(COOKIE_NAME).append('=').append(token == null ? "" : token);
        builder.append("; Path=/; HttpOnly; SameSite=Strict; Max-Age=").append(maxAgeSeconds);
        if (COOKIE_SECURE) {
            builder.append("; Secure");
        }
        exchange.getResponseHeaders().add("Set-Cookie", builder.toString());
    }

    /** Đọc giá trị cookie theo tên từ header Cookie của request. */
    private static String readCookie(HttpExchange exchange, String name) {
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders == null) {
            return null;
        }
        for (String header : cookieHeaders) {
            if (header == null) {
                continue;
            }
            for (String part : header.split(";")) {
                String trimmed = part.trim();
                int index = trimmed.indexOf('=');
                if (index <= 0) {
                    continue;
                }
                if (name.equals(trimmed.substring(0, index))) {
                    return stripQuotes(trimmed.substring(index + 1));
                }
            }
        }
        return null;
    }

    /**
     * Bỏ dấu nháy kép bao quanh giá trị cookie.
     * Client theo chuẩn cũ (RFC 2965) gửi giá trị dạng {@code "token"}; nếu không
     * bỏ nháy thì token sẽ không khớp với phiên đã lưu.
     */
    private static String stripQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /** Trả về JSON lỗi tối giản, không cache. */
    private static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        byte[] content = ("{\"success\":false,\"message\":\"" + escapeJson(message) + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ══════════════════════════════════════════════════════
    //  Chống dò mật khẩu
    // ══════════════════════════════════════════════════════

    /** Số giây còn bị khóa đăng nhập; 0 nghĩa là không bị khóa. */
    private static long lockedSeconds(String key) {
        Attempt attempt = ATTEMPTS.get(key);
        if (attempt == null) {
            return 0;
        }
        long lockUntil = attempt.lockedUntil;
        if (lockUntil == 0) {
            // Chưa từng bị khóa → giữ nguyên bộ đếm số lần sai
            return 0;
        }
        long remaining = lockUntil - System.currentTimeMillis();
        if (remaining <= 0) {
            // Hết thời gian khóa → mới xóa bộ đếm
            ATTEMPTS.remove(key);
            return 0;
        }
        return (remaining + 999) / 1000;
    }

    /** Ghi nhận một lần sai; đủ số lần thì tạm khóa cặp (username + IP). */
    private static void registerFailure(String key) {
        Attempt attempt = ATTEMPTS.computeIfAbsent(key, ignored -> new Attempt());
        attempt.failures++;
        if (attempt.failures >= MAX_ATTEMPTS) {
            attempt.lockedUntil = System.currentTimeMillis() + LOCK_MS;
            attempt.failures = 0;
        }
    }

    // ══════════════════════════════════════════════════════
    //  So khớp mật khẩu
    // ══════════════════════════════════════════════════════

    /**
     * So khớp mật khẩu người dùng nhập với giá trị lưu trong DB.
     *
     * <p>Hỗ trợ plaintext (tương thích server game hiện tại),
     * {@code sha256$<hex>} và {@code md5$<hex>} để có thể nâng cấp dần.</p>
     */
    private static boolean passwordMatches(String stored, String input) {
        if (stored == null || input == null) {
            return false;
        }
        if (stored.startsWith("sha256$")) {
            byte[] expected = hexToBytes(stored.substring("sha256$".length()));
            return expected != null && MessageDigest.isEqual(expected, digest("SHA-256", input));
        }
        if (stored.startsWith("md5$")) {
            byte[] expected = hexToBytes(stored.substring("md5$".length()));
            return expected != null && MessageDigest.isEqual(expected, digest("MD5", input));
        }
        // So sánh constant-time để hạn chế timing attack
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8),
                input.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] digest(String algorithm, String value) {
        try {
            return MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty() || hex.length() % 2 != 0) {
            return null;
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }

    private static boolean constantTimeEquals(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8),
                second.getBytes(StandardCharsets.UTF_8));
    }

    /** Sinh chuỗi hex ngẫu nhiên dùng SecureRandom (token phiên, CSRF, nonce). */
    private static String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    // ══════════════════════════════════════════════════════
    //  Đọc cấu hình (Config.properties)
    // ══════════════════════════════════════════════════════

    private static Properties loadConfig() {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream("Config.properties")) {
            properties.load(input);
        } catch (Exception e) {
            // Không có file cấu hình → dùng toàn bộ giá trị mặc định an toàn
        }
        return properties;
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        Object value = properties.get(key);
        if (value == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private static int readInt(Properties properties, String key, int fallback) {
        Object value = properties.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

