package com.example.satoken.sso.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Collections;

/**
 * SSO 认证中心 —— 启动类
 *
 * Sa-Token SSO 模式三（Http 请求调用）的 Server 端。
 * 端口：8080（通过 setDefaultProperties 设定）
 *
 * 启动方式（IDE 中直接运行本类的 main 方法即可）：
 *   1. 先启动 SsoServerApp（本类）
 *   2. 再启动 SsoClientApp（客户端）
 *   3. 浏览器访问 http://localhost:8081 → 自动跳转到本认证中心登录
 */
@SpringBootApplication(scanBasePackages = "com.example.satoken.sso.server")
public class SsoServerApp {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SsoServerApp.class);
        // 设定认证中心端口为 8080
        app.setDefaultProperties(Collections.singletonMap("server.port", "8080"));
        app.run(args);
        System.out.println("========================================");
        System.out.println("  Sa-Token SSO 认证中心已启动");
        System.out.println("  本地地址: http://localhost:8080");
        System.out.println("  登录页面: http://localhost:8080/sso/auth");
        System.out.println("  第三方登录: http://localhost:8080/sso/third-party");
        System.out.println("========================================");
    }
}
