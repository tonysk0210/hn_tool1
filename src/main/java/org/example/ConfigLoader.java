package org.example;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private static final Properties props = new Properties();

    static {
        String configPath = System.getProperty("config.path");
        if (configPath == null) {
            throw new RuntimeException("❌ 請使用 -Dconfig.path=路徑 設定資料庫設定檔");
        }

        try (InputStream in = new FileInputStream(configPath)) {
            props.load(in);
        } catch (Exception e) {
            throw new RuntimeException("❌ 無法載入設定檔，請確認路徑與檔案內容正確", e);
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }
}
