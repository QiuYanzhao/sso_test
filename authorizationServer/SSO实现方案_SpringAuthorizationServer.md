# Spring Authorization Server (OIDC) SSO 单点登录实现方案

## 一、方案概述

本方案基于 **Spring Authorization Server 0.2.3** 构建符合 **OAuth 2.1 / OIDC 1.0** 标准的单点登录（SSO）认证中心。采用经典的 **授权码模式（Authorization Code Flow + PKCE）** 作为核心认证流程，实现一个认证中心对多个客户端应用的统一认证。

### 核心组件

| 角色 | 说明 | 对应模块 |
|------|------|----------|
| **Authorization Server** | 认证授权中心，负责用户登录、颁发 Token | 本项目：`authorizationServer` |
| **Client Application** | 接入 SSO 的业务应用 | 独立客户端（可由本项目扩展） |
| **Resource Server** | 受保护的资源服务，验证 Token | 集成在客户端中 |

---

## 二、技术选型

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 2.6.13 | 基础框架 |
| Spring Security | 5.6.x | 安全框架 |
| Spring Authorization Server | 0.2.3 | OAuth2/OIDC 认证授权服务器 |
| Nimbus JOSE + JWT | 9.37.2 | JWT 生成与解析 |
| Spring Security OAuth2 Client | 5.6.x | 客户端集成 OAuth2 |
| Thymeleaf | - | 登录页面渲染 |

---

## 三、项目包结构设计

```
com.example.authorizationserver
├── AuthorizationServerApplication.java          # 启动类
├── config/
│   ├── AuthorizationServerConfig.java           # 认证服务器核心配置
│   ├── SecurityConfig.java                      # Spring Security 全局安全配置
│   ├── DefaultSecurityConfig.java               # 默认安全链（登录表单等）
│   └── JwkConfig.java                           # JWK 密钥配置
├── jose/
│   └── Jwks.java                                # JWK 密钥生成工具类
├── user/
│   ├── UserController.java                      # 用户信息端点（/userinfo）
│   └── UserInfo.java                            # 用户信息实体
└── client/
    └── ClientConfig.java                        # OAuth2 客户端注册配置
```

---

## 四、认证授权流程图

### 4.1 OIDC 授权码流程（Authorization Code Flow）

```
┌──────────────┐           ┌───────────────────┐           ┌───────────────────┐
│   用户浏览器   │           │   客户端应用(Client) │           │  认证中心(Server)  │
└──────┬───────┘           └─────────┬─────────┘           └─────────┬─────────┘
       │                             │                               │
       │  ① 访问客户端受保护资源        │                               │
       │ ──────────────────────────>  │                               │
       │                             │                               │
       │  ② 重定向到认证中心登录页       │                               │
       │ <───────────────────────────│                               │
       │                             │                               │
       │  ③ GET /oauth2/authorize?   │                               │
       │    client_id=xxx            │                               │
       │    redirect_uri=xxx         │                               │
       │    response_type=code       │                               │
       │    scope=openid profile     │                               │
       │ ──────────────────────────────────────────────────────>    │
       │                             │                               │
       │  ④ 返回登录页面              │                               │
       │ <──────────────────────────────────────────────────────    │
       │                             │                               │
       │  ⑤ POST /login (用户名/密码) │                               │
       │ ──────────────────────────────────────────────────────>    │
       │                             │                               │
       │  ⑥ 认证成功后重定向          │                               │
       │    redirect_uri?code=xxx    │                               │
       │ <──────────────────────────────────────────────────────    │
       │                             │                               │
       │  ⑦ 携带 code 重定向回客户端   │                               │
       │ ──────────────────────────>  │                               │
       │                             │  ⑧ POST /oauth2/token        │
       │                             │     code + client_secret      │
       │                             │ ────────────────────────────> │
       │                             │                               │
       │                             │  ⑨ 返回 access_token          │
       │                             │    + id_token + refresh_token │
       │                             │ <──────────────────────────── │
       │                             │                               │
       │  ⑩ 首页（已登录）             │                               │
       │ <───────────────────────────│                               │
       │                             │                               │
```

### 4.2 单点登录（SSO）会话共享流程

```
  ┌─────────┐     ┌─────────┐     ┌─────────┐     ┌──────────────────────┐
  │  用户    │     │ 应用 A   │     │ 应用 B   │     │  认证中心(SSO Server) │
  └────┬────┘     └────┬────┘     └────┬────┘     └──────────┬───────────┘
       │              │              │                      │
       │ ①登录应用A     │              │                      │
       │─────────────>│              │                      │
       │              │ ②未登录，重定向 │                      │
       │<─────────────│              │                      │
       │              │              │                      │
       │ ③跳转认证中心   │              │                      │
       │──────────────────────────────────────────────────>│
       │              │              │   ④输入用户名/密码       │
       │<──────────────────────────────────────────────────│
       │              │              │   ⑤创建 Session        │
       │              │              │                      │
       │ ⑥重定向(code) │              │                      │
       │─────────────>│              │                      │
       │              │ ⑦用code换token │                      │
       │              │─────────────────────────────────────>│
       │              │<─────────────────────────────────────│
       │              │ ⑧登录成功      │                      │
       │              │              │                      │
       │ ⑨访问应用B     │              │                      │
       │─────────────────────────────>│                      │
       │              │              │ ⑩未登录，重定向          │
       │<─────────────────────────────│                      │
       │              │              │                      │
       │ ⑪跳转认证中心   │              │                      │
       │──────────────────────────────────────────────────>│
       │              │              │  ⑫ 已有 Session，      │
       │              │              │     直接授权           │
       │<──────────────────────────────────────────────────│
       │              │              │                      │
       │ ⑬重定向(code)  │              │                      │
       │─────────────────────────────>│                      │
       │              │              │ ⑭换token，登录成功      │
```

---

## 五、核心配置实现要点

### 5.1 AuthorizationServerConfig — 认证服务器核心

```java
@Configuration
public class AuthorizationServerConfig {

    /**
     * 核心：注册 OAuth2 客户端信息
     *
     * 每个需要接入 SSO 的应用在此注册，需约定：
     * - client_id：客户端唯一标识
     * - client_secret：客户端密钥
     * - redirect_uris：授权码回调地址（必须与客户端配置一致）
     * - scopes：openid（必须）、profile、email 等
     * - grant_types：authorization_code、refresh_token、client_credentials
     */
    // implementation: RegisteredClient 注入到 RegisteredClientRepository

    /**
     * 授权服务器核心配置链
     *
     * 应用于 OAuth2 相关端点：
     * - /oauth2/authorize   授权端点
     * - /oauth2/token        令牌端点
     * - /oauth2/jwks         JWK 端点
     * - /oauth2/introspect   令牌内省端点
     * - /oauth2/revoke       令牌吊销端点
     * - /userinfo            OIDC 用户信息端点
     *
     * 关键配置项：
     * 1. OidcConfigurer：启用 OIDC，配置 idToken 签名算法（RS256）
     * 2. 授权端点需认证 → 用 formLogin
     * 3. 同意页可选（单点登录场景通常跳过 consent）
     * 4. 客户端认证方式：client_secret_basic / client_secret_post
     */
    // implementation: SecurityFilterChain
}
```

### 5.2 客户端注册配置

```java
// 核心：通过 Bean 方式注册接入 SSO 的客户端应用
@Bean
public RegisteredClientRepository registeredClientRepository() {
    RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("app-client")                              // 客户端 ID
            .clientSecret("{noop}secret")                        // 客户端密钥
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .redirectUri("http://127.0.0.1:8081/login/oauth2/code/app-client") // 回调地址
            .scope(OidcScopes.OPENID)                            // OIDC 必需
            .scope(OidcScopes.PROFILE)                           // 获取用户基本信息
            .scope("read")
            .clientSettings(ClientSettings.builder()
                    .requireAuthorizationConsent(false)          // 跳过同意页→实现无缝 SSO
                    .build())
            .tokenSettings(TokenSettings.builder()
                    .accessTokenTimeToLive(Duration.ofHours(2))  // access_token 有效期
                    .refreshTokenTimeToLive(Duration.ofDays(30)) // refresh_token 有效期
                    .build())
            .build();

    return new InMemoryRegisteredClientRepository(client);
}
```

### 5.3 用户在认证中心的会话管理

```java
/**
 * Session 共享是 SSO 的核心：
 * 用户在认证中心登录后，浏览器持有一个 session cookie（JSESSIONID）。
 * 后续访问其他客户端应用时，重定向到认证中心，认证中心读取 cookie 中的 session，
 * 发现用户已登录，直接颁发 code，无需重新登录。
 *
 * 关键配置：
 * - sessionCreationPolicy: IF_REQUIRED（认证端点需要 session）
 * - cookie name: JSESSIONID（或自定义）
 * - cookie domain: 共享域名（生产环境需设置在父域名下）
 */
```

### 5.4 JWK 密钥配置

```java
/**
 * JWK (JSON Web Key) 用于对 JWT 进行签名，是 OIDC 标准的一部分。
 * 对外暴露 /oauth2/jwks 端点供资源服务器验证 token 真伪。
 *
 * KeyPair 生成方式：
 * - 开发环境：使用 JDK KeyPairGenerator 生成 RSA 2048 位密钥对
 * - 生产环境：从外部密钥管理系统加载
 */
// implementation: JWKSource<SecurityContext>
```

### 5.5 Spring Security 主安全配置

```java
/**
 * 职责：
 * 1. 配置 formLogin：自定义登录页面、登录处理 URL
 * 2. 放行静态资源和公开端点
 * 3. 放行 /oauth2/** 认证端点（由 AuthorizationServerConfig 保护）
 * 4. 配置 CSRF（通常对 /oauth2/** 放行）
 */
// implementation: SecurityFilterChain
```

---

## 六、接入客户端（Client Application）配置要点

客户端应用如何接入本 SSO 认证中心（以 Spring Boot 为例）：

### 6.1 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

### 6.2 application.yml 配置

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          app-client:
            client-id: app-client
            client-secret: secret
            client-name: SSO Client
            provider: sso-provider
            scope: openid,profile
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            authorization-grant-type: authorization_code
        provider:
          sso-provider:
            issuer-uri: http://localhost:8080    # 认证中心地址
```

### 6.3 客户端安全配置

```java
// 核心配置：oauth2Login() 拦截所有需要认证的请求，自动跳转到认证中心
@Configuration
@EnableWebSecurity
public class ClientSecurityConfig {
    // implementation: SecurityFilterChain with oauth2Login()
}
```

---

## 七、OIDC 标准端点一览

| 端点 | 路径 | 说明 |
|------|------|------|
| 授权端点 | `GET /oauth2/authorize` | 发起授权请求，重定向到登录页 |
| 令牌端点 | `POST /oauth2/token` | 用 code 换取 token |
| JWK 端点 | `GET /oauth2/jwks` | 公钥信息，用于验证 JWT 签名 |
| 用户信息端点 | `GET /userinfo` | OIDC 标准，返回当前用户信息 |
| 内省端点 | `POST /oauth2/introspect` | 验证 token 有效性 |
| 吊销端点 | `POST /oauth2/revoke` | 吊销令牌 |
| OIDC 发现端点 | `GET /.well-known/openid-configuration` | OIDC Discovery 文档，列出所有端点 |

---

## 八、Token 体系说明

| Token | 说明 |
|-------|------|
| **access_token** | 访问令牌，携带在请求中访问受保护资源，JWT 格式，默认 2 小时有效 |
| **id_token** | OIDC 身份令牌，JWT 格式，包含用户身份声明（claims），用于客户端获取用户信息 |
| **refresh_token** | 刷新令牌，用于在 access_token 过期后获取新的 access_token，默认 30 天有效 |

---

## 九、关键设计决策

### 9.1 为什么默认跳过授权同意页
```
单点登录场景中，授权同意页（consent page）会打断用户体验。
通过 requireAuthorizationConsent(false) 关闭同意页，
使已登录用户完全无感知地完成 SSO 认证。
注意：这要求客户端受信任，不适用于第三方应用场景。
```

### 9.2 为什么使用 PKCE
```
即使是授权码模式，在 SPA / 移动端等公开客户端中，推荐启用 PKCE
（Proof Key for Code Exchange），防止授权码拦截攻击。
通过 requireProofKey(true) 开启。
```

### 9.3 Session 生命周期
```
认证中心 session 过期 ≠ Token 过期：
- 认证中心 session 控制"是否需要重新输入用户名密码"
- Token 内省 (introspection) 控制"API 能否访问"
- SSO 退出：需要清理认证中心 session + 通知所有客户端清理本地 token
```

---

## 十、文件清单

| 文件 | 说明 |
|------|------|
| `AuthorizationServerApplication.java` | Spring Boot 启动类 |
| `config/AuthorizationServerConfig.java` | 认证服务器核心：客户端注册、OIDC 配置、端点配置 |
| `config/DefaultSecurityConfig.java` | Spring Security 表单登录、CSRF 等全局安全配置 |
| `config/JwkConfig.java` | JWK 密钥对配置，Bean 申明 |
| `jose/Jwks.java` | RSA 密钥生成工具类 |
| `user/UserController.java` | 自定义 /userinfo 端点 |
| `user/UserInfo.java` | OIDC 标准用户信息实体 |
| `application.yml` | 服务端口、日志等基础配置 |

---

## 十一、参考规范

- [RFC 6749 - OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [Spring Authorization Server Reference](https://docs.spring.io/spring-authorization-server/reference/)
- [PKCE (RFC 7636)](https://datatracker.ietf.org/doc/html/rfc7636)
