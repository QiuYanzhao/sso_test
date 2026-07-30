package com.example.satoken.sso.client.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.sso.SaSsoConsts;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * SSO 客户端 —— 首页控制器
 *
 * 受保护的路由，未登录用户会被 SaInterceptor 拦截并重定向到认证中心。
 *
 * 通过 StpUtil 获取当前登录用户的信息：
 * - StpUtil.isLogin()        判断是否登录
 * - StpUtil.getLoginId()     获取当前用户 ID
 * - StpUtil.getTokenValue()  获取当前 Token 值
 * - StpUtil.logout()          退出登录（客户端本地退出）
 */
@Controller
public class HomeController {

    /**
     * 客户端首页 —— 需要登录才能访问
     *
     * 展示当前登录用户信息和 Token。
     * 若用户未登录，SaInterceptor 会在到达此方法前将用户重定向到认证中心。
     */
    @GetMapping("/")
    public String home(Model model) {
        // 获取当前登录用户 ID
        String loginId = StpUtil.getLoginIdAsString();
        // 获取当前 Token 值（用于展示）
        String tokenValue = StpUtil.getTokenValue();

        model.addAttribute("loginId", loginId);
        model.addAttribute("tokenValue", tokenValue);
        return "home";
    }
}
