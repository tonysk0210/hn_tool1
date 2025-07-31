package org.config;

public class DbConfig {
    public static String host;
    public static String databaseName;
    public static String username;
    public static String password;
    public static boolean integratedSecurity;

    public static void loadFromConfig() {
        host = ConfigLoader.get("db.host");
        databaseName = ConfigLoader.get("db.name");
        username = ConfigLoader.get("db.user");
        password = ConfigLoader.get("db.password");
        integratedSecurity = Boolean.parseBoolean(ConfigLoader.get("db.integratedSecurity")); // 預設 false
    }

    public static String getJdbcUrl() {
        if (integratedSecurity) {
            return "jdbc:sqlserver://" + host + ";databaseName=" + databaseName + ";integratedSecurity=true;"; // 回傳MS驗證
        }
        return "jdbc:sqlserver://" + host + ":1433;databaseName=" + databaseName + ";encrypt=true;trustServerCertificate=true;loginTimeout=5;";
    }
}