package org.config;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.io.File;

public class ConfigLoader {
    private static final Properties props = new Properties();

//    static {
//        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream("config.properties")) {
//            if (in == null) {
//                throw new RuntimeException("❌ 找不到內嵌設定檔 config.properties，請確認已打包進 resources");
//            }
//            props.load(in);
//        } catch (Exception e) {
//            throw new RuntimeException("❌ 載入內嵌設定檔失敗", e);
//        }
//    }
//
//    public static String get(String key) {
//        return props.getProperty(key);
//    }

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
