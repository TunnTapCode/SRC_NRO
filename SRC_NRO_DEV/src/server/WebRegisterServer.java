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
                main{position:relative;z-index:1;width:min(100%,440px);padding:32px;background:#1b2630ee;border:1px solid #596b73;box-shadow:0 18px 60px #000a}h1{margin:0 0 8px;font-size:30px}p{color:#9eb0ba;margin:0 0 24px}label{display:block;margin:15px 0 6px;color:#9eb0ba}input{width:100%;padding:12px;border:1px solid #334451;background:#111820;color:#edf4f7;font:inherit}button{width:100%;margin-top:24px;padding:13px;border:0;background:#f0b35a;color:#20170c;font:bold 16px Georgia,serif;cursor:pointer}.message{padding:12px;margin-bottom:18px}.error{background:#401f25;color:#ee817b}.success{background:#19382d;color:#73d3a2}@media(max-width:600px){.ball{width:48px;height:48px;font-size:17px}.b2,.b3{top:3vh}.b5,.b6{bottom:3vh}}
                </style></head><body><div class="dragon-balls"><i class="ball b1"></i><i class="ball b2"></i><i class="ball b3"></i><i class="ball b4"></i><i class="ball b5"></i><i class="ball b6"></i><i class="ball b7"></i></div><main><h1>Tao tai khoan</h1><p>Dang ky tai khoan de dang nhap may chu.</p>
                __MESSAGE__<form method="post"><label>Ten tai khoan</label><input name="username" value="__USERNAME__" minlength="4" maxlength="20" pattern="[a-z0-9]+" required>
                <label>Mat khau</label><input name="password" type="password" minlength="4" maxlength="100" required>
                <label>Nhap lai mat khau</label><input name="confirm" type="password" minlength="4" maxlength="100" required>
                <label>Email (khong bat buoc)</label><input name="email" type="email" value="__EMAIL__" maxlength="255"><button>Dang ky tai khoan</button></form></main></body></html>
                """.replace("__MESSAGE__", messageBlock).replace("__USERNAME__", escapedUsername).replace("__EMAIL__", escapedEmail);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
