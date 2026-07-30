package com.example.authorizationserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.ClientSettings;
import org.springframework.security.oauth2.server.authorization.config.TokenSettings;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.UUID;

/**
 * Spring Authorization Server 核心配置 —— OAuth2 / OIDC 认证服务器
 *
 * 这是整个 SSO 方案最关键的一环，负责：
 * 1. 定义哪些客户端应用可以接入 SSO（OAuth2 Client 注册）
 * 2. 配置 OAuth2/OIDC 协议端点（/oauth2/authorize、/oauth2/token 等）
 * 3. 启用 OIDC 标准（OpenID Connect），使认证中心具备 SSO 能力
 * 4. 配置 Token 策略（有效期、格式等）
 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationServerConfig {

    /**
     * 认证服务器协议端点配置
     *
     * 定义一个专门的 SecurityFilterChain，应用于所有 OAuth2 协议端点：
     * - GET  /oauth2/authorize     授权端点——用户在认证中心登录后，授权客户端
     * - POST /oauth2/token          令牌端点——客户端用授权码换取 access_token/id_token
     * - GET  /oauth2/jwks           JWK 端点——暴露公钥供资源服务器验签
     * - POST /oauth2/introspect     令牌内省端点——校验 access_token 有效性
     * - POST /oauth2/revoke         令牌吊销端点——使指定 token 失效
     * - GET  /userinfo              OIDC 用户信息端点——返回当前用户的 claims
     *
     * 优先级 HIGHEST_PRECEDENCE：确保 OAuth2 端点的安全策略优先于 DefaultSecurityConfig。
     * 未认证的请求会被重定向到 /login 登录页面。
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        // 创建 OAuth2 认证服务器配置器
        OAuth2AuthorizationServerConfigurer<HttpSecurity> authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer<>();

        // 获取 OAuth2 协议端点的匹配器
        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();

        http
            // 此安全链仅处理 OAuth2 协议端点
            .requestMatcher(endpointsMatcher)
            // 协议端点需要认证才能访问（确保用户已登录）
            .authorizeRequests(authorizeRequests ->
                authorizeRequests.anyRequest().authenticated()
            )
            // 未认证时重定向到登录页（实现 SSO 的关键：用户在此完成统一认证）
            .exceptionHandling(exceptions ->
                exceptions.authenticationEntryPoint(
                    new LoginUrlAuthenticationEntryPoint("/login"))
            )
            // OAuth2 端点自带 CSRF 保护，此处关闭 Spring Security 层面的 CSRF
            .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
            // 应用 OAuth2 认证服务器配置
            .apply(authorizationServerConfigurer);

        // 启用 OpenID Connect 1.0（SSO 的核心协议）
        authorizationServerConfigurer
            .oidc(Customizer.withDefaults());

        return http.build();
    }

    /**
     * 注册 OAuth2 客户端 —— SSO 接入方
     *
     * 每个需要接入 SSO 的业务应用，都要在此注册为一个 OAuth2 Client。
     * 客户端应用使用 OAuth2 授权码流程（AUTHORIZATION_CODE）获取 token。
     *
     * 关键参数说明：
     * - clientId              客户端的唯一标识（类似用户名）
     * - clientSecret          客户端密钥（类似密码，生产环境需加密存储）
     * - redirectUri           授权码回调地址，必须与客户端配置完全一致
     * - scopes                openid 是 OIDC 必需项，profile 获取用户信息
     * - requireAuthorizationConsent  false=跳过授权同意页，实现"无感知 SSO"
     *
     * 添加新客户端：在此方法中增加一个 RegisteredClient，
     * 并在对应应用的 application.yml 中配置同样的 client-id/secret。
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        // 示例客户端 1：Web 应用
        RegisteredClient clientApp1 = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("app-client-1")
                .clientSecret("{noop}secret1")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                // 授权码模式（最安全的 OAuth2 流程，适合有后端的客户端）
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                // 支持刷新令牌，避免频繁重新登录
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                // 客户端凭证模式（服务间调用，不涉及用户）
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                // 授权码回调地址（客户端接收 code 的地址）
                .redirectUri("http://localhost:8081/login/oauth2/code/app-client")
                .redirectUri("http://localhost:8081/callback")
                // OIDC scope：必须包含 openid
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope("read")
                .scope("write")
                // 客户端设置：关闭授权同意页 = 用户登录认证中心后直接授权
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .build())
                // Token 设置：access_token 2 小时，refresh_token 30 天
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(java.time.Duration.ofHours(2))
                        .refreshTokenTimeToLive(java.time.Duration.ofDays(30))
                        .build())
                .build();

        // 示例客户端 2：演示多个应用接入同一个 SSO
        RegisteredClient clientApp2 = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("app-client-2")
                .clientSecret("{noop}secret2")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8082/login/oauth2/code/app-client")
                .redirectUri("http://localhost:8082/callback")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(java.time.Duration.ofHours(2))
                        .refreshTokenTimeToLive(java.time.Duration.ofDays(30))
                        .build())
                .build();

        return new InMemoryRegisteredClientRepository(clientApp1, clientApp2);
    }
}
