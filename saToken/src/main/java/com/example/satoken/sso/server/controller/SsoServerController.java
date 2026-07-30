package com.example.satoken.sso.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.satoken.sso.server.auth.ThirdPartyAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * SSO 认证中心 —— 第三方登录控制器
 *
 * 负责处理 GitHub、微信、QQ 等第三方平台的 OAuth2 登录流程。
 *
 * 完整登录流程：
 * ============
 * 1. 用户访问客户端 → 被 SaInterceptor 拦截 → 重定向到认证中心 /sso/auth
 * 2. 认证中心展示登录页 → 用户点击"第三方登录" → 跳转到 /sso/third-party
 * 3. 第三方登录页展示各平台按钮 → 用户点击"GitHub 登录"
 * 4. 本 Controller 将用户重定向到 GitHub 授权页面
 * 5. 用户在 GitHub 完成授权 → GitHub 回调到 /sso/third-auth/github/callback
 * 6. 本 Controller 用 code 换取 access_token，获取用户信息
 * 7. 调用 StpUtil.login(userId) 在认证中心完成登录
 * 8. 重定向回 /sso/auth?redirect=原客户端地址 → SaToken 生成 ticket
 * 9. 用户带着 ticket 回到客户端 → 客户端校验 ticket → 登录完成
 */
@Controller
@RequestMapping("/sso")
public class SsoServerController {

    @Autowired
    private ThirdPartyAuthService thirdPartyAuthService;

    /**
     * 第三方登录选择页面
     *
     * 展示 GitHub / 微信 / QQ 的登录入口按钮。
     * redirect 参数记录了原始客户端回调地址，在整个 OAuth 流程中传递。
     */
    @GetMapping("/third-party")
    public String thirdPartyPage(HttpServletRequest request, Model model) {
        model.addAttribute("redirect", request.getParameter("redirect"));
        return "login";
    }

    /**
     * 第三方登录入口 —— 重定向到 OAuth 平台授权页
     *
     * 根据 platform 参数决定跳转到哪个 OAuth 平台：
     * - github → GitHub OAuth 授权页
     * - wechat → 微信开放平台扫码页
     * - qq     → QQ 互联授权页
     *
     * @param platform   平台标识（github / wechat / qq）
     * @param redirect   SSO 回调地址（客户端地址，需要通过 OAuth 的 state 参数保存）
     * @param response   HttpServletResponse，用于发送 302 重定向
     */
    @GetMapping("/third-auth/{platform}")
    public void thirdAuth(@PathVariable String platform,
                          @RequestParam(required = false) String redirect,
                          HttpServletResponse response) throws IOException {
        String authUrl = thirdPartyAuthService.buildAuthUrl(platform, redirect);
        response.sendRedirect(authUrl);
    }

    /**
     * 第三方 OAuth 回调处理
     *
     * OAuth 平台在用户授权后，将 code 和 state 回调到本方法。
     * 本方法完成以下操作：
     * 1. 用 code 向 OAuth 平台换取 access_token
     * 2. 用 access_token 获取用户信息（openid、昵称、头像等）
     * 3. 查询或创建本地用户（建立 openid → userId 映射）
     * 4. 调用 StpUtil.login() 在认证中心完成登录
     * 5. 通过 SaSsoUtil 生成 SSO ticket 并重定向到客户端
     *
     * @param platform  平台标识
     * @param code      OAuth 授权码
     * @param state     状态参数（包含原始 redirect 地址）
     * @param request   HttpServletRequest
     * @param response  HttpServletResponse
     */
    @GetMapping("/third-auth/{platform}/callback")
    public void thirdCallback(@PathVariable String platform,
                              @RequestParam String code,
                              @RequestParam(required = false) String state,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        // 1. 调用 ThirdPartyAuthService 完成 OAuth 认证，获取第三方用户信息
        Map<String, Object> thirdPartyUser = thirdPartyAuthService.authenticate(platform, code, state);

        // 2. 提取该用户在第三方平台的唯一标识
        String openId = (String) thirdPartyUser.get("openId");
        String nickname = (String) thirdPartyUser.getOrDefault("nickname", openId);

        // ===== 实际项目中的处理逻辑 =====
        // 此处应根据 openId 查询本地数据库，建立"第三方账号 → 本地用户"映射：
        //
        // User localUser = userService.findByOpenId(platform, openId);
        // if (localUser == null) {
        //     // 首次使用第三方登录，自动注册本地用户
        //     localUser = userService.register(platform, openId, nickname, avatar);
        // }
        // String userId = localUser.getId();

        // 演示：直接使用 "platform:openId" 作为 userId
        String userId = platform + ":" + openId;

        // 3. 在认证中心执行登录（此为 SSO Server 端的会话）
        StpUtil.login(userId);

        System.out.println("=== 第三方登录成功 === ");
        System.out.println("  平台: " + platform);
        System.out.println("  OpenId: " + openId);
        System.out.println("  昵称: " + nickname);
        System.out.println("  SSO UserId: " + userId);

        // 4. 重定向回 /sso/auth —— 利用 SaToken SSO 的标准流程生成 ticket
        //    由于此时 StpUtil.isLogin() 已经为 true，
        //    SaToken 的 /sso/auth 会检测到已登录状态，
        //    自动生成 ticket 并重定向回客户端。
        String ssoAuthUrl = "/sso/auth";
        if (state != null && !state.isEmpty()) {
            ssoAuthUrl += "?redirect=" + URLEncoder.encode(state, "UTF-8");
        }
        response.sendRedirect(ssoAuthUrl);
    }
}
