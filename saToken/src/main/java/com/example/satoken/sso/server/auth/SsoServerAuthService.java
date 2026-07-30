package com.example.satoken.sso.server.auth;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSO Server —— 本地用户认证服务
 *
 * 处理传统的用户名/密码登录逻辑。
 * 演示数据使用内存 Map 存储，实际项目应替换为数据库查询。
 *
 * 与第三方登录的区别：
 * - 账号密码登录：直接在此 Service 中校验
 * - 第三方登录：由 ThirdPartyAuthService 处理 OAuth 流程
 * - 两种方式最终都调用 StpUtil.login(userId) 完成 SaToken 登录
 */
@Service
public class SsoServerAuthService {

    /**
     * 模拟用户数据库
     * key：用户名，value：密码
     *
     * 测试账号（账号名即 userId）：
     * - admin / 123456
     * - user1 / 123456
     * - user2 / 123456
     */
    private static final Map<String, String> USER_DB = new ConcurrentHashMap<>();

    static {
        USER_DB.put("admin", "123456");
        USER_DB.put("user1", "123456");
        USER_DB.put("user2", "123456");
    }

    /**
     * 校验用户名和密码
     *
     * SaToken SSO 默认的 /sso/doLogin 会调用外部定义的认证逻辑。
     * 可以通过实现 SaSsoServerTemplate 的 getStpLogic 方法来对接此服务。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return userId（校验成功）或 null（校验失败）
     */
    public String validate(String username, String password) {
        String storedPwd = USER_DB.get(username);
        if (storedPwd != null && storedPwd.equals(password)) {
            return username;
        }
        return null;
    }

    /**
     * 根据 userId 查找用户信息
     *
     * @param userId 用户ID（即用户名）
     * @return 用户信息 Map，包含 nickname、avatar 等字段
     */
    public Map<String, Object> getUserInfo(String userId) {
        return Collections.singletonMap("userId", userId);
    }
}
