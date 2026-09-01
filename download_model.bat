@echo off
REM Windows 批处理包装：调用 PowerShell 执行 download_model.ps1
REM 双击本文件即可下载模型

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0download_model.ps1"
pause
