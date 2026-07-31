package com.example.oidcclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OIDC 客户端启动类
 *
 * 接入 Spring Authorization Server 认证中心的 OIDC 客户端应用。
 * 端口：8081
 *
 * 依赖 spring-boot-starter-oauth2-client，通过 OAuth2/OIDC 授权码流程
 * 对接认证中心实现单点登录。
 *
 * 启动前确保 authorizationServer 已在 8080 端口运行。
 */
@SpringBootApplication
public class OidcClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(OidcClientApplication.class, args);
        System.out.println("========================================");
        System.out.println("  OIDC 客户端已启动");
        System.out.println("  本地地址: http://localhost:8081");
        System.out.println("  认证中心: http://localhost:8080");
        System.out.println("========================================");
    }
}
