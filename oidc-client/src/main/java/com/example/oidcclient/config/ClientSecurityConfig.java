package com.example.oidcclient.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OIDC 客户端安全配置
 *
 * 核心职责：
 * 1. 注册 OAuth2 客户端信息（与认证中心注册信息对应）
 * 2. oauth2Login()：对所有需要认证的请求触发 OIDC 授权码流程
 * 3. 配置登录成功后的跳转行为
 *
 * 认证流程（客户端视角）：
 *   用户访问 / → 未登录 → 302 到认证中心 /oauth2/authorize
 *   → 用户在认证中心登录 → 302 带回 code
 *   → /login/oauth2/code/app-client?code=XXX
 *   → Spring Security 后端用 code 换 access_token + id_token
 *   → 用户登录完成 → 302 到原始访问地址
 */
@EnableWebSecurity
@Configuration
public class ClientSecurityConfig {

    /**
     * OAuth2 客户端注册信息
     *
     * 此处配置的 client-id、client-secret、redirect-uri 必须与
     * 认证中心（authorizationServer）的 RegisteredClientRepository 中
     * 注册的客户端信息完全一致，否则认证中心会拒绝授权。
     *
     * 认证中心已注册的客户端：
     *   client-id: app-client-1
     *   client-secret: secret1
     *   redirect-uri: http://localhost:8081/login/oauth2/code/app-client
     */
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration client = ClientRegistration
                .withRegistrationId("app-client")
                .clientId("app-client-1")             // 与认证中心注册的 clientId 一致
                .clientSecret("secret1")               // 与认证中心注册的 clientSecret 一致
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                // 授权码回调地址（必须精确匹配认证中心 redirectUri 配置）
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                // 认证中心的 OAuth2/OIDC 端点（手动指定而非 issuer-uri 自动发现）
                .authorizationUri("http://localhost:8080/oauth2/authorize")
                .tokenUri("http://localhost:8080/oauth2/token")
                .userInfoUri("http://localhost:8080/userinfo")
                .jwkSetUri("http://localhost:8080/oauth2/jwks")
                .userNameAttributeName(IdTokenClaimNames.SUB)  // OIDC 用户标识字段
                .clientName("OIDC SSO Client")
                .build();

        return new InMemoryClientRegistrationRepository(client);
    }

    /**
     * 客户端安全过滤链
     *
     * 关键配置：
     * - .anyRequest().authenticated()：所有请求需要认证
     * - .oauth2Login()：自动触发 OIDC 授权码流程
     *
     * oauth2Login() 背后做了什么：
     * 1. 创建 /oauth2/authorization/{registrationId} 入口
     * 2. 创建 /login/oauth2/code/{registrationId} 回调端点
     * 3. 未登录时自动重定向到认证中心
     * 4. 收到 code 后自动调用 /oauth2/token 换取 token
     */
    @Bean
    public SecurityFilterChain clientSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeRequests(authorizeRequests ->
                authorizeRequests
                    .antMatchers("/error", "/static/**", "/favicon.ico").permitAll()
                    .anyRequest().authenticated()          // 所有请求需要认证
            )
            // 核心：启用 OAuth2 登录，对接 OIDC 认证中心
            .oauth2Login(oauth2Login ->
                oauth2Login
                    .defaultSuccessUrl("/", true)          // 登录成功后跳转到首页
            )
            // 本地注销（仅清理客户端 Session，不涉及认证中心）
            .logout(logout ->
                logout
                    .logoutSuccessUrl("/")
                    .permitAll()
            );

        return http.build();
    }
}
