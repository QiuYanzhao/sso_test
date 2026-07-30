package com.example.authorizationserver.config;

import org.springframework.context.annotation.Configuration;

/**
 * 第三方 OAuth2 登录扩展配置
 *
 * 说明：
 * =====
 * Spring Security 的 OAuth2 Client 模块已内置对标准 OAuth2 Provider 的支持。
 *
 * GitHub 登录：
 *   完全符合标准 OAuth2，Spring Security 内置了 GitHub provider 配置。
 *   只需在 application.yml 中配置 client-id 和 client-secret 即可直接使用。
 *   流程：/oauth2/authorization/github → GitHub 授权页 → /login/oauth2/code/github 回调
 *
 * 微信登录（非标准 OAuth2）：
 *   微信开放平台的 OAuth2 实现与标准有差异：
 *   1. 授权页面 URL 以 #wechat_redirect 结尾（Spring Security 会自动处理）
 *   2. Token 响应中 access_token 字段符合标准，可直接解析
 *   3. 用户信息接口格式：{ openid, nickname, sex, headimgurl, ... }
 *   4. 用户信息请求需携带 access_token 和 openid 参数
 *   → Spring Security 的 DefaultOAuth2UserService 基本能处理，但返回格式需额外适配
 *
 * QQ 登录（非标准 OAuth2）：
 *   QQ 互联的 OAuth2 实现与标准的主要差异：
 *   1. Token 响应格式为 URL query string（非标准 JSON），需自定义解析
 *   2. 需先获取 openid（/oauth2.0/me），再用 openid 获取用户信息
 *   3. 用户信息接口格式：{ ret, msg, nickname, figureurl, ... }
 *   → 需要自定义 OAuth2UserService 和 AccessTokenResponseConverter
 *
 * 由于微信和 QQ 均非完全标准 OAuth2，Spring Security 的通用 OAuth2 Client
 * 可能无法直接适配。在生产环境中，通常采用以下方式之一：
 *
 * 方案 A：使用 JustAuth（国人开发的集成方案）
 *   引入 justauth-spring-boot-starter，一行配置即可接入微信/QQ/微博等数十个平台。
 *   <dependency>
 *       <groupId>com.xkcoding.justauth</groupId>
 *       <artifactId>justauth-spring-boot-starter</artifactId>
 *       <version>1.4.0</version>
 *   </dependency>
 *
 * 方案 B：手动实现（本模块采用）
 *   针对每个平台编写自定义的：
 *   - OAuth2UserService（用户信息获取与解析）
 *   - OAuth2AccessTokenResponseConverter（Token 响应解析，QQ 特别需要）
 *
 * 当前方案：
 * GitHub → 开箱即用，application.yml 配置后直接可用
 * 微信   → Spring Security 基本可处理，ThirdPartyUserService 中做字段映射
 * QQ     → 需要额外自定义，下方代码以注释形式展示关键实现思路
 */
@Configuration
public class OAuth2ThirdPartyConfig {

    /*
     * ============================================================================
     * 微信 / QQ 完整自定义接入示例（供学习参考）
     * ============================================================================
     *
     * 当 Spring Security 内置的 OAuth2 Client 无法直接适配微信/QQ 时，
     * 需要自定义以下几个组件：
     */

    /*
    // ===== 示例 1：自定义微信 OAuth2UserService =====
    // 如果微信的用户信息返回格式与标准 OAuth2 不兼容，
    // 可以创建专用的 OAuth2UserService：

    public class WechatOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

        @Override
        public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
            // 1. 获取 access_token
            String accessToken = userRequest.getAccessToken().getTokenValue();

            // 2. 根据 openid 获取用户信息
            //    微信的 user-info-uri 需要附带 access_token 和 openid 参数
            //    openid 来自 token 响应的附加参数
            Map<String, Object> additionalParams = userRequest.getAdditionalParameters();
            String openid = (String) additionalParams.get("openid");

            String userInfoUri = userRequest.getClientRegistration()
                    .getProviderDetails().getUserInfoEndpoint().getUri();

            // 3. 调用微信 /sns/userinfo 接口
            RestTemplate restTemplate = new RestTemplate();
            String url = userInfoUri + "?access_token=" + accessToken + "&openid=" + openid;
            Map<String, Object> userInfo = restTemplate.getForObject(url, Map.class);

            // 4. 构造 Spring Security OAuth2User
            Set<GrantedAuthority> authorities = Collections.singleton(
                    new SimpleGrantedAuthority("ROLE_USER"));
            return new DefaultOAuth2User(authorities, userInfo, "openid");
        }
    }
    */

    /*
    // ===== 示例 2：自定义 QQ Token 响应解析 =====
    // QQ 的 token 端点返回的是 URL 参数格式（非标准 JSON），
    // 需要自定义 Converter：

    public class QqAccessTokenResponseConverter
            implements Converter<Map<String, String>, OAuth2AccessTokenResponse> {

        @Override
        public OAuth2AccessTokenResponse convert(Map<String, String> params) {
            // QQ 返回格式示例：
            // access_token=FE04C...&expires_in=7776000&refresh_token=88E4C...
            String accessToken = params.get("access_token");
            String expiresIn = params.get("expires_in");
            String refreshToken = params.get("refresh_token");

            return OAuth2AccessTokenResponse.withToken(accessToken)
                    .tokenType(OAuth2AccessToken.TokenType.BEARER)
                    .expiresIn(Long.parseLong(expiresIn))
                    .refreshToken(refreshToken)
                    .build();
        }
    }
    */

    /*
    // ===== 示例 3：使用 JustAuth 集成（推荐的生产方案）=====
    //
    // JustAuth 封装了微信、QQ、微博、百度、Gitee 等数十个平台的 OAuth 细节差异，
    // 提供统一的调用接口：

    // pom.xml 依赖：
    // <dependency>
    //     <groupId>me.zhyd.oauth</groupId>
    //     <artifactId>JustAuth</artifactId>
    //     <version>1.16.5</version>
    // </dependency>

    // 微信登录：
    // AuthRequest authRequest = new AuthWeChatRequest(AuthConfig.builder()
    //         .clientId("your-app-id")
    //         .clientSecret("your-app-secret")
    //         .redirectUri("http://localhost:8080/login/oauth2/code/wechat")
    //         .build());
    // String authorizeUrl = authRequest.authorize("state");  // 生成授权URL
    // AuthResponse response = authRequest.login(callback);    // 处理回调

    // QQ 登录：
    // AuthRequest authRequest = new AuthQqRequest(AuthConfig.builder()
    //         .clientId("your-app-id")
    //         .clientSecret("your-app-secret")
    //         .redirectUri("http://localhost:8080/login/oauth2/code/qq")
    //         .build());

    // 然后只需要在 Controller 中手动处理 OAuth 回调，
    // 调用 JustAuth 的 login() 方法获取用户信息，
    // 再调用 Spring Security 的 SecurityContextHolder 完成认证。
    */
}
