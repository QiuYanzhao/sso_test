package com.example.satoken.sso.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Collections;

/**
 * SSO 客户端应用 —— 启动类
 *
 * 接入 Sa-Token SSO 模式三的业务应用。
 * 端口：8081（通过 setDefaultProperties 设定）
 *
 * 启动方式：
 *   1. 确保 SsoServerApp（认证中心，端口 8080）已启动
 *   2. 运行本类的 main 方法
 *   3. 浏览器访问 http://localhost:8081
 *   4. 首次访问将被重定向到 http://localhost:8080/sso/auth 登录
 *
 * 多客户端测试：
 *   修改 port 为 8082，再启动一个实例，验证单点登录效果。
 *   在客户端 A 登录后，访问客户端 B 无需重新登录（认证中心已有 Session）。
 */
@SpringBootApplication(scanBasePackages = "com.example.satoken.sso.client")
public class SsoClientApp {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SsoClientApp.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", "8081"));
        app.run(args);
        System.out.println("========================================");
        System.out.println("  SSO 客户端应用已启动");
        System.out.println("  本地地址: http://localhost:8081");
        System.out.println("  认证中心: http://localhost:8080/sso/auth");
        System.out.println("========================================");
    }
}
