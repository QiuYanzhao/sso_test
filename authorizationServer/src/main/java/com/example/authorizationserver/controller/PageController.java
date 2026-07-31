package com.example.authorizationserver.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面路由控制器
 *
 * Spring Security 的 formLogin().loginPage("/login") 不会自动创建 GET 路由，
 * 需要手动提供 Controller 将请求映射到 Thymeleaf 模板。
 */
@Controller
public class PageController {

    /**
     * 登录页面
     * 渲染 templates/login.html
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * 首页（登录后可见）
     * 渲染 templates/index.html（如果不存在则显示简单文本）
     */
    @GetMapping("/")
    public String home() {
        return "index";
    }
}
