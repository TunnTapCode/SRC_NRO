<?php
session_start();

function loadProperties(string $path): array
{
    $properties = [];
    foreach (file($path, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) as $line) {
        $line = trim($line);
        if ($line === '' || $line[0] === '#' || !str_contains($line, '=')) {
            continue;
        }
        [$key, $value] = explode('=', $line, 2);
        $properties[trim($key)] = trim($value);
    }
    return $properties;
}

function h(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES, 'UTF-8');
}

$config = loadProperties(__DIR__ . '/../Config.properties');
$_SESSION['register_token'] ??= bin2hex(random_bytes(32));
$message = '';
$messageType = '';
$username = '';
$email = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $username = strtolower(trim($_POST['username'] ?? ''));
    $password = (string) ($_POST['password'] ?? '');
    $passwordConfirm = (string) ($_POST['password_confirm'] ?? '');
    $email = trim($_POST['email'] ?? '');
    $token = (string) ($_POST['token'] ?? '');

    if (!hash_equals($_SESSION['register_token'], $token)) {
        $message = 'Phiên đăng ký không hợp lệ. Vui lòng tải lại trang.';
        $messageType = 'error';
    } elseif (!preg_match('/^[a-z0-9]{4,20}$/', $username)) {
        $message = 'Tài khoản chỉ gồm chữ thường và số, dài từ 4 đến 20 ký tự.';
        $messageType = 'error';
    } elseif (strlen($password) < 4 || strlen($password) > 100) {
        $message = 'Mật khẩu phải dài từ 4 đến 100 ký tự.';
        $messageType = 'error';
    } elseif ($password !== $passwordConfirm) {
        $message = 'Mật khẩu nhập lại không khớp.';
        $messageType = 'error';
    } elseif (strlen($email) > 255 || ($email !== '' && !filter_var($email, FILTER_VALIDATE_EMAIL))) {
        $message = 'Email không hợp lệ.';
        $messageType = 'error';
    } else {
        try {
            $dsn = sprintf(
                'mysql:host=%s;port=%s;dbname=%s;charset=utf8mb4',
                $config['database.host'] ?? '127.0.0.1',
                $config['database.port'] ?? '3306',
                $config['database.name'] ?? 'src_nro'
            );
            $pdo = new PDO($dsn, $config['database.user'] ?? '', $config['database.pass'] ?? '', [
                PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                PDO::ATTR_EMULATE_PREPARES => false,
            ]);

            $check = $pdo->prepare('SELECT id FROM account WHERE username = ? LIMIT 1');
            $check->execute([$username]);
            if ($check->fetch()) {
                $message = 'Tên tài khoản đã tồn tại.';
                $messageType = 'error';
            } else {
                $insert = $pdo->prepare(
                    "INSERT INTO account (username, password, email, token, xsrf_token, newpass) VALUES (?, ?, ?, '', '', '')"
                );
                $insert->execute([$username, $password, $email]);
                $message = 'Đăng ký thành công. Bạn có thể đăng nhập game ngay.';
                $messageType = 'success';
                $username = '';
                $email = '';
            }
        } catch (Throwable $exception) {
            error_log($exception->getMessage());
            $message = 'Không thể kết nối hoặc tạo tài khoản. Kiểm tra MySQL và Config.properties.';
            $messageType = 'error';
        }
    }
}
?>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Đăng ký tài khoản</title>
    <style>
        :root { color-scheme: dark; --bg: #111820; --panel: #1b2630; --line: #334451; --text: #edf4f7; --muted: #9eb0ba; --accent: #f0b35a; --danger: #ee817b; --success: #73d3a2; }
        * { box-sizing: border-box; }
        body { margin: 0; min-height: 100vh; display: grid; place-items: center; padding: 24px; background: radial-gradient(circle at 15% 10%, #253946 0, transparent 36%), var(--bg); color: var(--text); font: 16px/1.5 Georgia, serif; }
        main { width: min(100%, 460px); background: var(--panel); border: 1px solid var(--line); padding: 32px; box-shadow: 0 18px 60px #0007; }
        h1 { margin: 0 0 6px; font-size: 30px; letter-spacing: .02em; }
        p { color: var(--muted); margin: 0 0 24px; }
        label { display: block; margin: 16px 0 7px; color: var(--muted); }
        input { width: 100%; border: 1px solid var(--line); background: #111820; color: var(--text); padding: 12px 13px; font: inherit; outline: none; }
        input:focus { border-color: var(--accent); }
        button { width: 100%; margin-top: 24px; border: 0; padding: 13px; background: var(--accent); color: #20170c; font: bold 16px Georgia, serif; cursor: pointer; }
        button:hover { filter: brightness(1.08); }
        .message { margin: 0 0 18px; padding: 12px; border-left: 3px solid; }
        .error { color: var(--danger); border-color: var(--danger); background: #401f25; }
        .success { color: var(--success); border-color: var(--success); background: #19382d; }
        small { display: block; margin-top: 18px; color: var(--muted); }
    </style>
</head>
<body>
<main>
    <h1>Tạo tài khoản</h1>
    <p>Đăng ký tài khoản để đăng nhập vào máy chủ.</p>
    <?php if ($message !== ''): ?><div class="message <?= h($messageType) ?>"><?= h($message) ?></div><?php endif; ?>
    <form method="post" autocomplete="off">
        <input type="hidden" name="token" value="<?= h($_SESSION['register_token']) ?>">
        <label for="username">Tên tài khoản</label>
        <input id="username" name="username" value="<?= h($username) ?>" minlength="4" maxlength="20" pattern="[a-z0-9]+" required>
        <label for="password">Mật khẩu</label>
        <input id="password" name="password" type="password" minlength="4" maxlength="100" required>
        <label for="password_confirm">Nhập lại mật khẩu</label>
        <input id="password_confirm" name="password_confirm" type="password" minlength="4" maxlength="100" required>
        <label for="email">Email (không bắt buộc)</label>
        <input id="email" name="email" type="email" value="<?= h($email) ?>" maxlength="255">
        <button type="submit">Đăng ký tài khoản</button>
    </form>
    <small>Mật khẩu được lưu theo định dạng hiện tại của server game để đăng nhập tương thích.</small>
</main>
</body>
</html>
