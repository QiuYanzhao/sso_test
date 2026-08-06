package com.example.oidcclient.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ClientSecurityConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration client = ClientRegistration
                .withRegistrationId("app-client")
                .clientId("app-client-1")
                .clientSecret("secret1")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://127.0.0.1:8081/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("http://localhost:8080/oauth2/authorize")
                .tokenUri("http://localhost:8080/oauth2/token")
                // 不配置 userInfoUri：SAS 0.2.3 的 /userinfo 端点有缺陷（500），
                // 客户端改用 ID Token 的 claims 即可完成登录
                .jwkSetUri("http://localhost:8080/oauth2/jwks")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("OIDC SSO Client")
                .build();
        return new InMemoryClientRegistrationRepository(client);
    }

    @Bean
    public SecurityFilterChain clientSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeRequests(authorizeRequests ->
                authorizeRequests
                    .antMatchers("/oauth2/authorization/**").permitAll()
                    .antMatchers("/login/oauth2/code/**").permitAll()
                    .antMatchers("/logout").permitAll()
                    .antMatchers("/error").permitAll()
                    .antMatchers("/static/**").permitAll()
                    .antMatchers("/favicon.ico").permitAll()
                    .anyRequest().authenticated()
            )
            .oauth2Login(oauth2Login ->
                oauth2Login.defaultSuccessUrl("/", true)
            )
            .logout(logout ->
                logout
                    // 客户端本地退出后，再跳到认证中心 /logout，清除 SSO 全局会话
                    .logoutSuccessUrl("http://localhost:8080/logout")
                    .permitAll(false)
            );

        return http.build();
    }
}
