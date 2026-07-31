package com.example.oidcclient.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * OIDC 客户端首页控制器
 *
 * 受 oauth2Login() 保护，未登录用户会被重定向到认证中心。
 * 登录成功后，Spring Security 将 OidcUser 自动注入到请求参数中。
 */
@Controller
public class HomeController {

    /**
     * 客户端首页
     *
     * @param principal OidcUser：Spring Security 在 OIDC 登录成功后
     *                  自动注入，包含 id_token 中的所有 claims（声明）
     */
    @GetMapping("/")
    public String home(@AuthenticationPrincipal OidcUser principal, Model model) {
        if (principal != null) {
            // OIDC 用户基本信息
            model.addAttribute("name", principal.getFullName());
            model.addAttribute("email", principal.getEmail());
            // sub：用户的唯一标识（OIDC 标准字段）
            model.addAttribute("subject", principal.getSubject());
            // id_token 是 JWT 格式，可解析出用户身份声明
            model.addAttribute("idToken", principal.getIdToken().getTokenValue());
            // 所有 claims（sub, email, email_verified, ...）
            model.addAttribute("claims", principal.getClaims());
        }
        return "home";
    }
}
