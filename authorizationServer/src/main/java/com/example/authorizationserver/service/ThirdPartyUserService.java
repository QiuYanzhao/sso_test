package com.example.authorizationserver.service;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 第三方 OAuth2 登录——用户信息服务
 *
 * 当用户通过 GitHub / 微信 / QQ 等第三方账号登录后，
 * Spring Security 会调用此 Service 处理第三方返回的用户数据。
 *
 * 核心职责：
 * 1. 获取第三方返回的用户信息（昵称、头像、邮箱等）
 * 2. 查询本地数据库，建立"第三方账号 → 本地用户"的映射
 * 3. 如果本地没有对应账号，可选择自动注册或提示绑定已有账号
 * 4. 最终返回一个 Spring Security OAuth2User，完成认证
 *
 * 本示例为简化版：直接使用第三方返回的用户信息作为认证身份。
 * 生产环境应实现：openid → 本地 userId 的映射关系存储（数据库或缓存）。
 */
@Component
public class ThirdPartyUserService extends DefaultOAuth2UserService {

    /**
     * 加载第三方用户信息
     *
     * 调用链：
     * 1. super.loadUser() → 通过 OAuth2 协议获取用户信息
     * 2. 解析用户属性（各平台返回的字段不同）
     * 3. 映射为本地用户身份 → 授予相应权限
     *
     * @param userRequest 包含 client registration 信息（分辨是哪个平台）
     * @return 包装后的 OAuth2User，Spring Security 自动完成认证
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 调用父类方法，从 OAuth2 Provider 获取用户信息
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // 识别来源平台
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());

        System.out.println("=== 第三方登录，平台: " + registrationId + " ===");
        System.out.println("=== 用户属性: " + attributes + " ===");

        // 根据平台提取用户标识和基本信息
        String nameAttributeKey;
        String platformUserId;  // 该用户在第三方平台上的唯一标识

        switch (registrationId) {
            case "github":
                // GitHub 的标准 OAuth2 用户信息：
                //   id       → 用户数字ID
                //   login    → GitHub 用户名
                //   email    → 邮箱（需 scope: user:email）
                //   avatar_url → 头像URL
                platformUserId = attributes.get("id").toString();
                nameAttributeKey = "login";

                // ===== 实际项目中的处理逻辑 =====
                // 1. 查询本地数据库：SELECT user_id FROM oauth_user WHERE platform='github' AND openid=?
                // 2. 如果已有绑定用户 → 加载该用户权限
                // 3. 如果没有绑定 → 自动注册新用户或引导绑定
                // String localUserId = userService.findOrCreateByOAuth(registrationId, platformUserId, attributes);
                break;

            case "wechat":
                // 微信开放平台的用户信息（已通过 userService 处理为标准格式）：
                //   openid     → 微信用户唯一标识
                //   nickname   → 微信昵称
                //   headimgurl → 头像URL
                platformUserId = (String) attributes.get("openid");
                nameAttributeKey = "openid";
                break;

            case "qq":
                // QQ 互联的用户信息：
                //   openid     → QQ 用户唯一标识
                //   nickname   → QQ 昵称
                //   figureurl  → 头像URL
                platformUserId = (String) attributes.get("openid");
                nameAttributeKey = "openid";
                break;

            default:
                platformUserId = "unknown";
                nameAttributeKey = oAuth2User.getName();
                break;
        }

        // 返回认证用户：这里的 nameAttributeKey 决定了 getName() 返回哪个字段
        return new DefaultOAuth2User(
                oAuth2User.getAuthorities(),
                attributes,
                nameAttributeKey
        );
    }
}
