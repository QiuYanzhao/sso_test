package com.example.ssoclient.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token SSO 客户端配置
 *
 * 职责：
 * 1. 注册 SaInterceptor 拦截器，对受保护路由进行认证检查
 * 2. 放行 SSO 回调路由（/sso/**），避免死循环重定向
 *
 * 认证流程：
 *   用户访问 / → SaInterceptor 检测未登录
 *   → 重定向到认证中心 /sso/auth → 用户登录
 *   → 认证中心生成 ticket → 重定向回 /sso/login?ticket=xxx
 *   → SaToken 自动调用 /sso/checkTicket 验证 ticket
 *   → 验证通过 → 客户端完成本地登录 → 重定向到原始页面
 *
 * SaToken SSO 自动注册的端点（客户端侧）：
 *   GET /sso/login   —— 接收认证中心回调（带 ticket）
 *   GET /sso/logout  —— 触发单点注销
 */
@Configuration
public class SsoClientConfig implements WebMvcConfigurer {

    /**
     * 注册 SaToken 路由拦截器
     *
     * SaInterceptor 自动处理：
     * 1. 检查当前请求是否已通过 SaToken 认证
     * 2. 如果未登录 → 重定向到 application.yml 配置的 sa-token.sso.auth-url
     * 3. 如果请求包含 ticket 参数 → 自动调用认证中心校验 ticket
     *
     * 放行规则：
     * - /sso/**   SSO 回调路由，拦截会导致死循环
     * - /error    Spring Boot 错误页
     * - /static/** 静态资源
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/**")               // 拦截所有请求
                .excludePathPatterns(
                        "/sso/**",                    // SSO 回调（接收 ticket）
                        "/error",                     // 错误页
                        "/static/**",                 // 静态资源
                        "/favicon.ico"
                );
    }
}
