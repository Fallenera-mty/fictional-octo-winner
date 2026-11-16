package com.campus;

import com.campus.config.AppConfig;
import com.campus.config.DbPool;
import com.campus.config.DbSchema;
import com.campus.control.RouterController;

/**
 * NirvanaFlower - 校园活动平台启动入口
 *
 * 在 IDEA 中直接运行此类的 main 方法即可启动整个应用。
 */
public class NirvanaFlower {

    public static void main(String[] args) throws Exception {
        var config = AppConfig.getInstance();

        // 初始化数据库
        var pool = DbPool.getInstance();
        var schema = new DbSchema();
        schema.initSchema();
        schema.initSampleData();

        // 创建HTTP服务器
        var server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress(config.getPort()), 0);

        RouterController.register(server);

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(config.getHttpThreadPoolSize()));
        server.start();

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║                                                      ║");
        System.out.println("║       NirvanaFlower  校园活动平台  已启动             ║");
        System.out.println("║                                                      ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("  访问地址:  http://localhost:" + config.getPort());
        System.out.println("  数据库:    " + config.getDbPath());
        System.out.println("  连接池:    " + config.getDbPoolSize() + " 连接");
        System.out.println("  线程池:    " + config.getHttpThreadPoolSize() + " 线程");
        System.out.println();
        System.out.println("  按 Ctrl+C 停止服务器");
        System.out.println();

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println();
            System.out.println("[NirvanaFlower] 正在关闭服务器...");
            server.stop(0);
            pool.shutdown();
            System.out.println("[NirvanaFlower] 服务器已关闭");
        }));
    }
}
