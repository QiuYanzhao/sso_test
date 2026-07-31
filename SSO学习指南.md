# SSO 单点登录系统学习指南

基于此项目的两个生产级 SSO 实现（Spring Authorization Server + Sa-Token），从零开始系统学习 SSO。

---

## 目录

- [第一部分：SSO 核心概念（理论篇）](#第一部分sso-核心概念理论篇)
- [第二部分：环境准备与项目结构](#第二部分环境准备与项目结构)
- [第三部分：Spring Authorization Server 方案（OIDC 标准方案）](#第三部分spring-authorization-server-方案oidc-标准方案)
- [第四部分：Sa-Token 方案（轻量级国产方案）](#第四部分sa-token-方案轻量级国产方案)
- [第五部分：两种方案深度对比](#第五部分两种方案深度对比)
- [第六部分：第三方 OAuth2 登录集成](#第六部分第三方-oauth2-登录集成)
- [第七部分：生产环境进阶](#第七部分生产环境进阶)
- [附录：术语速查表](#附录术语速查表)

---

## 第一部分：SSO 核心概念（理论篇）

### 学习目标

- 理解什么是 SSO，解决什么问题
- 掌握 SSO 的核心概念：会话、令牌、凭证
- 理解 Cookie 与 Token 在 SSO 中的角色
- 了解常见的 SSO 协议

### 1.1 没有 SSO 的世界

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│  应用 A      │      │  应用 B      │      │  应用 C      │
│  ┌───────┐  │      │  ┌───────┐  │      │  ┌───────┐  │
│  │用户DB  │  │      │  │用户DB  │  │      │  │用户DB  │  │
│  │admin   │  │      │  │admin   │  │      │  │admin   │  │
│  │user    │  │      │  │user    │  │      │  │user    │  │
│  └───────┘  │      │  └───────┘  │      │  └───────┘  │
└─────────────┘      └─────────────┘      └─────────────┘
      ↑                    ↑                    ↑
      │                    │                    │
  ┌───┴────────────────────┴────────────────────┴───┐
  │                  用户                            │
  │   登录 A → 输入账号密码                           │
  │   登录 B → 又输入账号密码                          │
  │   登录 C → 再输入账号密码                          │
  └──────────────────────────────────────────────────┘
```

**痛点：**
- 每个应用都需要独立的用户体系
- 用户需要反复登录
- 密码需要在多个系统间同步
- 安全策略难以统一管控

### 1.2 引入 SSO 后

```
┌──────────────────────────────────────────────────┐
│              认证中心 (SSO Server)                 │
│  ┌─────────┐  ┌──────────┐  ┌────────────────┐  │
│  │ 用户存储  │  │ 登录页面   │  │ 会话/Token 管理 │  │
│  └─────────┘  └──────────┘  └────────────────┘  │
└──────────────────────┬───────────────────────────┘
           ↑           │           ↑
           │   ┌───────┴───────┐   │
           │   │    票据/令牌    │   │
           │   └───────┬───────┘   │
           │           │           │
    ┌──────┴───┐  ┌────┴────┐  ┌──┴───────┐
    │  应用 A   │  │  应用 B  │  │  应用 C   │
    │  一次登录  │  │  无需登录 │  │  无需登录  │
    └──────────┘  └─────────┘  └──────────┘
```

**一次登录，处处通行。**

### 1.3 SSO 的核心概念

#### 1.3.1 什么是"登录"？

登录的本质是**服务器确认"你是谁"**的过程。技术上等价于：

```
登录 = 服务器创建了一个包含用户身份信息的"会话"或"令牌"
```

从实现角度，登录分为两种模式：

| 模式 | 机制 | 存储位置 | 代表技术 |
|------|------|----------|----------|
| **会话模式** | 服务端创建 Session，浏览器持有 Session ID (Cookie) | 服务端 | 传统 Web 应用、Spring Security 表单登录 |
| **令牌模式** | 服务端签发加密令牌(Token)，客户端持有并每次请求携带 | 客户端 | JWT、Sa-Token、OAuth2 |

两种模式的对比：

```
会话模式（Session-Cookie）：
┌──────────┐                    ┌──────────┐
│  浏览器    │                    │  服务器    │
│           │  ① POST /login     │           │
│           │ ─────────────────> │ ② 创建    │
│           │                    │ Session   │
│           │  ③ Set-Cookie:     │  id: a1b2 │
│           │    JSESSIONID=a1b2 │           │
│           │ <───────────────── │           │
│           │                    │           │
│           │  ④ GET /api/user   │           │
│           │  Cookie: JSESSIONID│ ⑤ 根据    │
│           │  =a1b2            │ a1b2      │
│           │ ─────────────────> │ 查 Session│
│           │                    │ → 找到用户 │
│           │  ⑥ 返回数据        │           │
│           │ <───────────────── │           │
└──────────┘                    └──────────┘

令牌模式（Token）：
┌──────────┐                    ┌──────────┐
│  浏览器    │                    │  服务器    │
│           │  ① POST /login     │           │
│           │ ─────────────────> │ ② 验证    │
│           │                    │ 用户名密码  │
│           │  ③ 返回Token:      │ ③ 签发    │
│           │    {"token":"eyJ..│   JWT     │
│           │ <───────────────── │           │
│           │                    │           │
│           │  ④ GET /api/user   │           │
│           │  Authorization:    │ ⑤ 验证    │
│           │  Bearer eyJ...    │ JWT签名   │
│           │ ─────────────────> │ → 解析用户 │
│           │                    │           │
│           │  ⑥ 返回数据        │           │
│           │ <───────────────── │           │
└──────────┘                    └──────────┘
```

#### 1.3.2 SSO 如何实现"一次登录，处处通行"？

SSO 的核心思想是**集中认证**——所有应用的登录都由同一个"认证中心"处理。

两个关键机制：

**机制一：会话共享（Cookie 传递）**

```
用户先访问应用A：
  应用A → 没登录 → 重定向到认证中心 → 输入密码登录
  → 认证中心创建 Session → 浏览器拿到 Cookie: JSESSIONID=abc

用户再访问应用B：
  应用B → 没登录 → 重定向到认证中心
  → 浏览器自动带上 Cookie: JSESSIONID=abc
  → 认证中心发现已有有效 Session → 不用再输入密码！
  → 直接颁发授权，跳回应用B → 应用B 登录成功
```

> 这种方式依赖 Cookie 和同源策略。如果认证中心和应用不在同一域名下，需要额外处理跨域。

**机制二：票据传递（Ticket 重定向）**

```
用户访问应用A：
  应用A → 没登录 → 重定向到认证中心 → 输入密码登录
  → 认证中心签发一次性票据 ticket=xyz
  → 302 重定向: http://应用A/callback?ticket=xyz

  应用A 收到 ticket：
    → 后端用 Http 请求向认证中心校验 ticket
    → 认证中心返回用户信息，ticket 立即作废
    → 应用A 创建本地会话，用户登录成功
```

> 票据模式通过**服务端到服务端的 HTTP 通信**传递认证结果，绕过了浏览器 Cookie 的跨域限制。本项目中的 Sa-Token 方案即采用此模式。

#### 1.3.3 关键概念的通俗理解

| 概念 | 通俗理解 | 本项目中的体现 |
|------|----------|----------------|
| **Session（会话）** | 服务器记住你的"便签"，上面写着你是谁 | Spring Auth Server 的 JSESSIONID |
| **Token（令牌）** | 一张由服务器签发、带签名的"身份证" | JWT access_token（2小时有效）|
| **Cookie** | 浏览器帮你保管的"小纸条"，每次访问都自动带上 | JSESSIONID, sso-token |
| **Ticket（票据）** | 一次性的"通行证"，用完就作废 | Sa-Token 的 ticket（5 分钟有效）|
| **Authorization Code（授权码）** | OAuth2 中的"兑换券"，用来换 Token | 步骤⑤⑥⑦中的 code |
| **JWT** | 一段包含用户信息 + 数字签名的 JSON 字符串 | id_token 的格式 |

### 1.4 常见 SSO 协议对比

| 协议 | 全称 | 核心机制 | 复杂程度 | 本项目对应 |
|------|------|----------|----------|------------|
| **OAuth 2.0** | Open Authorization | 授权框架，定义四种授权模式 | 中 | — |
| **OIDC** (OpenID Connect) | — | 基于 OAuth2 的身份认证层，引入 id_token | 中 | Spring Auth Server 方案 |
| **SAML 2.0** | Security Assertion Markup Language | XML 格式的安全断言 | 高 | (传统企业方案) |
| **CAS** | Central Authentication Service | Ticket 票据验证 | 中 | (早期 Java SSO 方案) |
| **自定义** | — | 各家框架自有的 SSO 协议 | 不定 | Sa-Token 方案 |

**关系图谱：**

```
OAuth 2.0（授权协议）
  │
  ├── OIDC（身份认证层） ← Spring Authorization Server 方案
  │    │
  │    └── 授权码模式（Authorization Code Flow）
  │          │
  │          └── id_token (JWT) + access_token + refresh_token
  │
  └── 第三方 OAuth 登录（GitHub/微信/QQ）

Sa-Token（自有的认证框架）
  │
  └── 模式三（HTTP 请求调用 + Ticket 票据）
```

---

## 第二部分：环境准备与项目结构

### 学习目标

- 理解项目的 Maven 多模块结构
- 掌握两个 SSO 方案的启动方式
- 理解各模块的职责划分

### 2.1 项目整体结构

```
sso_test/                              # 根项目 (pom.xml, 聚合模块)
│
├── authorizationServer/               # 【方案1】Spring Authorization Server 认证中心 (端口 8080)
│   ├── pom.xml                        #   → 依赖: spring-authorization-server 0.2.3
│   ├── SSO实现方案_SpringAuthorizationServer.md  # 方案文档
│   └── src/main/java/com/example/authorizationserver/
│       ├── AuthorizationServerApplication.java
│       ├── config/
│       │   ├── AuthorizationServerConfig.java     # ★ OAuth2 服务器核心
│       │   ├── DefaultSecurityConfig.java         # 表单登录、用户管理
│       │   ├── JwkConfig.java                     # JWT 密钥
│       │   └── OAuth2ThirdPartyConfig.java         # 第三方登录示例(注释)
│       ├── controller/PageController.java
│       ├── jose/Jwks.java                         # RSA 密钥生成
│       └── service/ThirdPartyUserService.java     # 第三方用户处理
│
├── oidc-client/                       # 【方案1】OIDC 客户端应用 (端口 8081)
│   ├── pom.xml                        #   → 依赖: spring-boot-starter-oauth2-client
│   └── src/main/java/com/example/oidcclient/
│       ├── OidcClientApplication.java
│       ├── config/ClientSecurityConfig.java       # ★ 客户端 OAuth2 配置
│       └── controller/HomeController.java
│
├── saToken/                           # 【方案2】Sa-Token 认证中心 (端口 8080)
│   ├── pom.xml                        #   → 依赖: sa-token-spring-boot-starter + sa-token-sso
│   ├── SSO实现方案_SaToken.md          # 方案文档
│   └── src/main/java/com/example/satoken/sso/
│       ├── SsoConst.java                           # OAuth 平台常量
│       └── server/
│           ├── SsoServerApp.java
│           ├── config/SaTokenServerConfig.java     # ★ SaToken 拦截器
│           ├── controller/SsoServerController.java # 第三方登录
│           └── auth/
│               ├── SsoServerAuthService.java       # 本地账号认证
│               └── ThirdPartyAuthService.java      # ★ 手动 OAuth 实现
│
└── sso-client/                        # 【方案2】Sa-Token 客户端应用 (端口 8081)
    ├── pom.xml
    └── src/main/java/com/example/ssoclient/
        ├── SsoClientApplication.java
        ├── config/SsoClientConfig.java             # ★ 客户端拦截器
        └── controller/HomeController.java
```

> **注意**：两种方案使用了相同的端口号（8080/8081），因此**无法同时运行**。需要学习哪个方案，就启动哪一对模块。

### 2.2 启动方式

#### 方案1：Spring Authorization Server

```bash
# 终端1：启动认证中心
mvn -pl authorizationServer spring-boot:run

# 终端2：启动客户端应用
mvn -pl oidc-client spring-boot:run

# 浏览器访问 http://localhost:8081
# 测试账号：admin/123456 或 user/123456
```

#### 方案2：Sa-Token

```bash
# 终端1：启动认证中心
mvn -pl saToken spring-boot:run

# 终端2：启动客户端应用
mvn -pl sso-client spring-boot:run

# 浏览器访问 http://localhost:8081
# 测试账号：admin/123456 或 user1/123456
```

### 2.3 动手实验 1：观察你的第一个 SSO 流程

> **实验目标**：亲眼看到认证中心如何介入登录流程

1. 启动方案1的认证中心和客户端
2. 打开浏览器的**开发者工具 → Network 标签**，勾选"Preserve log"
3. 访问 `http://localhost:8081`（客户端）
4. 观察 Network 面板中的请求序列：
   - `http://localhost:8081/` → 302 重定向
   - `http://localhost:8080/oauth2/authorize?...` → 302 重定向
   - `http://localhost:8080/login` → 显示登录页面
   - `POST http://localhost:8080/login` → 提交登录表单
   - `http://localhost:8081/login/oauth2/code/app-client?code=...` → 回调
   - `http://localhost:8081/` → 最终回到首页（已登录）

5. **关键观察点**：
   - 浏览器地址栏从 `8081` 跳到了 `8080`，又跳回了 `8081`
   - 认证中心返回的 URL 中有一个 `code=...` 参数
   - 完成登录后，客户端的 Cookie 中有了自己的会话

---

## 第三部分：Spring Authorization Server 方案（OIDC 标准方案）

### 学习目标

- 理解 OAuth 2.0 授权码流程的每一步
- 理解 OIDC 如何在 OAuth2 基础上提供身份认证
- 掌握认证中心的核心配置
- 掌握客户端接入 SSO 的方法
- 理解 Session 如何实现 SSO

### 3.1 理论：OAuth 2.0 授权码流程（核心协议）

这是 OAuth 2.0 中最安全、最常用的授权模式，也是本项目的基础。**理解这个流程是学习 SSO 的关键**。

#### 三步走模型

```
    步骤1：获取授权码          步骤2：兑换令牌          步骤3：访问资源
    ─────────────────       ─────────────────       ─────────────────
    
    浏览器 → 认证中心          客户端后端 → 认证中心      客户端后端 → 资源服务器
    (用户在浏览器完成登录)      (后端对后端，不经过浏览器)    (携带令牌)
```

#### 完整时序图（9个步骤）

```
┌──────────────┐           ┌───────────────────┐           ┌───────────────────┐
│   用户浏览器   │           │   Client (8081)    │           │   Server (8080)   │
└──────┬───────┘           └─────────┬─────────┘           └─────────┬─────────┘
       │                             │                               │
       │  ① 访问 http://localhost:8081│                               │
       │ ──────────────────────────>  │                               │
       │                             │                               │
       │  ② 未登录，302 重定向         │                               │
       │     GET /oauth2/authorize?   │                               │
       │     response_type=code       │                               │
       │     client_id=app-client-1   │                               │
       │     redirect_uri=...         │                               │
       │     scope=openid profile     │                               │
       │     state=随机防CSRF值        │                               │
       │ <───────────────────────────│                               │
       │                             │                               │
       │  ③ 浏览器跟随 302            │                               │
       │     访问认证中心              │                               │
       │ ──────────────────────────────────────────────────────>    │
       │                             │                               │
       │  ④ 认证中心发现未登录          │                               │
       │     返回登录页面 /login       │                               │
       │ <──────────────────────────────────────────────────────    │
       │                             │                               │
       │  ⑤ 用户输入账号密码并提交      │                               │
       │     POST /login             │                               │
       │ ──────────────────────────────────────────────────────>    │
       │                             │                               │
       │  ⑥ 认证中心验证密码：          │                               │
       │     创建 Session (JSESSIONID)│                               │
       │     生成一次性授权码 code      │                               │
       │     302 重定向到客户端的       │                               │
       │     redirect_uri             │                               │
       │ <──────────────────────────────────────────────────────    │
       │                             │                               │
       │  ⑦ 浏览器携带 code 访问客户端   │                               │
       │     GET /login/oauth2/code/  │                               │
       │       app-client?code=XXXX   │                               │
       │ ──────────────────────────>  │                               │
       │                             │                               │
       │                             │  ⑧ 客户端后端直接请求认证中心：    │
       │                             │     POST /oauth2/token        │
       │                             │     grant_type=authorization_code
       │                             │     code=XXXX                │
       │                             │     client_id=app-client-1    │
       │                             │     client_secret=secret1    │
       │                             │     (后端到后端，浏览器看不到！)  │
       │                             │ ────────────────────────────> │
       │                             │                               │
       │                             │  ⑨ 认证中心验证 code+secret：   │
       │                             │     code 一次性作废             │
       │                             │     返回 JSON：                │
       │                             │     {                         │
       │                             │       "access_token": "eyJ..",│
       │                             │       "id_token": "eyJ..",    │
       │                             │       "refresh_token": "xxx", │
       │                             │       "expires_in": 7200      │
       │                             │     }                         │
       │                             │ <──────────────────────────── │
       │                             │                               │
       │                             │  ⑩ 客户端完成本地登录            │
       │  ⑪ 302 重定向到首页 /        │                               │
       │ <───────────────────────────│                               │
```

**为什么这是安全的？**

| 安全措施 | 说明 |
|----------|------|
| **code 只用一次** | 授权码被认证中心使用后立即作废，即使被窃取也无法重复使用 |
| **后端换 token** | 步骤⑧使用 client_secret 在服务器间完成，不经过浏览器 |
| **state 参数** | 防止 CSRF 攻击，客户端校验 state 值与发起时一致 |
| **redirect_uri 白名单** | 认证中心只重定向到已注册的回调地址 |
| **HTTPS 传输** | 生产环境必须使用 HTTPS 保护传输中的 code 和 token |

#### 重点区分：code 和 token 的区别

```
授权码 (code)：
  - 用途：证明"用户已在认证中心授权"
  - 有效期：极短（通常 1-5 分钟）
  - 传递方式：浏览器 URL 参数（可被看见）
  - 安全性：中等（即使泄露，没有 client_secret 也无法使用）

访问令牌 (access_token)：
  - 用途：代表"这个客户端可以代表用户访问资源"
  - 有效期：较长（本项目 2 小时）
  - 传递方式：后端存储（不透出给浏览器）
  - 安全性：高（必须保密存储）
```

### 3.2 OIDC：在 OAuth2 上构建身份认证

OAuth 2.0 的设计目标是**授权**（"允许应用 A 访问我的照片"），不是**认证**（"我是张三"）。

**OIDC (OpenID Connect)** 是在 OAuth 2.0 基础上增加了一个"身份层"，专门用于认证。核心就是 **id_token**。

```
OAuth 2.0 返回的 Token：                  OIDC 额外返回的 id_token：
┌─────────────────────┐                 ┌─────────────────────────────────┐
│ access_token        │                 │ id_token (JWT 格式)             │
│ "授权：允许你访问API" │                 │ "认证：这个用户是 admin@xxx.com"  │
│ 存活 2 小时          │                 │ 包含用户身份声明 (claims)        │
└─────────────────────┘                 │ 存活 2 小时                      │
                                        └─────────────────────────────────┘

JWT 结构（三段式，用 . 分隔）：
  eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.签名部分
  
  Header                  Payload               Signature
  {"alg":"RS256"}        {"sub":"admin",...}    用 RSA 私钥签名
```

### 3.3 代码深度解析

#### 3.3.1 认证中心：注册客户端

> **阅读顺序**：先看 `AuthorizationServerConfig.registeredClientRepository()` 方法（文件：`authorizationServer/.../config/AuthorizationServerConfig.java:102`）

```java
@Bean
public RegisteredClientRepository registeredClientRepository() {
    RegisteredClient clientApp1 = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("app-client-1")              // ← 客户端唯一标识（用户名）
            .clientSecret("{noop}secret1")          // ← 客户端密钥（密码）
            //         ↑ {noop} 表示"不加密存储"，仅开发环境使用
            //         生产环境应使用 BCrypt 加密，或从密钥管理系统获取
            .clientAuthenticationMethod(
                    ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            //       ↑ 客户端通过 HTTP Basic Auth 发送 clientId:clientSecret

            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            //       ↑ 授权码模式：最安全的 OAuth2 流程，适合有后端的 Web 应用

            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            //       ↑ 支持用 refresh_token 换新的 access_token

            .redirectUri("http://localhost:8081/login/oauth2/code/app-client")
            //       ↑ 【关键】回调地址必须与客户端配置严格一致！
            //         认证中心会精确比较此 URI，不匹配则拒绝

            .scope(OidcScopes.OPENID)    // ← OIDC 必需的 scope
            .scope(OidcScopes.PROFILE)   // ← 获取用户基本信息

            .clientSettings(ClientSettings.builder()
                    .requireAuthorizationConsent(false)
                    //  ↑ SSO 的关键配置 ============================
                    //  设为 true 时：用户审批授权后展示"是否同意授予权限"页面
                    //  设为 false 时：跳过同意页，实现"无感知 SSO"
                    //  仅适用于企业/内部应用场景，第三方应用必须 true
                    .build())

            .tokenSettings(TokenSettings.builder()
                    .accessTokenTimeToLive(Duration.ofHours(2))     // 令牌 2 小时
                    .refreshTokenTimeToLive(Duration.ofDays(30))    // 刷新令牌 30 天
                    .build())
            .build();
    // ...
}
```

**理解要点：**
- 每个接入 SSO 的应用称为一个 "OAuth2 Client"
- `clientId` + `clientSecret` 是应用的"账号密码"
- `redirectUri` 是授权码的接收地址，必须精确匹配
- 新增应用时，只需加一个新的 `RegisteredClient` 对象

#### 3.3.2 认证中心：OAuth2 安全过滤器

> **文件**：`authorizationServer/.../config/AuthorizationServerConfig.java:52`

```java
@Bean
@Order(Ordered.HIGHEST_PRECEDENCE)  // ← 最高优先级
public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) {
    OAuth2AuthorizationServerConfigurer<HttpSecurity> authorizationServerConfigurer =
            new OAuth2AuthorizationServerConfigurer<>();
    RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();

    http
        .requestMatcher(endpointsMatcher)   // ← 仅处理 OAuth2 协议端点
        .authorizeRequests(authorize ->
            authorize.anyRequest().authenticated()
            //         ↑ OAuth2 端点需要用户已认证
            //           （用户必须先在认证中心登录，才能使用授权功能）
        )
        .exceptionHandling(exceptions ->
            exceptions.authenticationEntryPoint(
                new LoginUrlAuthenticationEntryPoint("/login"))
                //  ↑ 如果访问 OAuth2 端点时未登录 → 302 重定向到登录页
                //  这就是 SSO 的入口：用户在此完成统一认证
        )
        .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
        .apply(authorizationServerConfigurer);  // ← 自动配置所有 OAuth2 端点

    authorizationServerConfigurer
        .oidc(Customizer.withDefaults());  // ← 启用 OIDC 协议
    return http.build();
}
```

**为什么有两个 SecurityFilterChain？**

```
请求流程：
  GET /oauth2/authorize?...
    ↓
  AuthorizationServerConfig 的 filter (Order=HIGHEST_PRECEDENCE)
    ← requestMatcher 匹配 → 处理 OAuth2 逻辑
    ← 如果未登录 → 302 到 /login
    
  GET /login
    ↓
  AuthorizationServerConfig 的 filter
    ← requestMatcher 不匹配 → 跳过此 filter
    ↓
  DefaultSecurityConfig 的 filter (Order=2)
    ← 匹配 → 允许匿名访问 /login → 展示登录页
```

这保证了：
1. OAuth2 协议端点使用 OAuth2 专用的安全策略（无 CSRF、需要认证）
2. 普通页面（/login、/、静态资源）使用通用的安全策略

#### 3.3.3 认证中心：表单登录与用户管理

> **文件**：`authorizationServer/.../config/DefaultSecurityConfig.java`

```java
@Bean
public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
    UserDetails admin = User.builder()
            .username("admin")
            .password(passwordEncoder.encode("123456"))  // ← BCrypt 加密
            .roles("USER", "ADMIN")
            .build();
    // ...
    return new InMemoryUserDetailsManager(admin, user);
    //     ↑ 演示用内存存储，生产环境 → JdbcUserDetailsManager
}
```

```java
@Bean
@Order(2)
public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) {
    http
        .authorizeRequests(authorize ->
            authorize
                .antMatchers("/login", "/static/**").permitAll()
                .antMatchers("/oauth2/authorization/**").permitAll()
                .anyRequest().authenticated()
        )
        .formLogin(form ->
            form.loginPage("/login").permitAll()
            //      ↑ 自定义登录页面 → templates/login.html
        )
        .oauth2Login(oauth2Login ->          // ← 第三方 OAuth2 登录
            oauth2Login
                .loginPage("/login")
                .userInfoEndpoint()
                    .userService(thirdPartyUserService)
                    //    ↑ 自定义处理第三方返回的用户信息
        );
    return http.build();
}
```

#### 3.3.4 OIDC 客户端：如何接入 SSO

> **文件**：`oidc-client/.../config/ClientSecurityConfig.java`

这是整个项目中最容易理解的文件之一——客户端只需做两件事：

**第一件事：告诉框架"认证中心在哪里"**

```java
@Bean
public ClientRegistrationRepository clientRegistrationRepository() {
    ClientRegistration client = ClientRegistration
            .withRegistrationId("app-client")
            .clientId("app-client-1")               // 必须与认证中心注册的一致
            .clientSecret("secret1")                 // 必须一致
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            //           ↑ {baseUrl} 自动替换为 http://localhost:8081
            //           ↑ {registrationId} 自动替换为 "app-client"
            //           展开为：http://localhost:8081/login/oauth2/code/app-client
            .authorizationUri("http://localhost:8080/oauth2/authorize")  // 授权端点
            .tokenUri("http://localhost:8080/oauth2/token")              // 令牌端点
            .userInfoUri("http://localhost:8080/userinfo")               // 用户信息
            .jwkSetUri("http://localhost:8080/oauth2/jwks")              // 公钥端点
            .userNameAttributeName(IdTokenClaimNames.SUB) // OIDC 用户唯一标识
            .build();
    return new InMemoryClientRegistrationRepository(client);
}
```

**第二件事：启用 OAuth2 登录**

```java
@Bean
public SecurityFilterChain clientSecurityFilterChain(HttpSecurity http) {
    http
        .authorizeRequests(authorize ->
            authorize.anyRequest().authenticated()  // 所有请求需要登录
        )
        .oauth2Login(oauth2Login ->                // ← 一行代码，完成所有！
            oauth2Login.defaultSuccessUrl("/", true)
            // oauth2Login() 的背后：
            // 1. 自动创建 /oauth2/authorization/{id} → 发起 OAuth2 授权
            // 2. 自动创建 /login/oauth2/code/{id}    → 接收授权码
            // 3. 未登录时自动重定向到认证中心
            // 4. 收到 code 后自动用 client_secret 换 token
            // 5. 解析 id_token，提取 OidcUser
            // 6. 创建客户端本地 SecurityContext
        );
    return http.build();
}
```

**理解要点：**
- 客户端不需要自己写登录页面——用户被重定向到认证中心登录
- 客户端不需要写验证 code、换 token 的代码——`oauth2Login()` 全部自动化
- 对接新应用只需复制这段配置，改 `clientId` 和 `redirectUri`

#### 3.3.5 在客户端获取登录用户信息

> **文件**：`oidc-client/.../controller/HomeController.java`

```java
@GetMapping("/")
public String home(@AuthenticationPrincipal OidcUser principal, Model model) {
    // @AuthenticationPrincipal 注解 ← Spring Security 自动注入当前登录用户
    // OidcUser ← OIDC 登录用户的专属类型，包含 id_token 的所有信息

    model.addAttribute("subject", principal.getSubject());   // 用户唯一标识 (sub)
    model.addAttribute("name", principal.getFullName());     // 用户全名
    model.addAttribute("email", principal.getEmail());       // 邮箱
    model.addAttribute("idToken", principal.getIdToken().getTokenValue());
    //                               ↑ 原始 JWT 字符串，可用于调试
    model.addAttribute("claims", principal.getClaims());
    //                            ↑ 所有用户声明 (Map<String, Object>)
    return "home";
}
```

### 3.4 SSO 如何生效：Session 共享原理

```
第一次登录（应用A）：

  浏览器 ───→ 应用A (8081) ───→ 认证中心 (8080)
                                    │
                              ① 用户输入 admin/123456
                              ② 认证中心创建 Session
                                 浏览器保存 Cookie: JSESSIONID=abc123
                                    │
                              ③ 认证中心生成 code，302 回应用A
                                    │
  浏览器 ───→ 应用A (8081)
              ④ 带着 code 调用认证中心 POST /oauth2/token
              ⑤ 获得 access_token + id_token
              ⑥ 应用A 完成本地登录 ✓

第二次登录（应用B，无需输入密码！）：

  浏览器 ───→ 应用B (8082) ───→ 认证中心 (8080)
                                    │
                              ⑦ 浏览器自动带上 Cookie: JSESSIONID=abc123
                              ⑧ 认证中心检查 Session：已存在！用户是 admin
                              ⑨ 直接生成新 code（无需登录！），302 回应用B
                                    │
  浏览器 ───→ 应用B (8082)
              ⑩ 带着 code 调用认证中心 POST /oauth2/token
              ⑪ 获得 access_token + id_token
              ⑫ 应用B 完成本地登录 ✓  ← 用户完全没有输入密码！
```

**关键：** 步骤⑦⑧ 是整个 SSO 的魔法所在。浏览器在访问认证中心时自动带上了之前登录时获得的 Cookie（JSESSIONID），认证中心通过这个 Cookie 知道"哦，这是 admin，他已经登录过了"。

**Cookie 的同域要求：**
- 如果认证中心绑定在 `sso.example.com`，所有应用在 `app.example.com` 下，可以通过设置 Cookie domain 为 `.example.com` 实现共享
- 如果域名完全不同，Cookie 无法共享，需要使用票据(Ticket)模式

### 3.5 动手实验 2：验证 SSO 效果

> **实验目标**：亲眼看到"登录一次，两个应用都可用"

1. 在认证中心再注册一个客户端 `app-client-2`（已包含在 `AuthorizationServerConfig` 中）
2. 启动一个新客户端应用（可以复制 `oidc-client` 模块，改端口为 8082，client-id 改为 `app-client-2`）
3. 访问 `http://localhost:8081` → 被重定向到认证中心 → 登录
4. 访问 `http://localhost:8082` → 被重定向到认证中心 → **无需登录，自动返回！**
5. 观察 Network 面板：第二次访问认证中心时，浏览器请求中带上了 `Cookie: JSESSIONID=...`

### 3.6 动手实验 3：观察 OIDC 端点

> **实验目标**：直接访问认证中心的标准端点，理解它们的作用

启动方案1的认证中心后，在浏览器中访问：

| URL | 返回内容 | 说明 |
|-----|----------|------|
| `http://localhost:8080/.well-known/openid-configuration` | OIDC 元数据 JSON | 列出所有端点的完整 URL |
| `http://localhost:8080/oauth2/jwks` | JWK 公钥 JSON | 用于验证 JWT 签名的公钥信息 |
| `http://localhost:8080/userinfo` | 需要 Bearer token | 获取当前 token 对应的用户信息 |

---

## 第四部分：Sa-Token 方案（轻量级国产方案）

### 学习目标

- 理解 Ticket 票据模式的 SSO 原理
- 掌握 Sa-Token 的模式三（HTTP 请求模式）
- 理解表单向 Spring Authorization Server 方案的差异

### 4.1 核心差异：Ticket 替代 Cookie

Spring Authorization Server 方案依赖浏览器的 Cookie（JSESSIONID）实现"一次登录"，这在跨域场景下会失效。

Sa-Token 采用 **Ticket 票据** 机制绕过了 Cookie 依赖：

```
Spring Authorization Server 的 SSO：
  浏览器 ─→ 应用A ─→ 认证中心 ← 浏览器带 Cookie 证明已登录
                         ↑
                    依赖浏览器发送 Cookie

Sa-Token 模式三的 SSO：
  浏览器 ─→ 应用A ─→ 认证中心 → 签发 ticket → 302 回应用A
                         │
  应用A (后端) ─────────────────→ 认证中心 POST /sso/checkTicket
                         │         用 ticket 换用户信息
                     ← HTTP 返回用户信息
                         ↑
                后端到后端的 HTTP 通信，不依赖浏览器 Cookie！
```

### 4.2 Sa-Token 模式三完整流程图

```
┌──────────┐      ┌──────────────────┐      ┌──────────────────┐
│   浏览器   │      │   SSO Client      │      │   SSO Server     │
│           │      │  (客户端:8081)     │      │  (认证中心:8080)  │
└─────┬─────┘      └────────┬─────────┘      └────────┬─────────┘
      │                     │                         │
      │ ① 访问受保护页面       │                         │
      │ http://localhost:8081 │                         │
      │ ────────────────────>│                         │
      │                     │                         │
      │                     │ ② SaInterceptor 检测      │
      │                     │   未登录 → 302 重定向      │
      │                     │                         │
      │ ③ 浏览器跟随重定向     │                         │
      │ http://localhost:8080/sso/auth                │
      │ ──────────────────────────────────────────────>│
      │                     │                         │
      │                     │  ④ 展示登录页面            │
      │ <──────────────────────────────────────────────│
      │                     │                         │
      │ ⑤ 输入用户名/密码     │                         │
      │ POST /sso/doLogin   │                         │
      │ ──────────────────────────────────────────────>│
      │                     │                         │
      │                     │  ⑥ 验证凭证，生成登录态     │
      │                     │    StpUtil.login(userId) │
      │                     │    生成一次性 ticket       │
      │                     │    ("一次性"是指用完作废)   │
      │                     │                         │
      │ ⑦ 302 重定向         │                         │
      │ 客户端地址?ticket=abc234                       │
      │ <──────────────────────────────────────────────│
      │                     │                         │
      │ ⑧ 浏览器携带 ticket    │                         │
      │ 访问客户端            │                         │
      │ GET /sso/login       │                         │
      │   ?ticket=abc234     │                         │
      │ ────────────────────>│                         │
      │                     │                         │
      │                     │ ⑨ 客户端后端向认证中心发送   │
      │                     │   POST /sso/checkTicket  │
      │                     │   验证 ticket            │
      │                     │ ────────────────────────> │
      │                     │                         │
      │                     │ ⑩ 认证中心校验 ticket：    │
      │                     │   - ticket 是否存在？      │
      │                     │   - ticket 是否过期？     │
      │                     │   - ticket 签名是否正确？  │
      │                     │   校验通过 → 返回用户信息    │
      │                     │   ticket 立即作废！        │
      │                     │ <──────────────────────── │
      │                     │                         │
      │                     │ ⑪ 客户端完成本地登录        │
      │                     │    StpUtil.login(userId)  │
      │                     │                         │
      │ ⑫ 302 重定向到        │                         │
      │ 原始请求地址          │                         │
      │ <────────────────────│                         │
      │                     │                         │
      │ ⑬ 用户看到首页         │                         │
      │ 已登录状态 ✓          │                         │
```

### 4.3 代码深度解析

#### 4.3.1 认证中心配置文件

> **文件**：`saToken/src/main/resources/application.yml`

```yaml
sa-token:
  token-name: sso-token           # Token 名称 (Cookie 或 Header 中的 key)
  timeout: 259200                 # Token 有效期：3 天
  activity-timeout: -1            # 不限制无操作有效期 (-1 表示永久)
  is-concurrent: true             # 允许同一账号多处登录
  is-share: false                 # 模式三：每个客户端独立维护 token

  sso:
    enable: true                  # 启用 SSO 功能
    ticket-timeout: 300           # Ticket 有效期：5 分钟
    # ↑ Ticket 是临时凭证，设置较短有效期防止重放攻击
    
    allow-url: >
      http://localhost:8081/sso/callback,
      http://localhost:8082/sso/callback
    # ↑ 回调地址白名单，防止开放重定向漏洞
    
    is-http: true                 # 启用 HTTP 调用模式（模式三）
    is-slo: true                  # 启用单点注销
    auth-url: http://localhost:8080 # 认证中心地址
    is-v-mode: true               # 启用签名校验
    secret-key: my-sso-secret-key # SSO API 密钥（服务端间通信加密用）
```

#### 4.3.2 认证中心拦截器

> **文件**：`saToken/.../server/config/SaTokenServerConfig.java`

```java
@Configuration
public class SaTokenServerConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // SaInterceptor 内部逻辑：
                    //   1. 检查 StpUtil.isLogin() 是否已登录
                    //   2. 如果已登录 → 放行
                    //   3. 如果未登录 → 302 重定向到配置的 auth-url
                }))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/sso/**",      // ← 核心：放行 SSO 端点
                        //   这些端点负责处理登录，拦截会导致死循环：
                        //   访问 /sso/auth → 未登录 → 302 到 /sso/auth → ...
                        "/error", "/static/**", "/favicon.ico"
                );
    }
}
```

**关键理解：为什么要放行 `/sso/**`？**

```
如果 P 不放行 /sso/auth：
  用户访问 /sso/auth（登录页）
  → SaInterceptor: 未登录！
  → 302 到 /sso/auth（登录页）
  → SaInterceptor: 未登录！
  → 302 到 /sso/auth（登录页）
  → ... 无限循环（ERR_TOO_MANY_REDIRECTS）
```

#### 4.3.3 客户端配置

> **文件**：`sso-client/src/main/resources/application.yml`

```yaml
sa-token:
  sso:
    enable: true
    auth-url: http://localhost:8080
    # ↑ 客户端告诉 SaToken："我未登录时，把用户带去这个地址"
    ticket-timeout: 300
    is-http: true       # 使用模式三（后端 HTTP 校验 ticket）
    is-slo: true        # 参与单点注销
    is-v-mode: true     # 校验 ticket 签名
    secret-key: my-sso-secret-key  # 必须与认证中心一致
```

> **文件**：`sso-client/.../config/SsoClientConfig.java`

```java
@Configuration
public class SsoClientConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/sso/**",      // ← 同样放行 SSO 端点
                        //  其中 /sso/login 接收认证中心的重定向（携带 ticket）
                        "/error", "/static/**", "/favicon.ico"
                );
    }
}
```

#### 4.3.4 客户端获取登录用户

> **文件**：`sso-client/.../controller/HomeController.java`

```java
@GetMapping("/")
public String home(Model model) {
    String loginId = StpUtil.getLoginIdAsString();
    String tokenValue = StpUtil.getTokenValue();
    model.addAttribute("username", loginId);
    model.addAttribute("tokenValue", tokenValue);
    return "home";
}
```

### 4.4 Ticket 票据安全机制详解

```
Ticket 的生命周期（5 分钟，一次性使用）：

  时间 T0：用户登录认证中心
    ↓
  时间 T1：认证中心生成 ticket=abc123def456
          存储：ticket_map["abc123def456"] = {userId:"admin", expire:T0+300s}
    ↓
  时间 T2：302 重定向→ 浏览器收到 ticket
    ↓
  时间 T3：客户端后端携带 ticket POST → 认证中心 /sso/checkTicket
        → 认证中心查找：ticket_map["abc123def456"] → 找到！
        → 校验时间：T3 < T0+300s → 未过期！
        → 校验签名：使用 secret-key 验证 → 通过！
        → 返回用户信息：{loginId: "admin", token: "..."}
        → 从 ticket_map 删除 "abc123def456"  ← 一次性使用，立即作废！
    ↓
  时间 T4：攻击者尝试用同样的 ticket=abc123def456
        → 认证中心查找：ticket_map["abc123def456"] → 找不到！(已被删除)
        → 返回：ticket 无效 ❌
```

**多重安全机制：**

| 机制 | 说明 |
|------|------|
| **时效性** | ticket 默认 5 分钟过期，防止长期有效 |
| **一次性** | 使用后立即作废，防止重放攻击 |
| **签名校验** | 使用 secret-key 签名，防止伪造 ticket |
| **回调白名单** | `allow-url` 限制重定向目标，防止开放重定向 |
| **服务端通信** | 校验发生在后端，ticket 不在浏览器可读的 JSON 中暴露 |

### 4.5 动手实验 4：对比两个方案的网络请求

> **实验目标**：理解两种方案的网络请求差异

1. 启动方案2的认证中心和客户端
2. 打开浏览器开发者工具 → Network
3. 执行登录流程
4. 对比观察：
   - 方案1：`code=...` 参数在浏览器可见的一次重定向中
   - 方案2：`ticket=...` 参数在浏览器可见的 URL 中，但客户端通过后端 HTTP 验证

---

## 第五部分：两种方案深度对比

### 学习目标

- 理解两种方案的本质差异
- 掌握不同场景下的方案选型依据

### 5.1 架构对比

```
Spring Authorization Server (Session-Cookie 模式)
─────────────────────────────────────────────────
┌──────────────────────────────────────────┐
│              认证中心 (8080)               │
│  ┌────────────────────────────────────┐  │
│  │  用户登录 → 创建 Server Session     │  │
│  │  Session ID → Cookie (JSESSIONID)  │  │
│  │  返回 code → 客户端换 access_token  │  │
│  └────────────────────────────────────┘  │
│         ↑                           │    │
│    Cookie 验证              签发 Token   │
│         │                           ↓    │
│  ┌──────┴──────────────────────────┐     │
│  │         浏览器                   │     │
│  │  Cookie: JSESSIONID=abc         │     │
│  └──────┬──────────────────────────┘     │
│         │                                 │
│    ┌────┴────┐    ┌────────────┐          │
│    │ 应用 A   │    │  应用 B    │          │
│    │持有Token │    │ 持有Token  │          │
│    └─────────┘    └───────────┘           │
└──────────────────────────────────────────┘
SSO 关键：浏览器在访问认证中心时携带 Cookie 证明已登录


Sa-Token (Ticket 模式)
──────────────────────
┌──────────────────────────────────────────┐
│              认证中心 (8080)               │
│  ┌────────────────────────────────────┐  │
│  │  用户登录 → StpUtil.login(userId)   │  │
│  │  签发一次性 ticket                  │  │
│  │  ticket → URL 参数传回客户端         │  │
│  └────────────────────────────────────┘  │
│                ↕                          │
│         POST /sso/checkTicket             │
│      (后端 HTTP，不经过浏览器)              │
│                ↕                          │
│  ┌──────────────────────────────────┐    │
│  │         浏览器                     │    │
│  │  (无需携带认证中心 Cookie)         │    │
│  └──────┬──────────────┬────────────┘    │
│         │              │                  │
│    ┌────┴────┐    ┌────┴────┐            │
│    │ 应用 A   │    │  应用 B  │            │
│    │持有Token │    │ 持有Token │            │
│    └─────────┘    └───────────┘           │
└──────────────────────────────────────────┘
SSO 关键：客户端后端到认证中心的 HTTP 调用验证 ticket
```

### 5.2 特性对比表

| 对比维度 | Spring Authorization Server | Sa-Token |
|----------|----------------------------|----------|
| **SSO 机制** | Session-Cookie（浏览器 Cookie 证明登录态） | Ticket 票据（后端 HTTP 校验） |
| **协议标准** | OAuth 2.1 / OIDC 1.0（国际标准） | 自有协议 |
| **Token 格式** | JWT (RS256 签名) | 自有格式 |
| **依赖重量** | 较重（Spring Security + 多个 OAuth2 模块） | 较轻（Sa-Token 两个 jar 包） |
| **学习曲线** | 陡峭（需理解 OAuth2、OIDC、JWT、JWK 等） | 平缓（API 简洁，中文社区支持好） |
| **跨域支持** | 需要同一父域 Cookie，否则需额外处理 | 天然支持（后端 HTTP 通信） |
| **移动端支持** | 需实现 PKCE 流程 | 天然支持（HTTP 调用） |
| **第三方登录** | Spring Security OAuth2 Client 内置支持 | 需手动实现（本项目含完整示例） |
| **单点注销(SLO)** | 需自行实现 Back-Channel Logout | 内置支持（`is-slo: true`） |
| **权限控制** | Spring Security 体系 | Sa-Token 内置 RBAC |
| **分布式支持** | Spring Session + Redis | Sa-Token Redis 插件 |
| **社区生态** | Spring 生态，英文社区 | 国产框架，中文社区 |

### 5.3 方案选型决策树

```
你的 SSO 场景是？
│
├── 所有应用在同一父域名下 (app1.example.com, app2.example.com)
│   └── 推荐：Spring Authorization Server（架构标准，生态丰富）
│
├── 应用在不同域名下 (app1.com, app2.cn)
│   └── 推荐：Sa-Token 模式三（Ticket 不依赖 Cookie 跨域）
│
├── 需要对接第三方 OAuth2 应用（如让用户用 GitHub 登录你的系统）
│   └── 推荐：Spring Authorization Server（OAuth2 标准天然支持）
│
├── 移动端 App + Web 混合架构
│   └── 推荐：Sa-Token 模式三（HTTP 调用不依赖浏览器环境）
│
├── 快速开发、中小型项目
│   └── 推荐：Sa-Token（轻量、API 简洁、开箱即用）
│
├── 需要符合国际安全审计标准 (SOC2, ISO27001)
│   └── 推荐：Spring Authorization Server（基于 RFC 标准，审计友好）
│
└── 微服务架构、前后端分离
    └── 两者皆可：
        - Spring Auth Server：前端独立部署，后端 API 网关校验 JWT
        - Sa-Token：前后端分离可使用 @SaCheckLogin 注解校验
```

### 5.4 动手实验 5：理解核心差异

> **实验目标**：通过实验验证两种方案的跨域能力差异

1. 启动方案1，清除 cookie 后访问客户端
2. 启动方案2，清除 cookie 后访问客户端
3. 思考：如果把认证中心部署在 `sso.a.com`，客户端部署在 `app.b.com`（完全不同域名），哪个方案仍能正常工作？

**答案**：方案2（Sa-Token 模式三）能正常工作，因为客户端的后端直接通过 HTTP 调用认证中心校验 ticket，不依赖浏览器 Cookie 的同源策略。

---

## 第六部分：第三方 OAuth2 登录集成

### 学习目标

- 理解 SSO 认证中心提供第三方登录的架构
- 掌握标准 OAuth2 平台（GitHub）的集成
- 了解非标准平台（微信/QQ）的差异处理

### 6.1 架构模式：第三方 OAuth2 + SSO

```
用户 ──→ 应用A ──→ 认证中心
                      │
                      ├── 本地登录（用户名/密码）
                      │
                      └── 第三方登录
                            │
                      ┌─────┴─────┐
                      │ OAuth2 流程 │
                      └─────┬─────┘
                            │
              返回 openid/用户信息
                            │
                  建立 openid → 本地账号映射
                            │
                  ┌─────────┴─────────┐
                  │    StpUtil.login   │
                  │ 或创建 Session      │
                  └─────────┬─────────┘
                            │
                  SSO 流程继续（生成 ticket/code）
                            │
                      ──→ 回到应用A ✓
```

**关键理解**：第三方 OAuth2 登录只是认证中心多种登录方式之一。认证完成后，认证中心仍通过同样的 SSO 机制（ticket/code）将认证结果传递给业务应用。业务应用不关心用户用了哪种登录方式——这是 SSO 架构的优势。

### 6.2 Spring Authorization Server 方案中的第三方登录

> **文件**：`authorizationServer/.../config/DefaultSecurityConfig.java:106`

```java
.oauth2Login(oauth2Login ->
    oauth2Login
        .loginPage("/login")
        .userInfoEndpoint()
            .userService(thirdPartyUserService)
            //    ↑ 关键：自定义处理第三方返回的用户信息
)
```

Spring Security 内置了对 GitHub、Google、Facebook 等标准 OAuth2 平台的支持。只需在 `application.yml` 中配置即可：

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: your-github-client-id
            client-secret: your-github-client-secret
```

> **文件**：`authorizationServer/.../service/ThirdPartyUserService.java`

```java
@Service
public class ThirdPartyUserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // 获取平台标识 (github, wechat, qq 等)
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // 获取用户在第三方平台的唯一标识
        // GitHub 用 "id"，微信/QQ 用 "openid"
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String nameAttributeKey;

        if ("github".equals(registrationId)) {
            nameAttributeKey = "id";
        } else {
            nameAttributeKey = "openid";  // 微信/QQ 的标识字段
        }

        // 实际应用中，这里应该：
        // 1. 提取 openid/id
        // 2. 查询数据库中是否已有关联的本地用户
        // 3. 如果没有 → 自动注册一个本地用户
        // 4. 如果有 → 直接绑定现有用户
        // 5. 返回带有本地用户信息的 DefaultOAuth2User

        return new DefaultOAuth2User(
            oAuth2User.getAuthorities(),
            attributes,
            nameAttributeKey
        );
    }
}
```

### 6.3 Sa-Token 方案中的第三方登录（手动实现）

> **文件**：`saToken/.../server/auth/ThirdPartyAuthService.java`

这是项目中非常有学习价值的部分——展示了如何**手动实现完整的 OAuth2 客户端**，不依赖任何框架：

```java
@Service
public class ThirdPartyAuthService {

    /**
     * GitHub OAuth 认证（标准流程）
     */
    private Map<String, Object> authenticateGitHub(String code) throws IOException {
        // 步骤 1：用 code 换 access_token
        // POST https://github.com/login/oauth/access_token
        //   Body: client_id=xxx&client_secret=xxx&code=xxx
        //   Response (JSON): {"access_token":"gho_xxx","token_type":"bearer"}

        // 步骤 2：用 access_token 获取用户信息
        // GET https://api.github.com/user
        //   Header: Authorization: token gho_xxx
        //   Response (JSON): {"id":12345,"login":"octocat",...}

        // 步骤 3：提取关键信息
        result.put("openId", userJson.get("id").asText());     // GitHub 的用户 ID
        result.put("nickname", userJson.get("login").asText()); // GitHub 用户名
        return result;
    }

    /**
     * QQ OAuth 认证（非标准流程！）
     *
     * QQ 的 OAuth2 与标准协议有三处不同：
     * 1. Token 接口返回 URL 参数格式（不是 JSON！）
     *    响应：access_token=xxx&expires_in=7776000
     * 2. openid 接口返回 JSONP 格式（不是 JSON！）
     *    响应：callback( {"client_id":"xxx","openid":"xxx"} );
     * 3. 用户信息接口需要额外的 oauth_consumer_key 参数
     */
    private Map<String, Object> authenticateQQ(String code) throws IOException {
        // 步骤 1：用 code 换 access_token
        String tokenResponse = httpGet(tokenUrl);
        // 响应是 URL 参数格式！需要手动解析
        Map<String, String> tokenParams = parseQueryString(tokenResponse);
        String accessToken = tokenParams.get("access_token");

        // 步骤 2：用 access_token 获取 openid
        String openidResponse = httpGet(openidUrl);
        // 响应是 JSONP！需要手动提取 JSON
        String jsonStr = openidResponse.substring(
            "callback(".length(),
            openidResponse.length() - 3
        );
        JsonNode openidJson = OBJECT_MAPPER.readTree(jsonStr);

        // 步骤 3：用 access_token + openid 获取用户信息
        // ...
    }
}
```

### 6.4 各家 OAuth2 平台对比

| 平台 | 标准化程度 | Token 响应格式 | 用户信息接口 | 特别注意 |
|------|-----------|---------------|-------------|----------|
| **GitHub** | 标准 OAuth2 | JSON | GET /user (Bearer token) | 最规范，推荐首个学习 |
| **Google** | 标准 OAuth2 + OIDC | JSON | GET /userinfo (OIDC 标准) | 最标准，支持 OIDC |
| **微信开放平台** | 非标准 | JSON | 需 access_token + openid | 授权 URL 必须以 `#wechat_redirect` 结尾 |
| **QQ 互联** | 非标准 | URL 参数格式 (不是JSON) | 需先获取 openid (JSONP格式) | 三步流程：code → token → openid → userinfo |

> **经验**：学习第三方 OAuth2 集成时，从 **GitHub** 开始。它的实现最标准，理解了 GitHub 的流程后，微信/QQ 的"非标准"之处就很容易识别了。

### 6.5 动手实验 6：分析第三方登录的关键代码

> **实验目标**：真实理解手动 OAuth2 客户端的实现

1. 阅读 `ThirdPartyAuthService.java:138-168`（GitHub authenticate 方法）
2. 用纸笔画出 GitHub 的三步流程（code → token → userinfo）
3. 对比阅读 `ThirdPartyAuthService.java:254-307`（QQ authenticate 方法）
4. 用不同颜色的笔标出 QQ 流程中与标准 OAuth2 不同的地方

---

## 第七部分：生产环境进阶

### 学习目标

- 了解演示代码与生产代码的差异
- 掌握关键的安全加固措施
- 理解分布式场景下的扩展方案

### 7.1 演示代码 → 生产代码的改进清单

| 当前（演示代码） | 需要改为（生产代码） |
|-----------------|---------------------|
| 用户存储在内存 Map | 数据库/目录服务(LDAP) |
| Client Secret 明文 `{noop}secret1` | BCrypt 加密存储，或密钥管理服务(Vault) |
| JWK 密钥每次启动重新生成 | 从外部密钥管理系统加载，持久化密钥对 |
| HTTP（明文传输） | HTTPS（TLS 加密） |
| 无 Token 吊销机制 | 实现 Token 黑名单/吊销列表 |
| 无审计日志 | 记录所有登录/登出/授权事件 |
| 无并发登录控制 | 限制同一账号的最大同时登录数 |
| 无密码强度策略 | 实现密码复杂度校验和过期策略 |

### 7.2 关键安全加固

#### 7.2.1 Client Secret 的安全存储

```java
// ❌ 演示代码 - 明文存储
.clientSecret("{noop}secret1")

// ✅ 生产代码 - BCrypt 加密
.clientSecret("{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMye...")

// ✅ 更佳方案 - 从环境变量或密钥管理服务读取
.clientSecret(System.getenv("OAUTH_CLIENT_SECRET"))
```

#### 7.2.2 生产环境的 JWK 密钥管理

```java
// ❌ 演示代码 - 每次启动重新生成
public class Jwks {
    public static RSAKey generateRsa() {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        // ... 生成新密钥对
    }
}

// ✅ 生产代码 - 从外部加载或持久化
// 方案1：从 Keystore 加载
KeyStore ks = KeyStore.getInstance("JKS");
ks.load(new FileInputStream("sso-keystore.jks"), password);

// 方案2：从密钥管理服务获取 (AWS KMS, HashiCorp Vault)
RSAKey jwk = keyManagementService.getSigningKey("sso-signing-key");
```

> **为什么 JWK 密钥必须持久化？**
> - 如果重启后密钥变了，所有已签发的 JWT 都会失效
> - 所有客户端都会突然被"踢下线"，因为无法验证旧的 JWT 签名
> - 用户必须重新登录，造成大量客服投诉

#### 7.2.3 HTTPS 配置

```yaml
server:
  port: 8443
  ssl:
    key-store: classpath:sso-keystore.p12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```

生产环境 OAuth2 协议必须走 HTTPS，因为：
- `code` 参数在 URL 中经浏览器传输
- `token` 的交换请求包含 client_secret
- 明文 HTTP 下这些信息可被中间人截获

### 7.3 分布式场景扩展

单机模式下 Session/Token 存储在内存，重启即丢失。分布式部署需要共享存储：

```
Spring Authorization Server 分布式方案：

┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Auth Server  │     │ Auth Server  │     │ Auth Server  │
│  实例 1      │     │  实例 2      │     │  实例 3      │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       └───────────────────┼───────────────────┘
                           │
                    ┌──────┴──────┐
                    │    Redis    │
                    │ (共享存储)   │
                    │             │
                    │ - Session   │
                    │ - OAuth2    │
                    │  授权信息    │
                    │ - Token     │
                    └─────────────┘

依赖：Spring Session + spring-session-data-redis
```

```
Sa-Token 分布式方案：

只需添加一个依赖：
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-dao-redis-jackson</artifactId>
    <version>1.37.0</version>
</dependency>

其余代码无需任何修改，Sa-Token 自动将 Token 数据持久化到 Redis。
```

### 7.4 动手实验 7：安全性审查练习

> **实验目标**：从安全审计角度审视代码

1. 搜索项目中所有 "secret" 关键字，列出所有硬编码的密钥
2. 搜索 `{noop}`，理解这些明文的含义和风险
3. 检查 `allow-url` 白名单配置，确认是否足够严格
4. 思考：如果有人获取到了 `my-sso-secret-key`（Sa-Token 的 secret-key），会发生什么？

---

## 附录：术语速查表

| 术语 | 英文 | 解释 |
|------|------|------|
| 单点登录 | SSO (Single Sign-On) | 一次登录，多个系统共享认证状态 |
| 单点注销 | SLO (Single Logout) | 一次注销，所有系统同时退出 |
| 授权码 | Authorization Code | OAuth2 中的一次性凭证，用于换取 Token |
| 授权码模式 | Authorization Code Flow | OAuth2 中最安全的授权模式 |
| 访问令牌 | Access Token | 代表授权，用于访问受保护资源 |
| 身份令牌 | ID Token | OIDC 中代表身份认证的 JWT |
| 刷新令牌 | Refresh Token | 用于在 Access Token 过期后获取新的 |
| JSON Web Token | JWT | 三段式 Token 格式：Header.Payload.Signature |
| JSON Web Key | JWK | JWT 签名公钥的 JSON 表示 |
| 票据 | Ticket | Sa-Token 中的一次性凭证，用于 SSO 认证 |
| 会话 | Session | 服务端存储的"用户已登录"状态 |
| 声明 | Claims | JWT/OIDC 中关于用户的信息片段 |
| 客户端 | Client | 接入 SSO 的业务应用 |
| 认证中心 | Authorization Server / SSO Server | 统一处理登录的中央服务器 |
| PKCE | Proof Key for Code Exchange | 增强授权码模式安全性的扩展 |
| 开放重定向 | Open Redirect | 将用户重定向到攻击者指定的 URL 的漏洞 |
| CSRF | Cross-Site Request Forgery | 跨站请求伪造攻击 |

---

## 学习路线总结

```
第 1 天：理论篇
├── 阅读第一部分：SSO 核心概念
├── 理解 Session 和 Token 的区别
└── 理解 SSO 的两种实现机制（Cookie 共享 vs Ticket 传递）

第 2 天：Spring Authorization Server 方案
├── 阅读第三部分
├── 动手实验 1、2、3
├── 重点：理解授权码流程的 9 个步骤
└── 画出完整的时序图

第 3 天：Sa-Token 方案
├── 阅读第四部分
├── 动手实验 4
├── 重点：理解 Ticket 机制的 5 个安全措施
└── 对比两种方案的架构图

第 4 天：核心代码精读
├── 逐行阅读 AuthorizationServerConfig.java
├── 逐行阅读 ClientSecurityConfig.java
├── 逐行阅读 ThirdPartyAuthService.java
└── 用纸笔画出"代码调用链"（哪个类调哪个类）

第 5 天：综合实践与进阶
├── 动手实验 5、6、7
├── 阅读第五部分（方案对比）
├── 阅读第七部分（生产进阶）
└── 尝试修改代码：添加一个新客户端应用
```

---

## 推荐阅读资源

- [RFC 6749 - OAuth 2.0 授权框架](https://datatracker.ietf.org/doc/html/rfc6749)
- [OpenID Connect Core 1.0 规范](https://openid.net/specs/openid-connect-core-1_0.html)
- [Spring Authorization Server 参考文档](https://docs.spring.io/spring-authorization-server/reference/)
- [Sa-Token 官方文档 - SSO 集成](https://sa-token.cc/doc.html#/sso/sso-server)
- [OAuth 2.0 简化版图解](https://darutk.medium.com/diagrams-of-all-the-openid-connect-flows-6968e3990660)
