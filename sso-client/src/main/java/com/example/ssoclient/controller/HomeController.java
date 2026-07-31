package com.example.ssoclient.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Sa-Token SSO 客户端首页控制器
 *
 * 受 SaInterceptor 保护，未登录用户会被重定向到认证中心。
 * 通过 StpUtil 工具类获取当前登录用户信息。
 */
@Controller
public class HomeController {

    /**
     * 客户端首页
     *
     * StpUtil 是 Sa-Token 的核心 API：
     * - StpUtil.isLogin()       判断当前会话是否登录
     * - StpUtil.getLoginId()    获取当前用户 ID
     * - StpUtil.getTokenValue() 获取当前 Token 值
     */
    @GetMapping("/")
    public String home(Model model) {
        // 获取当前登录用户 ID（此处已经是登录状态，否则拦截器已重定向）
        String loginId = StpUtil.getLoginIdAsString();
        // 获取当前 Token 值
        String tokenValue = StpUtil.getTokenValue();

        model.addAttribute("loginId", loginId);
        model.addAttribute("tokenValue", tokenValue);
        return "home";
    }
}
