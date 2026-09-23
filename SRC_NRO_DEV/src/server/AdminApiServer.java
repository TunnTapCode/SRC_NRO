package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import database.DatabaseManager;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import utils.Logger;

public final class AdminApiServer {

    private static final int PORT = 8081;
    private static HttpServer server;
    private static final Pattern JSON_VALUE_PATTERN = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*(\\\"(?:\\\\.|[^\\\"])*\\\"|\\d+|true|false|null)");

    private AdminApiServer() {
    }

    public static void start() {
        if (server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
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
            Logger.success("Admin API: http://127.0.0.1:" + PORT + "/admin\n");
        } catch (IOException e) {
            Logger.logException(AdminApiServer.class, e);
        }
    }

    private static void handleAdmin(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getPath();
        if ("/admin".equals(rawPath)) {
            exchange.getResponseHeaders().set("Location", "/admin/");
            exchange.sendResponseHeaders(301, -1);
            return;
        }
        if ("/styles.css".equals(rawPath) || "/app.js".equals(rawPath) || "/favicon.ico".equals(rawPath)) {
            rawPath = "/admin" + rawPath;
        }
        if (rawPath == null || "/".equals(rawPath) || "/admin/".equals(rawPath)) {
            rawPath = "/admin/index.html";
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
        exchange.getResponseHeaders().set("Content-Type", mime + (mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json") ? "; charset=UTF-8" : ""));
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
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
            if (lower.endsWith(".png")) mime = "image/png";
            else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) mime = "image/jpeg";
            else if (lower.endsWith(".gif")) mime = "image/gif";
            else if (lower.endsWith(".webp")) mime = "image/webp";
            else mime = "application/octet-stream";
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
        if (resource.isEmpty()) {
            sendJson(exchange, 200, mapOf("success", true, "message", "Admin API ready", "endpoints", new String[]{
                "/api/accounts",
                "/api/players",
                "/api/giftcodes",
                "/api/items",
                "/api/shops",
                "/api/npcs"
            }));
            return;
        }

        String idParam = pathParts.length > 1 ? pathParts[1] : null;
        String method = exchange.getRequestMethod();
        Map<String, String> payload = readBody(exchange);

        switch (resource) {
            case "accounts":
                handleCrud(exchange, method, idParam, payload, "account", "id", "username", "password", "email", "is_admin", "active", "ban");
                return;
            case "players":
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 200, queryPlayers(idParam));
                } else {
                    handleCrud(exchange, method, idParam, payload, "player", "id", "account_id", "name", "head", "gender", "clan_id", "rank", "power");
                }
                return;
            case "giftcodes":
                handleCrud(exchange, method, idParam, payload, "giftcode", "id", "code", "count_left", "detail", "expired");
                return;
            case "items":
                handleCrud(exchange, method, idParam, payload, "item_template", "id", "TYPE", "NAME", "description", "level", "icon_id", "part", "is_up_to_up", "power_require", "gold", "gem", "head", "body", "leg");
                return;
            case "shops":
                handleCrud(exchange, method, idParam, payload, "shop", "id", "npc_id", "tag_name", "type_shop");
                return;
            case "npcs":
                handleCrud(exchange, method, idParam, payload, "npc_template", "id", "NAME", "head", "body", "leg", "avatar");
                return;
            case "head-avatars":
                // Trả về map { head_id: avatar_id } dạng flat JSON object để frontend tra nhanh
                sendJson(exchange, 200, queryHeadAvatars());
                return;
            default:
                sendText(exchange, 404, "Unknown resource: " + resource);
        }
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
                try (Connection connection = DatabaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
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
                try (Connection connection = DatabaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
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
                try (Connection connection = DatabaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
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
        String sql = "SELECT * FROM " + table + (idParam != null ? " WHERE " + primaryKey + " = ?" : "") + " ORDER BY " + primaryKey + " ASC";
        try (Connection connection = DatabaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
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
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            Logger.logException(AdminApiServer.class, e);
        }
        return rows;
    }

    /**
     * Query players với LEFT JOIN head_avatar để lấy avatar_id.
     * Kết quả trả thêm field "avatar_id" — dùng load ảnh /data/icon/x1/{avatar_id}.png.
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

    private static void bindInsertParams(PreparedStatement statement, Map<String, String> payload, String... fields) throws SQLException {
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

    private static void bindUpdateParams(PreparedStatement statement, Map<String, String> payload, String[] fields, String idParam) throws SQLException {
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

    private static void sendJson(HttpExchange exchange, int statusCode, Map<String, Object> payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        String content = jsonFromObject(payload);
        send(exchange, statusCode, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendJson(HttpExchange exchange, int statusCode, List<Map<String, Object>> payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        String content = jsonFromList(payload);
        send(exchange, statusCode, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendText(HttpExchange exchange, int statusCode, String text) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        send(exchange, statusCode, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int statusCode, byte[] content) throws IOException {
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
}
