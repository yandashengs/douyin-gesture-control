# 清理 Gradle wrapper 缓存中下载了一半 / 损坏的 gradle-8.7-bin.zip
$ErrorActionPreference = "SilentlyContinue"

$distsDir = Join-Path $env:USERPROFILE ".gradle\wrapper\dists"
if (-not (Test-Path $distsDir)) {
    Write-Host "[INFO] 缓存目录不存在，无需清理: $distsDir"
    exit 0
}

Write-Host "扫描: $distsDir"
$found = Get-ChildItem -Path $distsDir -Recurse -Filter "gradle-8.7-bin*"
if (-not $found) {
    Write-Host "[OK] 未发现 gradle-8.7-bin 缓存，无需清理"
    exit 0
}

foreach ($item in $found) {
    Write-Host ("删除: " + $item.FullName)
    Remove-Item -Path $item.FullName -Force -Recurse
}
Write-Host "[OK] 清理完成"
