package org.schemaexporter;

import javax.swing.*;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.config.DbConfig;


public class App {
    private static final Logger logger = LogManager.getLogger(App.class);

    public static void main(String[] args) {
        try {
            // ✅ 改為從 DbConfig 取得
            String url = DbConfig.getJdbcUrl();
            String user = DbConfig.username;
            String password = DbConfig.password;

            try (Connection conn = DriverManager.getConnection(url, user, password)) {
//                System.out.println("資料庫連線成功");

                Scanner scanner = new Scanner(System.in);

                while (true) {
                    System.out.println("\n====== 功能選單 ======");
                    System.out.println("1. 匯出所有資料表 Schema(Excel)");
                    System.out.println("2. 匯出所有資料表 Schema(PDF)");
                    System.out.println("0. 離開");
                    System.out.print("請輸入選項：");

                    String choice = scanner.nextLine().trim();

                    switch (choice) {
                        case "1":
                            try {
                                File dir = chooseDirectory();
                                if (dir == null) break;
                                SchemaExcelExporter.exportSchemaToExcel(conn, dir.getAbsolutePath());
                            } catch (Exception e) {
                                System.out.println(" 匯出 Schema 失敗：" + e.getMessage());
                                logger.error("匯出 Excel Schema 失敗", e);
                            }
                            break;

                        case "2":
                            try {
                                File dir = chooseDirectory();
                                if (dir == null) break;

                                String defaultName = "Schema_" + getTimestampFilename();
                                String fileName = askFileName("請輸入檔名（預設 " + defaultName + "）");
                                String base = fileName.isEmpty() ? defaultName : fileName;
                                String path = dir + File.separator + base;

                                SchemaPdfExporter.exportSchemaToPdf(conn, path + ".pdf");
                            } catch (Exception e) {
                                System.out.println(" 匯出 Schema 失敗：" + e.getMessage());
                                logger.error("匯出 PDF Schema 失敗", e);
                            }
                            break;

                        case "0":
                            System.out.println("\n程式結束 謝謝使用");
                            return;

                        default:
                            System.out.println("\n-----無效選項 請重新輸入-----");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("-----資料庫連線失敗-----");
            logger.error("資料庫連線失敗", e);
        }
    }

    static String getTimestampFilename() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");
        return LocalDateTime.now().format(formatter);
    }

    private static File chooseDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("選擇匯出資料夾");

        JFrame frame = new JFrame();
        frame.setAlwaysOnTop(true);
        int result = chooser.showOpenDialog(frame);
        frame.dispose();

        return (result == JFileChooser.APPROVE_OPTION) ? chooser.getSelectedFile() : null;
    }

    private static String askFileName(String title) {
        String input = JOptionPane.showInputDialog(null, title, "自訂檔名", JOptionPane.PLAIN_MESSAGE);
        return input == null ? "" : input.trim();
    }
}
