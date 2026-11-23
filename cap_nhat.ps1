# Cap nhat ban moi (goi tu cap_nhat.bat): chi tai cac file da doi so voi ban tren may, khong tai ca repo.
# Danh sach file lay tu nhanh update-manifest (GitHub Actions tu tao moi lan main doi).
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$R = 'Eng77-gif/Nro_Project'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $here
$cacheFile = Join-Path $here '.capnhat_cache'

function Bar([double]$pct, [string]$text) {
    $pct = [Math]::Max(0, [Math]::Min(100, $pct))
    $w = 40; $fill = [int][Math]::Round($w * $pct / 100)
    $line = '[' + ('#' * $fill) + ('-' * ($w - $fill)) + '] ' + ('{0,3}' -f [int]$pct) + '%  ' + $text
    Write-Host ("`r" + $line.PadRight(79).Substring(0, 79)) -NoNewline
}

$wc = New-Object Net.WebClient
$wc.Headers.Add('User-Agent', 'nro-update')
$wc.Encoding = [Text.Encoding]::UTF8
$man = $wc.DownloadString("https://raw.githubusercontent.com/$R/update-manifest/manifest.txt?t=" + [DateTime]::Now.Ticks)
$lines = $man -split "`n" | Where-Object { $_ -ne '' }
$sha = $lines[0].Substring(2).Trim()
$short = $sha.Substring(0, 9)
$cur = ''
if (Test-Path 'VERSION') { $cur = (Get-Content 'VERSION' -TotalCount 1).Trim() }
if ($cur -like '*Format*') { $cur = '' }
Write-Host ('Ban dang dung : ' + $(if ($cur) { $cur } else { 'chua ro' }))
Write-Host ('Ban moi nhat  : ' + $short)

# file khong cap nhat: tai lieu, file Termux, cau hinh / du lieu rieng cua may
$skip = @('nro_termux.sh', 'run_termux.sh', 'termux_install.sh', 'nro-ui.sh', 'Config.properties', 'backup.key', 'VERSION')
$remote = New-Object Collections.ArrayList
foreach ($l in $lines[1..($lines.Count - 1)]) {
    $p = $l.TrimEnd("`r") -split "`t", 3
    if ($p.Count -lt 3) { continue }
    $path = $p[2]; $name = [IO.Path]::GetFileName($path)
    if ($path.EndsWith('.md') -or $skip -contains $name -or $path -eq 'sql/team2026.sql.enc' -or $path.StartsWith('.github/')) { continue }
    [void]$remote.Add(@($p[0], [long]$p[1], $path))
}

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
$done = 0L; $k = 0
foreach ($r in $need) {
    $k++
    $url = "https://raw.githubusercontent.com/$R/$sha/" + (($r[2] -split '/' | ForEach-Object { [Uri]::EscapeDataString($_) }) -join '/')
    $full = Join-Path $here ($r[2] -replace '/', '\')
    New-Item -ItemType Directory -Force -Path (Split-Path $full) | Out-Null
    Bar (100.0 * $done / [Math]::Max(1, $total)) ('Tai ' + $k + '/' + $need.Count + ': ' + [IO.Path]::GetFileName($r[2]))
    $wc.DownloadFile($url, $full)
    $done += $r[1]
    $fi = New-Object IO.FileInfo($full)
    [void]$newCache.Add("$($r[0])`t$($fi.Length)`t$($fi.LastWriteTimeUtc.Ticks)`t$($r[2])")
}
Bar 100 ('Da tai ' + $need.Count + ' file (' + [Math]::Round($total / 1MB, 1) + ' MB)')
Write-Host ''
[IO.File]::WriteAllLines($cacheFile, [string[]]$newCache)
Set-Content 'VERSION' $short
Write-Host ('Da cap nhat len ban ' + $short + '.')
