<?php
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

$tab = $_GET['tab'] ?? 'items';
$search = trim((string) ($_GET['q'] ?? ''));

$tabs = ['items' => 'Trang bị', 'npc' => 'NPC', 'shop' => 'Shop'];

if ($tab === 'npc') {
    $sql = "SELECT id, NAME AS name, head, body, leg, avatar FROM npc_template";
    if ($search !== '') {
        $sql .= " WHERE NAME LIKE :q OR CAST(id AS CHAR) LIKE :q";
    }
    $sql .= " ORDER BY id ASC LIMIT 200";
    $stmt = $pdo->prepare($sql);
    if ($search !== '') {
        $stmt->bindValue(':q', '%' . $search . '%');
    }
    $stmt->execute();
    $rows = $stmt->fetchAll();
} elseif ($tab === 'shop') {
    $sql = "
        SELECT s.id, s.npc_id, s.tab, s.item_id, s.gold, s.gem, s.quantity, s.itemOption, s.isUpTop, s.isBuy,
               i.NAME AS item_name, i.icon_id, i.level, i.power_require, i.part
        FROM shop_ky_gui s
        LEFT JOIN item_template i ON i.id = s.item_id
    ";
    if ($search !== '') {
        $sql .= " WHERE i.NAME LIKE :q OR CAST(s.item_id AS CHAR) LIKE :q OR CAST(s.id AS CHAR) LIKE :q";
    }
    $sql .= " ORDER BY s.id ASC LIMIT 200";
    $stmt = $pdo->prepare($sql);
    if ($search !== '') {
        $stmt->bindValue(':q', '%' . $search . '%');
    }
    $stmt->execute();
    $rows = $stmt->fetchAll();
} else {
    $sql = "SELECT id, TYPE, gender, NAME AS name, description, level, icon_id, part, is_up_to_up, power_require, gold, gem FROM item_template";
    if ($search !== '') {
        $sql .= " WHERE NAME LIKE :q OR CAST(id AS CHAR) LIKE :q OR description LIKE :q";
    }
    $sql .= " ORDER BY id ASC LIMIT 200";
    $stmt = $pdo->prepare($sql);
    if ($search !== '') {
        $stmt->bindValue(':q', '%' . $search . '%');
    }
    $stmt->execute();
    $rows = $stmt->fetchAll();
}

function itemTypeLabel(int $type): string
{
    $map = [
        0 => 'Áo', 1 => 'Quần', 2 => 'Găng', 3 => 'Giày', 4 => 'Rada',
        5 => 'Hộp', 6 => 'Đậu thần', 7 => 'Sách', 8 => 'Quest', 9 => 'Vàng',
        10 => 'Ngọc', 11 => 'Khác', 12 => 'Ngọc rồng', 23 => 'Đặc biệt'
    ];
    return $map[$type] ?? 'Khác';
}
?>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width,initial-scale=1" />
    <title>DB Manager - NPC & Trang bị</title>
    <style>
        :root {
            --bg: #dfeaf3;
            --panel: #edf4f6;
            --panel-2: #f9fbfb;
            --line: #c3d1db;
            --text: #1d2d38;
            --muted: #5f7c8b;
            --blue: #6ca3d4;
            --blue-deep: #3a7fc5;
            --green: #5abf90;
            --orange: #f0b35b;
            --shadow: 0 10px 30px rgba(32, 66, 88, .12);
        }
        * { box-sizing: border-box; }
        body {
            margin: 0;
            min-height: 100vh;
            font-family: Arial, Helvetica, sans-serif;
            background: var(--bg);
            color: var(--text);
        }
        .window {
            width: min(1220px, calc(100% - 28px));
            margin: 18px auto 40px;
            background: rgba(255,255,255,0.32);
            border: 1px solid rgba(120,154,178,.35);
            box-shadow: var(--shadow);
            border-radius: 10px;
            overflow: hidden;
        }
        .topbar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 16px;
            padding: 12px 16px;
            background: linear-gradient(#dfeaf3, #d4e3f2);
            border-bottom: 1px solid var(--line);
        }
        .topbar .nav {
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            min-height: 38px;
            padding: 0 14px;
            border: 1px solid #2d4d5f;
            border-radius: 6px;
            background: #3e5360;
            color: white;
            cursor: pointer;
            font-weight: 700;
            text-decoration: none;
        }
        .btn.alt {
            background: #f2f7fa;
            color: var(--text);
            border-color: var(--line);
        }
        .btn.success {
            background: #56b886;
            border-color: #4ba374;
        }
        .title {
            flex: 1;
            text-align: center;
            font-size: clamp(32px, 3vw, 46px);
            font-weight: 700;
            letter-spacing: -0.04em;
            color: #2f4f7b;
        }
        .toolbar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 12px;
            padding: 12px 18px;
            background: rgba(255,255,255,.24);
            border-bottom: 1px solid var(--line);
        }
        .tabs {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
        }
        .tab {
            padding: 10px 18px;
            border-radius: 6px;
            border: 1px solid var(--line);
            background: #f8fafa;
            color: var(--text);
            text-decoration: none;
            font-weight: 700;
        }
        .tab.active {
            background: var(--blue-deep);
            border-color: var(--blue-deep);
            color: white;
        }
        .search {
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 8px 12px;
            border: 1px solid var(--line);
            background: white;
            border-radius: 8px;
        }
        .search input {
            border: 0;
            outline: none;
            background: transparent;
            min-width: 240px;
            font-size: 14px;
            color: var(--text);
        }
        .content {
            padding: 18px;
        }
        .grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
            gap: 14px;
        }
        .card {
            background: rgba(255,255,255,.72);
            border: 1px solid var(--line);
            border-radius: 12px;
            box-shadow: 0 7px 16px rgba(30,40,50,.08);
            padding: 12px;
            min-height: 146px;
            position: relative;
        }
        .card .meta {
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 11px;
            color: var(--muted);
            margin-bottom: 8px;
        }
        .chip {
            display: inline-block;
            padding: 4px 7px;
            border-radius: 999px;
            background: #eef6ff;
            color: var(--blue-deep);
            font-weight: 700;
            font-size: 11px;
        }
        .item-icon {
            width: 54px;
            height: 54px;
            margin: 0 auto 10px;
            border-radius: 10px;
            display: grid;
            place-items: center;
            background: linear-gradient(145deg, #e7f2ff, #cfddd8);
            border: 1px solid #bdd3e4;
            font-weight: 700;
            color: #2d4965;
            overflow: hidden;
        }
        .item-name {
            margin: 0;
            font-size: 15px;
            font-weight: 700;
            text-align: center;
            line-height: 1.3;
            min-height: 40px;
        }
        .stats {
            margin-top: 10px;
            display: grid;
            gap: 4px;
            font-size: 12px;
            color: var(--muted);
            text-align: center;
        }
        .stats strong { color: var(--text); }
        .npc-card {
            min-height: 154px;
        }
        .avatar {
            width: 58px;
            height: 58px;
            border-radius: 14px;
            margin: 0 auto 8px;
            background: linear-gradient(135deg, #d0e3ff, #f1f8f7);
            border: 1px solid var(--line);
            display: grid;
            place-items: center;
            font-weight: 700;
            color: #2b4761;
        }
        .empty {
            padding: 32px 18px;
            border: 1px dashed var(--line);
            border-radius: 10px;
            background: rgba(255,255,255,.28);
            color: var(--muted);
            text-align: center;
        }
        @media (max-width: 760px) {
            .topbar { flex-wrap: wrap; }
            .title { order: 3; width: 100%; }
            .toolbar { flex-direction: column; align-items: stretch; }
            .search { width: 100%; }
            .search input { min-width: 0; width: 100%; }
        }
    </style>
</head>
<body>
<div class="window">
    <div class="topbar">
        <div class="nav">
            <a class="btn alt" href="register.php">← Quay lại</a>
            <button class="btn alt" type="button" onclick="window.location.reload()">Làm mới</button>
        </div>
        <div class="title">Shop Vàng _ Ngọc</div>
        <div class="nav">
            <button class="btn success" type="button" onclick="window.location.href='admin-manager.php?tab=items'">Reload DB</button>
        </div>
    </div>

    <div class="toolbar">
        <div class="tabs">
            <?php foreach ($tabs as $key => $label): ?>
                <a class="tab <?= $tab === $key ? 'active' : '' ?>" href="admin-manager.php?tab=<?= $key ?><?= $search !== '' ? '&q=' . urlencode($search) : '' ?>"><?= $label ?></a>
            <?php endforeach; ?>
        </div>

        <form class="search" method="get" action="admin-manager.php">
            <input type="hidden" name="tab" value="<?= h($tab) ?>" />
            <span>🔎</span>
            <input type="text" name="q" value="<?= h($search) ?>" placeholder="Tìm theo tên, ID..." />
            <button class="btn alt" type="submit">Tìm</button>
        </form>
    </div>

    <div class="content">
        <?php if (empty($rows)): ?>
            <div class="empty">Không có dữ liệu phù hợp.</div>
        <?php else: ?>
            <div class="grid">
                <?php foreach ($rows as $row): ?>
                    <?php if ($tab === 'npc'): ?>
                        <div class="card npc-card">
                            <div class="meta">
                                <span>ID: <?= (int) $row['id'] ?></span>
                                <span class="chip">NPC</span>
                            </div>
                            <div class="avatar"><?= h((string) (($row['avatar'] ?? 0) ?: $row['id'])) ?></div>
                            <p class="item-name"><?= h((string) $row['name']) ?></p>
                            <div class="stats">
                                <div>Head: <strong><?= (int) ($row['head'] ?? 0) ?></strong></div>
                                <div>Body: <strong><?= (int) ($row['body'] ?? 0) ?></strong></div>
                                <div>Leg: <strong><?= (int) ($row['leg'] ?? 0) ?></strong></div>
                            </div>
                        </div>
                    <?php elseif ($tab === 'shop'): ?>
                        <div class="card">
                            <div class="meta">
                                <span><?= (int) $row['id'] ?></span>
                                <span class="chip">Shop</span>
                            </div>
                            <div class="item-icon">#<?= (int) ($row['item_id'] ?? 0) ?></div>
                            <p class="item-name"><?= h((string) ($row['item_name'] ?? 'Item')) ?></p>
                            <div class="stats">
                                <div>Gold: <strong><?= (int) ($row['gold'] ?? 0) ?></strong></div>
                                <div>Gem: <strong><?= (int) ($row['gem'] ?? 0) ?></strong></div>
                                <div>NPC: <strong><?= (int) ($row['npc_id'] ?? 0) ?></strong></div>
                            </div>
                        </div>
                    <?php else: ?>
                        <div class="card">
                            <div class="meta">
                                <span>ID: <?= (int) $row['id'] ?></span>
                                <span class="chip"><?= h(itemTypeLabel((int) ($row['TYPE'] ?? 0))) ?></span>
                            </div>
                            <div class="item-icon">#<?= (int) ($row['icon_id'] ?? 0) ?></div>
                            <p class="item-name"><?= h((string) $row['name']) ?></p>
                            <div class="stats">
                                <div>Cấp: <strong><?= (int) ($row['level'] ?? 0) ?></strong></div>
                                <div>Power: <strong><?= (int) ($row['power_require'] ?? 0) ?></strong></div>
                                <div>Gold/Gem: <strong><?= (int) ($row['gold'] ?? 0) ?> / <?= (int) ($row['gem'] ?? 0) ?></strong></div>
                            </div>
                        </div>
                    <?php endif; ?>
                <?php endforeach; ?>
            </div>
        <?php endif; ?>
    </div>
</div>
</body>
</html>
