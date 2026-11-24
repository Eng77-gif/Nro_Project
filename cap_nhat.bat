@echo off
rem Cap nhat ban moi tu GitHub, khong can cai git. Tat server truoc khi chay.
rem Phan PowerShell nam ngay trong file nay (sau dong danh dau PS), khong can file .ps1 rieng.
cd /d "%~dp0"
mode con cols=100 >nul 2>nul
set "NRO_DIR=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$s=[IO.File]::ReadAllText('%~f0'); iex $s.Substring($s.IndexOf('#PS'+'START'))" & (if errorlevel 1 (echo. & echo Cap nhat loi. Kiem tra mang, hoac tat server truoc ^(20.jar dang chay khong ghi de duoc^).) else (echo Xong. Khoi dong lai server neu vua cap nhat.)) & pause & exit /b
#PSSTART
# Phan PowerShell cua cap_nhat.bat: chi tai cac file da doi so voi ban tren may, khong tai ca repo.
# Danh sach file lay tu nhanh update-manifest (GitHub Actions tu tao moi lan main doi).
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$Repo = 'Eng77-gif/Nro_Project' # khong dat ten $R: PowerShell khong phan biet hoa thuong, trung voi bien vong lap $r (loi 404 truoc day)
$here = $env:NRO_DIR.TrimEnd('\')
Set-Location $here
$cacheFile = Join-Path $here '.capnhat_cache'
Remove-Item (Join-Path $here 'cap_nhat.ps1') -ErrorAction SilentlyContinue # ban cu tach 2 file

function Bar([double]$pct, [string]$text) {
    $pct = [Math]::Max(0, [Math]::Min(100, $pct))
    $w = 40; $fill = [int][Math]::Round($w * $pct / 100)
    $line = '[' + ('#' * $fill) + ('-' * ($w - $fill)) + '] ' + ('{0,3}' -f [int]$pct) + '%  ' + $text
    Write-Host ("`r" + $line.PadRight(79).Substring(0, 79)) -NoNewline
}

# mang VPS hay bi ngat giua chung ("connection forcibly closed"): thu lai toi 5 lan
function Retry([scriptblock]$job) {
    for ($t = 1; ; $t++) {
        try { & $job; return } catch { if ($t -ge 5) { throw }; Start-Sleep -Seconds (2 * $t) }
    }
}

$wc = New-Object Net.WebClient
$wc.Headers.Add('User-Agent', 'nro-update')
$wc.Encoding = [Text.Encoding]::UTF8
# tai byte roi giai ma UTF-8 ro rang (duong dan co dau tieng Viet); gan trong khoi de PowerShell khong tach mang byte
Retry { $script:manBytes = $wc.DownloadData("https://raw.githubusercontent.com/$Repo/update-manifest/manifest.txt?t=" + [DateTime]::Now.Ticks) }
$man = [Text.Encoding]::UTF8.GetString($script:manBytes)
$lines = $man -split "`n" | Where-Object { $_ -ne '' }
$sha = $lines[0].Substring(2).Trim()
$short = $sha.Substring(0, 9)
$cur = ''
if (Test-Path 'VERSION') { $cur = (Get-Content 'VERSION' -TotalCount 1).Trim() }
if ($cur -like '*Format*') { $cur = '' }
Write-Host ('Ban dang dung : ' + $(if ($cur) { $cur } else { 'chua ro' }))
Write-Host ('Ban moi nhat  : ' + $short)

# file khong cap nhat: cau hinh / du lieu rieng cua may
$skip = @('Config.properties', 'backup.key', 'VERSION')
# khong can tren may Windows chay server: ma nguon, cong cu, tai lieu, file chay Linux/Termux (.sh). 20.jar da gom du thu vien cua lib/
$devDirs = @('src/', 'tools/', 'client/', 'lib/', 'nbproject/', 'docs/', 'preview/', 'cloudflare/', 'out_files/', '.github/')
$devFiles = @('build.xml', 'manifest.mf', 'Dockerfile', '.dockerignore', '.gitignore', '.gitattributes', 'item.xlsx')
function IsDev([string]$path) {
    if ($path.EndsWith('.md') -or $path.EndsWith('.sh') -or $devFiles -contains $path) { return $true }
    foreach ($d in $devDirs) { if ($path.StartsWith($d)) { return $true } }
    return $false
}
$remote = New-Object Collections.ArrayList
$drop = New-Object Collections.ArrayList
foreach ($l in $lines[1..($lines.Count - 1)]) {
    $p = $l.TrimEnd("`r") -split "`t", 3
    if ($p.Count -lt 3) { continue }
    $path = $p[2]; $name = [IO.Path]::GetFileName($path)
    if (IsDev $path) { [void]$drop.Add($path); continue }
    if ($skip -contains $name -or $path -eq 'sql/team2026.sql.enc') { continue }
    [void]$remote.Add(@($p[0], [long]$p[1], $path))
}

# xoa file cua repo khong can (tai tu ban cu): chi xoa dung duong dan co trong repo, khong dung file rieng cua may
$freed = 0L; $nDel = 0; $dirs = @{}
foreach ($path in $drop) {
    try {
        $fi = New-Object IO.FileInfo((Join-Path $here ($path -replace '/', '\')))
        if ($fi.Exists) { $len = $fi.Length; $fi.Delete(); $freed += $len; $nDel++; $dirs[$fi.DirectoryName] = 1 }
    } catch { }
}
# xoa thu muc da rong (sau nhat truoc); loi quyen truy cap thi bo qua, khong lam hong lan cap nhat
foreach ($d in ([string[]]$dirs.Keys | Sort-Object { $_.Length } -Descending)) {
    $x = $d
    try {
        while ($x.Length -gt $here.Length -and [IO.Directory]::Exists($x) -and -not [IO.Directory]::EnumerateFileSystemEntries($x).GetEnumerator().MoveNext()) {
            [IO.Directory]::Delete($x); $x = [IO.Path]::GetDirectoryName($x)
        }
    } catch { }
}
if ($nDel -gt 0) { Write-Host ('Da xoa ' + $nDel + ' file khong can tren may Windows (' + [Math]::Round($freed / 1MB, 1) + ' MB)') }

# ma blob git cua file tren may (luu cache theo kich thuoc + gio sua de lan sau khong phai doc lai)
$cache = @{}
if (Test-Path $cacheFile) { foreach ($c in [IO.File]::ReadAllLines($cacheFile)) { $q = $c -split "`t", 4; if ($q.Count -eq 4) { $cache[$q[3]] = $q } } }
$sha1 = [Security.Cryptography.SHA1]::Create()
function BlobSha([string]$file) {
    $bytes = [IO.File]::ReadAllBytes($file)
    $head = [Text.Encoding]::ASCII.GetBytes('blob ' + $bytes.Length + [char]0)
    $sha1.Initialize()
    [void]$sha1.TransformBlock($head, 0, $head.Length, $null, 0)
    [void]$sha1.TransformFinalBlock($bytes, 0, $bytes.Length)
    return ([BitConverter]::ToString($sha1.Hash) -replace '-', '').ToLower()
}

$need = New-Object Collections.ArrayList
$newCache = New-Object Collections.ArrayList
$i = 0; $last = [DateTime]::MinValue
foreach ($r in $remote) {
    $i++
    $full = Join-Path $here ($r[2] -replace '/', '\')
    $ok = $false
    $fi = New-Object IO.FileInfo($full)
    if ($fi.Exists) {
        $key = $r[2]; $mt = $fi.LastWriteTimeUtc.Ticks
        if ($fi.Length -eq $r[1]) {
            if ($cache.ContainsKey($key) -and $cache[$key][1] -eq [string]$fi.Length -and $cache[$key][2] -eq [string]$mt) { $local = $cache[$key][0] } else { $local = BlobSha $full }
            [void]$newCache.Add("$local`t$($fi.Length)`t$mt`t$key")
            $ok = ($local -eq $r[0])
        }
    }
    if (-not $ok) { [void]$need.Add($r) }
    if (([DateTime]::Now - $last).TotalMilliseconds -gt 300) { Bar (100.0 * $i / $remote.Count) ('Kiem tra file ' + $i + '/' + $remote.Count); $last = [DateTime]::Now }
}
Bar 100 ('Kiem tra xong: ' + $need.Count + ' file can cap nhat')
Write-Host ''

if ($need.Count -eq 0) {
    [IO.File]::WriteAllLines($cacheFile, [string[]]$newCache)
    Set-Content 'VERSION' $short
    Write-Host 'Da la ban moi nhat.'
    exit 0
}

# tai tung file da doi, tien trinh theo tong dung luong
$total = 0L; foreach ($r in $need) { $total += $r[1] }
$done = 0L; $k = 0; $failed = New-Object Collections.ArrayList
foreach ($r in $need) {
    $k++
    $url = "https://raw.githubusercontent.com/$Repo/$sha/" + (($r[2] -split '/' | ForEach-Object { [Uri]::EscapeDataString($_) }) -join '/')
    $full = Join-Path $here ($r[2] -replace '/', '\')
    New-Item -ItemType Directory -Force -Path (Split-Path $full) | Out-Null
    Bar (100.0 * $done / [Math]::Max(1, $total)) ('Tai ' + $k + '/' + $need.Count + ': ' + [IO.Path]::GetFileName($r[2]))
    try {
        Retry { $wc.DownloadFile($url, $full) }
    } catch {
        # file loi khong lam dung ca lan cap nhat: ghi lai ly do, chay tiep
        $msg = $_.Exception.Message; if ($_.Exception.InnerException) { $msg = $_.Exception.InnerException.Message }
        [void]$failed.Add($r[2] + '  : ' + $msg); $done += $r[1]; continue
    }
    $done += $r[1]
    $fi = New-Object IO.FileInfo($full)
    [void]$newCache.Add("$($r[0])`t$($fi.Length)`t$($fi.LastWriteTimeUtc.Ticks)`t$($r[2])")
}
Bar 100 ('Da tai ' + ($need.Count - $failed.Count) + ' file (' + [Math]::Round($total / 1MB, 1) + ' MB)')
Write-Host ''
[IO.File]::WriteAllLines($cacheFile, [string[]]$newCache)
if ($failed.Count -gt 0) {
    Write-Host ('Khong tai duoc ' + $failed.Count + ' file (chay lai cap_nhat.bat de thu lai):')
    $failed | Select-Object -First 10 | ForEach-Object { Write-Host ('  ' + $_) }
    exit 1
}
Set-Content 'VERSION' $short
Write-Host ('Da cap nhat len ban ' + $short + '.')
