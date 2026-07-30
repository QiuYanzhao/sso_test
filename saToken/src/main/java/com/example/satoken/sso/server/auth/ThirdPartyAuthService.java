package com.example.satoken.sso.server.auth;

import com.example.satoken.sso.SsoConst;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * 第三方 OAuth 登录服务
 *
 * 手动实现 GitHub / 微信 / QQ 的 OAuth2 授权流程，
 * 不依赖 Spring Security OAuth2 Client，完全由代码控制。
 *
 * 每个平台的对接流程：
 * =====================
 *
 * 【GitHub】（标准 OAuth2）
 *   1. 构建授权 URL → 重定向用户到 GitHub
 *   2. 用户授权 → GitHub 回调返回 code
 *   3. POST 请求用 code 换 access_token
 *   4. GET 请求用 access_token 获取用户信息
 *   5. 提取 id、login 等字段作为用户标识
 *
 * 【微信开放平台】（非标准 OAuth2）
 *   微信的 OAuth2 与标准协议有以下差异：
 *   - 授权 URL 必须以 #wechat_redirect 结尾
 *   - Token 接口返回 JSON，字段名符合标准
 *   - 用户信息接口需要 access_token + openid 两个参数
 *   - 返回字段：openid, nickname, sex, headimgurl, unionid（需申请）
 *
 * 【QQ 互联】（非标准 OAuth2）
 *   QQ 的 OAuth2 与标准协议有以下差异：
 *   - Token 接口返回的是 URL 参数格式（非 JSON）：access_token=xxx&expires_in=xxx
 *   - 获取用户信息前必须先获取 openid（/oauth2.0/me）
 *   - openid 接口返回：callback( {"client_id":"xxx","openid":"xxx"} );
 *   - 用户信息接口需要 oauth_consumer_key + access_token + openid
 *   - 返回字段：ret, msg, nickname, figureurl, gender 等
 */
@Service
public class ThirdPartyAuthService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 构建 OAuth 授权 URL
     *
     * 生成各平台的授权页面地址，将用户重定向过去进行授权。
     *
     * @param platform  平台标识（github / wechat / qq）
     * @param redirect  原始 SSO 重定向地址（保存在 state 参数中）
     * @return OAuth 授权页完整 URL
     */
    public String buildAuthUrl(String platform, String redirect) throws IOException {
        // 构造本服务的回调地址
        String callbackUrl = SsoConst.SERVER_URL + "/sso/third-auth/" + platform + "/callback";

        switch (platform) {
            case "github":
                // GitHub 标准 OAuth2 授权 URL
                // scope=read:user 获取基本用户信息
                // state 参数用于防止 CSRF 攻击，并保存 redirect 地址
                return SsoConst.GITHUB_AUTH_URL
                        + "?client_id=" + SsoConst.GITHUB_CLIENT_ID
                        + "&redirect_uri=" + encode(callbackUrl)
                        + "&scope=read:user"
                        + "&state=" + encode(redirect != null ? redirect : "");

            case "wechat":
                // 微信扫码登录授权 URL
                // 注意：必须以 #wechat_redirect 结尾（微信非标准 OAuth2）
                return SsoConst.WECHAT_AUTH_URL
                        + "?appid=" + SsoConst.WECHAT_APP_ID
                        + "&redirect_uri=" + encode(callbackUrl)
                        + "&response_type=code"
                        + "&scope=snsapi_login"
                        + "&state=" + encode(redirect != null ? redirect : "")
                        + "#wechat_redirect";

            case "qq":
                // QQ 互联 OAuth2 授权 URL
                return SsoConst.QQ_AUTH_URL
                        + "?response_type=code"
                        + "&client_id=" + SsoConst.QQ_APP_ID
                        + "&redirect_uri=" + encode(callbackUrl)
                        + "&scope=get_user_info"
                        + "&state=" + encode(redirect != null ? redirect : "");

            default:
                throw new IllegalArgumentException("不支持的第三方登录平台: " + platform);
        }
    }

    /**
     * 完成第三方 OAuth 认证
     *
     * 步骤：
     * 1. 用授权码（code）换取访问令牌（access_token）
     * 2. 用访问令牌获取用户信息
     * 3. 返回标准化的用户信息 Map
     *
     * @param platform  平台标识
     * @param code      OAuth 授权码
     * @param state     状态参数
     * @return 标准化的用户信息 Map（包含 openId, nickname, avatar, platform 等字段）
     */
    public Map<String, Object> authenticate(String platform, String code, String state) throws IOException {
        switch (platform) {
            case "github":
                return authenticateGitHub(code);
            case "wechat":
                return authenticateWechat(code);
            case "qq":
                return authenticateQQ(code);
            default:
                throw new IllegalArgumentException("不支持的平台: " + platform);
        }
    }

    // ========================= GitHub OAuth =========================

    /**
     * GitHub OAuth 认证
     *
     * 流程：
     * 1. POST https://github.com/login/oauth/access_token → 获取 access_token
     * 2. GET https://api.github.com/user → 获取用户信息
     */
    private Map<String, Object> authenticateGitHub(String code) throws IOException {
        String callbackUrl = SsoConst.SERVER_URL + "/sso/third-auth/github/callback";

        // 步骤 1：用 code 换取 access_token
        // GitHub 的 token 接口支持两种响应格式：
        //   Accept: application/json → JSON
        //   Accept: application/x-www-form-urlencoded → URL 参数格式
        String tokenParams = "client_id=" + SsoConst.GITHUB_CLIENT_ID
                + "&client_secret=" + SsoConst.GITHUB_CLIENT_SECRET
                + "&code=" + code
                + "&redirect_uri=" + encode(callbackUrl);

        String tokenResponse = httpPostJson(SsoConst.GITHUB_TOKEN_URL, tokenParams);
        JsonNode tokenJson = OBJECT_MAPPER.readTree(tokenResponse);
        String accessToken = tokenJson.get("access_token").asText();

        // 步骤 2：用 access_token 获取用户信息
        String userInfoResponse = httpGet(SsoConst.GITHUB_USER_API,
                "Authorization", "token " + accessToken);
        JsonNode userJson = OBJECT_MAPPER.readTree(userInfoResponse);

        // 步骤 3：提取关键信息
        Map<String, Object> result = new HashMap<>();
        result.put("openId", userJson.get("id").asText());
        result.put("nickname", userJson.get("login").asText());
        result.put("avatar", userJson.has("avatar_url") ? userJson.get("avatar_url").asText() : "");
        result.put("email", userJson.has("email") ? userJson.get("email").asText() : "");
        result.put("platform", "github");
        result.put("rawUser", userJson);
        return result;
    }

    // ========================= 微信 OAuth =========================

    /**
     * 微信开放平台 OAuth 认证
     *
     * 流程：
     * 1. GET token 接口 → 获取 access_token + openid
     * 2. GET userinfo 接口 → 获取用户详细信息
     *
     * 微信 token 接口返回示例：
     * {
     *   "access_token": "xxx",
     *   "expires_in": 7200,
     *   "refresh_token": "xxx",
     *   "openid": "xxx",
     *   "scope": "snsapi_login",
     *   "unionid": "xxx"
     * }
     *
     * 微信 userinfo 接口返回示例：
     * {
     *   "openid": "xxx",
     *   "nickname": "用户昵称",
     *   "sex": 1,
     *   "headimgurl": "http://...",
     *   "unionid": "xxx"
     * }
     */
    private Map<String, Object> authenticateWechat(String code) throws IOException {
        // 步骤 1：用 code 换取 access_token + openid
        String tokenUrl = SsoConst.WECHAT_TOKEN_URL
                + "?appid=" + SsoConst.WECHAT_APP_ID
                + "&secret=" + SsoConst.WECHAT_APP_SECRET
                + "&code=" + code
                + "&grant_type=authorization_code";

        String tokenResponse = httpGet(tokenUrl);
        JsonNode tokenJson = OBJECT_MAPPER.readTree(tokenResponse);

        // 检查微信返回的错误
        if (tokenJson.has("errcode")) {
            throw new RuntimeException("微信 OAuth 错误: " + tokenJson.get("errmsg").asText());
        }

        String accessToken = tokenJson.get("access_token").asText();
        String openId = tokenJson.get("openid").asText();

        // 步骤 2：用 access_token + openid 获取用户信息
        String userInfoUrl = SsoConst.WECHAT_USER_INFO_URL
                + "?access_token=" + accessToken
                + "&openid=" + openId;

        String userInfoResponse = httpGet(userInfoUrl);
        JsonNode userJson = OBJECT_MAPPER.readTree(userInfoResponse);

        // 步骤 3：提取信息
        Map<String, Object> result = new HashMap<>();
        result.put("openId", openId);
        result.put("nickname", userJson.has("nickname") ? userJson.get("nickname").asText() : "");
        result.put("avatar", userJson.has("headimgurl") ? userJson.get("headimgurl").asText() : "");
        result.put("unionId", userJson.has("unionid") ? userJson.get("unionid").asText() : "");
        result.put("platform", "wechat");
        return result;
    }

    // ========================= QQ OAuth =========================

    /**
     * QQ 互联 OAuth 认证
     *
     * QQ 的 OAuth2 流程比较特殊，需要三步：
     * 1. GET token 接口 → 获取 access_token（响应是 URL 参数格式！）
     * 2. GET openid 接口 → 获取用户的 QQ openid（响应是 JSONP 格式！）
     * 3. GET userinfo 接口 → 获取用户详细信息
     *
     * 步骤 1 的响应（非标准！）：
     *   access_token=FE04C...&expires_in=7776000&refresh_token=88E4C...
     *
     * 步骤 2 的响应（JSONP 格式！）：
     *   callback( {"client_id":"xxx","openid":"xxx"} );
     *
     * 步骤 3 的响应：
     *   { "ret":0, "msg":"", "nickname":"xxx", "figureurl":"http://..." }
     */
    private Map<String, Object> authenticateQQ(String code) throws IOException {
        String callbackUrl = SsoConst.SERVER_URL + "/sso/third-auth/qq/callback";

        // 步骤 1：用 code 换取 access_token（响应为 URL 参数格式！）
        String tokenUrl = SsoConst.QQ_TOKEN_URL
                + "?grant_type=authorization_code"
                + "&client_id=" + SsoConst.QQ_APP_ID
                + "&client_secret=" + SsoConst.QQ_APP_SECRET
                + "&code=" + code
                + "&redirect_uri=" + encode(callbackUrl);

        String tokenResponse = httpGet(tokenUrl);
        // QQ token 响应不是 JSON，而是 URL query string 格式
        // 示例：access_token=xxx&expires_in=7776000&refresh_token=xxx
        Map<String, String> tokenParams = parseQueryString(tokenResponse);
        String accessToken = tokenParams.get("access_token");

        if (accessToken == null) {
            throw new RuntimeException("QQ OAuth 获取 access_token 失败: " + tokenResponse);
        }

        // 步骤 2：用 access_token 获取 openid（响应为 JSONP 格式！）
        String openidUrl = SsoConst.QQ_OPENID_URL
                + "?access_token=" + accessToken;

        String openidResponse = httpGet(openidUrl);
        // QQ openid 响应格式：callback( {"client_id":"xxx","openid":"xxx"} );
        // 需要从 JSONP 中提取 JSON
        String jsonStr = openidResponse;
        if (jsonStr.startsWith("callback(")) {
            jsonStr = jsonStr.substring("callback(".length(), jsonStr.length() - 3);
        }
        JsonNode openidJson = OBJECT_MAPPER.readTree(jsonStr);
        String openId = openidJson.get("openid").asText();

        // 步骤 3：用 access_token + openid 获取用户信息
        String userInfoUrl = SsoConst.QQ_USER_INFO_URL
                + "?access_token=" + accessToken
                + "&oauth_consumer_key=" + SsoConst.QQ_APP_ID
                + "&openid=" + openId;

        String userInfoResponse = httpGet(userInfoUrl);
        JsonNode userJson = OBJECT_MAPPER.readTree(userInfoResponse);

        // 步骤 4：提取信息
        Map<String, Object> result = new HashMap<>();
        result.put("openId", openId);
        result.put("nickname", userJson.has("nickname") ? userJson.get("nickname").asText() : "");
        result.put("avatar", userJson.has("figureurl_qq_2") ?
                userJson.get("figureurl_qq_2").asText() :  // 100*100 头像
                userJson.has("figureurl") ? userJson.get("figureurl").asText() : "");
        result.put("gender", userJson.has("gender") ? userJson.get("gender").asText() : "");
        result.put("platform", "qq");
        return result;
    }

    // ========================= HTTP 工具方法 =========================

    /**
     * HTTP GET 请求
     */
    private String httpGet(String urlString) throws IOException {
        return httpGet(urlString, null, null);
    }

    /**
     * HTTP GET 请求（可附带自定义 Header）
     */
    private String httpGet(String urlString, String headerName, String headerValue) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        if (headerName != null) {
            conn.setRequestProperty(headerName, headerValue);
        }
        return readResponse(conn);
    }

    /**
     * HTTP POST 请求（发送 JSON 或 URL-encoded 格式的请求体）
     *
     * GitHub token 接口要求 Accept: application/json
     */
    private String httpPostJson(String urlString, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Accept", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes("UTF-8"));
        }

        return readResponse(conn);
    }

    /**
     * 读取 HTTP 响应
     */
    private String readResponse(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        InputStream inputStream = (responseCode >= 200 && responseCode < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        if (inputStream == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, len, "UTF-8"));
        }
        inputStream.close();
        conn.disconnect();
        return sb.toString();
    }

    /**
     * 解析 URL Query String 为 Map
     *
     * QQ Token 接口返回的不是 JSON，而是 URL 参数格式：
     *   access_token=xxx&expires_in=7776000&refresh_token=xxx
     */
    private Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new HashMap<>();
        if (queryString == null || queryString.isEmpty()) {
            return params;
        }
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }

    /**
     * URL 编码
     */
    private String encode(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, "UTF-8");
    }
}
