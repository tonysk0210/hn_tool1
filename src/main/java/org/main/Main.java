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
    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        // 1. 載入設定檔選擇
        System.out.println("請選擇要使用的資料庫連線設定檔：");
        System.out.println("1. 一般連線（config.properties）");
        System.out.println("2. MS驗證連線（config-msv.properties）");
        System.out.print("請輸入選項（1 或 2）：");

        String choice = scanner.nextLine().trim();
        String configFile;

        switch (choice) {
            case "1":
                configFile = "config.properties";
                break;
            case "2":
                configFile = "config-msv.properties";
                break;
            default:
                System.out.println("⚠ 輸入錯誤，預設使用 config.properties");
                configFile = "config.properties";
        }

        ConfigLoader.load(configFile);
        DbConfig.loadFromConfig();

        //------------
        Connection conn = null;

        while (conn == null) {

            System.out.println("請輸入資料庫連線資訊：");

            System.out.print("Host（預設: " + ConfigLoader.get("db.host") + "）：");
            String host = scanner.nextLine().trim();
            DbConfig.host = host.isEmpty() ? ConfigLoader.get("db.host") : host;

            System.out.print("Database Name（預設: " + ConfigLoader.get("db.name") + "）：");
            String dbName = scanner.nextLine().trim();
            DbConfig.databaseName = dbName.isEmpty() ? ConfigLoader.get("db.name") : dbName;

            System.out.print("Username（預設: " + ConfigLoader.get("db.user") + "）：");
            String user = scanner.nextLine().trim();
            DbConfig.username = user.isEmpty() ? ConfigLoader.get("db.user") : user;

            System.out.print("Password（可留空使用預設）：");
            String pwd = scanner.nextLine().trim();
            DbConfig.password = pwd.isEmpty() ? ConfigLoader.get("db.password") : pwd;

            System.out.println("\n🧪 連線參數確認");
            System.out.println("Host          : " + DbConfig.host);
            System.out.println("Database Name : " + DbConfig.databaseName);
            System.out.println("Username      : " + (DbConfig.password.isEmpty() ? "(空)" : DbConfig.username));
            System.out.println("Password      : " + (DbConfig.password.isEmpty() ? "(空)" : "(已輸入)"));
            System.out.println("JDBC URL      : " + DbConfig.getJdbcUrl());

            //  連線
            try {
                conn = DriverManager.getConnection(DbConfig.getJdbcUrl(), DbConfig.username, DbConfig.password);
                System.out.println("✅ 資料庫連線成功！");
            } catch (SQLException e) {
                System.out.println("❌ 資料庫連線失敗，請重新輸入！");
                System.out.println("➡️ 錯誤訊息：" + e.getMessage());
                System.out.println("─────────────────────────────");
            }
        }

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
                    return;
                default:
                    System.out.println("⚠️ 無效選項，請重新輸入 0～2。");
            }
        }
    }
}
