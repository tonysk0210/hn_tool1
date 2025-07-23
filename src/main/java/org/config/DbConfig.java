package org.config;

public class DbConfig {
    public static String host;
    public static String databaseName;
    public static String username;
    public static String password;

    public static String getJdbcUrl() {
        return "jdbc:sqlserver://" + host + ":1433;databaseName=" + databaseName + ";encrypt=true;trustServerCertificate=true;";
    }
}