package com.example.satoken.sso;

/**
 * SSO 常量定义
 *
 * 存放 SSO 相关的配置常量、第三方 OAuth 平台参数等。
 *
 * ============================================================================
 * 第三方登录接入说明（GitHub / 微信 / QQ）
 * ============================================================================
 *
 * 以下 APP_ID / APP_SECRET / REDIRECT_URI 需要在各平台开发者后台申请：
 *
 * GitHub OAuth App 申请地址：https://github.com/settings/developers
 *   回调地址设置：http://localhost:8080/sso/third-auth/github/callback
 *
 * 微信开放平台申请地址：https://open.weixin.qq.com/
 *   回调地址设置：http://localhost:8080/sso/third-auth/wechat/callback
 *   注意：需要企业资质，个人开发者可使用"微信测试号"
 *
 * QQ 互联申请地址：https://connect.qq.com/
 *   回调地址设置：http://localhost:8080/sso/third-auth/qq/callback
 *
 * 使用前将这些占位值替换为你的真实 AppID/AppSecret。
 */
public final class SsoConst {

    private SsoConst() {
    }

    /** SSO Server 端口 */
    public static final int SERVER_PORT = 8080;

    /** SSO Client 默认端口 */
    public static final int CLIENT_PORT = 8081;

    /** SSO Server 地址 */
    public static final String SERVER_URL = "http://localhost:" + SERVER_PORT;

    // ========================= GitHub OAuth =========================

    /** GitHub OAuth App Client ID */
    public static final String GITHUB_CLIENT_ID = "your-github-client-id";

    /** GitHub OAuth App Client Secret */
    public static final String GITHUB_CLIENT_SECRET = "your-github-client-secret";

    /** GitHub OAuth 授权地址 */
    public static final String GITHUB_AUTH_URL = "https://github.com/login/oauth/authorize";

    /** GitHub OAuth Token 地址 */
    public static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";

    /** GitHub 用户信息 API */
    public static final String GITHUB_USER_API = "https://api.github.com/user";

    // ========================= 微信开放平台 OAuth =========================

    /** 微信开放平台 AppID */
    public static final String WECHAT_APP_ID = "your-wechat-app-id";

    /** 微信开放平台 AppSecret */
    public static final String WECHAT_APP_SECRET = "your-wechat-app-secret";

    /** 微信扫码登录授权地址 */
    public static final String WECHAT_AUTH_URL = "https://open.weixin.qq.com/connect/qrconnect";

    /** 微信 Token 接口 */
    public static final String WECHAT_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";

    /** 微信用户信息接口 */
    public static final String WECHAT_USER_INFO_URL = "https://api.weixin.qq.com/sns/userinfo";

    // ========================= QQ 互联 OAuth =========================

    /** QQ 互联 AppID */
    public static final String QQ_APP_ID = "your-qq-app-id";

    /** QQ 互联 AppSecret */
    public static final String QQ_APP_SECRET = "your-qq-app-secret";

    /** QQ 授权地址 */
    public static final String QQ_AUTH_URL = "https://graph.qq.com/oauth2.0/authorize";

    /** QQ Token 接口 */
    public static final String QQ_TOKEN_URL = "https://graph.qq.com/oauth2.0/token";

    /** QQ 获取 OpenID 接口 */
    public static final String QQ_OPENID_URL = "https://graph.qq.com/oauth2.0/me";

    /** QQ 用户信息接口 */
    public static final String QQ_USER_INFO_URL = "https://graph.qq.com/user/get_user_info";
}
