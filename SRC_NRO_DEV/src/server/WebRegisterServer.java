package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import database.DatabaseManager;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import utils.Logger;

public final class WebRegisterServer {

    private static final int START_PORT = 8080;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9]{4,20}");
    private static HttpServer server;

    private WebRegisterServer() {
    }

    public static void start() {
        if (server != null) {
            return;
        }
        for (int port = START_PORT; port < START_PORT + 10; port++) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                server.createContext("/register", WebRegisterServer::handleRegister);
                server.createContext("/admin", WebRegisterServer::handleAdmin);
                server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                    Thread thread = new Thread(runnable, "Web-register");
                    return thread;
                }));
                server.start();
                Logger.success("Web dang ky: http://127.0.0.1:" + port + "/register\n");
                return;
            } catch (IOException e) {
                server = null;
            }
        }
        Logger.error("Khong the khoi dong web dang ky tu cong " + START_PORT + " den " + (START_PORT + 9) + "\n");
    }

    private static void handleAdmin(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String query = exchange.getRequestURI().getRawQuery();
        String tab = getQueryParam(query, "tab", "items");
        String search = getQueryParam(query, "q", "");
        sendHtml(exchange, buildAdminPage(tab, search));
    }

    private static void handleRegister(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendHtml(exchange, page("", false, "", ""));
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET, POST");
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = parseForm(body);
        String username = form.getOrDefault("username", "").trim().toLowerCase();
        String password = form.getOrDefault("password", "");
        String confirm = form.getOrDefault("confirm", "");
        String email = form.getOrDefault("email", "").trim();

        String message;
        boolean success = false;
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            message = "Tai khoan chi gom chu thuong va so, dai tu 4 den 20 ky tu.";
        } else if (password.length() < 4 || password.length() > 100) {
            message = "Mat khau phai dai tu 4 den 100 ky tu.";
        } else if (!password.equals(confirm)) {
            message = "Mat khau nhap lai khong khop.";
        } else if (email.length() > 255) {
            message = "Email qua dai.";
        } else {
            message = createAccount(username, password, email);
            success = message.startsWith("Dang ky thanh cong");
        }
        sendHtml(exchange, page(message, success, username, email));
    }

    private static String createAccount(String username, String password, String email) {
        String checkSql = "SELECT id FROM account WHERE username = ? LIMIT 1";
        String insertSql = "INSERT INTO account (username, password, email, token, xsrf_token, newpass) VALUES (?, ?, ?, '', '', '')";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement check = connection.prepareStatement(checkSql)) {
            check.setString(1, username);
            try (ResultSet result = check.executeQuery()) {
                if (result.next()) {
                    return "Tai khoan da ton tai.";
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setString(1, username);
                insert.setString(2, password);
                insert.setString(3, email);
                insert.executeUpdate();
            }
            return "Dang ky thanh cong. Ban co the dang nhap game ngay.";
        } catch (Exception e) {
            Logger.logException(WebRegisterServer.class, e);
            return "Khong the tao tai khoan. Hay kiem tra ket noi database.";
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> values = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private static void sendHtml(HttpExchange exchange, String html) throws IOException {
        byte[] content = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private static String page(String message, boolean success, String username, String email) {
        String escapedMessage = escapeHtml(message);
        String escapedUsername = escapeHtml(username);
        String escapedEmail = escapeHtml(email);
        String messageBlock = message.isEmpty() ? "" : "<div class=\"message " + (success ? "success" : "error") + "\">" + escapedMessage + "</div>";
        return """
                <!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Dang ky tai khoan</title><style>
                *{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;padding:24px;background:
    linear-gradient(#07111acc, #07111acc),
    url("https://bizweb.dktcdn.net/100/330/208/files/hinh-nen-7-vien-ngoc-rong-8.jpg?v=1655530313662")
    center/cover no-repeat fixed;color:#edf4f7;font:16px Georgia,serif;overflow:hidden}
                .dragon-balls{position:fixed;inset:0;pointer-events:none;filter:drop-shadow(0 12px 18px #0008)}.ball{position:absolute;width:68px;height:68px;border-radius:50%;display:grid;place-items:center;background:radial-gradient(circle at 32% 26%,#ffd477 0,#f39a28 35%,#ad4816 100%);border:2px solid #ffcc62;box-shadow:inset -10px -12px 18px #7d2c15,inset 8px 8px 12px #fff5a055;color:#a62c12;font:bold 22px Georgia,serif}.ball:after{content:"*";transform:translateY(-1px)}.b1{left:8vw;top:14vh}.b2{left:24vw;top:8vh}.b3{right:23vw;top:10vh}.b4{right:7vw;top:22vh}.b5{right:10vw;bottom:14vh}.b6{left:22vw;bottom:8vh}.b7{left:7vw;bottom:22vh}
                main{position:relative;z-index:1;width:min(100%,440px);padding:32px;background:#1b2630ee;border:1px solid #596b73;box-shadow:0 18px 60px #000a}h1{margin:0 0 8px;font-size:30px}p{color:#9eb0ba;margin:0 0 24px}label{display:block;margin:15px 0 6px;color:#9eb0ba}input{width:100%;padding:12px;border:1px solid #334451;background:#111820;color:#edf4f7;font:inherit}button{width:100%;margin-top:24px;padding:13px;border:0;background:#f0b35a;color:#20170c;font:bold 16px Georgia,serif;cursor:pointer}.message{padding:12px;margin-bottom:18px}.error{background:#401f25;color:#ee817b}.success{background:#19382d;color:#73d3a2}.link-row{margin-top:18px;text-align:center}.link-row a{color:#f0b35a;text-decoration:none;font-weight:bold}.link-row a:hover{text-decoration:underline}@media(max-width:600px){.ball{width:48px;height:48px;font-size:17px}.b2,.b3{top:3vh}.b5,.b6{bottom:3vh}}
                </style></head><body><div class="dragon-balls"><i class="ball b1"></i><i class="ball b2"></i><i class="ball b3"></i><i class="ball b4"></i><i class="ball b5"></i><i class="ball b6"></i><i class="ball b7"></i></div><main><h1>Tao tai khoan</h1><p>Dang ky tai khoan de dang nhap may chu.</p>
                __MESSAGE__<form method="post"><label>Ten tai khoan</label><input name="username" value="__USERNAME__" minlength="4" maxlength="20" pattern="[a-z0-9]+" required>
                <label>Mat khau</label><input name="password" type="password" minlength="4" maxlength="100" required>
                <label>Nhap lai mat khau</label><input name="confirm" type="password" minlength="4" maxlength="100" required>
                <label>Email (khong bat buoc)</label><input name="email" type="email" value="__EMAIL__" maxlength="255"><button>Dang ky tai khoan</button></form><div class="link-row"><a href="/admin">Quản lý NPC / Trang bị</a></div></main></body></html>
                """.replace("__MESSAGE__", messageBlock).replace("__USERNAME__", escapedUsername).replace("__EMAIL__", escapedEmail);
    }

    private static String buildAdminPage(String tab, String search) {
        String selected = (tab == null || tab.isBlank()) ? "items" : tab;
        String keyword = search == null ? "" : search.trim();
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang='vi'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'><title>Admin Manager</title>");
        html.append("<style>*{box-sizing:border-box}body{margin:0;font-family:Arial,Helvetica,sans-serif;background:#dfeaf3;color:#1f2d38}a{text-decoration:none} .window{width:min(1200px,calc(100% - 28px));margin:18px auto;border:1px solid #bfd0d8;border-radius:10px;overflow:hidden;box-shadow:0 10px 30px rgba(30,50,60,.12)} .topbar{display:flex;justify-content:space-between;align-items:center;padding:12px 18px;background:linear-gradient(#daebf6,#d6e4f4);border-bottom:1px solid #bfd0d8} .nav{display:flex;gap:8px;align-items:center} .btn{display:inline-block;padding:9px 16px;border:1px solid #324a59;border-radius:7px;background:#3d5764;color:#fff;font-weight:700;cursor:pointer} .btn.alt{background:#f3f8fa;border-color:#c4d5dc;color:#233d4c} .title{font-size:clamp(32px,3vw,46px);font-weight:700;letter-spacing:-0.04em;color:#2f4f7d} .toolbar{display:flex;justify-content:space-between;gap:12px;align-items:center;padding:12px 18px;border-bottom:1px solid #bfd0d8;background:rgba(255,255,255,.2)} .tabs{display:flex;gap:10px;flex-wrap:wrap}.tab{padding:9px 16px;border:1px solid #c4d5dc;border-radius:7px;background:#f8fbfd;color:#284b5d;font-weight:700}.tab.active{background:#3e7ec2;border-color:#3e7ec2;color:#fff}.search{display:flex;align-items:center;gap:8px;padding:7px 10px;border:1px solid #c4d5dc;border-radius:8px;background:#fff}.search input{border:0;outline:none;min-width:220px;font-size:14px;background:transparent}.content{padding:18px}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:14px}.card{background:rgba(255,255,255,.72);border:1px solid #c6d9e6;border-radius:12px;padding:12px;min-height:160px}.meta{display:flex;justify-content:space-between;align-items:center;font-size:11px;color:#678094;margin-bottom:8px}.chip{display:inline-block;padding:4px 7px;border-radius:999px;background:#edf6ff;color:#3d7dbf;font-weight:700;font-size:11px}.icon{width:56px;height:56px;margin:10px auto;border-radius:10px;display:grid;place-items:center;border:1px solid #bdd3e4;background:linear-gradient(145deg,#edf5ff,#d7e7e2);font-weight:700;color:#2a4964}.name{margin:0;font-size:15px;font-weight:700;text-align:center;line-height:1.3;min-height:40px}.stats{margin-top:10px;display:grid;gap:4px;text-align:center;font-size:12px;color:#5e7b8d}.empty{padding:28px 18px;border:1px dashed #bfd0d8;border-radius:10px;background:rgba(255,255,255,.30);text-align:center;color:#5f7d8d} @media (max-width: 760px){ .topbar,.toolbar{flex-wrap:wrap} .search{width:100%}.search input{min-width:0;width:100%}} </style></head><body>");
        html.append("<div class='window'><div class='topbar'><div class='nav'><a class='btn alt' href='/register'>← Quay lại</a><button class='btn alt' type='button' onclick='window.location.reload()'>Làm mới</button></div><div class='title'>Shop Vàng _ Ngọc</div><div class='nav'><a class='btn' href='/admin?tab=items'>Reload</a></div></div>");
        html.append("<div class='toolbar'><div class='tabs'><a class='tab" + ("items".equals(selected) ? " active" : "") + "' href='/admin?tab=items" + (keyword.isEmpty() ? "" : "&q=" + escapeHtml(keyword)) + "'>Trang bị</a>");
        html.append("<a class='tab" + ("npc".equals(selected) ? " active" : "") + "' href='/admin?tab=npc" + (keyword.isEmpty() ? "" : "&q=" + escapeHtml(keyword)) + "'>NPC</a>");
        html.append("<a class='tab" + ("shop".equals(selected) ? " active" : "") + "' href='/admin?tab=shop" + (keyword.isEmpty() ? "" : "&q=" + escapeHtml(keyword)) + "'>Shop</a></div>");
        html.append("<form class='search' method='get' action='/admin'><input type='hidden' name='tab' value='" + escapeHtml(selected) + "'><span>🔎</span><input type='text' name='q' value='" + escapeHtml(keyword) + "' placeholder='Tìm theo tên, ID...'><button class='btn alt' type='submit'>Tìm</button></form></div>");
        html.append("<div class='content'>");

        try (Connection connection = DatabaseManager.getConnection()) {
            String sql;
            if ("npc".equals(selected)) {
                sql = "SELECT id, NAME, head, body, leg, avatar FROM npc_template";
                if (!keyword.isEmpty()) {
                    sql += " WHERE NAME LIKE ? OR CAST(id AS CHAR) LIKE ?";
                }
                sql += " ORDER BY id ASC LIMIT 200";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    if (!keyword.isEmpty()) {
                        ps.setString(1, "%" + keyword + "%");
                        ps.setString(2, "%" + keyword + "%");
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        html.append(renderRows(rs, selected));
                    }
                }
            } else if ("shop".equals(selected)) {
                sql = "SELECT s.id, s.npc_id, s.tab, s.item_id, s.gold, s.gem, s.quantity, s.itemOption, s.isUpTop, s.isBuy, i.NAME AS item_name FROM shop_ky_gui s LEFT JOIN item_template i ON i.id = s.item_id";
                if (!keyword.isEmpty()) {
                    sql += " WHERE i.NAME LIKE ? OR CAST(s.item_id AS CHAR) LIKE ? OR CAST(s.id AS CHAR) LIKE ?";
                }
                sql += " ORDER BY s.id ASC LIMIT 200";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    if (!keyword.isEmpty()) {
                        ps.setString(1, "%" + keyword + "%");
                        ps.setString(2, "%" + keyword + "%");
                        ps.setString(3, "%" + keyword + "%");
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        html.append(renderRows(rs, selected));
                    }
                }
            } else {
                sql = "SELECT id, TYPE, gender, NAME, description, level, icon_id, part, is_up_to_up, power_require, gold, gem FROM item_template";
                if (!keyword.isEmpty()) {
                    sql += " WHERE NAME LIKE ? OR CAST(id AS CHAR) LIKE ? OR description LIKE ?";
                }
                sql += " ORDER BY id ASC LIMIT 200";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    if (!keyword.isEmpty()) {
                        ps.setString(1, "%" + keyword + "%");
                        ps.setString(2, "%" + keyword + "%");
                        ps.setString(3, "%" + keyword + "%");
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        html.append(renderRows(rs, selected));
                    }
                }
            }
        } catch (Exception e) {
            Logger.logException(WebRegisterServer.class, e);
            html.append("<div class='empty'>Không thể tải dữ liệu DB: ").append(escapeHtml(e.getMessage())).append("</div>");
        }

        html.append("</div></div></body></html>");
        return html.toString();
    }

    private static String renderRows(ResultSet rs, String selected) throws SQLException {
        StringBuilder sb = new StringBuilder();
        boolean hasRows = false;

        while (rs.next()) {
            hasRows = true;
            if ("npc".equals(selected)) {
                sb.append("<div class='card'><div class='meta'><span>ID: ")
                        .append(rs.getInt("id")).append("</span><span class='chip'>NPC</span></div>");
                sb.append("<div class='icon'>").append(rs.getInt("id")).append("</div>");
                sb.append("<p class='name'>").append(escapeHtml(rs.getString("NAME"))).append("</p>");
                sb.append("<div class='stats'><div>Head: <strong>").append(rs.getInt("head")).append("</strong></div><div>Body: <strong>")
                        .append(rs.getInt("body")).append("</strong></div><div>Leg: <strong>").append(rs.getInt("leg")).append("</strong></div></div></div>");
            } else if ("shop".equals(selected)) {
                sb.append("<div class='card'><div class='meta'><span>")
                        .append(rs.getInt("id")).append("</span><span class='chip'>Shop</span></div>");
                sb.append("<div class='icon'>#").append(rs.getInt("item_id")).append("</div>");
                sb.append("<p class='name'>").append(escapeHtml(rs.getString("item_name"))).append("</p>");
                sb.append("<div class='stats'><div>Gold: <strong>").append(rs.getInt("gold")).append("</strong></div><div>Gem: <strong>")
                        .append(rs.getInt("gem")).append("</strong></div><div>NPC: <strong>").append(rs.getInt("npc_id")).append("</strong></div></div></div>");
            } else {
                sb.append("<div class='card'><div class='meta'><span>ID: ")
                        .append(rs.getInt("id")).append("</span><span class='chip'>")
                        .append(escapeHtml(itemTypeLabel(rs.getInt("TYPE")))).append("</span></div>");
                sb.append("<div class='icon'>#").append(rs.getInt("icon_id")).append("</div>");
                sb.append("<p class='name'>").append(escapeHtml(rs.getString("NAME"))).append("</p>");
                sb.append("<div class='stats'><div>Cấp: <strong>").append(rs.getInt("level")).append("</strong></div>")
                        .append("<div>Power: <strong>").append(rs.getInt("power_require")).append("</strong></div>")
                        .append("<div>Gold / Gem: <strong>").append(rs.getInt("gold")).append(" / ").append(rs.getInt("gem")).append("</strong></div></div></div>");
            }
        }

        if (!hasRows) {
            return "<div class='empty'>Không có dữ liệu phù hợp.</div>";
        }
        return "<div class='grid'>" + sb + "</div>";
    }

    private static String itemTypeLabel(int type) {
        switch (type) {
            case 0: return "Áo";
            case 1: return "Quần";
            case 2: return "Găng";
            case 3: return "Giày";
            case 4: return "Rada";
            case 6: return "Đậu thần";
            case 7: return "Sách";
            case 12: return "Ngọc rồng";
            default: return "Khác";
        }
    }

    private static String getQueryParam(String query, String key, String fallback) {
        if (query == null || query.isBlank()) {
            return fallback;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && key.equals(URLDecoder.decode(parts[0], StandardCharsets.UTF_8))) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return fallback;
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
