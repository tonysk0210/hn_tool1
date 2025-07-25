package org.main;

import org.example.TableMetaDataCSVHandler;
import org.schemaexporter.App;
import org.config.DbConfig;
import org.config.ConfigLoader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Scanner;

public class Main {
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {

        // 1. 選擇資料庫設定檔
        String configFile = selectConfigFile();

        // 2. 載入資料庫設定與初始化
        ConfigLoader.load(configFile);
        DbConfig.loadFromConfig();

        // 3. 建立連線
        Connection conn = createDatabaseConnection();

        // 4. 主選單
        showMainMenu();
    }

    private static String selectConfigFile() {
        System.out.println("請選擇要使用的資料庫連線設定檔：");
        System.out.println("1. 一般連線（config.properties）");
        System.out.println("2. MS驗證連線（config-msv.properties）");
        System.out.print("請輸入選項（1 或 2）: ");

        String choice = scanner.nextLine().trim();
        System.out.println();

        switch (choice) {
            case "1":
                return "config.properties";
            case "2":
                return "config-msv.properties";
            default:
                System.out.println("⚠️ 輸入錯誤，預設使用 config.properties");
                return "config.properties";
        }
    }

    private static Connection createDatabaseConnection() {
        Connection conn = null;

        while (conn == null) {
            System.out.println("請輸入資料庫連線資訊：");

            DbConfig.host = prompt("Host", DbConfig.host);
            DbConfig.databaseName = prompt("Database Name", DbConfig.databaseName);
            DbConfig.username = prompt("Username", DbConfig.username);
            DbConfig.password = prompt("Password（可留空使用預設）", DbConfig.password);

            System.out.println("\n🧪 連線參數確認");
            System.out.println("Host          : " + DbConfig.host);
            System.out.println("Database Name : " + DbConfig.databaseName);
            System.out.println("Username      : " + (DbConfig.username.isEmpty() ? "(空)" : DbConfig.username));
            System.out.println("Password      : " + (DbConfig.password.isEmpty() ? "(空)" : "(已輸入)"));
            System.out.println("JDBC URL      : " + DbConfig.getJdbcUrl());

            try {
                if (DbConfig.integratedSecurity) {
                    conn = DriverManager.getConnection(DbConfig.getJdbcUrl());
                } else {
                    conn = DriverManager.getConnection(DbConfig.getJdbcUrl(), DbConfig.username, DbConfig.password);
                }
                System.out.println("✅ 資料庫連線成功！");
            } catch (SQLException e) {
                System.out.println("❌ 資料庫連線失敗，請重新輸入！");
                System.out.println("➡️ 錯誤訊息：" + e.getMessage());
                System.out.println("─────────────────────────────");
            }
        }

        return conn;
    }

    private static String prompt(String label, String defaultValue) {
        System.out.print(label + "（預設: " + defaultValue + "）：");
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? defaultValue : input;
    }

    private static void showMainMenu() {
        while (true) {
            System.out.println("\n========== 主選單 ==========");
            System.out.println("1. 匯出 / 匯入 CSV 模組資料");
            System.out.println("2. 匯出資料表 Schema (Excel / PDF)");
            System.out.println("0. 離開程式");
            System.out.print("請輸入選項：");

            String input = scanner.nextLine().trim();

            switch (input) {
                case "1":
                    TableMetaDataCSVHandler.main(null); // CSV 功能主程式
                    break;
                case "2":
                    App.main(null); // Schema 匯出主程式
                    break;
                case "0":
                    System.out.println("👋 程式結束，再見！");
                    return;
                default:
                    System.out.println("⚠️ 無效選項，請重新輸入 0～2。");
            }
        }
    }
}
