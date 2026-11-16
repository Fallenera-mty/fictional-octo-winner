package com.campus.config;

import java.util.logging.Level;

/**
 * 应用配置 - 单例，集中管理所有配置项
 */
public final class AppConfig {
    private static final AppConfig INSTANCE = new AppConfig();

    private int port = 8080;
    private String webRoot = ".";
    private String dbPath = "campus_activity.db";
    private int dbPoolSize = 5;
    private int httpThreadPoolSize = 8;
    private Level logLevel = Level.INFO;
    private long sessionTimeoutMs = 24 * 60 * 60 * 1000L; // 24小时
    private int maxRequestBodySize = 1024 * 1024; // 1MB

    private AppConfig() {}

    public static AppConfig getInstance() { return INSTANCE; }

    public int getPort() { return port; }
    public String getWebRoot() { return webRoot; }
    public String getDbPath() { return dbPath; }
    public int getDbPoolSize() { return dbPoolSize; }
    public int getHttpThreadPoolSize() { return httpThreadPoolSize; }
    public Level getLogLevel() { return logLevel; }
    public long getSessionTimeoutMs() { return sessionTimeoutMs; }
    public int getMaxRequestBodySize() { return maxRequestBodySize; }
}
