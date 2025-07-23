::@echo off
::setlocal

:: 指定 config 檔
::set CONFIG=config.properties

:: 執行 fat jar（已被覆蓋）
::java -Dconfig.path=%CONFIG% -jar target\CsvDatabaseHandler-1.0-SNAPSHOT.jar

::pause


@echo off
chcp 65001 > nul

set CONFIG=config.properties
set JAR=target\DatabaseExporterMerge-1.0-SNAPSHOT.jar

echo 🔍 檢查執行檔與設定檔...

if not exist "%JAR%" (
    echo ❌ 找不到執行檔 [%JAR%]，請先執行 mvn clean package 打包！
    pause
    exit /b
)

if not exist "%CONFIG%" (
    echo ❌ 找不到設定檔 [%CONFIG%]，請確認檔案存在！
    pause
    exit /b
)

echo ✅ 啟動工具中...
java -Dconfig.path=%CONFIG% -jar %JAR%

echo.
pause
