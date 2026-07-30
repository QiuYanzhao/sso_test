package com.example.satoken.sso.client.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.sso.SaSsoProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SSO Client 端配置
 *
 * 职责：
 * 1. 注册 SaInterceptor，拦截所有需要认证的路由
 * 2. 未登录的用户被 SaInterceptor 拦截后，自动重定向到认证中心
 * 3. 放行 SSO 回调路由（/sso/*），避免死循环
 *
 * SaToken SSO 自动注册的端点（Client 端）：
 *   GET /sso/login    —— 接收认证中心的回调（带 ticket 参数）
 *   GET /sso/logout   —— 触发单点注销（通知认证中心 + 清理本地 Session）
 *
 * 认证流程：
 *   用户访问受保护页面 → SaInterceptor 检测未登录
 *   → 重定向到认证中心 /sso/auth → 用户登录
 *   → 认证中心重定向回 /sso/login?ticket=xxx
 *   → SaToken 自动校验 ticket → 完成客户端登录 → 重定向到原始页面
 */
@Configuration
public class SaTokenClientConfig implements WebMvcConfigurer {

    /**
     * 注册 SaToken 路由拦截器
     *
     * 拦截所有请求（/**），对未登录的用户：
     * - SaInterceptor 自动调用 SaSsoProcessor 进行 SSO 重定向
     * - 用户将被重定向到 sa-token.sso.auth-url 所配置的认证中心
     *
     * 放行的路径：
     * - /sso/**：SSO 回调路由（接收认证中心的 ticket）
     * - /error：Spring Boot 默认错误页
     * - /static/** / /favicon.ico：静态资源
     *
     * 注意：如果放行 / 根路径，用户首次访问不会触发 SSO。
     *       这里故意拦截所有路径（包括 /），确保未登录用户一定跳转到认证中心。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // SaInterceptor 自动处理：
                    // 1. 检查当前请求是否已登录（StpUtil.isLogin()）
                    // 2. 如果请求包含 ticket 参数 → 自动调用 /sso/checkTicket 校验
                    // 3. 如果未登录 → 重定向到认证中心
                }))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/sso/**",       // SSO 回调路由
                        "/error",        // 错误页
                        "/static/**",    // 静态资源
                        "/favicon.ico"
                );
    }
}
