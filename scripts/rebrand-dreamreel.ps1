param(
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot

# ---- 1. 品牌名替换（全仓库，保留 UTF-8 无 BOM）----
$nameRules = @(
    @("DREAMREEL", "DREAMREEL"),
    @("Dreamreel", "Dreamreel"),
    @("dreamreel", "dreamreel"),
    @("dreamreel", "dreamreel")
)

# ---- 2. 配色替换（仅前端源码，荧光绿 -> 电影感紫）----
$colorRules = @(
    @("#b6ff00", "#7c3aed"),   # 主色
    @("#a8f000", "#6d28d9"),   # 主色 hover
    @("#78a900", "#8b5cf6"),   # 高亮/对勾
    @("#efffc7", "#f3e8ff"),   # 柔和高亮底
    @("#466400", "#5b21b6"),   # 柔和高亮文字
    @("#d7ed9d", "#ddd6fe"),   # 柔和边框
    @("#9bd900", "#8b5cf6"),
    @("#608800", "#6d28d9"),
    @("#6f9d00", "#7c3aed"),
    @("#8bc400", "#7c3aed"),
    @("#f7f8fa", "#f8f7fc"),   # 浅底色
    @("#111318", "#17131f"),   # 墨色（带紫调）
    @("rgba(182, 255, 0, ", "rgba(124, 58, 237, "),
    @("rgba(139, 196, 0, ", "rgba(124, 58, 237, "),
    @("rgba(17, 19, 24, ", "rgba(23, 19, 31, "),
    @("#1a2236", "#241539"),
    @("#121826", "#180f29")
)

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Invoke-Replace([string]$path, [array]$rules) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    if ($bytes.Length -ge 2 -and $bytes[0] -eq 0xFF -and $bytes[1] -eq 0xFE) { return } # UTF-16 skip
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    $orig = $text
    foreach ($r in $rules) {
        $text = $text.Replace($r[0], $r[1])
    }
    if ($text -ne $orig) {
        if ($DryRun) {
            Write-Host "  [dry-run] $path"
        } else {
            [System.IO.File]::WriteAllText($path, $text, $utf8NoBom)
            Write-Host "  [ok] $path"
        }
    }
}

$binaryExts = @(".png", ".jpg", ".jpeg", ".gif", ".ico", ".jar", ".zip", ".tar", ".gz", ".woff", ".woff2", ".ttf")

Write-Host "== brand name replacement =="
Get-ChildItem -LiteralPath $Root -Recurse -File | Where-Object {
    $_.FullName -notmatch '\\.git\\|\\node_modules\\|\\.next\\|\\target\\' -and
    $binaryExts -notcontains $_.Extension.ToLower()
} | ForEach-Object { Invoke-Replace $_.FullName $nameRules }

Write-Host "== color replacement (apps/web/src) =="
Get-ChildItem -LiteralPath (Join-Path $Root "apps\web\src") -Recurse -File | Where-Object {
    $binaryExts -notcontains $_.Extension.ToLower()
} | ForEach-Object { Invoke-Replace $_.FullName $colorRules }

Write-Host "Done."
