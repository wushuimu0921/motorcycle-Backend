package com.motor.erp.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.sql2o.Sql2o;

public class DatabaseConfig {
    private static HikariDataSource dataSource;
    private static Sql2o sql2o;

    static {
        HikariConfig config = new HikariConfig();

        // 讀取環境變數 (Railway/Supabase 部署用)
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USER");
        String dbPass = System.getenv("DB_PASSWORD");

        if (dbUrl == null) {
            // 本地開發環境 (Mac Docker)
            System.out.println(">>> 偵測為本地環境，連接 Docker PostgreSQL...");
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/postgres");
            config.setUsername("postgres");
            config.setPassword("610921");
        } else {
            // 雲端部署環境
            System.out.println(">>> 偵測為雲端環境，連接 Supabase...");
            config.setJdbcUrl(dbUrl);
            config.setUsername(dbUser);
            config.setPassword(dbPass);
        }

        // HikariCP 效能優化
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");

        dataSource = new HikariDataSource(config);

        // 2. 初始化 Sql2o 並使用 DataSource
        // 使用 NoQuirks 代表使用標準 SQL 規範
        sql2o = new Sql2o(dataSource);

        System.out.println(">>> Database Connection Pool Initialized (HikariCP).");
    }

    public static DataSource getDataSource() {
        return dataSource;
    }

    public static Sql2o getSql2o() {
        return sql2o;
    }
}