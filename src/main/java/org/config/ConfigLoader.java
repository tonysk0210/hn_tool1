package org.config;

import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private static final Properties props = new Properties();

    public static void load(String configFileName) {
        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(configFileName)) {
            if (in == null) {
                throw new RuntimeException("❌ 找不到設定檔 " + configFileName + "，請確認已打包進 resources");
            }
            props.load(in);
        } catch (Exception e) {
            throw new RuntimeException("❌ 載入設定檔失敗: " + configFileName, e);
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }
}
