# 一键构建：前端打包 → 拷贝进后端静态资源 → 打可执行 jar
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

Write-Host '[1/3] 构建前端 ...' -ForegroundColor Cyan
Push-Location web
npm install --no-fund --no-audit
npm run build
Pop-Location

Write-Host '[2/3] 拷贝静态资源 ...' -ForegroundColor Cyan
$static = 'src/main/resources/static'
if (Test-Path $static) { Remove-Item $static -Recurse -Force }
Copy-Item 'web/dist' $static -Recurse

Write-Host '[3/3] 打包后端 ...' -ForegroundColor Cyan
mvn -DskipTests package

Write-Host '完成：target/library-system.jar（内置前端页面）' -ForegroundColor Green