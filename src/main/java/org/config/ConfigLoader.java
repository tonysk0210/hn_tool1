package org.config;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.io.File;

public class ConfigLoader {
    private static final Properties props = new Properties();

/*    static {
        String configPath = System.getProperty("config.path");

        if (configPath == null) {
            if (new File("./config/config.properties").exists()) {
                configPath = "./config/config.properties";
//                System.out.println("⚠️ 未指定 config.path，預設使用：" + configPath);
            } else if (new File("config.properties").exists()) {
                configPath = "config.properties";

    //                System.out.println("⚠️ 未指定 config.path，預設使用：" + configPath);
            } else {
                throw new RuntimeException("❌ 找不到資料庫資料,請確認後重新輸入");
            }
        }

        try (InputStream in = new FileInputStream(configPath)) {
            props.load(in);
        } catch (Exception e) {
            throw new RuntimeException("❌ 無法載入設定檔，請確認路徑與檔案內容正確", e);
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }*/

    static {
        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in == null) {
                throw new RuntimeException("❌ 找不到內嵌設定檔 config.properties，請確認已打包進 resources");
            }
            props.load(in);
        } catch (Exception e) {
            throw new RuntimeException("❌ 載入內嵌設定檔失敗", e);
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }
}
