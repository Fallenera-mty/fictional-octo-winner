package com.campus;

import com.campus.config.AppConfig;
import com.campus.config.DbPool;
import com.campus.config.DbSchema;
import com.campus.control.RouterController;

/**
 * 校园活动平台 - 企业级后端服务器
 *
 * 分层架构：
 *   config/        - 配置与连接池
 *   model/         - 异常与响应模型
 *   dataobject/    - 数据对象(DO)
 *   dao/           - 数据访问层
 *   interceptor/   - 请求拦截器
 *   service/       - 业务逻辑层
 *   control/       - 路由控制器
 *   api/           - HTTP/JSON工具
 *
 * 启动方式：见项目构建脚本
 */
public class Application {
    public static void main(String[] args) throws Exception {
        var config = AppConfig.getInstance();

        // 初始化数据库连接池
        var pool = DbPool.getInstance();
        var schema = new DbSchema();
        schema.initSchema();
        schema.initSampleData();

        // 创建HTTP服务器
        var server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress(config.getPort()), 0);

        // 注册路由
        RouterController.register(server);

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(config.getHttpThreadPoolSize()));
        server.start();

        System.out.println("============================================");
        System.out.println("  校园活动平台服务器已启动 (企业级架构)");
        System.out.println("  访问地址: http://localhost:" + config.getPort());
        System.out.println("  数据库: " + config.getDbPath());
        System.out.println("  连接池: " + config.getDbPoolSize() + " 连接");
        System.out.println("  线程池: " + config.getHttpThreadPoolSize() + " 线程");
        System.out.println("============================================");
        System.out.println();
        System.out.println("按 Ctrl+C 停止服务器");
    }
}
