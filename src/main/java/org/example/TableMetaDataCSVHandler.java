package org.example;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import javax.swing.*;
import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class TableMetaDataCSVHandler {

    private static final String JDBC_URL = ConfigLoader.get("jdbc.url");
    private static final String USER = ConfigLoader.get("jdbc.user");
    private static final String PASSWORD = ConfigLoader.get("jdbc.password");

    //自動產出timestamp檔名
    private static String generateTimestampedFilename() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String timestamp = LocalDateTime.now().format(formatter);
        return "OUTPUT_" + timestamp + ".csv";
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("====MENU====");
        System.out.println("1. 匯出 CSV");
        System.out.println("2. 將 CSV 寫入 HN_Table_List 資料表");

        int option = scanner.nextInt();
        scanner.nextLine(); //吃掉換行

        try {
            if (option == 1) {
                String outputPath = generateTimestampedFilename();
                System.out.println("⏳ 自動產生的檔名為： " + outputPath);
                exportToCSV(outputPath);
            } else if (option == 2) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("請選擇要匯入的 CSV 檔案");
                int result = fileChooser.showOpenDialog(null);

                if (result == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    String inputPath = selectedFile.getAbsolutePath();
                    System.out.println("📂 選擇的檔案：" + inputPath);
                    importFromCSV(inputPath);
                } else {
                    System.out.println("⚠ 已取消選擇檔案，未執行匯入。");
                }
            }
        } catch (FileNotFoundException fnfe) {
            System.err.println("❌ 找不到指定的檔案或路徑錯誤：" + fnfe.getMessage());
        } catch (IOException ioe) {
            System.err.println("❌ IO 錯誤（可能是檔案權限、磁碟錯誤）：" + ioe.getMessage());
        } catch (SQLException sqle) {
            System.err.println("❌ 資料庫錯誤：" + sqle.getMessage());
        } catch (Exception e) {
            System.err.println("❌ 未知錯誤：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 匯出 SQL Server 中所有資料表及其說明屬性，產生 UTF-8（含 BOM）編碼的 CSV 檔案。
     *
     * <p>輸出的 CSV 檔包含以下欄位：</p>
     * <ul>
     *   <li><b>System_Name</b>：模組別（來自 extended_properties 的「模組別」屬性）</li>
     *   <li><b>Seq</b>：模組別內的表格序號（ROW_NUMBER）</li>
     *   <li><b>Table_Name</b>：資料表名稱</li>
     *   <li><b>Table_Desc</b>：用途說明（來自 extended_properties 的「用途說明」屬性）</li>
     * </ul>
     *
     * <p>本方法會將中文資料以 UTF-8 編碼輸出，並加上 BOM 標頭，確保 CSV 檔可直接在 Excel 中正常顯示中文。</p>
     * <p>同時自動處理欄位值中出現的特殊字元（逗號、雙引號、換行）以符合 RFC 4180 CSV 規範。</p>
     *
     * @param csvPath 匯出目標的 CSV 檔案完整路徑（例如：1output.csv）
     * @throws SQLException 資料庫連線或查詢過程中發生錯誤時拋出
     * @throws IOException  檔案寫入過程中發生錯誤時拋出
     * @throws Exception    其他未預期的例外狀況
     */
    private static void exportToCSV(String csvPath) throws Exception {
        String query = """
                SELECT ISNULL(f.value, '') AS System_Name,
                       ROW_NUMBER() OVER (PARTITION BY f.value ORDER BY f.value, t.name) AS Seq,
                       t.name AS Table_Name,
                       ISNULL(e.value, '') AS Table_Desc
                FROM sys.tables t
                LEFT JOIN (
                    SELECT major_id, name, value FROM sys.extended_properties
                    WHERE name = '用途說明'
                ) e ON t.object_id = e.major_id
                LEFT JOIN (
                    SELECT major_id, name, value FROM sys.extended_properties
                    WHERE name = '模組別'
                ) f ON t.object_id = f.major_id
                WHERE ISNULL(f.value, '') <> 'HN_Tools' -- ❗ 根據模組別排除
                ORDER BY t.name
                """;

        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet resultSet = stmt.executeQuery(query);
             FileOutputStream fos = new FileOutputStream(csvPath);
             OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8")) {

            // 寫入 UTF-8 BOM，直接用 Excel 開啟 .csv 時中文不會亂碼
            osw.write('\uFEFF');

            try (PrintWriter writer = new PrintWriter(osw)) {
                //1. 寫入Column name
                writer.println("System_Name,Seq,Table_Name,Table_Desc");

                //2. 寫入Column value
                while (resultSet.next()) {
                    String systemName = resultSet.getString("System_Name");
                    int seq = resultSet.getInt("Seq");
                    String tableName = resultSet.getString("Table_Name");
                    String tableDesc = resultSet.getString("Table_Desc");

                    writer.printf("%s,%d,%s,%s%n",
                            escapeCsv(systemName),
                            seq,
                            escapeCsv(tableName),
                            escapeCsv(tableDesc));
                }
                System.out.println("✅ 匯出完成：" + csvPath);
            }
        }
    }

    /**
     * @param input 欲處理的字串欄位內容
     * @return 適合寫入 CSV 的格式化字串
     */
    private static String escapeCsv(String input) {
        if (input == null) return ""; //如果欄位值是 null，輸出空字串（避免 NullPointerException）
        boolean special = input.contains(",") || input.contains("\"") || input.contains("\n"); //檢查欄位中是否含有 CSV 的特殊字元（,、"、\n），這些字元需要特殊處理
        String escaped = input.replace("\"", "\"\""); //若欄位中有雙引號，根據 CSV 規則需要變成兩個雙引號（""）
        return special ? "\"" + escaped + "\"" : escaped; //如果欄位含有特殊字元，就用雙引號包起來，否則直接輸出
    }


    /**
     * 匯入指定 CSV 檔案內容，寫入 HN_Table_List 資料表。
     * <p>
     * 此方法會執行以下動作：
     * <ul>
     *     <li>建立 <code>HN_Table_List</code> 資料表（若尚未存在）</li>
     *     <li>清空該資料表中的所有舊資料</li>
     *     <li>逐行讀取 CSV 檔案資料並寫入資料表</li>
     *     <li>統計成功與失敗的筆數，並印出處理結果</li>
     * </ul>
     *
     * @param csvPath 匯入的 CSV 檔案完整路徑（如 C:/data/hn_table.csv）
     * @throws FileNotFoundException 如果檔案不存在
     * @throws IOException           如果檔案讀取發生錯誤
     * @throws SQLException          如果資料庫操作發生錯誤
     */
    private static void importFromCSV(String csvPath) throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
             CSVReader csvReader = new CSVReader(new InputStreamReader(new FileInputStream(csvPath), "UTF-8"))) {

            conn.setAutoCommit(false);
            createTableIfNotExists(conn); //1. 建立 HN_Table_List 資料表 (若不存在)
            clearTable(conn); //2. 清空舊資料

            int successCount = 0, failCount = 0;
            String[] row;
            boolean firstLine = true;
            int lineNumber = 0;

            // 新增：追蹤每個 System_Name 的 Seq 值
            Map<String, Integer> seqMap = new HashMap<>();

            // 3. 準備插入
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO HN_Table_List (System_Name, Seq, Table_Name, Table_Desc) VALUES (?, ?, ?, ?)")) {

                while ((row = csvReader.readNext()) != null) {
                    lineNumber++;
                    if (firstLine) {
                        firstLine = false;
                        continue;
                    } // 跳過標題行

//                    String[] parts = line.split(",", -1); // -1 保留空白欄位

                    // 偵測問題行
                    if (row.length < 4) {
                        System.err.printf("⚠ 第 %d 行格式錯誤（欄位數不足）：%s%n", lineNumber, String.join(",", row));
                        failCount++;
                        continue;
                    }

                    try {
                        String systemName = row[0].trim();
                        String tableName = row[2].trim();
                        String tableDesc = row[3].trim();

                        // 自動計算 Seq：每個 System_Name 從 1 開始遞增
                        int seq = seqMap.compute(systemName, (k, v) -> (v == null) ? 1 : v + 1);

                        pstmt.setString(1, systemName);
                        pstmt.setInt(2, seq);
                        pstmt.setString(3, tableName);
                        pstmt.setString(4, tableDesc);
                        pstmt.addBatch();
                        successCount++;
                    } catch (NumberFormatException nfe) {
                        System.err.printf("⚠ 第 %d 行 Seq 欄位非數字：%s%n", lineNumber, String.join(",", row));
                        failCount++;
                    }
                }
                if (failCount == 0) {
                    pstmt.executeBatch();
                    conn.commit();
                    System.out.printf("✅ 匯入成功，總筆數：%d%n", successCount);
                } else {
                    conn.rollback();
                    System.err.printf("❌ 匯入失敗，偵測到 %d 筆錯誤，已取消所有變更。%n", failCount);
                }
            } catch (CsvValidationException e) {
                conn.rollback();
                throw new IOException("CSV 格式解析錯誤", e);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }


    /**
     * 檢查資料庫中是否已存在 <code>HN_Table_List</code> 資料表，若無則建立該資料表。
     *
     * @param conn 目前使用中的資料庫連線
     * @throws SQLException 若建立資料表或執行 SQL 時發生錯誤
     */
    private static void createTableIfNotExists(Connection conn) throws SQLException {
        String sql = """
                    IF NOT EXISTS (
                        SELECT * FROM INFORMATION_SCHEMA.TABLES 
                        WHERE TABLE_NAME = 'HN_Table_List'
                    )
                    CREATE TABLE HN_Table_List (
                        System_Name NVARCHAR(100),
                        Seq INT,
                        Table_Name NVARCHAR(100),
                        Table_Desc NVARCHAR(500)
                    )
                """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * 清除 <code>HN_Table_List</code> 資料表中所有資料。
     *
     * @param conn 目前使用中的資料庫連線
     * @throws SQLException 若執行清空資料表的 SQL 指令時發生錯誤
     */
    private static void clearTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE HN_Table_List");
        }
    }
}