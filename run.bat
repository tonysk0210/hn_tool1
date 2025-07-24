@echo off
setlocal
chcp 65001 > nul

:: 設定執行檔
set JAR=target\DatabaseExporterMerge-1.0-SNAPSHOT.jar

echo 🔍 檢查執行檔...

if not exist "%JAR%" (
    echo ❌ 找不到執行檔 [%JAR%]，請先執行 mvn clean package 打包！
    pause
    exit /b
)

echo ✅ 啟動工具中...
java -jar "%JAR%"

echo.
pause