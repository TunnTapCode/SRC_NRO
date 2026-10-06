package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import combine.CombineSystem;
import database.DatabaseManager;
import database.NTTSqlFetcher;
import database.PlayerDAO;
import item.Item;
import item.Template;
import player.Player;
import player.Service.InventoryService;
import services.ItemService;
import services.Service;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import managers.DropRateManager;
import managers.CombineRateManager;
import utils.Logger;

// Runtime server references
import static server.Maintenance.isRunning;

public final class AdminApiServer {

    private static final int START_PORT = 8080;
    private static HttpServer server;
    private static int actualPort = -1;
    private static final Pattern JSON_VALUE_PATTERN = Pattern
            .compile("\\\"([^\\\"]+)\\\"\\s*:\\s*(\\\"(?:\\\\.|[^\\\"])*\\\"|-?\\d+(?:\\.\\d+)?|true|false|null)");

    // Format nhãn trục thời gian cho biểu đồ lượng người chơi
    private static final DateTimeFormatter STATS_DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter STATS_MONTH_LABEL = DateTimeFormatter.ofPattern("MM/yyyy");
    // Số mốc thống kê: ngày (30 ngày gần nhất), tuần (12 tuần), tháng (12 tháng)
    private static final int STATS_DAY_BUCKETS = 30;
    private static final int STATS_WEEK_BUCKETS = 12;
    private static final int STATS_MONTH_BUCKETS = 12;

    private AdminApiServer() {
    }

    public static int getPort() {
        return actualPort;
    }

    public static void start() {
        if (server != null) {
            return;
        }
        for (int port = START_PORT; port < START_PORT + 10; port++) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                actualPort = port;
                server.createContext("/api", AdminApiServer::handleApi);
                server.createContext("/admin", AdminApiServer::handleAdmin);
                server.createContext("/admin/", AdminApiServer::handleAdmin);
                server.createContext("/data", AdminApiServer::handleData);
                server.createContext("/data/", AdminApiServer::handleData);
                server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                    Thread thread = new Thread(runnable, "Admin-API");
                    return thread;
                }));
                server.start();
                Logger.success("Admin API: http://127.0.0.1:" + port + "/admin\n");
                return;
            } catch (IOException e) {
                server = null;
                actualPort = -1;
            }
        }
        Logger.error("Khong the khoi dong Admin API tu cong " + START_PORT + " den " + (START_PORT + 9) + "\n");
    }

    private static void handleAdmin(HttpExchange exchange) throws IOException {
        AdminAuth.cleanup();
        String rawPath = exchange.getRequestURI().getPath();
        if (rawPath == null) {
            rawPath = "/admin/index.html";
        }
        if ("/admin".equals(rawPath)) {
            exchange.getResponseHeaders().set("Location", "/admin/");
            exchange.sendResponseHeaders(301, -1);
            return;
        }
        if ("/styles.css".equals(rawPath) || "/app.js".equals(rawPath) || "/favicon.ico".equals(rawPath)) {
            rawPath = "/admin" + rawPath;
        }
        if ("/".equals(rawPath) || "/admin/".equals(rawPath)) {
            rawPath = "/admin/index.html";
        }
        // Alias gọn cho trang đăng nhập: /admin/login → /admin/login.html
        if ("/admin/login".equals(rawPath) || "/admin/login/".equals(rawPath)) {
            rawPath = "/admin/login.html";
        }

        String relative = rawPath;
        if (rawPath.startsWith("/admin")) {
            relative = rawPath.substring("/admin".length());
        }
        relative = relative.replaceFirst("^/", "");
        if (relative.isEmpty()) {
            relative = "index.html";
        }
        if (relative.contains("..")) {
            sendText(exchange, 400, "Bad Request");
            return;
        }

        // ── XÁC THỰC: chỉ tài nguyên công khai (trang login, css, js) mới xem được khi chưa đăng nhập ──
        if (!isPublicAdminAsset(relative) && AdminAuth.isEnabled() && AdminAuth.getSession(exchange) == null) {
            if ("index.html".equals(relative)) {
                // Người dùng mở thẳng trang quản trị → đưa về trang đăng nhập
                exchange.getResponseHeaders().set("Location", "/admin/login");
                exchange.sendResponseHeaders(302, -1);
            } else {
                // Tài nguyên nội bộ (partials, ...) trả 401 để client tự chuyển hướng
                sendText(exchange, 401, "Unauthorized");
            }
            exchange.close();
            return;
        }

        Path base = Paths.get(System.getProperty("user.dir"), "web", "admin");
        Path file = base.resolve(relative).normalize();
        if (!file.startsWith(base)) {
            sendText(exchange, 403, "Forbidden");
            return;
        }
        if (!Files.exists(file)) {
            sendText(exchange, 404, "Not Found: " + relative);
            return;
        }

        byte[] content = Files.readAllBytes(file);
        // Trang đăng nhập cần nonce dùng một lần để chống login CSRF / replay
        if ("login.html".equals(relative)) {
            String html = new String(content, StandardCharsets.UTF_8);
            content = html.replace("__LOGIN_NONCE__", AdminAuth.createLoginNonce())
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
        }
        String mime = Files.probeContentType(file);
        if (mime == null) {
            String lower = file.getFileName().toString().toLowerCase();
            if (lower.endsWith(".css")) {
                mime = "text/css";
            } else if (lower.endsWith(".js")) {
                mime = "application/javascript";
            } else if (lower.endsWith(".html")) {
                mime = "text/html";
            } else {
                mime = "application/octet-stream";
            }
        }
        exchange.getResponseHeaders().set("Content-Type",
                mime + (mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json")
                        ? "; charset=UTF-8"
                        : ""));
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    /**
     * Tài nguyên tĩnh của Admin Panel được phép tải khi CHƯA đăng nhập.
     * Gồm trang đăng nhập và các file css/js thuần giao diện (không chứa dữ liệu).
     */
    private static boolean isPublicAdminAsset(String relative) {
        String lower = relative.toLowerCase(Locale.ROOT);
        if ("login.html".equals(lower)) {
            return true;
        }
        return lower.startsWith("css/") || lower.startsWith("js/") || lower.equals("favicon.ico");
    }

    private static void handleData(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getPath();
        if (rawPath == null || "/data".equals(rawPath) || "/data/".equals(rawPath)) {
            sendText(exchange, 404, "No data file specified");
            return;
        }
        String relative = rawPath.startsWith("/data") ? rawPath.substring("/data".length()) : rawPath;
        relative = relative.replaceFirst("^/", "");
        if (relative.contains("..")) {
            sendText(exchange, 400, "Bad Request");
            return;
        }

        Path base = Paths.get(System.getProperty("user.dir"), "data");
        Path file = base.resolve(relative).normalize();
        if (!file.startsWith(base)) {
            sendText(exchange, 403, "Forbidden");
            return;
        }
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            sendText(exchange, 404, "Not Found: " + relative);
            return;
        }

        byte[] content = Files.readAllBytes(file);
        String mime = Files.probeContentType(file);
        if (mime == null) {
            String lower = file.getFileName().toString().toLowerCase();
            if (lower.endsWith(".png"))
                mime = "image/png";
            else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
                mime = "image/jpeg";
            else if (lower.endsWith(".gif"))
                mime = "image/gif";
            else if (lower.endsWith(".webp"))
                mime = "image/webp";
            else
                mime = "application/octet-stream";
        }
        exchange.getResponseHeaders().set("Content-Type", mime);
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private static void handleApi(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] pathParts = path == null ? new String[0] : path.replaceFirst("^/api/?", "").split("/");
        String resource = pathParts.length > 0 ? pathParts[0].toLowerCase(Locale.ROOT) : "";
        String idParam = pathParts.length > 1 ? pathParts[1] : null;
        String method = exchange.getRequestMethod();
        AdminAuth.cleanup();

        if (resource.isEmpty()) {
            if (AdminAuth.isEnabled() && AdminAuth.requireSession(exchange) == null) {
                return;
            }
            sendJson(exchange, 200, mapOf("success", true, "message", "Admin API ready", "endpoints", new String[] {
                    "/api/accounts",
                    "/api/players",
                    "/api/online-players",
                    "/api/give-item",
                    "/api/giftcodes",
                    "/api/items",
                    "/api/shops",
                    "/api/npcs",
                    "/api/tasks",
                    "/api/player-stats",
                    "/api/drop-rates"
            }));
            return;
        }

        Map<String, String> payload = readBody(exchange);

        // ── Nhóm /api/auth/* dùng để đăng nhập, đăng xuất, kiểm tra phiên (không cần phiên trước đó) ──
        if ("auth".equals(resource)) {
            handleAuth(exchange, idParam, method, payload);
            return;
        }

        // ── XÁC THỰC + PHÂN QUYỀN: mọi API còn lại chỉ dành cho admin đã đăng nhập ──
        if (AdminAuth.isEnabled()) {
            AdminAuth.Session session = AdminAuth.requireSession(exchange);
            if (session == null) {
                return;
            }
            if (!AdminAuth.requireCsrf(exchange, session)) {
                return;
            }
        }

        switch (resource) {
            case "accounts":
                handleCrud(exchange, method, idParam, payload, "account", "id", "username", "password", "email",
                        "is_admin", "active", "ban");
                return;
            case "players":
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 200, queryPlayers(idParam));
                } else {
                    handleCrud(exchange, method, idParam, payload, "player", "id", "account_id", "name", "head",
                            "gender", "clan_id", "rank", "power");
                }
                return;
            case "online-players":
                handleOnlinePlayers(exchange, method);
                return;
            case "give-item":
                handleGiveItem(exchange, method, payload);
                return;
            case "giftcodes":
                handleCrud(exchange, method, idParam, payload, "giftcode", "id", "code", "count_left", "detail",
                        "expired");
                return;
            case "server":
                if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                    handleServerControl(exchange, payload);
                } else if ("GET".equalsIgnoreCase(method)) {
                    sendJson(exchange, 200, mapOf("success", true, "running", ServerManager.isRunning,
                            "message", ServerManager.isRunning ? "Server đang chạy" : "Server đang dừng"));
                } else {
                    exchange.getResponseHeaders().set("Allow", "GET, POST, PUT");
                    sendText(exchange, 405, "Method Not Allowed");
                }
                return;
            case "items":
                handleCrud(exchange, method, idParam, payload, "item_template", "id", "TYPE", "NAME", "description",
                        "level", "icon_id", "part", "is_up_to_up", "power_require", "gold", "gem", "head", "body",
                        "leg");
                return;
            case "shops":
                handleCrud(exchange, method, idParam, payload, "shop", "id", "npc_id", "tag_name", "type_shop");
                return;
            case "tab-shops":
                // GET /api/tab-shops?shop_id=1 → tabs của shop đó; không có param → tất cả
                if ("GET".equalsIgnoreCase(method)) {
                    String shopIdParam = exchange.getRequestURI().getQuery();
                    String shopId = null;
                    if (shopIdParam != null) {
                        for (String part : shopIdParam.split("&")) {
                            if (part.startsWith("shop_id="))
                                shopId = part.substring(8);
                        }
                    }
                    sendJson(exchange, 200,
                            queryRowsWhere("tab_shop", "id", shopId != null ? "shop_id" : null, shopId));
                } else {
                    handleCrud(exchange, method, idParam, payload, "tab_shop", "id", "shop_id", "NAME");
                }
                return;
            case "item-shops":
                // GET /api/item-shops?tab_id=1 → items của tab đó
                if ("GET".equalsIgnoreCase(method)) {
                    String tabIdParam = exchange.getRequestURI().getQuery();
                    String tabId = null;
                    if (tabIdParam != null) {
                        for (String part : tabIdParam.split("&")) {
                            if (part.startsWith("tab_id="))
                                tabId = part.substring(7);
                        }
                    }
                    sendJson(exchange, 200, queryRowsWhere("item_shop", "id", tabId != null ? "tab_id" : null, tabId));
                } else {
                    handleCrud(exchange, method, idParam, payload, "item_shop", "id", "tab_id", "temp_id", "is_new",
                            "is_sell", "type_sell", "cost", "icon_spec");
                }
                return;
            case "item-shop-options":
                // GET /api/item-shop-options?item_shop_id=1
                if ("GET".equalsIgnoreCase(method)) {
                    String itemShopIdParam = exchange.getRequestURI().getQuery();
                    String itemShopId = null;
                    if (itemShopIdParam != null) {
                        for (String part : itemShopIdParam.split("&")) {
                            if (part.startsWith("item_shop_id="))
                                itemShopId = part.substring(13);
                        }
                    }
                    sendJson(exchange, 200, queryRowsWhere("item_shop_option", "id",
                            itemShopId != null ? "item_shop_id" : null, itemShopId));
                } else {
                    handleCrud(exchange, method, idParam, payload, "item_shop_option", "id", "item_shop_id",
                            "option_id", "param");
                }
                return;
            case "item-options":
                // GET /api/item-options → toàn bộ { id, NAME } từ bảng item_option_template
                handleCrud(exchange, method, idParam, payload, "item_option_template", "id", "NAME");
                return;
            case "item-options-of":
                // GET /api/item-options-of?temp_id=1 → toàn bộ option của item_template đó.
                // Option của vật phẩm nằm ở item_shop_option (gắn qua item_shop.temp_id),
                // đây cũng là nguồn game server đọc khi bán hàng (ShopDAO.loadItemShopOption).
                if ("GET".equalsIgnoreCase(method)) {
                    String itemQuery = exchange.getRequestURI().getQuery();
                    String tempId = null;
                    if (itemQuery != null) {
                        for (String part : itemQuery.split("&")) {
                            if (part.startsWith("temp_id="))
                                tempId = part.substring(8);
                        }
                    }
                    if (tempId == null || tempId.isEmpty()) {
                        sendJson(exchange, 400, mapOf("success", false, "message", "Missing temp_id"));
                        return;
                    }
                    sendJson(exchange, 200, queryItemOptionsOfTemp(tempId));
                } else {
                    exchange.getResponseHeaders().set("Allow", "GET");
                    sendText(exchange, 405, "Method Not Allowed");
                }
                return;
            case "item-default-options":
                // GET /api/item-default-options?temp_id=1 → option mặc định của item do
                // game code định nghĩa (Item.getOptionDaPhaLe / CombineSystem), dùng cho
                // sao pha lê, ngọc rồng... vốn không có option trong DB. Chỉ đọc.
                if ("GET".equalsIgnoreCase(method)) {
                    String itemQuery = exchange.getRequestURI().getQuery();
                    String tempId = null;
                    if (itemQuery != null) {
                        for (String part : itemQuery.split("&")) {
                            if (part.startsWith("temp_id="))
                                tempId = part.substring(8);
                        }
                    }
                    if (tempId == null || tempId.isEmpty()) {
                        sendJson(exchange, 400, mapOf("success", false, "message", "Missing temp_id"));
                        return;
                    }
                    sendJson(exchange, 200, queryItemDefaultOptions(tempId));
                } else {
                    exchange.getResponseHeaders().set("Allow", "GET");
                    sendText(exchange, 405, "Method Not Allowed");
                }
                return;
            case "napthe":
                handleCrud(exchange, method, idParam, payload, "napthe", "id", "user_nap", "telco", "serial", "code",
                        "amount", "status", "request_id");
                return;
            case "settings":
                handleSettings(exchange, method, payload);
                return;
            case "announce":
                handleAnnounce(exchange, payload);
                return;
            case "bosses":
                sendJson(exchange, 200, queryBosses());
                return;
            case "mobs":
                handleCrud(exchange, method, idParam, payload, "mob_template", "id", "TYPE", "NAME", "hp");
                return;
            case "npcs":
                handleCrud(exchange, method, idParam, payload, "npc_template", "id", "NAME", "head", "body", "leg",
                        "avatar");
                return;
            case "maps":
                handleCrud(exchange, method, idParam, payload, "map_template", "id",
                        "NAME", "zones", "max_player", "type", "planet_id",
                        "bg_type", "tile_id", "bg_id", "data", "waypoints", "mobs", "npcs");
                return;
            case "head-avatars":
                // Trả về map { head_id: avatar_id } dạng flat JSON object để frontend tra nhanh
                sendJson(exchange, 200, queryHeadAvatars());
                return;
            case "tasks":
                // GET /api/tasks → danh sách nhiệm vụ chính + các bước (dùng cho Top nhiệm vụ)
                sendJson(exchange, 200, queryTasks());
                return;
            case "player-stats":
                // GET /api/player-stats?range=day|week|month → lượng người chơi theo mốc thời gian
                sendJson(exchange, 200, queryPlayerStats(queryParam(exchange.getRequestURI().getQuery(), "range")));
                return;
            case "drop-rates":
                // GET/PUT /api/drop-rates → bảng tỉ lệ rơi đồ (Admin > Vận hành)
                handleDropRates(exchange, method, payload);
                return;
            case "combine-rates":
                // GET/PUT /api/combine-rates → bảng tỉ lệ đập đồ (Admin > Vận hành)
                handleCombineRates(exchange, method, payload);
                return;
            default:
                sendText(exchange, 404, "Unknown resource: " + resource);
        }
    }

    /**
     * Xử lý nhóm endpoint xác thực của Admin Panel:
     * <pre>
     *   GET  /api/auth/nonce   → nonce dùng một lần cho form đăng nhập
     *   POST /api/auth/login   → đăng nhập { username, password, nonce }
     *   POST /api/auth/register → tạo tài khoản người chơi { username, password, confirm, email, nonce }
     *   POST /api/auth/logout  → hủy phiên hiện tại
     *   GET  /api/auth/session → thông tin phiên + CSRF token cho client
     * </pre>
     */
    private static void handleAuth(HttpExchange exchange, String action, String method, Map<String, String> payload)
            throws IOException {
        String command = action == null ? "" : action.toLowerCase(Locale.ROOT);
        switch (command) {
            case "nonce": {
                if (!"GET".equalsIgnoreCase(method)) {
                    methodNotAllowed(exchange, "GET");
                    return;
                }
                if (!AdminAuth.isEnabled()) {
                    sendJson(exchange, 200, mapOf("success", true, "nonce", ""));
                    return;
                }
                sendJson(exchange, 200, mapOf("success", true, "nonce", AdminAuth.createLoginNonce()));
                return;
            }
            case "session": {
                AdminAuth.Session session = AdminAuth.getSession(exchange);
                if (session == null) {
                    sendJson(exchange, 401, mapOf("success", false, "authenticated", false,
                            "message", "Chua dang nhap"));
                    return;
                }
                sendJson(exchange, 200, mapOf("success", true, "authenticated", true,
                        "username", session.username,
                        "accountId", session.accountId,
                        "csrfToken", session.csrfToken,
                        "expiresIn", session.remainingSeconds()));
                return;
            }
            case "login": {
                if (!"POST".equalsIgnoreCase(method)) {
                    methodNotAllowed(exchange, "POST");
                    return;
                }
                if (!AdminAuth.isEnabled()) {
                    sendJson(exchange, 200, mapOf("success", true, "username", "development"));
                    return;
                }
                // Nonce chống login CSRF / replay: phải khớp nonce đã phát khi tải trang login
                if (!AdminAuth.consumeLoginNonce(payload.get("nonce"))) {
                    sendJson(exchange, 403, mapOf("success", false,
                            "message", "Phien dang nhap khong hop le. Vui long tai lai trang."));
                    return;
                }
                String ip = AdminAuth.clientIp(exchange);
                AdminAuth.AuthResult result = AdminAuth.authenticate(payload.get("username"),
                        payload.get("password"), ip);
                if (result.credentials == null) {
                    sendJson(exchange, 401, mapOf("success", false, "message", result.error));
                    return;
                }
                AdminAuth.Session session = AdminAuth.createSession(exchange, result.credentials.accountId,
                        result.credentials.username, ip);
                sendJson(exchange, 200, mapOf("success", true, "username", session.username,
                        "csrfToken", session.csrfToken, "expiresIn", session.remainingSeconds()));
                return;
            }
            case "register": {
                if (!"POST".equalsIgnoreCase(method)) {
                    methodNotAllowed(exchange, "POST");
                    return;
                }
                // Chống CSRF / replay: dùng chung nonce một lần với form đăng nhập
                if (AdminAuth.isEnabled() && !AdminAuth.consumeLoginNonce(payload.get("nonce"))) {
                    sendJson(exchange, 403, mapOf("success", false,
                            "message", "Phien dang ky khong hop le. Vui long tai lai trang."));
                    return;
                }
                AdminAuth.RegisterResult result = AdminAuth.registerAccount(
                        payload.get("username"), payload.get("password"),
                        payload.get("confirm"), payload.get("email"), AdminAuth.clientIp(exchange));
                sendJson(exchange, result.status,
                        mapOf("success", result.success, "message", result.message));
                return;
            }
            case "logout": {
                if (!"POST".equalsIgnoreCase(method)) {
                    methodNotAllowed(exchange, "POST");
                    return;
                }
                AdminAuth.Session session = AdminAuth.getSession(exchange);
                if (session != null && !AdminAuth.requireCsrf(exchange, session)) {
                    return;
                }
                AdminAuth.destroySession(exchange);
                sendJson(exchange, 200, mapOf("success", true, "message", "Da dang xuat"));
                return;
            }
            default:
                sendText(exchange, 404, "Unknown auth action: " + command);
        }
    }

    private static void methodNotAllowed(HttpExchange exchange, String allow) throws IOException {
        exchange.getResponseHeaders().set("Allow", allow);
        sendText(exchange, 405, "Method Not Allowed");
    }

    private static void handleCrud(HttpExchange exchange, String method, String idParam, Map<String, String> payload,
            String table, String primaryKey, String... fields) throws IOException {
        String sql;
        switch (method.toUpperCase(Locale.ROOT)) {
            case "GET":
                if (idParam != null && !idParam.isEmpty()) {
                    sendJson(exchange, 200, queryRows(table, primaryKey, idParam));
                } else {
                    sendJson(exchange, 200, queryRows(table, primaryKey, null));
                }
                return;
            case "POST":
                if (payload.isEmpty()) {
                    sendJson(exchange, 400, mapOf("success", false, "message", "Empty payload"));
                    return;
                }
                sql = buildInsertSql(table, fields);
                try (Connection connection = DatabaseManager.getConnection();
                        PreparedStatement statement = connection.prepareStatement(sql)) {
                    bindInsertParams(statement, payload, fields);
                    statement.executeUpdate();
                    sendJson(exchange, 200, mapOf("success", true, "message", "Created"));
                } catch (SQLException e) {
                    Logger.logException(AdminApiServer.class, e);
                    sendJson(exchange, 500, mapOf("success", false, "message", "Insert failed"));
                }
                return;
            case "PUT":
                if (idParam == null || idParam.isEmpty()) {
                    sendJson(exchange, 400, mapOf("success", false, "message", "Missing id"));
                    return;
                }
                sql = buildUpdateSql(table, primaryKey, fields);
                try (Connection connection = DatabaseManager.getConnection();
                        PreparedStatement statement = connection.prepareStatement(sql)) {
                    bindUpdateParams(statement, payload, fields, idParam);
                    statement.executeUpdate();
                    sendJson(exchange, 200, mapOf("success", true, "message", "Updated"));
                } catch (SQLException e) {
                    Logger.logException(AdminApiServer.class, e);
                    sendJson(exchange, 500, mapOf("success", false, "message", "Update failed"));
                }
                return;
            case "DELETE":
                if (idParam == null || idParam.isEmpty()) {
                    sendJson(exchange, 400, mapOf("success", false, "message", "Missing id"));
                    return;
                }
                sql = "DELETE FROM " + table + " WHERE " + primaryKey + " = ?";
                try (Connection connection = DatabaseManager.getConnection();
                        PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setInt(1, Integer.parseInt(idParam));
                    statement.executeUpdate();
                    sendJson(exchange, 200, mapOf("success", true, "message", "Deleted"));
                } catch (SQLException e) {
                    Logger.logException(AdminApiServer.class, e);
                    sendJson(exchange, 500, mapOf("success", false, "message", "Delete failed"));
                }
                return;
            default:
                exchange.getResponseHeaders().set("Allow", "GET, POST, PUT, DELETE");
                sendText(exchange, 405, "Method Not Allowed");
        }
    }

    private static List<Map<String, Object>> queryRows(String table, String primaryKey, String idParam) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT * FROM " + table + (idParam != null ? " WHERE " + primaryKey + " = ?" : "") + " ORDER BY "
                + primaryKey + " ASC";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (idParam != null) {
                statement.setInt(1, Integer.parseInt(idParam));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                        String col = resultSet.getMetaData().getColumnName(i);
                        row.put(col, resultSet.getObject(i));
                    }
                    if ("account".equals(table)) {
                        maskAccountSecrets(row);
                    }
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            Logger.logException(AdminApiServer.class, e);
        }
        return rows;
    }

    /**
     * Ẩn các cột nhạy cảm của bảng account trước khi trả JSON về trình duyệt.
     * Mật khẩu không bao giờ được gửi xuống client.
     */
    private static void maskAccountSecrets(Map<String, Object> row) {
        row.remove("password");
        row.remove("token");
        row.remove("xsrf_token");
        row.remove("newpass");
    }

    /**
 * Lấy option mặc định do game code định nghĩa cho item (sao pha lê, ngọc rồng...).
 *
 * <p>Những item này không có option trong DB; option được gắn khi tạo item hoặc khi ép,
 * theo bảng ánh xạ hardcode trong {@link combine.CombineSystem} và
 * {@link item.Item#getOptionDaPhaLe()}. Hàm này gọi lại chính code đó nên web hiển thị
 * đúng với game — không nhân bản bảng ánh xạ.
 *
 * <p>Kết quả chỉ để xem, game không đọc giá trị từ DB cho nhóm option này.
 *
 * @return mỗi dòng = { option_id, param, name, text }; rỗng nếu item không có option mặc định
     */
    private static List<Map<String, Object>> queryItemDefaultOptions(String tempId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int id;
        try {
            id = Integer.parseInt(tempId);
        } catch (NumberFormatException e) {
            return rows;
        }

        Template.ItemTemplate template = Manager.ITEM_TEMPLATES.get(id);
        if (template == null) {
            return rows;
        }

        // Item mẫu chỉ để hỏi code game về option mặc định, không đưa vào túi người chơi
        Item probe = new Item((short) id);
        probe.template = template;

        int optionId = -1;
        int param = -1;

        // Nguồn chính: bảng ánh xạ theo template id trong Item.getOptionDaPhaLe()
        // (phủ sao pha lê 441-447, 1416-1422, 1426-1434 và ngọc rồng 14-20).
        try {
            Item.ItemOption defaultOption = probe.getOptionDaPhaLe();
            if (defaultOption != null && defaultOption.optionTemplate != null) {
                optionId = defaultOption.optionTemplate.id;
                param = defaultOption.param;
            }
        } catch (Exception e) {
            // default của switch là itemOptions.get(0) nên item rỗng sẽ lỗi — coi như không có
            optionId = -1;
            param = -1;
        }

        // Dự phòng: item type 30 không nằm trong bảng id thì hỏi CombineSystem
        if (optionId < 0 || param < 0) {
            try {
                if (CombineSystem.isDaPhaLe(probe)) {
                    optionId = CombineSystem.getOptionDaPhaLe(probe);
                    param = CombineSystem.getParamDaPhaLe(probe);
                }
            } catch (Exception e) {
                // type 30 đọc option của chính item đó nên không suy ra được từ template
                optionId = -1;
                param = -1;
            }
        }

        // Item không thuộc nhóm sao pha lê / option ngẫu nhiên → không có option mặc định
        if (optionId < 0 || param < 0) {
            return rows;
        }
        Template.ItemOptionTemplate optionTemplate = Manager.ITEM_OPTION_TEMPLATES.get(optionId);
        if (optionTemplate == null) {
            return rows;
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("option_id", optionId);
        row.put("param", param);
        row.put("name", optionTemplate.name);
        row.put("text", optionTemplate.name.replace("#", String.valueOf(param)));
        rows.add(row);
        return rows;
    }

    /**
     * Lấy toàn bộ option của một item_template, gồm option gắn trên từng bản ghi shop.
     *
     * <p>item_template không lưu option trực tiếp; option nằm ở {@code item_shop_option}
     * và được gắn với {@code item_shop.temp_id}. Một item có thể xuất hiện ở nhiều shop
     * (nhiều tab), nên mỗi dòng trả về gắn kèm {@code item_shop_id} + tên tab để
     * trình duyệt biết đang sửa option của bản ghi shop nào.
     *
     * @return mỗi dòng = { id, option_id, param, item_shop_id, tab_id, tab_name, shop_tag }
     */
    private static List<Map<String, Object>> queryItemOptionsOfTemp(String tempId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT o.id, o.option_id, o.param, s.id AS item_shop_id, s.tab_id, "
                + "t.name AS tab_name, sh.tag_name AS shop_tag "
                + "FROM item_shop s "
                + "LEFT JOIN item_shop_option o ON o.item_shop_id = s.id "
                + "LEFT JOIN tab_shop t ON t.id = s.tab_id "
                + "LEFT JOIN shop sh ON sh.id = t.shop_id "
                + "WHERE s.temp_id = ? "
                + "ORDER BY s.id ASC, o.id ASC";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Integer.parseInt(tempId));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", resultSet.getObject("id"));
                    row.put("option_id", resultSet.getObject("option_id"));
                    row.put("param", resultSet.getObject("param"));
                    row.put("item_shop_id", resultSet.getObject("item_shop_id"));
                    row.put("tab_id", resultSet.getObject("tab_id"));
                    row.put("tab_name", resultSet.getObject("tab_name"));
                    row.put("shop_tag", resultSet.getObject("shop_tag"));
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            Logger.logException(AdminApiServer.class, e);
        }
        return rows;
    }

    /** Query với WHERE col = value tùy chọn, nếu filterCol null thì lấy tất cả */
    private static List<Map<String, Object>> queryRowsWhere(String table, String primaryKey, String filterCol,
            String filterVal) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT * FROM " + table
                + (filterCol != null ? " WHERE " + filterCol + " = ?" : "")
                + " ORDER BY " + primaryKey + " ASC";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (filterCol != null && filterVal != null) {
                statement.setInt(1, Integer.parseInt(filterVal));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                        String col = resultSet.getMetaData().getColumnName(i);
                        row.put(col, resultSet.getObject(i));
                    }
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            Logger.logException(AdminApiServer.class, e);
        }
        return rows;
    }

    private static void handleOnlinePlayers(HttpExchange exchange, String method) throws IOException {
        if (!"GET".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendJson(exchange, 405, mapOf("success", false, "message", "Method Not Allowed"));
            return;
        }
        List<Map<String, Object>> players = new ArrayList<>();
        for (Player player : Client.gI().getPlayers()) {
            if (player != null && player.name != null) {
                players.add(mapOf("id", player.id, "name", player.name));
            }
        }
        sendJson(exchange, 200, players);
    }

    private static void handleGiveItem(HttpExchange exchange, String method, Map<String, String> payload)
            throws IOException {
        if (!"POST".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Allow", "POST");
            sendJson(exchange, 405, mapOf("success", false, "message", "Method Not Allowed"));
            return;
        }

        final long playerId;
        final int itemId;
        final int quantity;
        try {
            playerId = Long.parseLong(payload.getOrDefault("playerId", ""));
            itemId = Integer.parseInt(payload.getOrDefault("itemId", ""));
            quantity = Integer.parseInt(payload.getOrDefault("quantity", ""));
        } catch (NumberFormatException e) {
            sendJson(exchange, 400, mapOf("success", false, "message", "Thông tin người chơi hoặc vật phẩm không hợp lệ."));
            return;
        }
        if (playerId <= 0 || itemId < 0 || itemId > Short.MAX_VALUE || quantity < 1 || quantity > 99_999) {
            sendJson(exchange, 400, mapOf("success", false, "message", "Số lượng phải từ 1 đến 99.999."));
            return;
        }

        // Không yêu cầu người chơi phải online — nếu offline thì tải dữ liệu từ DB
        Player onlinePlayer = Client.gI().getPlayer(playerId);
        final boolean isOnline = onlinePlayer != null;
        Player player = isOnline ? onlinePlayer : NTTSqlFetcher.loadById(playerId);
        if (player == null) {
            sendJson(exchange, 404, mapOf("success", false, "message", "Không tìm thấy người chơi."));
            return;
        }
        if (itemId >= Manager.ITEM_TEMPLATES.size()
                || ItemService.gI().getTemplate(itemId) == null) {
            sendJson(exchange, 404, mapOf("success", false, "message", "Không tìm thấy vật phẩm."));
            return;
        }

        Item item = ItemService.gI().createNewItem((short) itemId, quantity);
        if (item == null || item.template == null) {
            sendJson(exchange, 404, mapOf("success", false, "message", "Không thể tạo vật phẩm."));
            return;
        }
        if (!InventoryService.gI().addItemBag(player, item)) {
            sendJson(exchange, 409, mapOf("success", false, "message", "Hành trang người chơi không đủ chỗ."));
            return;
        }

        if (isOnline) {
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendThongBao(player, "Admin đã tặng bạn " + quantity + " " + item.template.name + ".");
        }
        // Lưu ngay vào DB (với người chơi offline đây là bước bắt buộc)
        PlayerDAO.updatePlayer(player);
        sendJson(exchange, 200, mapOf("success", true, "message",
                "Đã tặng " + quantity + " " + item.template.name + " cho " + player.name
                        + (isOnline ? "." : " (offline, vật phẩm đã lưu vào hành trang).")));
    }

    /**
     * Query players với LEFT JOIN head_avatar để lấy avatar_id.
     * Kết quả trả thêm field "avatar_id" — dùng load ảnh
     * /data/icon/x4/{avatar_id}.png.
     * Nếu player.head không có trong head_avatar thì avatar_id = null.
     */
    private static List<Map<String, Object>> queryPlayers(String idParam) {
        List<Map<String, Object>> rows = new ArrayList<>();
        // LEFT JOIN để giữ tất cả player, kể cả head không có trong head_avatar
        // Alias ha.avatar_id → head_avatar_id để tránh conflict với cột khác
        String sql = "SELECT p.*, ha.avatar_id AS head_avatar_id "
                + "FROM player p "
                + "LEFT JOIN head_avatar ha ON p.head = ha.head_id "
                + (idParam != null ? "WHERE p.id = ? " : "")
                + "ORDER BY p.id ASC";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (idParam != null) {
                statement.setInt(1, Integer.parseInt(idParam));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                        String col = resultSet.getMetaData().getColumnLabel(i); // dùng getColumnLabel để lấy alias
                        row.put(col, resultSet.getObject(i));
                    }
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            Logger.logException(AdminApiServer.class, e);
        }
        return rows;
    }

    /**
     * Trả về map head_id → avatar_id dạng JSON object phẳng.
     * Frontend dùng để tra cứu nhanh không cần vòng lặp.
     * Ví dụ: { "0": 516, "6": 520, "102": 1363 }
     */
    private static Map<String, Object> queryHeadAvatars() {
        Map<String, Object> result = new LinkedHashMap<>();
        String sql = "SELECT head_id, avatar_id FROM head_avatar ORDER BY head_id ASC";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                result.put(String.valueOf(rs.getInt("head_id")), rs.getInt("avatar_id"));
            }
        } catch (Exception e) {
            Logger.logException(AdminApiServer.class, e);
        }
        return result;
    }

    /**
     * GET /api/tasks → nhiệm vụ chính + các bước của nó (task_sub_template).
     * Mỗi phần tử: { task_id, task_name, sub_index, sub_name, sub_max }
     * Frontend gom lại thành map task_id → { name, subs[] } để hiển thị
     * "đang ở nhiệm vụ nào" từ player.data_task ([task_id, sub_index, count, lastTime]).
     */
    private static List<Map<String, Object>> queryTasks() {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT tm.id AS task_id, tm.NAME AS task_name, ts.NAME AS sub_name, "
                + "ts.max_count AS sub_max "
                + "FROM task_main_template tm "
                + "JOIN task_sub_template ts ON tm.id = ts.task_main_id "
                + "ORDER BY tm.id ASC, ts.NguyenTanTaiPro ASC";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            int currentTask = -1;
            int subIndex = -1;
            while (resultSet.next()) {
                int taskId = resultSet.getInt("task_id");
                if (taskId != currentTask) {
                    currentTask = taskId;
                    subIndex = 0;
                } else {
                    subIndex++;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("task_id", taskId);
                row.put("task_name", resultSet.getString("task_name"));
                row.put("sub_index", subIndex);
                row.put("sub_name", resultSet.getString("sub_name"));
                row.put("sub_max", resultSet.getInt("sub_max"));
                rows.add(row);
            }
        } catch (Exception e) {
            Logger.logException(AdminApiServer.class, e);
        }
        return rows;
    }

    /**
     * GET /api/player-stats?range=day|week|month
     * Thống kê lượng người chơi theo từng mốc thời gian, mỗi phần tử gồm:
     * - label         : nhãn trục X (dd/MM với ngày & tuần, MM/yyyy với tháng)
     * - from / to     : ngày bắt đầu / kết thúc của mốc (yyyy-MM-dd)
     * - newPlayers    : số nhân vật tạo mới trong mốc (player.create_time)
     * - activePlayers : số nhân vật đăng nhập trong mốc (player.firstTimeLogin = ngày login gần nhất)
     * - totalPlayers  : tổng nhân vật đã tạo tính đến cuối mốc
     */
    private static List<Map<String, Object>> queryPlayerStats(String range) {
        String mode = range == null ? "day" : range.trim().toLowerCase(Locale.ROOT);
        int bucketCount;
        if ("week".equals(mode)) {
            bucketCount = STATS_WEEK_BUCKETS;
        } else if ("month".equals(mode)) {
            bucketCount = STATS_MONTH_BUCKETS;
        } else {
            mode = "day";
            bucketCount = STATS_DAY_BUCKETS;
        }

        LocalDate today = LocalDate.now();
        List<LocalDate> starts = new ArrayList<>();
        List<LocalDate> ends = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        switch (mode) {
            case "week": {
                LocalDate monday = today.with(DayOfWeek.MONDAY);
                for (int i = bucketCount - 1; i >= 0; i--) {
                    LocalDate start = monday.minusWeeks(i);
                    starts.add(start);
                    ends.add(start.plusDays(6));
                    labels.add(start.format(STATS_DAY_LABEL));
                }
                break;
            }
            case "month": {
                LocalDate firstOfMonth = today.withDayOfMonth(1);
                for (int i = bucketCount - 1; i >= 0; i--) {
                    LocalDate start = firstOfMonth.minusMonths(i);
                    starts.add(start);
                    ends.add(start.plusMonths(1).minusDays(1));
                    labels.add(start.format(STATS_MONTH_LABEL));
                }
                break;
            }
            default: {
                for (int i = bucketCount - 1; i >= 0; i--) {
                    LocalDate day = today.minusDays(i);
                    starts.add(day);
                    ends.add(day);
                    labels.add(day.format(STATS_DAY_LABEL));
                }
            }
        }

        long[] newPlayers = new long[bucketCount];
        long[] activePlayers = new long[bucketCount];
        long createdBeforeWindow = 0;

        LocalDate windowStart = starts.get(0);
        LocalDate windowEnd = ends.get(bucketCount - 1);
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("SELECT create_time, firstTimeLogin FROM player");
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Timestamp created = resultSet.getTimestamp("create_time");
                if (created != null) {
                    LocalDate day = created.toLocalDateTime().toLocalDate();
                    if (day.isBefore(windowStart)) {
                        createdBeforeWindow++;
                    } else if (!day.isAfter(windowEnd)) {
                        int index = findStatsBucket(starts, ends, day);
                        if (index >= 0) {
                            newPlayers[index]++;
                        }
                    }
                }
                Timestamp lastLogin = resultSet.getTimestamp("firstTimeLogin");
                if (lastLogin != null) {
                    int index = findStatsBucket(starts, ends, lastLogin.toLocalDateTime().toLocalDate());
                    if (index >= 0) {
                        activePlayers[index]++;
                    }
                }
            }
        } catch (Exception e) {
            Logger.logException(AdminApiServer.class, e);
        }

        List<Map<String, Object>> points = new ArrayList<>();
        long cumulative = createdBeforeWindow;
        for (int i = 0; i < bucketCount; i++) {
            cumulative += newPlayers[i];
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("label", labels.get(i));
            point.put("range", mode);
            point.put("from", starts.get(i).toString());
            point.put("to", ends.get(i).toString());
            point.put("newPlayers", newPlayers[i]);
            point.put("activePlayers", activePlayers[i]);
            point.put("totalPlayers", cumulative);
            points.add(point);
        }
        return points;
    }

    /** Tìm mốc thời gian chứa ngày day, trả về -1 nếu nằm ngoài khoảng thống kê */
    private static int findStatsBucket(List<LocalDate> starts, List<LocalDate> ends, LocalDate day) {
        for (int i = 0; i < starts.size(); i++) {
            if (!day.isBefore(starts.get(i)) && !day.isAfter(ends.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /** Lấy giá trị tham số trên query string. VD: queryParam("range=day", "range") → "day" */
    private static String queryParam(String query, String name) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String part : query.split("&")) {
            if (part.startsWith(name + "=")) {
                String value = part.substring(name.length() + 1);
                try {
                    return URLDecoder.decode(value, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Map<String, String> readBody(HttpExchange exchange) throws IOException {
        Map<String, String> values = new HashMap<>();
        if (exchange.getRequestBody() == null) {
            return values;
        }
        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (raw == null || raw.isEmpty()) {
            return values;
        }
        if (raw.startsWith("{")) {
            Matcher matcher = JSON_VALUE_PATTERN.matcher(raw);
            while (matcher.find()) {
                String key = matcher.group(1);
                String value = matcher.group(2);
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    values.put(key, value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\"));
                } else {
                    values.put(key, value.equals("null") ? "" : value);
                }
            }
            return values;
        }
        String[] pairs = raw.split("&");
        for (String pair : pairs) {
            int index = pair.indexOf('=');
            if (index < 0) {
                continue;
            }
            String key = pair.substring(0, index);
            String value = pair.substring(index + 1);
            values.put(key, value);
        }
        return values;
    }

    private static String buildInsertSql(String table, String... fields) {
        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                columns.append(", ");
                placeholders.append(", ");
            }
            columns.append(fields[i]);
            placeholders.append("?");
        }
        return "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")";
    }

    private static String buildUpdateSql(String table, String primaryKey, String... fields) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(fields[i]).append(" = ?");
        }
        return "UPDATE " + table + " SET " + builder + " WHERE " + primaryKey + " = ?";
    }

    private static void bindInsertParams(PreparedStatement statement, Map<String, String> payload, String... fields)
            throws SQLException {
        int index = 1;
        for (String field : fields) {
            String rawValue = payload.get(field);
            if (rawValue == null) {
                statement.setNull(index++, java.sql.Types.NULL);
            } else {
                setValue(statement, index++, rawValue);
            }
        }
    }

    private static void bindUpdateParams(PreparedStatement statement, Map<String, String> payload, String[] fields,
            String idParam) throws SQLException {
        int index = 1;
        for (String field : fields) {
            String rawValue = payload.get(field);
            if (rawValue == null) {
                statement.setNull(index++, java.sql.Types.NULL);
            } else {
                setValue(statement, index++, rawValue);
            }
        }
        statement.setInt(index, Integer.parseInt(idParam));
    }

    private static void setValue(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            statement.setNull(index, java.sql.Types.NULL);
            return;
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            statement.setBoolean(index, Boolean.parseBoolean(value));
            return;
        }
        try {
            Integer intValue = Integer.valueOf(value);
            statement.setInt(index, intValue);
            return;
        } catch (NumberFormatException ignored) {
        }
        try {
            Long longValue = Long.valueOf(value);
            statement.setLong(index, longValue);
            return;
        } catch (NumberFormatException ignored) {
        }
        statement.setString(index, value);
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Map<String, Object> payload)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        String content = jsonFromObject(payload);
        send(exchange, statusCode, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendJson(HttpExchange exchange, int statusCode, List<Map<String, Object>> payload)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        String content = jsonFromList(payload);

        send(exchange, statusCode, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendText(HttpExchange exchange, int statusCode, String text) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        send(exchange, statusCode, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int statusCode, byte[] content) throws IOException {
        // Header bảo mật cho mọi response của Admin API / Admin Panel
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.sendResponseHeaders(statusCode, content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private static String jsonFromList(List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(jsonFromObject(rows.get(i)));
        }
        builder.append("]");
        return builder.toString();
    }

    private static String jsonFromObject(Map<String, Object> row) {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        int idx = 0;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (idx++ > 0) {
                builder.append(",");
            }
            builder.append('"').append(escapeJson(entry.getKey())).append('"').append(":");
            Object value = entry.getValue();
            if (value == null) {
                builder.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else if (value instanceof String[]) {
                builder.append(jsonFromStringArray((String[]) value));
            } else if (value instanceof Object[]) {
                builder.append(jsonFromObjectArray((Object[]) value));
            } else if (value instanceof List) {
                builder.append(jsonFromAnyList((List<?>) value));
            } else {
                builder.append('"').append(escapeJson(String.valueOf(value))).append('"');
            }
        }
        builder.append("}");
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static String jsonFromAnyList(List<?> values) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            Object value = values.get(i);
            if (value == null) {
                builder.append("null");
            } else if (value instanceof Map) {
                builder.append(jsonFromObject((Map<String, Object>) value));
            } else if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else {
                builder.append('"').append(escapeJson(String.valueOf(value))).append('"');
            }
        }
        builder.append("]");
        return builder.toString();
    }

    private static String jsonFromStringArray(String[] values) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append('"').append(escapeJson(values[i])).append('"');
        }
        builder.append("]");
        return builder.toString();
    }

    private static String jsonFromObjectArray(Object[] values) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(String.valueOf(values[i]));
        }
        builder.append("]");
        return builder.toString();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * GET /api/drop-rates → toàn bộ bảng tỉ lệ rơi đồ + hệ số nhân chung
     * PUT /api/drop-rates { "<key>": "<phần trăm>", ..., "globalMultiplier": "150", "reset": "true" }
     *     → cập nhật, lưu vào DropRate.properties rồi trả về bảng mới
     */
    private static void handleDropRates(HttpExchange exchange, String method, Map<String, String> payload)
            throws IOException {
        if ("GET".equalsIgnoreCase(method)) {
            sendDropRates(exchange);
            return;
        }
        if (!"PUT".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, PUT, POST");
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        boolean changed = false;
        if ("true".equalsIgnoreCase(payload.get("reset"))) {
            DropRateManager.resetDefaults();
            // Khôi phục luôn hệ số nhân chung về 100 (bình thường)
            Manager.RATE_DROP_ITEM = 100;
            changed = true;
        }

        String global = payload.containsKey("globalMultiplier") ? payload.get("globalMultiplier")
                : payload.get("rateDrop");
        if (global != null) {
            try {
                int v = (int) Math.round(Double.parseDouble(global.trim()));
                if (v >= 0) {
                    Manager.RATE_DROP_ITEM = v;
                    changed = true;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        int updated = 0;
        for (DropRateManager.Rule rule : DropRateManager.rules()) {
            String raw = payload.get(rule.key);
            if (raw == null) {
                continue;
            }
            try {
                if (DropRateManager.setPercent(rule.key, Double.parseDouble(raw.trim()))) {
                    updated++;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (updated > 0) {
            changed = true;
        }
        if (changed) {
            DropRateManager.save();
        }
        sendDropRates(exchange);
    }

    private static void sendDropRates(HttpExchange exchange) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", Boolean.TRUE);
        body.put("globalMultiplier", Manager.RATE_DROP_ITEM);
        body.put("rules", DropRateManager.forApi());
        sendJson(exchange, 200, body);
    }

    /**
     * GET /api/combine-rates → toàn bộ tỉ lệ đập / nâng cấp đồ + hệ số nhân chung
     * PUT /api/combine-rates { "<key>": "<phần trăm>", ... (-1 = trở về tỉ lệ gốc),
     *     "globalMultiplier": "150", "reset": "true" }
     */
    private static void handleCombineRates(HttpExchange exchange, String method, Map<String, String> payload)
            throws IOException {
        if ("GET".equalsIgnoreCase(method)) {
            sendCombineRates(exchange);
            return;
        }
        if (!"PUT".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, PUT, POST");
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        boolean changed = false;
        if ("true".equalsIgnoreCase(payload.get("reset"))) {
            CombineRateManager.resetOverrides();
            Manager.RATE_COMBINE = 100;
            changed = true;
        }

        String global = payload.containsKey("globalMultiplier") ? payload.get("globalMultiplier")
                : payload.get("rateCombine");
        if (global != null) {
            try {
                int v = (int) Math.round(Double.parseDouble(global.trim()));
                if (v >= 0) {
                    Manager.RATE_COMBINE = v;
                    changed = true;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        int updated = 0;
        for (CombineRateManager.Rule rule : CombineRateManager.rules()) {
            String raw = payload.get(rule.key);
            if (raw == null) {
                continue;
            }
            try {
                if (CombineRateManager.setPercent(rule.key, Double.parseDouble(raw.trim()))) {
                    updated++;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (updated > 0) {
            changed = true;
        }
        if (changed) {
            CombineRateManager.save();
        }
        sendCombineRates(exchange);
    }

    private static void sendCombineRates(HttpExchange exchange) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", Boolean.TRUE);
        body.put("globalMultiplier", Manager.RATE_COMBINE);
        body.put("rules", CombineRateManager.forApi());
        sendJson(exchange, 200, body);
    }

    /**
     * GET /api/settings → trả về các thông số server hiện tại
     * PUT /api/settings { "rateExp": 10, "rateDrop": 150, "rateCombine": 80 }
     */
    private static void handleSettings(HttpExchange exchange, String method, Map<String, String> payload)
            throws IOException {
        if ("GET".equalsIgnoreCase(method)) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("rateExp", (Object) (Integer) Manager.RATE_EXP_SERVER);
            s.put("rateDrop", (Object) (Integer) Manager.RATE_DROP_ITEM);
            s.put("rateCombine", (Object) (Integer) Manager.RATE_COMBINE);
            s.put("maintenance", (Object) Maintenance.isRunning);
            s.put("maintenanceMinutes", (Object) Maintenance.getMaintenanceDelayMinutes());
            s.put("serverRunning", (Object) Boolean.valueOf(ServerManager.isRunning));
            sendJson(exchange, 200, s);
            return;
        }
        if ("PUT".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) {
            int maintenanceMinutes = Maintenance.getMaintenanceDelayMinutes();
            if (payload.containsKey("maintenanceMinutes")) {
                try {
                    int v = Integer.parseInt(payload.get("maintenanceMinutes"));
                    if (v >= 1) {
                        maintenanceMinutes = v;
                        Maintenance.setMaintenanceDelayMinutes(v);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (payload.containsKey("rateExp")) {
                try {
                    int v = Integer.parseInt(payload.get("rateExp"));
                    if (v >= 1)
                        Manager.RATE_EXP_SERVER = v;
                } catch (NumberFormatException ignored) {
                }
            }
            if (payload.containsKey("rateDrop")) {
                try {
                    int v = Integer.parseInt(payload.get("rateDrop"));
                    if (v >= 1) {
                        Manager.RATE_DROP_ITEM = v;
                        // Lưu lại để giữ sau khi restart
                        DropRateManager.save();
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (payload.containsKey("rateCombine")) {
                try {
                    int v = Integer.parseInt(payload.get("rateCombine"));
                    if (v >= 1)
                        Manager.RATE_COMBINE = v;
                } catch (NumberFormatException ignored) {
                }
            }
            if (payload.containsKey("maintenance")) {
                boolean wantMaintenance = "true".equalsIgnoreCase(payload.get("maintenance"));
                if (wantMaintenance && !Maintenance.isRunning) {
                    // Thời gian bảo trì được cấu hình theo phút, chuyển về giây để đếm ngược
                    Maintenance.gI().startNew(maintenanceMinutes * 60);
                } else if (!wantMaintenance) {
                    // Tắt cờ bảo trì (nếu chưa kick thì huỷ)
                    Maintenance.isRunning = false;
                }
            }
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("success", (Object) Boolean.TRUE);
            s.put("rateExp", (Object) (Integer) Manager.RATE_EXP_SERVER);
            s.put("rateDrop", (Object) (Integer) Manager.RATE_DROP_ITEM);
            s.put("rateCombine", (Object) (Integer) Manager.RATE_COMBINE);
            s.put("maintenance", (Object) Maintenance.isRunning);
            s.put("maintenanceMinutes", (Object) Maintenance.getMaintenanceDelayMinutes());
            s.put("serverRunning", (Object) Boolean.valueOf(ServerManager.isRunning));
            sendJson(exchange, 200, s);
            return;
        }
        exchange.getResponseHeaders().set("Allow", "GET, PUT");
        sendText(exchange, 405, "Method Not Allowed");
    }

    private static void handleServerControl(HttpExchange exchange, Map<String, String> payload) throws IOException {
        if (ServerManager.isRunning) {
            sendJson(exchange, 409, mapOf("success", false, "message", "Server đang chạy rồi."));
            return;
        }

        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        Path batPath = projectRoot.resolve("run.bat");
        if (!Files.exists(batPath)) {
            sendJson(exchange, 404, mapOf("success", false, "message", "Không tìm thấy file run.bat trong thư mục dự án."));
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "start", "", "/B", "run.bat");
            pb.directory(projectRoot.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            sendJson(exchange, 200, mapOf("success", exitCode == 0, "message",
                    exitCode == 0 ? "Đã gửi lệnh khởi động server." : "Lệnh khởi động server đã chạy nhưng trả về mã lỗi: " + exitCode));
        } catch (Exception e) {
            Logger.logException(AdminApiServer.class, e);
            sendJson(exchange, 500, mapOf("success", false, "message", "Không thể chạy lệnh khởi động server: " + e.getMessage()));
        }
    }

    /** POST /api/announce { "text": "Thông báo..." } → gửi thông báo toàn server */
    private static void handleAnnounce(HttpExchange exchange, Map<String, String> payload) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"POST".equalsIgnoreCase(method) && !"PUT".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Allow", "POST");
            sendJson(exchange, 405, mapOf("success", false, "message", "Method Not Allowed"));
            return;
        }
        String text = payload.getOrDefault("text", "").trim();
        if (text.isEmpty()) {
            sendJson(exchange, 400, mapOf("success", false, "message", "text is required"));
            return;
        }
        ServerNotify.gI().notify(text);
        sendJson(exchange, 200, mapOf("success", true, "message", "Đã gửi thông báo"));
    }

    /** Trả về danh sách boss từ tất cả BossManager runtime */
    private static List<Map<String, Object>> queryBosses() {
        List<Map<String, Object>> result = new ArrayList<>();

        // Load head_avatar map: { head_item_id -> avatar_icon_id }
        java.util.Map<Integer, Integer> headAvatarMap = new java.util.HashMap<>();
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT head_id, avatar_id FROM head_avatar");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                headAvatarMap.put(rs.getInt("head_id"), rs.getInt("avatar_id"));
            }
        } catch (Exception e) {
            Logger.logException(AdminApiServer.class, e);
        }

        // Gom boss từ tất cả manager
        List<boss.Boss> allBosses = new ArrayList<>();
        allBosses.addAll(boss.BossManager.BossManager.gI().getBosses());
        allBosses.addAll(boss.BossManager.FinalBossManager.gI().getBosses());
        allBosses.addAll(boss.BossManager.BrolyManager.gI().getBosses());

        try {
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (boss.Boss b : allBosses) {
                if (b == null || b.name == null)
                    continue;

                String key = String.valueOf(b.id);
                if (seen.contains(key))
                    continue;
                seen.add(key);

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", b.id);
                row.put("name", b.name);
                row.put("hp", b.nPoint != null ? b.nPoint.hpMax : 0);
                row.put("dame", b.nPoint != null ? b.nPoint.dame : 0);
                row.put("secondsRest", b.getSecondsRest());
                row.put("status", b.bossStatus != null ? b.bossStatus.toString() : "REST");

                // outfit[0] = head item id → tra head_avatar
                int avatarId = -1;
                try {
                    if (b.data != null && b.data.length > 0
                            && b.data[0] != null
                            && b.data[0].getOutfit() != null
                            && b.data[0].getOutfit().length > 0) {
                        int headItemId = b.data[0].getOutfit()[0];
                        avatarId = headAvatarMap.getOrDefault(headItemId, -1);
                    }
                } catch (Exception ignored) {
                }

                row.put("headIcon", avatarId);
                result.add(row);
            }
        } catch (Exception e) {
            Logger.logException(AdminApiServer.class, e);
        }
        return result;
    }
}