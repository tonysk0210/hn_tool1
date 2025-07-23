package org.main;

import org.example.TableMetaDataCSVHandler;
import org.schemaexporter.App;
import org.config.DbConfig;
import org.config.ConfigLoader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

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
        System.out.println("Username      : " + DbConfig.username);
        System.out.println("Password      : " + (DbConfig.password.isEmpty() ? "(空)" : "(已輸入)"));
        System.out.println("JDBC URL      : " + DbConfig.getJdbcUrl());

        //  連線
        try (Connection conn = DriverManager.getConnection(DbConfig.getJdbcUrl(), DbConfig.username, DbConfig.password)) {
            System.out.println("✅ 資料庫連線成功！");
        } catch (Exception e) {
            System.out.println("❌ 資料庫連線失敗！請確認輸入資訊是否正確");
            e.printStackTrace();
            return;
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
                    System.out.println("👋 感謝使用，再見！");
                    return;
                default:
                    System.out.println("⚠️ 無效選項，請重新輸入 0～2。");
            }
        }
    }
}
