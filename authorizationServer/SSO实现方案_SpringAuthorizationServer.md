# Spring Authorization Server (OIDC) SSO 单点登录实现方案

## 一、方案概述

本方案基于 **Spring Authorization Server 0.2.3** 构建符合 **OAuth 2.1 / OIDC 1.0** 标准的单点登录（SSO）认证中心。采用经典的 **授权码模式（Authorization Code Flow）** 作为核心认证流程，实现一个认证中心对多个客户端应用的统一认证。

### 核心角色

| 角色 | 说明 | 对应模块 |
|------|------|----------|
| **Authorization Server** | 认证中心，负责用户登录、颁发 Token | `authorizationServer` (端口 8080) |
| **OIDC Client** | 接入 SSO 的业务应用 | `oidc-client` (端口 8081) |

认证中心和客户端位于独立的 Maven 模块中，通过 OAuth2/OIDC 协议交互。

---

## 二、技术选型

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 2.6.13 | 基础框架 |
| Spring Security | 5.6.x | 安全框架 |
| Spring Authorization Server | 0.2.3 | OAuth2/OIDC 认证授权服务器 |
| Nimbus JOSE + JWT | 9.37.2 | JWT 生成与解析 |
| Spring Security OAuth2 Client | 5.6.x | 客户端集成 OAuth2（Server 端用于第三方登录，Client 端用于接入 SSO） |
| Thymeleaf | - | 登录页面渲染 |
| thymeleaf-extras-springsecurity5 | - | Thymeleaf 模板中 #authentication 等安全表达式 |

---

## 三、项目包结构

### 认证中心（authorizationServer 模块）

```
com.example.authorizationserver/
├── AuthorizationServerApplication.java      # Server 启动类 (端口 8080)
├── config/
│   ├── AuthorizationServerConfig.java       # 【核心】OAuth2 服务器配置 + ProviderSettings
│   ├── DefaultSecurityConfig.java           # 表单登录 + 第三方 OAuth2 登录
│   ├── JwkConfig.java                       # JWK 密钥配置
│   └── OAuth2ThirdPartyConfig.java          # 第三方登录扩展说明
├── controller/
│   └── PageController.java                  # 页面路由 (/login、/)
├── jose/
│   └── Jwks.java                            # RSA 密钥生成工具
└── service/
    └── ThirdPartyUserService.java           # 第三方用户信息处理
```

### OIDC 客户端（oidc-client 模块）

```
com.example.oidcclient/
├── OidcClientApplication.java              # Client 启动类 (端口 8081)
├── config/
│   └── ClientSecurityConfig.java            # ClientRegistration + oauth2Login()
└── controller/
    └── HomeController.java                  # 首页（展示 OIDC 用户信息）
```

---

## 四、认证授权流程图

### 4.1 OIDC 授权码流程（Client → Server → Client）

```
┌──────────────┐           ┌───────────────────┐           ┌───────────────────┐
│   用户浏览器   │           │   Client (8081)    │           │   Server (8080)   │
└──────┬───────┘           └─────────┬─────────┘           └─────────┬─────────┘
       │                             │                               │
       │  ① 访问 http://localhost:8081│                               │
       │ ──────────────────────────>  │                               │
       │                             │                               │
       │  ② 未登录，302 重定向         │                               │
       │     /oauth2/authorize?       │                               │
       │     client_id=app-client-1   │                               │
       │     redirect_uri=...         │                               │
       │     response_type=code       │                               │
       │     scope=openid profile     │                               │
       │ <───────────────────────────│                               │
       │                             │                               │
       │  ③ GET /oauth2/authorize?   │                               │
       │     (跟随上一步的 302)        │                               │
       │ ──────────────────────────────────────────────────────>    │
       │                             │                               │
       │  ④ 返回登录页面 /login       │                               │
       │ <──────────────────────────────────────────────────────    │
       │                             │                               │
       │  ⑤ POST /login (admin/123456)                               │
       │ ──────────────────────────────────────────────────────>    │
       │                             │                               │
       │  ⑥ 认证成功，302 重定向       │                               │
       │     http://localhost:8081/   │                               │
       │     login/oauth2/code/       │                               │
       │     app-client?code=XXXX     │                               │
       │ <──────────────────────────────────────────────────────    │
       │                             │                               │
       │  ⑦ 携带 code 访问客户端回调    │                               │
       │     GET /login/oauth2/code/  │                               │
       │     app-client?code=XXXX     │                               │
       │ ──────────────────────────>  │                               │
       │                             │                               │
       │                             │  ⑧ Server 端：POST /oauth2/token
       │                             │     code + client_secret       │
       │                             │     (后端对后端，不经过浏览器)    │
       │                             │ ────────────────────────────> │
       │                             │                               │
       │                             │  ⑨ 返回 access_token          │
       │                             │    + id_token + refresh_token │
       │                             │ <──────────────────────────── │
       │                             │                               │
       │                             │  ⑩ 客户端完成登录              │
       │  ⑪ 302 到首页 /              │                               │
       │ <───────────────────────────│                               │
       │                             │                               │
       │  ⑫ 显示 client-home.html    │                               │
       │     (已登录，含 OIDC 用户信息)  │                               │
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
       │              │              │                      │
       │ ③跳转认证中心   │              │                      │
       │──────────────────────────────────────────────────>│
       │              │              │   ④输入用户名/密码       │
       │              │              │   ⑤创建 Session        │
       │              │              │     (JSESSIONID Cookie) │
       │ ⑥重定向(code)  │              │                      │
       │─────────────>│              │                      │
       │              │ ⑦换token，登录 │                      │
       │              │              │                      │
       │ ⑧访问应用B     │              │                      │
       │─────────────────────────────>│                      │
       │              │              │ ⑨未登录，重定向          │
       │              │              │                      │
       │ ⑩跳转认证中心   │              │                      │
       │──────────────────────────────────────────────────>│
       │              │              │  ⑪ 已有 Session！      │
       │              │              │     (浏览器携带 Cookie)  │
       │              │              │     直接颁发 code       │
       │              │              │                      │
       │ ⑫重定向(code)  │              │                      │
       │─────────────────────────────>│                      │
       │              │              │ ⑬换token，登录成功      │
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

    /**
     * ProviderSettings：认证中心元数据配置（0.2.3 使用此 API）
     * - issuer：可显式指定，默认为请求根路径
     * - 生产环境建议：.issuer("https://sso.example.com")
     */
    // implementation: ProviderSettings Bean
}
```

### 5.2 客户端注册配置

```java
// 核心：通过 Bean 方式注册接入 SSO 的客户端应用
@Bean
public RegisteredClientRepository registeredClientRepository() {
    RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("app-client-1")                           // 客户端 ID
            .clientSecret("{noop}secret1")                      // 客户端密钥
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .redirectUri("http://localhost:8081/login/oauth2/code/app-client") // 回调地址
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
 * 对外暴露 /oauth2/jwks 端点供客户端解析 token 签名。
 *
 * KeyPair 生成方式：
 * - 开发环境：使用 JDK KeyPairGenerator 生成 RSA 2048 位密钥对
 * - 生产环境：从外部密钥管理系统加载
 */
// implementation: JWKSource<SecurityContext>
```

### 5.5 页面路由配置

```java
/**
 * Spring Security 的 formLogin().loginPage("/login") 不会自动创建 GET 路由。
 * 需要手动提供 Controller 将请求映射到 Thymeleaf 模板。
 *
 * PageController 提供：
 * - GET /login → templates/login.html
 * - GET /      → templates/index.html
 */
```

---

## 六、接入客户端（Client Application）配置要点

客户端如何接入本 SSO 认证中心（Spring Boot + spring-boot-starter-oauth2-client）：

### 6.1 客户端注册（ClientRegistrationRepository）

```java
@Bean
public ClientRegistrationRepository clientRegistrationRepository() {
    ClientRegistration client = ClientRegistration
            .withRegistrationId("app-client")
            .clientId("app-client-1")           // 与 Server 的 RegisteredClient 一致
            .clientSecret("secret1")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri("http://localhost:8080/oauth2/authorize")
            .tokenUri("http://localhost:8080/oauth2/token")
            .userInfoUri("http://localhost:8080/userinfo")
            .jwkSetUri("http://localhost:8080/oauth2/jwks")
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .clientName("SSO Client")
            .build();
    return new InMemoryClientRegistrationRepository(client);
}
```

### 6.2 客户端安全配置

```java
@Bean
public SecurityFilterChain clientSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeRequests(authorize ->
            authorize.anyRequest().authenticated()
        )
        // 核心：oauth2Login() 自动触发 OIDC 授权码流程
        .oauth2Login(oauth2 ->
            oauth2.defaultSuccessUrl("/", true)
        );
    return http.build();
}
```

### 6.3 获取 OIDC 用户信息

```java
@GetMapping("/")
public String home(@AuthenticationPrincipal OidcUser principal, Model model) {
    // OidcUser 由 Spring Security 自动注入
    // 包含 id_token 的所有 claims（sub, email, name, ...）
    model.addAttribute("name", principal.getFullName());
    model.addAttribute("subject", principal.getSubject());
    model.addAttribute("claims", principal.getClaims());
    model.addAttribute("idToken", principal.getIdToken().getTokenValue());
    return "client-home";
}
```

---

## 七、OIDC 标准端点一览（认证中心）

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
| **access_token** | 访问令牌，JWT 格式，默认 2 小时有效，用于访问受保护资源 |
| **id_token** | OIDC 身份令牌，JWT 格式，包含用户身份声明（claims），客户端用于获取用户信息 |
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

### 9.2 Session 生命周期
```
认证中心 session 过期 ≠ Token 过期：
- 认证中心 session 控制"是否需要重新输入用户名密码"
- Token 内省 (introspection) 控制"API 能否访问"
- SSO 退出：需要清理认证中心 session + 通知所有客户端清理本地 token
```

### 9.3 组件扫描隔离
```
Server 和 Client 在同一项目但不同包路径：
- AuthorizationServerApplication 在 com.example.authorizationserver，扫描所有子包
- SsoClientApp 在 com.example.authorizationserver.sso_client（带 scanBasePackages），
  仅扫描 sso_client 包，避免加载 Server 配置
```

### 9.4 第三方登录支持
```
- GitHub：标准 OAuth2，开箱即用（Spring Security 内置 GitHub provider）
- 微信/QQ：非标准 OAuth2，代码中有完整的手动实现示例（OAuth2ThirdPartyConfig.java）
- 推荐生产方案：使用 JustAuth（封装了数十个平台）
```

---

## 十、文件清单

| 文件 | 说明 |
|------|------|
| `AuthorizationServerApplication.java` | Server 启动类（端口 8080） |
| `config/AuthorizationServerConfig.java` | 认证服务器核心：客户端注册、OIDC 配置、ProviderSettings |
| `config/DefaultSecurityConfig.java` | 表单登录、本地用户、第三方 OAuth2 配置 |
| `config/JwkConfig.java` | JWK 密钥对 Bean 注册 |
| `config/OAuth2ThirdPartyConfig.java` | 第三方登录扩展说明（含 JustAuth 示例） |
| `controller/PageController.java` | 页面路由（/login → login.html） |
| `jose/Jwks.java` | RSA 密钥生成工具 |
| `service/ThirdPartyUserService.java` | 第三方用户信息处理 |
| `sso_client/SsoClientApp.java` | Client 启动类（端口 8081） |
| `sso_client/config/ClientSecurityConfig.java` | 客户端 OAuth2 配置（ClientRegistration + oauth2Login） |
| `sso_client/controller/HomeController.java` | 客户端首页（展示 OIDC claims） |
| `application.yml` | 服务端口、第三方登录配置 |
| `templates/login.html` | 登录页面（含第三方按钮） |
| `templates/index.html` | 认证中心首页 |
| `templates/client-home.html` | 客户端首页（展示 id_token） |

---

## 十一、参考规范

- [RFC 6749 - OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [Spring Authorization Server Reference](https://docs.spring.io/spring-authorization-server/reference/)
- [PKCE (RFC 7636)](https://datatracker.ietf.org/doc/html/rfc7636)
