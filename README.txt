# 資料庫匯出工具 使用說明書

本工具提供從 SQL Server 資料庫連線,批次的將Schema依指定格式匯出為csv,經過人工篩選加工後，以與匯出相同的格式匯入至HN_Table_List表中
之後可以用工具選擇匯出Table Schema，格式可選Excel或PDF，適用於系統分析、文件備份等需求。

---

## 📁 專案目錄結構

- `config.properties`：資料庫連線設定檔
- `run.bat`：一鍵執行批次檔，用於啟動工具
- `pom.xml`：Maven 專案管理檔案
- `src/main/java/org/...`：主要 Java 程式碼
  - `DbConfig.java`：讀取資料庫設定
  - `ConfigLoader.java`：載入組態
  - `Main.java`：應用主程式進入點
  - `SchemaExcelExporter.java`：匯出 Schema 為 Excel
  - `SchemaPdfExporter.java`：匯出 Schema 為 PDF
  - `TableMetaDataCSVHandler.java`：處理 CSV 的匯入與匯出

---

## ⚙️ 環境需求

- 作業系統：Windows
- Java JDK：建議使用 JDK 17 或以上
- Maven：用於打包
- SQL Server：支援 SQL Server 資料庫連線

---

## 🧩 初次使用步驟

1. **執行工具**
   - Intellij IDEA ˋclass Mainˋ資料執行程式
   - 雙擊 `run.bat`
   - 或在命令列執行(切換到專案資料夾)：`java -jar target/DatabaseExporterMerge-1.0-SNAPSHOT-shaded.jar`

2. **輸入資料庫連線資訊**

Host（預設: 192.168.1.94）：
Database Name（預設: HN_Test）：
Username（預設: sa）：
Password（可留空使用預設）：

可直接Enter直接使用預設資料庫連線

---

## 📦 匯出選項

啟動後可選擇功能：

========== 主選單 ==========
1. 匯出 / 匯入 CSV 模組資料
2. 匯出資料表 Schema (Excel / PDF)
0. 離開程式
請輸入選項：

主選單1
====== 功能選單 ======
1. 匯出 .csv 供人工修改
2. 將修改後 .csv 寫入 HN_Table_List 資料表
0. 離開程式
請輸入選項：

主選單2
====== 功能選單 ======
1. 匯出所有資料表 Schema(Excel)
2. 匯出所有資料表 Schema(PDF)
0. 離開
請輸入選項：


---

## 📝 注意事項

- Schema 欄位如未設定「模組別」，將不匯出資料，並顯示 ⚠️ 錯誤提示。
- 請確保目標資料庫連線正確，且使用者帳號具備讀取 `sys.*` 相關資料的權限。

---

## 🛠️ 常見問題

- **Q: 無法建立連線？**
  - 檢查 `config.properties` 的連線資訊是否正確
  - 資料庫需開放 TCP/IP 連線，且防火牆未封鎖 Port

- **Q: 執行後出現 `The document has no pages`？**
  - 代表所有表格都因條件不符未被匯出，請確認模組別與描述是否正確設定

- **Q: 同事要怎麼使用？**
  - 將整包專案下載
  - 安裝 JDK
  - 編輯 `config.properties`
  - 直接執行 `run.bat` 即可

---

製作人：林俊翰 Henry / 上官孟平 Anthony
版本：v1.0
