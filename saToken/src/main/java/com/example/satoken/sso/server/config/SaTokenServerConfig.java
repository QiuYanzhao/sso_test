package com.example.satoken.sso.server.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SSO Server 端配置
 *
 * 职责：
 * 1. 注册 SaToken 路由拦截器，保护需要认证的路由
 * 2. 放行 SSO 相关端点（登录页、登录接口、第三方回调等），避免死循环重定向
 *
 * SaToken SSO 自动注册的端点（Server 端）：
 *   GET  /sso/auth          —— 登录页面（SaToken 内置，账号密码登录）
 *   POST /sso/doLogin       —— 处理登录表单提交
 *   POST /sso/checkTicket   —— 校验 ticket（Http 模式，供 Client 调用）
 *   POST /sso/getData       —— 获取用户数据 API
 *   GET  /sso/logout        —— 退出登录
 *
 * 第三方登录不是 SSO 标准的一部分，通过额外的 Controller 端点提供。
 */
@Configuration
public class SaTokenServerConfig implements WebMvcConfigurer {

    /**
     * 注册 SaToken 路由拦截器
     *
     * 拦截规则：
     * - /**          所有路由都需要认证
     * - /sso/**      放行——SSO 相关端点本身就是认证入口，拦截会死循环
     * - /sso/third-**放行——第三方 OAuth 登录相关路由
     * - /login*      放行——自定义登录页面
     * - /error       放行——Spring Boot 错误页
     * - /static/**   放行——静态资源
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // SaInterceptor 自动处理：
                    // 1. 检查当前请求是否已登录（StpUtil.isLogin()）
                    // 2. 未登录 → 重定向到 SaToken 默认的 /sso/auth 登录页
                    // 3. 已登录 → 放行
                }))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/sso/**",         // SSO 核心端点（登录页、登录处理、回调等）
                        "/error",          // 错误页面
                        "/static/**",      // 静态资源
                        "/favicon.ico"
                );
    }
}
