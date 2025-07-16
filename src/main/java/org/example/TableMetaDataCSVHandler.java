package org.example;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Scanner;

public class TableMetaDataCSVHandler {

    private static final String JDBC_URL = "jdbc:sqlserver://192.168.1.112:1433;databaseName=LMS;encrypt=true;trustServerCertificate=true";
    private static final String USER = "27A27";
    private static final String PASSWORD = "hn2#1234";

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
    private static void exportToCSV(String outputPath) throws Exception {
        String query = """
                SELECT   
                    ISNULL(f.value, '') AS [System_Name],
                    ROW_NUMBER() OVER (PARTITION BY f.value ORDER BY f.value, t.name) AS Seq,
                    t.name AS [Table_Name],
                    ISNULL(e.value, '') AS [Table_Desc]
                FROM  
                    sys.tables t 
                LEFT JOIN 
                    (SELECT major_id, name, value FROM sys.extended_properties WHERE name = '用途說明') e  
                    ON t.object_id = e.major_id  
                LEFT JOIN 
                    (SELECT major_id, name, value FROM sys.extended_properties WHERE name = '模組別') f  
                    ON t.object_id = f.major_id  
                ORDER BY 
                    t.name
                """;

        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
             Statement statement = conn.createStatement();
             ResultSet resultSet = statement.executeQuery(query);
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputPath), "UTF-8"))) {
            writer.println("System_Name,Seq,Table_Name,Table_Desc");

            while (resultSet.next()) {
                String systemName = resultSet.getString("System_Name").replaceAll(",", "");
                int seq = resultSet.getInt("Seq");
                String tableName = resultSet.getString("Table_Name").replaceAll(",", "");
                String tableDesc = resultSet.getString("Table_Desc").replaceAll(",", "");
                writer.printf("%s,%d,%s,%s%n", systemName, seq, tableName, tableDesc);
            }
            System.out.println("✅ 匯出完成：" + outputPath);
        }
    }

    //匯入CSV寫入資料表
    private static void importFromCSV(String inputPath) {
    }
}