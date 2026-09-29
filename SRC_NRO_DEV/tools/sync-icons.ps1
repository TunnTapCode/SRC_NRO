# Dong bo icon giua cac muc zoom (x1, x2, x3, x4) cua server.
#
# LY DO CAN THIET: client gui "zoom_level" khi login (Server -> MySession.zoomLevel),
# va server doc icon tu duong dan "data/icon/x{zoomLevel}/{id}.png" (DataGame.sendIcon).
# => Neu 1 icon chi co o x4, client o zoom 2 se KHONG bao gio nhan duoc icon do
#    (doc file null -> khong gui anh) => o trong game bi trong/rong, du web admin van hien icon.
#
# CACH DUNG (PowerShell):
#   powershell -ExecutionPolicy Bypass -File tools\sync-icons.ps1
#   powershell -ExecutionPolicy Bypass -File tools\sync-icons.ps1 -Source x4
#   powershell -ExecutionPolicy Bypass -File tools\sync-icons.ps1 -Verbose
#
# Tham so:
#   -Source   x4 (mac dinh): thu muc nguon se duoc dung lam chuan
#   -Targets  x1,x2,x3 (mac dinh): cac muc se duoc sinh thieu
#   -Force    ghi de ca khi da co file
param(
    [string]$Source = 'x4',
    [string[]]$Targets = @('x1', 'x2', 'x3'),
    [switch]$Force,
    [switch]$Verbose
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Join-Path (Split-Path -Parent $PSScriptRoot) 'data/icon'
if (-not (Test-Path -LiteralPath $root)) {
    Write-Host "[LOI] Khong tim thay thu muc data/icon: $root" -ForegroundColor Red
    exit 1
}

# Ty le kich thuoc giua cac muc zoom (x4 = 80px, x3 = 60px, x2 = 40px, x1 = 20px)
$factors = @{ 'x1' = 0.25; 'x2' = 0.5; 'x3' = 0.75; 'x4' = 1.0 }

$srcDir = Join-Path $root $Source
if (-not (Test-Path -LiteralPath $srcDir)) {
    Write-Host "[LOI] Thu muc nguon khong ton tai: $srcDir" -ForegroundColor Red
    exit 1
}

$srcFiles = Get-ChildItem -LiteralPath $srcDir -Filter *.png -File
Write-Host "Nguon $Source : $($srcFiles.Count) icon" -ForegroundColor Cyan

$totalCreated = 0
$totalSkipped = 0
$totalError = 0

foreach ($t in $Targets) {
    $targetDir = Join-Path $root $t
    if (-not (Test-Path -LiteralPath $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }
    if (-not $factors.ContainsKey($t)) {
        Write-Host "[BO QUA] Muc zoom khong hop le: $t" -ForegroundColor Yellow
        continue
    }

    $f = [double]$factors[$t]
    $created = 0; $skipped = 0; $err = 0

    foreach ($file in $srcFiles) {
        $dst = Join-Path $targetDir ($file.Name)
        if ((Test-Path -LiteralPath $dst) -and (-not $Force)) { $skipped++; continue }
        try {
            $img = [System.Drawing.Image]::FromFile($file.FullName)
            $w = [int][Math]::Round($img.Width * $f)
            $h = [int][Math]::Round($img.Height * $f)
            if ($w -lt 1) { $w = 1 }
            if ($h -lt 1) { $h = 1 }
            $bmp = New-Object System.Drawing.Bitmap -ArgumentList $w, $h
            $g = [System.Drawing.Graphics]::FromImage($bmp)
            $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $g.DrawImage($img, 0, 0, $w, $h)
            $g.Dispose()
            $bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
            $bmp.Dispose(); $img.Dispose()
            $created++
            if ($Verbose) { Write-Host "  [+] $t/$($file.Name) ($($img.Width)x$($img.Height) -> ${w}x${h})" }
        } catch {
            $err++
            if ($Verbose) { Write-Host "  [!] Loi: $t/$($file.Name) - $($_.Exception.Message)" -ForegroundColor Red }
        }
    }

    Write-Host ("{0}: tao {1} | bo qua {2} | loi {3}" -f $t, $created, $skipped, $err) -ForegroundColor Green
    $totalCreated += $created; $totalSkipped += $skipped; $totalError += $err
}

Write-Host "HOAN TAT: tao $totalCreated icon moi | bo qua $totalSkipped | loi $totalError" -ForegroundColor Cyan
Write-Host "Nho: restart server game + logout/login lai client de client nhan ban moi." -ForegroundColor Yellow
