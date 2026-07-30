package com.example.authorizationserver.jose;

import com.nimbusds.jose.jwk.RSAKey;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * JWK（JSON Web Key）工具类
 *
 * 生成 RSA 密钥对，用于对 JWT（id_token、access_token）进行签名。
 * Spring Authorization Server 通过 /oauth2/jwks 端点对外暴露公钥，
 * 资源服务器可以获取公钥来验证 JWT 的真伪。
 *
 * 密钥算法：RSA 2048 位（生产环境建议 4096 位）
 * 签名算法：RS256（OIDC 标准默认）
 */
public final class Jwks {

    private Jwks() {
    }

    /**
     * 生成 RSA 密钥对，包装为 Nimbus 的 RSAKey 对象。
     * 每次调用生成一个新的密钥对，并附带随机 keyID。
     */
    public static RSAKey generateRsa() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(UUID.randomUUID().toString())   // 每次生成的 keyID 唯一
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("无法生成 RSA 密钥对", e);
        }
    }
}
