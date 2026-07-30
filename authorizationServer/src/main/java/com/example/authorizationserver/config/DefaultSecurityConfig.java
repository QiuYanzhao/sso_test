package com.example.authorizationserver.config;

import com.example.authorizationserver.service.ThirdPartyUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 全局安全配置
 *
 * 职责：
 * 1. 配置表单登录（为用户提供登录页面）
 * 2. 配置本地用户（用户名/密码认证）
 * 3. 放行静态资源和公开端点
 * 4. 配置 OAuth2 第三方登录入口
 * 5. CSRF 策略配置
 *
 * 本配置的优先级低于 AuthorizationServerConfig，
 * 负责处理非 OAuth2 端点的安全（登录页、静态资源等）。
 */
@EnableWebSecurity
@Configuration(proxyBeanMethods = false)
public class DefaultSecurityConfig {

    private final ThirdPartyUserService thirdPartyUserService;

    public DefaultSecurityConfig(ThirdPartyUserService thirdPartyUserService) {
        this.thirdPartyUserService = thirdPartyUserService;
    }

    /**
     * 密码编码器：使用 BCrypt 加密密码
     *
     * 所有密码在存储前必须加密，Spring Security 会自动匹配。
     * {noop} 前缀表示不加密（仅开发环境使用），生产环境应始终使用 BCrypt。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 本地用户存储（内存模式，仅供演示）
     *
     * 预置两个测试账号：
     * - admin / 123456（拥有 USER 和 ADMIN 角色）
     * - user  / 123456（仅 USER 角色）
     *
     * 生产环境应替换为数据库 DAO 实现，如 JdbcUserDetailsManager。
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("123456"))
                .roles("USER", "ADMIN")
                .build();
        UserDetails user = User.builder()
                .username("user")
                .password(passwordEncoder.encode("123456"))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(admin, user);
    }

    /**
     * 全局安全过滤链（最低优先级）
     *
     * 处理非 OAuth2 端点的安全逻辑：
     * - /login                   登录页面（允许匿名访问）
     * - /                         首页
     * - /static/**                静态资源
     * - /oauth2/authorization/*  第三方 OAuth2 登录入口
     * - /favicon.ico              网站图标
     *
     * 所有其他请求需要认证。
     * 启用表单登录，登录页面使用自定义模板 login.html。
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeRequests(authorizeRequests ->
                authorizeRequests
                    .antMatchers("/login", "/static/**", "/favicon.ico").permitAll()
                    .antMatchers("/oauth2/authorization/**").permitAll()  // 第三方登录入口
                    .anyRequest().authenticated()
            )
            // 表单登录配置
            .formLogin(formLogin ->
                formLogin
                    .loginPage("/login")              // 自定义登录页
                    .loginProcessingUrl("/login")      // 登录表单提交地址
                    .permitAll()
            )
            // OAuth2 第三方登录（由 spring-boot-starter-oauth2-client 提供）
            .oauth2Login(oauth2Login ->
                oauth2Login
                    .loginPage("/login")              // 第三方登录也使用同一登录页
                    .userInfoEndpoint()
                        // 使用自定义 UserService 处理第三方返回的用户信息
                        .userService(thirdPartyUserService)
            )
            // 注销配置
            .logout(logout ->
                logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/login?logout")
                    .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringAntMatchers("/oauth2/**")    // OAuth2 端点已内置 CSRF 保护，无需额外校验
            );

        return http.build();
    }
}
