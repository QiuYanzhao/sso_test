# Spring Authorization Server 0.2.3 `/userinfo` 端点缺陷分析与解决方案

> 本文档记录项目在接入 OIDC SSO 时遇到的一个**框架固有缺陷**（Spring Authorization Server 0.2.3 的 OIDC UserInfo 端点不可用），包括问题现象、源码级根因分析、当前采用的解决方案以及后期优化建议。

---

## 一、问题背景与现象

### 1.1 环境

| 组件 | 版本 |
|------|------|
| Spring Boot | 2.6.13 |
| Spring Security | 5.6.8 |
| Spring Authorization Server (SAS) | 0.2.3 |
| 模块 | `authorizationServer`（认证中心，端口 8080）、`oidc-client`（客户端，端口 8081） |

### 1.2 现象

OIDC 授权码流程的前半段全部正常：

```
客户端(127.0.0.1:8081) → 302 → 认证中心 /oauth2/authorize
→ 登录(admin/123456) → 认证中心签发 authorization code
→ 客户端用 code 换 access_token + id_token（成功）
```

但在客户端**拉取用户信息（UserInfo）**这一环失败，浏览器最终落在 `/login?error`：

```
Login with OAuth 2.0 [invalid_user_info_response]
An error occurred while attempting to retrieve the UserInfo Resource:
500 : {"status":500,"error":"Internal Server Error","path":"/userinfo"}
```

认证中心日志（关闭包装后）显示的真正错误链：

```
OAuth2AuthenticationException : invalid_token
  ↓ 写错误响应时再次抛错
IllegalStateException : Expected @Transient Authentication
```

即 `/userinfo` 端点返回 500，客户端 OIDC 登录流程因此整体失败。

---

## 二、根因分析（源码级）

对 `spring-security-oauth2-authorization-server-0.2.3.jar` 反编译定位到**两处框架缺陷**叠加，导致 `/userinfo` 端点在本版本下不可用。

### 2.1 缺陷一：UserInfo 过滤器从 SecurityContext 读认证，而非解析 Bearer Token

`OidcUserInfoEndpointFilter.doFilterInternal()` 反编译逻辑：

```java
if (!userInfoEndpointMatcher.matches(request)) {
    chain.doFilter(request, response); return;
}
// 关键：直接取 SecurityContext，而不是从 Authorization: Bearer <token> 解析
Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
OidcUserInfoAuthenticationToken authRequest = new OidcUserInfoAuthenticationToken(authentication);
OidcUserInfoAuthenticationToken authResult =
        (OidcUserInfoAuthenticationToken) authenticationManager.authenticate(authRequest);
```

而 `OidcUserInfoAuthenticationProvider` 要求 principal 必须是已认证的 `AbstractOAuth2TokenAuthenticationToken`（即资源服务器风格的 Bearer 令牌认证）：

```java
if (!(principal instanceof AbstractOAuth2TokenAuthenticationToken) || !principal.isAuthenticated()) {
    throw new OAuth2AuthenticationException("invalid_token");   // ← 匿名上下文必然命中这里
}
```

**结论**：`/userinfo` 依赖链路上先有一个 `BearerTokenAuthenticationFilter`（资源服务器过滤器）把 Bearer 令牌解析并放入 `SecurityContext`。但 SAS 0.2.3 的授权服务器过滤链**默认不安装** `BearerTokenAuthenticationFilter`，所以匿名请求走到这里必然抛 `invalid_token`。

> 对比：OIDC 发现端点 `/.well-known/openid-configuration` 正常，是因为它的过滤器注册在 `SecurityContextPersistenceFilter` 之后（早于授权拦截器），能直接短路响应；而 UserInfo 过滤器被注册在 `FilterSecurityInterceptor` **之后**，根本没机会处理令牌。

### 2.2 缺陷二：授权服务器安全上下文断言 `Expected @Transient Authentication`

`OAuth2AuthorizationServerConfigurer.init()` 会把自己的 `SecurityContextRepository` 包一层包装器 `OAuth2AuthorizationServerConfigurer$1$1`，其 `saveContext()` 反编译逻辑：

```java
protected void saveContext(SecurityContext context) {
    if (context.getAuthentication() != null) {
        // 认证类型必须带 @Transient 注解，否则直接断言失败
        Assert.state(isTransientAuthentication(context.getAuthentication()),
                "Expected @Transient Authentication");
    }
}
```

而 `isTransientAuthentication` 检查认证类是否标注了 `@org.springframework.security.core.Transient`：

- `OAuth2AuthorizationCodeRequestAuthenticationToken` → 有 `@Transient`（authorize 流程可用）
- `AnonymousAuthenticationToken` / `JwtAuthenticationToken` 等 → **没有** `@Transient`

由于响应提交（`SaveContextOnUpdateOrErrorResponseWrapper.onResponseCommitted`）会触发 `saveContext`，UserInfo 请求在写入响应时上下文里是匿名认证（或 JWT 认证），断言失败 → 抛 500。

**结论**：即使缺陷一被绕过（例如手动加资源服务器过滤器让 Bearer 令牌进入上下文），缺陷二仍会让响应写入阶段抛 `Expected @Transient Authentication` → 500。两个缺陷叠加，`/userinfo` 在 0.2.3 下**基本无法修复到可用状态**（配置层面无解）。

---

## 三、已采用的解决方案（当前代码）

### 3.1 方案：客户端去掉 `userInfoUri`，改用 ID Token 的 claims

OIDC 登录所需的最小用户身份信息**本来就在 `id_token` 里**（`sub`、`iss`、`aud`、`exp` 等），UserInfo 端点是可选增强。因此客户端不再调用 `/userinfo`，改为仅凭 ID Token 完成登录。

改动位置：`oidc-client/.../config/ClientSecurityConfig.java`

```java
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
        .userNameAttributeName(IdTokenClaimNames.SUB)   // 用户标识取 ID Token 的 sub 声明
        .clientName("OIDC SSO Client")
        .build();
```

> 当 `userInfoUri` 为空时，Spring Security 的 `OidcUserService` 会跳过 UserInfo 调用，直接用 ID Token 的 claims 构造 `OidcUser`，登录流程可正常完成。

### 3.2 配套修复（让整个 SSO 流程能跑通）

`/userinfo` 缺陷是流程跑通的最后一个障碍，在此之前还有三个问题需要一并修复：

| # | 问题 | 修复 |
|---|------|------|
| 1 | SAS 0.2.3 拒绝 `localhost` 作为 redirect_uri（`isValidRedirectUri` 对 host 为 `localhost` 直接返回 false → 400 Whitelabel） | 客户端与认证中心注册的 redirect_uri 全部改用 `127.0.0.1` |
| 2 | 认证中心全局 `PasswordEncoder` 是 `BCryptPasswordEncoder`，无法校验 `{noop}secret1` 客户端密钥 → token 端点 `invalid_client` | 改为 `PasswordEncoderFactories.createDelegatingPasswordEncoder()` |
| 3 | `/userinfo` 被 `anyRequest().authenticated()` 拦截（过滤器在 `FilterSecurityInterceptor` 之后） | 授权服务器过滤链对 `/userinfo` 加 `permitAll()`（配合方案 3.1 后实际已不再调用，但保留放行避免报错） |

另有两个使用层面的注意事项（与框架缺陷无关但影响体验）：

- **必须用 `http://127.0.0.1:8081` 访问客户端**，不能用 `localhost:8081`——两者被浏览器视为不同主机，会话 Cookie 不同域，回调到 `127.0.0.1` 时找不到客户端会话而报 `/login?error`。
- **退出登录**需走 POST 表单（带 CSRF），且本地退出后需跳到认证中心 `/logout` 清除 SSO 全局会话，否则下次访问会被自动重新登录（OIDC 本地退出特性）。

---

## 四、验证结果

修复后完整链路通过 curl 验证：

```
登录：127.0.0.1:8081 → 302 → 认证中心登录(admin/123456)
     → 签发 code → 客户端换 token（含 id_token）→ 首页 200
首页：显示「欢迎，admin」，展示 id_token 的 claims（sub=admin ...）

退出：POST /logout（带 CSRF）→ 302 → 认证中心 /logout
     → 认证中心会话清除 → /login?logout
     → 再次访问客户端需重新登录（完整退出生效）
```

---

## 五、后期优化建议

### 5.1 升级 Spring Authorization Server（推荐）

缺陷一、缺陷二均已在后续版本修复。升级是恢复标准 OIDC UserInfo 端点的正路。

| SAS 版本 | 要求 | 说明 |
|----------|------|------|
| 0.3.x | Spring Security 5.7 / Spring Boot 2.7+ | 移除 `Expected @Transient Authentication` 断言；但仍拒绝 `localhost` redirect_uri，UserInfo 过滤器仍读 SecurityContext，需在链上补资源服务器过滤器 |
| 0.4.x | Spring Security 5.7 / Spring Boot 2.7+ | UserInfo 端点行为趋于稳定 |
| 1.x | Spring Boot 3.x（Jakarta） | OIDC/UserInfo 完整支持，推荐的生产选择 |

> 升级到 0.3.1 及以上后，应将本方案中「去掉 userInfoUri」的改动还原，并在授权服务器过滤链上配置资源服务器（如 `oauth2ResourceServer().jwt()`），让 `/userinfo` 能解析 Bearer 令牌。

### 5.2 若坚持 0.2.3：自行实现 UserInfo（不推荐）

可在认证中心新增一个普通 Controller（走默认过滤链，不经过 SAS 授权链）：

```java
@RestController
public class UserInfoController {
    // 自行解析 Bearer Token、查 OAuth2AuthorizationService、
    // 返回用户 claims（JSON）
}
```

需要自实现令牌解析、校验、作用域检查等，工作量大且易出错，仅作学习目的时可参考。

### 5.3 关于是否保留 `/userinfo` 放行规则

- 当前客户端已不调用 `/userinfo`，但 `AuthorizationServerConfig` 中对 `/userinfo` 的 `permitAll()` 保留无害。
- 若未来升级版本并恢复 UserInfo 功能，请同步评估该放行规则是否需要保留（放行后由 UserInfo 过滤器自行鉴权，属合理做法）。

### 5.4 长期：接入 OIDC RP-Initiated Logout

当前「退出」通过「客户端本地退出 + 重定向到认证中心 `/logout`」实现，属于手动方案。若要符合 OIDC 标准，后期可：

- 在升级后的认证中心启用 `end_session_endpoint`；
- 客户端按 `id_token_hint` + `post_logout_redirect_uri` 实现 RP-Initiated Logout，实现跨应用统一注销。

---

## 六、总结

| 项目 | 内容 |
|------|------|
| 根因 | 0.2.3 的 UserInfo 过滤器依赖资源服务器 Bearer 认证（缺陷一）+ 安全上下文 `@Transient` 断言（缺陷二），两处叠加导致 `/userinfo` 返回 500 |
| 当前方案 | 客户端去掉 `userInfoUri`，登录身份取自 `id_token` claims |
| 效果 | OIDC SSO 登录/退出完整可用 |
| 后期方向 | 升级 SAS（0.3.x 需 Boot 2.7+，1.x 需 Boot 3）后恢复标准 `/userinfo`，并考虑接入 OIDC RP-Initiated Logout |
