package com.example.authorizationserver.config;

import com.example.authorizationserver.jose.Jwks;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWK 密钥配置
 *
 * 将生成的 RSA 密钥对注册为 JWKSource Bean。
 * Spring Authorization Server 会自动使用此 JWKSource 对 JWT 签名。
 *
 * 外部资源服务器通过 /oauth2/jwks 端点获取公钥来验证 token 签名。
 */
@Configuration
public class JwkConfig {

    /**
     * 生成 RSA 密钥对并注册为 JWKSource。
     *
     * 使用 Lambda 实现 JWKSource 接口（推荐方式，兼容各版本 nimbus-jose-jwt）：
     * - jwkSelector.select(jwkSet)：根据 JWS 头中的 keyID 等参数筛选匹配的 key
     * - 生产环境应从安全的密钥库加载已有密钥，而非每次启动生成新密钥
     *
     * 注意：开发环境每次重启都会生成新密钥，已签发的 JWT 将无法验证。
     *       生产环境建议从安全密钥管理服务加载持久化的密钥对。
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = Jwks.generateRsa();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }
}
