@echo off
setlocal

:: 指定 config 檔
set CONFIG=config.properties

:: 執行 fat jar（已被覆蓋）
java -Dconfig.path=%CONFIG% -jar target\CsvDatabaseHandler-1.0-SNAPSHOT.jar

pause