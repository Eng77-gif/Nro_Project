# Cap nhat ban moi (goi tu cap_nhat.bat). Hien thanh tien trinh 0-100% bang ky tu ASCII de console VPS nao cung hien dung.
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$R = 'Eng77-gif/Nro_Project'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $here

function Bar([double]$pct, [string]$text) {
    $pct = [Math]::Max(0, [Math]::Min(100, $pct))
    $w = 40; $fill = [int][Math]::Round($w * $pct / 100)
    $line = '[' + ('#' * $fill) + ('-' * ($w - $fill)) + '] ' + ('{0,3}' -f [int]$pct) + '%  ' + $text
    Write-Host ("`r" + $line.PadRight(79).Substring(0, 79)) -NoNewline
}

$sha = (Invoke-RestMethod -Uri ("https://api.github.com/repos/$R/commits/main") -Headers @{ 'User-Agent' = 'nro-update' }).sha
$short = $sha.Substring(0, 9)
$cur = ''
if (Test-Path 'VERSION') { $cur = (Get-Content 'VERSION' -TotalCount 1).Trim() }
Write-Host ('Ban dang dung : ' + $(if ($cur -and $cur -notlike '*Format*') { $cur } else { 'chua ro' }))
Write-Host ('Ban moi nhat  : ' + $short)
if ($cur -and $cur -notlike '*Format*' -and $short.StartsWith($cur.Substring(0, [Math]::Min(7, $cur.Length)))) { Write-Host 'Da la ban moi nhat.'; exit 0 }
Write-Host ''

$zip = Join-Path $env:TEMP 'nro_update.zip'
$tmp = Join-Path $env:TEMP 'nro_update'
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }

# 0-70%: tai ZIP (GitHub thuong khong bao kich thuoc: uoc theo lan tai truoc, mac dinh 1 GB)
$sizeFile = Join-Path $env:TEMP 'nro_update_size.txt'
$total = 1GB
$req = [Net.HttpWebRequest]::Create("https://codeload.github.com/$R/zip/$sha")
$req.UserAgent = 'nro-update'
$resp = $req.GetResponse()
if ($resp.ContentLength -gt 0) { $total = $resp.ContentLength } elseif (Test-Path $sizeFile) { $total = [long](Get-Content $sizeFile) }
$in = $resp.GetResponseStream(); $out = [IO.File]::Create($zip)
$buf = New-Object byte[] 262144; $got = 0L; $last = [DateTime]::MinValue
while (($n = $in.Read($buf, 0, $buf.Length)) -gt 0) {
    $out.Write($buf, 0, $n); $got += $n
    if (([DateTime]::Now - $last).TotalMilliseconds -gt 300) {
        Bar ([Math]::Min(69, 70.0 * $got / $total)) ('Tai ban ' + $short + ': ' + [Math]::Round($got / 1MB) + ' MB')
        $last = [DateTime]::Now
    }
}
$out.Close(); $in.Close(); $resp.Close()
Set-Content $sizeFile $got
Bar 70 ('Da tai ' + [Math]::Round($got / 1MB) + ' MB')

# 70-80%: giai nen
Bar 72 'Giai nen...'
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory($zip, $tmp)
$src = (Get-ChildItem $tmp -Directory | Select-Object -First 1).FullName
Bar 80 'Giai nen xong'

# 80-100%: chep de (bo .md, file Termux, Config.properties, backup.key)
$skip = @('nro_termux.sh', 'run_termux.sh', 'termux_install.sh', 'nro-ui.sh', 'Config.properties', 'backup.key')
$files = @(Get-ChildItem $src -Recurse -File | Where-Object { $_.Extension -ne '.md' -and $skip -notcontains $_.Name })
$i = 0
foreach ($f in $files) {
    $rel = $f.FullName.Substring($src.Length + 1)
    $dst = Join-Path $here $rel
    New-Item -ItemType Directory -Force -Path (Split-Path $dst) | Out-Null
    Copy-Item $f.FullName $dst -Force
    $i++
    if ($i % 50 -eq 0 -or $i -eq $files.Count) { Bar (80 + 20.0 * $i / [Math]::Max(1, $files.Count)) ('Chep file ' + $i + '/' + $files.Count) }
}
Set-Content 'VERSION' $short
Remove-Item $zip, $tmp -Recurse -Force
Bar 100 ('Xong ban ' + $short)
Write-Host ''
Write-Host ('Da cap nhat len ban ' + $short + ' (' + $files.Count + ' file).')
