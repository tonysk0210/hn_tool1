package org.example;

import java.io.*;
import java.sql.*;
import java.util.Scanner;

public class TableMetaDataCSVHandler {

    private static final String JDBC_URL = "jdbc:sqlserver://192.168.1.94:1433;databaseName=HN_Test;encrypt=true;trustServerCertificate=true";
    private static final String USER = "sa";
    private static final String PASSWORD = "1qaz2wsx";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("選擇功能:");
        System.out.println("1. 匯出 metadata 為 CSV");
        System.out.println("2. 匯入修改後的 CSV 到資料表");

        int option = scanner.nextInt();
        scanner.nextLine(); //吃掉換行

        try {
            if (option == 1) {
                System.out.println("請輸入匯出CSV的檔案名稱 (例如 output.csv)");
                String outputPath = scanner.nextLine();
                exportToCSV(outputPath);
            } else if (option == 2) {
                System.out.print("請輸入修改後的 CSV 路徑（例如 C:\\\\data\\\\metadata.csv）：");
                String inputPath = scanner.nextLine();
                importFromCSV(inputPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //匯出成CSV
    private static void exportToCSV(String csvPath) throws Exception {
        String query = """
    SELECT 
        ISNULL(f.value, '') AS [System_Name],
        ROW_NUMBER() OVER (
            PARTITION BY f.value 
            ORDER BY f.value, t.name
        ) AS Seq,
        t.name AS [Table_Name],
        ISNULL(e.value, '') AS [Table_Desc]
    FROM 
        sys.tables t
    LEFT JOIN (
        SELECT 
            major_id, 
            name, 
            value 
        FROM 
            sys.extended_properties
        WHERE 
            name = '用途說明'
    ) e ON t.object_id = e.major_id
    LEFT JOIN (
        SELECT 
            major_id, 
            name, 
            value 
        FROM 
            sys.extended_properties
        WHERE 
            name = '模組別'
    ) f ON t.object_id = f.major_id
    ORDER BY 
        t.name;
    """;

        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet resultSet = stmt.executeQuery(query);
             //write to .csv
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(csvPath), "UTF-8"))) {
            writer.println("System_Name,Seq,Table_Name,Table_Desc");

            while (resultSet.next()) {
                String systemName = resultSet.getString("System_Name").replaceAll(",", "");
                int seq = resultSet.getInt("Seq");
                String tableName = resultSet.getString("Table_Name").replaceAll(",", "");
                String tableDesc = resultSet.getString("Table_Desc").replaceAll(",", "");

                writer.printf("%s,%d,%s,%s%n",
                        escapeCsv(systemName),
                        seq,
                        escapeCsv(tableName),
                        escapeCsv(tableDesc));
            }
            System.out.println("✅ 匯出完成：" + csvPath);
        }
    }

    //處理欄位中逗號、雙引號、換行
    private static String escapeCsv(String input) {
        if (input == null) return ""; //如果欄位值是 null，輸出空字串（避免 NullPointerException）
        boolean special = input.contains(",") || input.contains("\"") || input.contains("\n"); //檢查欄位中是否含有 CSV 的特殊字元（,、"、\n），這些字元需要特殊處理
        String escaped = input.replace("\"", "\"\""); //若欄位中有雙引號，根據 CSV 規則需要變成兩個雙引號（""）
        return special ? "\"" + escaped + "\"" : escaped; //如果欄位含有特殊字元，就用雙引號包起來，否則直接輸出
    }

    //匯入CSV寫入資料表
    private static void importFromCSV(String csvPath) throws Exception {
        try (
                Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
                PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT INTO HN_Table_List (system_name, seq, table_name, table_desc) VALUES (?, ?, ?, ?)");
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvPath), "UTF-8")) //read from .csv
        ) {
            String line;
            boolean firstLine = true;

            // 清空舊資料
            conn.createStatement().execute("TRUNCATE TABLE HN_Table_List");

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                } // 跳過標題列
                String[] parts = line.split(",", -1); // -1 保留空白欄位
                if (parts.length < 4) continue;

                pstmt.setString(1, parts[0].trim());
                pstmt.setInt(2, Integer.parseInt(parts[1].trim()));
                pstmt.setString(3, parts[2].trim());
                pstmt.setString(4, parts[3].trim());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            System.out.println("✅ 匯入完成，資料已寫入 table_metadata");
        }
    }
}