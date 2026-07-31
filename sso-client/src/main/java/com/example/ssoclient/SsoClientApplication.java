package com.example.ssoclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sa-Token SSO 客户端启动类
 *
 * 接入 Sa-Token SSO 认证中心的客户端应用。
 * 端口：8081
 *
 * 使用 Sa-Token SSO 模式三（Http 请求调用），通过 Ticket 机制
 * 与认证中心交互完成单点登录。
 *
 * 启动前确保 saToken 模块的 SsoServerApp 已在 8080 端口运行。
 */
@SpringBootApplication
public class SsoClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(SsoClientApplication.class, args);
        System.out.println("========================================");
        System.out.println("  Sa-Token SSO 客户端已启动");
        System.out.println("  本地地址: http://localhost:8081");
        System.out.println("  认证中心: http://localhost:8080/sso/auth");
        System.out.println("========================================");
    }
}
