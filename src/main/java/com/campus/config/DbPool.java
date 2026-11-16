package com.campus.config;

import java.sql.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

/**
 * 数据库连接池 - 提升并发性能，避免频繁创建连接
 */
public final class DbPool {
    private static final Logger LOG = Logger.getLogger(DbPool.class.getName());
    private static final DbPool INSTANCE = new DbPool();

    private final BlockingQueue<Connection> pool;
    private final int poolSize;
    private volatile boolean initialized = false;

    private DbPool() {
        var config = AppConfig.getInstance();
        this.poolSize = config.getDbPoolSize();
        this.pool = new ArrayBlockingQueue<>(poolSize);
    }

    public static DbPool getInstance() { return INSTANCE; }

    /**
     * 初始化连接池（如果尚未初始化则自动初始化）
     */
    public synchronized void init() throws SQLException {
        if (initialized) return;
        try { Class.forName("org.sqlite.JDBC"); }
        catch (ClassNotFoundException e) { throw new SQLException("SQLite JDBC驱动未找到", e); }

        String url = "jdbc:sqlite:" + AppConfig.getInstance().getDbPath();
        for (int i = 0; i < poolSize; i++) {
            var conn = DriverManager.getConnection(url);
            try (var stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA foreign_keys=ON");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA cache_size=-64000");
                stmt.execute("PRAGMA temp_store=MEMORY");
            }
            pool.offer(conn);
        }
        initialized = true;
        LOG.info("数据库连接池初始化完成，大小: " + poolSize);
    }

    /**
     * 获取连接（阻塞等待），自动初始化
     */
    public Connection borrow() throws SQLException {
        if (!initialized) init();
        try {
            var conn = pool.take();
            if (conn.isClosed()) {
                String url = "jdbc:sqlite:" + AppConfig.getInstance().getDbPath();
                conn = DriverManager.getConnection(url);
                try (var stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL");
                    stmt.execute("PRAGMA foreign_keys=ON");
                }
            }
            return conn;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("获取数据库连接被中断");
        }
    }

    /**
     * 归还连接
     */
    public void release(Connection conn) {
        if (conn != null) pool.offer(conn);
    }

    /**
     * 关闭所有连接
     */
    public synchronized void shutdown() {
        Connection conn;
        while ((conn = pool.poll()) != null) {
            try { conn.close(); } catch (SQLException e) { /* ignore */ }
        }
        initialized = false;
        LOG.info("数据库连接池已关闭");
    }
}
