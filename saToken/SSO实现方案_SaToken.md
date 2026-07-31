# Sa-Token SSO 单点登录实现方案

## 一、方案概述

本方案基于国产安全框架 **Sa-Token** 构建单点登录（SSO）系统。Sa-Token 提供三种 SSO 模式，覆盖同域、跨域、前后端分离等各类场景。本项目以 **模式三（Http 请求调用）** 作为主要实现范例，因其架构清晰、适用面最广。

### 核心角色

| 角色 | 说明 | 对应模块 |
|------|------|----------|
| **SSO Server** | 认证中心，统一处理登录、注册、Token 颁发 | `saToken` (端口 8080) |
| **SSO Client** | 接入 SSO 的业务应用，委托认证中心完成认证 | `sso-client` (端口 8081) |
| **Ticket** | 临时凭证，客户端凭 ticket 到认证中心换取 Token | — |

---

## 二、技术选型

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 2.6.13 | 基础框架 |
| Sa-Token | 1.37.0+ | SSO 认证、会话管理、权限控制 |
| Sa-Token SSO Plugin | 对应版本 | SSO 单点登录插件 |
| Thymeleaf | - | 登录页面渲染 |
| Redis (可选) | - | 分布式会话共享 |

### Maven 依赖

```xml
<!-- Sa-Token 核心 -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-spring-boot-starter</artifactId>
    <version>1.37.0</version>
</dependency>

<!-- Sa-Token SSO 插件 -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-sso</artifactId>
    <version>1.37.0</version>
</dependency>

<!-- Sa-Token Redis 集成（分布式场景） -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-dao-redis-jackson</artifactId>
    <version>1.37.0</version>
</dependency>
```

---

## 三、项目包结构设计

### 认证中心（saToken 模块）

```
com.example.satoken.sso/
├── SsoConst.java                                   # SSO 常量 + 第三方平台参数
└── server/                                         # === SSO 认证中心 ===
    ├── SsoServerApp.java                           # Server 启动类 (端口 8080)
    ├── config/
    │   └── SaTokenServerConfig.java                # 路由拦截器配置
    ├── controller/
    │   └── SsoServerController.java                # 第三方登录页面 + OAuth 回调
    └── auth/
        ├── SsoServerAuthService.java               # 本地账号密码认证
        └── ThirdPartyAuthService.java              # GitHub/微信/QQ OAuth 手动实现
```

### SSO 客户端（sso-client 模块）

```
com.example.ssoclient/
├── SsoClientApplication.java                      # Client 启动类 (端口 8081)
├── config/
│   └── SsoClientConfig.java                        # SaInterceptor 拦截器
└── controller/
    └── HomeController.java                         # 首页（展示登录用户）
```

---

## 四、Sa-Token 三种 SSO 模式对比

| 模式 | 实现方式 | Cookie | 适用场景 | 复杂度 |
|------|----------|--------|----------|--------|
| **模式一** | Cookie 共享 + Redis | 同域 | 同域名下的多个子应用 | 低 |
| **模式二** | 重定向 + Ticket | 跨域 | 不同域名下的 Web 应用 | 中 |
| **模式三** | Http 请求转发 + Ticket | 跨域 | 前后端分离、跨域复杂场景 | 中 |

**本方案采用模式三**，因其架构清晰、客户端无需关心 Cookie 跨域问题，且最能体现 SSO 的核心原理。

---

## 五、模式三（Http 请求调用）认证流程图

```
┌──────────┐      ┌──────────────────┐      ┌──────────────────┐
│   浏览器   │      │   SSO Client      │      │   SSO Server     │
│           │      │  (客户端应用:8081)  │      │  (认证中心:8080)  │
└─────┬─────┘      └────────┬─────────┘      └────────┬─────────┘
      │                     │                         │
      │ ① 访问客户端受保护页面   │                         │
      │ ────────────────────>│                         │
      │                     │                         │
      │                     │ ② SaCheckLogin 拦截器     │
      │                     │   发现未登录              │
      │                     │                         │
      │ ③ 302 重定向        │                         │
      │   302 /sso/login?    │                         │
      │   redirect=原地址     │                         │
      │ <────────────────────│                         │
      │                     │                         │
      │ ④ 跳转到 SSO Server  │                         │
      │   GET /sso/login?     │                         │
      │   back=客户端回调地址   │                         │
      │ ──────────────────────────────────────────────>│
      │                     │                         │
      │                     │  ⑤ 返回登录页面            │
      │ <──────────────────────────────────────────────│
      │                     │                         │
      │ ⑥ POST /sso/doLogin │                         │
      │   用户名 + 密码        │                         │
      │ ──────────────────────────────────────────────>│
      │                     │                         │
      │                     │  ⑦ 验证凭证，生成 Ticket   │
      │                     │     SaToken 登录          │
      │                     │                         │
      │ ⑧ 302 重定向        │                         │
      │   ticket=xxxx       │                         │
      │ <──────────────────────────────────────────────│
      │                     │                         │
      │ ⑨ 携带 ticket 跳转到  │                         │
      │   客户端回调地址       │                         │
      │   GET /sso/callback?  │                         │
      │   ticket=xxxx        │                         │
      │ ────────────────────>│                         │
      │                     │                         │
      │                     │ ⑩ 客户端 Server 端        │
      │                     │   向 SSO Server 发 Http   │
      │                     │   请求验证 ticket         │
      │                     │   POST /sso/checkTicket  │
      │                     │ ────────────────────────>│
      │                     │                         │
      │                     │  ⑪ 返回校验结果           │
      │                     │   用户信息、session_id    │
      │                     │ <────────────────────────│
      │                     │                         │
      │                     │ ⑫ 客户端完成本地登录       │
      │                     │    302 到原始请求地址      │
      │                     │                         │
      │ ⑬ 302 最终页面       │                         │
      │ <────────────────────│                         │
      │                     │                         │
      │ ⑭ 访问客户端受保护页面  │                         │
      │    已登录，正常访问    │                         │
      │ ────────────────────>│                         │
```

---

## 六、SSO 认证中心（Server）核心实现要点

### 6.1 SaTokenSsoServerConfig — 核心配置

```java
@Configuration
public class SaTokenSsoServerConfig implements WebMvcConfigurer {

    /**
     * SSO Server 端配置初始化
     *
     * 关键配置项：
     * 1. ticketTimeout：Ticket 有效时间（默认 300 秒）
     *    Ticket 是临时凭证，设置较短的有效期防止重放攻击
     * 2. allowUrl：允许授权的回调地址白名单
     *    校验 client 传来的 back/redirect 参数，防止开放重定向漏洞
     * 3. isHttp：启用 Http 模式（模式三），服务端验证 ticket
     * 4. isSlo：启用单点注销（Single Logout），退出时通知所有客户端
     * 5. secretKey：SSO API 调用密钥，用于 Server 端接口鉴权
     */
    @Autowired
    public void configSsoServer(SaTokenConfig config) {
        config.setTokenName("sso-token");
        config.setTimeout(60 * 60 * 24);          // Token 有效期：1 天
        config.setActivityTimeout(-1);            // 永不过期
        config.setIsShare(false);                   // 模式三不需要共享 token
    }

    /**
     * SSO 核心参数配置
     * 通过 @Bean 注入，在 application.yml 中可覆盖默认值
     */
    // implementation: SaSsoServerTemplate 或 sa-token.sso 配置前缀
}
```

### 6.2 application.yml — Server 端配置

```yaml
server:
  port: 8080

spring:
  application:
    name: sso-server

# Sa-Token 配置
sa-token:
  token-name: sso-token
  timeout: 259200           # 3 天
  activity-timeout: -1      # 不限制无操作有效期
  is-share: false
  is-concurrent: true       # 允许同一账号多处登录

  # === SSO 配置 ===
  sso:
    # 启用 SSO 功能
    enable: true
    # Ticket 有效期（秒）
    ticket-timeout: 300
    # 允许的回调地址白名单（逗号分隔）
    allow-url: >
      http://localhost:8081/sso/callback,
      http://localhost:8082/sso/callback
    # 模式三：Http 请求调用
    is-http: true
    # 单点注销
    is-slo: true
    # 认证中心域名（用于拼接返回地址）
    auth-url: http://localhost:8080
    # 是否校验签名（模式三开启）
    is-v-mode: true
    # SSO Server API 密钥
    secret-key: my-sso-secret-key
```

### 6.3 SsoServerController — 登录页面控制器

```java
/**
 * SSO 认证中心核心控制器
 *
 * 职责：
 * 1. 提供登录页面（GET /sso/login）
 * 2. 处理登录请求（POST /sso/doLogin）
 * 3. 处理登出请求（GET /sso/logout）
 * 4. 提供 SSO API 接口（用于服务端间通信）
 *
 * SSO API 接口（模式三特有，供客户端服务端调用）：
 * - POST /sso/checkTicket  校验 ticket 并返回用户信息
 * - POST /sso/getData       获取用户详细信息
 * - GET  /sso/logout        单点注销（SLO）
 */
@Controller
public class SsoServerController {

    /**
     * 登录页面
     *
     * 接收参数：
     * - back：客户端回调地址（已在白名单中）
     *
     * 逻辑：
     * 1. 检查是否已有登录会话 → 若有，直接生成 ticket 并跳转
     * 2. 若无，展示登录表单
     */
    // @GetMapping("/sso/login")

    /**
     * 处理登录
     *
     * 接收参数：name, pwd, back
     *
     * 逻辑：
     * 1. 校验用户名密码（调用自定义认证服务）
     * 2. StpUtil.login(userId) 执行 SaToken 登录
     * 3. 生成 ticket 并重定向到 back 地址
     */
    // @PostMapping("/sso/doLogin")

    /**
     * API：校验 Ticket
     *
     * 此为服务端接口，由客户端服务端通过 Http 调用，不经过浏览器。
     * 客户端收到 ticket 后，使用 HttpUtil 或 RestTemplate 调用此接口。
     *
     * 返回：用户 session 信息，包含 loginId、tokenValue
     */
    // @PostMapping("/sso/checkTicket")
}
```

### 6.4 SsoServerAuthService — 自定义认证逻辑

```java
/**
 * 自定义认证服务
 *
 * 在此实现实际的用户名/密码校验逻辑：
 * - 数据库查询
 * - LDAP 认证
 * - 短信验证码
 * - 第三方 OAuth 等
 *
 * 示例实现：
 * 1. 模拟用户数据（Map 或数据库 DAO）
 * 2. 密码比对（BCrypt）
 * 3. 返回用户 ID
 */
@Service
public class SsoServerAuthService {

    // 模拟用户数据
    private static final Map<String, String> USERS = new HashMap<>();

    static {
        USERS.put("admin", "123456");
        USERS.put("user1", "123456");
    }

    /**
     * 校验用户名密码
     * @return 用户 ID（用于 StpUtil.login）
     */
    public String validate(String username, String password) {
        String pwd = USERS.get(username);
        if (pwd != null && pwd.equals(password)) {
            return username;  // 返回 userId
        }
        return null;
    }
}
```

---

## 七、SSO 客户端（Client）核心实现要点

### 7.1 SaTokenSsoClientConfig — 客户端配置

```java
@Configuration
public class SaTokenSsoClientConfig implements WebMvcConfigurer {

    /**
     * 注册 SaToken 路由拦截器
     *
     * 拦截所有需要认证的路由：
     * - 发现未登录 → 重定向到 SSO Server 登录页
     * - 放行 /sso/* 相关路由（避免死循环）
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            StpUtil.checkLogin();
        }))
        .addPathPatterns("/**")                          // 拦截所有
        .excludePathPatterns("/sso/**")                  // 排除 SSO 回调
        .excludePathPatterns("/static/**", "/error");    // 排除静态资源
    }
}
```

### 7.2 application.yml — Client 端配置

```yaml
server:
  port: 8081

spring:
  application:
    name: sso-client

# Sa-Token 客户端配置
sa-token:
  token-name: sso-token
  is-share: false          # 模式三：每个客户端独立维护 token

  # === SSO Client 配置 ===
  sso:
    # 启用 SSO
    enable: true
    # 认证中心地址
    auth-url: http://localhost:8080
    # Ticket 有效期（需与 Server 一致）
    ticket-timeout: 300
    # 启用 Http 模式（模式三）
    is-http: true
    # 单点注销
    is-slo: true
    # 是否校验签名
    is-v-mode: true
    # SSO API 密钥（需与 Server 一致）
    secret-key: my-sso-secret-key
    # 客户端路由配置
    # SSO 回调地址（本客户端）
    callback-url: /sso/callback
    # 默认跳转地址（登录后跳转）
    home-url: /home
```

### 7.3 SsoClientController — 客户端回调控制器

```java
/**
 * SSO 客户端核心控制器
 *
 * 职责：
 * 1. 接收 SSO Server 回调（GET /sso/callback?ticket=xxx）
 * 2. 处理登出回调（GET /sso/logoutCall）
 *
 * 模式三的 ticket 验证由 SaToken 框架自动完成：
 * - SaToken 拦截器检测到请求带了 ticket 参数
 * - 自动向 SSO Server 发起 Http 请求校验 ticket
 * - 校验通过后自动完成本地登录
 * - 重定向到原始访问地址
 *
 * 开发者几乎无需编写 ticket 校验代码，框架已封装！
 */
@Controller
public class SsoClientController {

    /**
     * SSO 回调
     *
     * 此路由对应 SSO Server 重定向后的地址。
     * ticket 作为参数附加在 URL 上。
     * SaToken 的 SaSsoClientProcessor 自动处理整个校验流程。
     */
    // @GetMapping("/sso/callback")

    /**
     * 单点注销回调
     *
     * SSO Server 通知本客户端有用户需要登出
     */
    // @GetMapping("/sso/logoutCall")
}
```

### 7.4 HomeController — 受保护的业务页面

```java
/**
 * 客户端首页（受保护页面）
 *
 * 访问此页面时：
 * 1. SaToken 拦截器检查登录状态
 * 2. 未登录 → 重定向到 SSO Server 登录
 * 3. 已登录 → 正常返回，可通过 StpUtil.getLoginId() 获取当前用户
 */
@Controller
public class HomeController {

    @GetMapping("/home")
    public String home(Model model, HttpSession session) {
        // 获取当前登录用户
        String loginId = StpUtil.getLoginIdAsString();
        model.addAttribute("username", loginId);
        model.addAttribute("tokenValue", StpUtil.getTokenValue());
        return "home";
    }
}
```

---

## 八、Ticket 机制详解

```
Ticket 是 SaToken SSO 的核心概念：

1. 目的
   Ticket 是一个临时一次性凭证，用于在 SSO Server 和 Client 之间
   安全地传递"用户已在认证中心登录"这一信息。

2. 生命周期
   ┌──────────────┐    ┌──────────────┐
   │ SSO Server    │    │ SSO Client   │
   │ 用户登录成功   │───>│ 收到 ticket  │
   │ 生成 ticket   │    │              │
   │              │<───│ 回传 ticket  │
   │ 校验 ticket  │    │              │
   │ ticket 作废  │───>│ 完成本地登录  │
   └──────────────┘    └──────────────┘

3. 安全机制
   - 有效期短（默认 5 分钟）
   - 一次性使用（校验后立即作废）
   - 签名校验（is-v-mode=true）
   - 回调地址白名单（allow-url）
   - SSO API 密钥保护服务端接口

4. 跨域问题的解决
   模式三通过服务端 Http 调用验证 ticket（而非浏览器 Cookie），
   天然规避了浏览器同源策略的限制，适合微服务和跨域场景。
```

---

## 九、单点注销（Single Logout）流程

```
   ┌──────────┐               ┌──────────────┐               ┌──────────┐
   │ 客户端 A  │               │  SSO Server  │               │ 客户端 B  │
   └─────┬─────┘               └──────┬───────┘               └─────┬─────┘
        │                             │                             │
        │ ① 用户点击退出               │                             │
        │ ──────────────────────────> │                             │
        │                             │                             │
        │                             │ ② 通知所有客户端登出             │
        │                             │────────────────────────────>│
        │                             │                             │
        │ ③ 各客户端清理本地 session    │                             │
        │                             │                             │
        │ ④ 统一跳转到登录页            │                             │
```

```java
/**
 * 客户端端触发退出：
 * 调用 SaSsoClientUtil.ssoLogout() 即可。
 * 框架自动完成：
 * 1. 通知 SSO Server 需要退出
 * 2. SSO Server 广播退出通知给所有已登录客户端
 * 3. 各客户端清理本地 token
 *
 * 配置关键：
 * sa-token.sso.is-slo = true  // 启用单点注销
 */
```

---

## 十、三种模式的选择建议

| 场景 | 推荐模式 | 理由 |
|------|----------|------|
| 同域名多子应用（如 a.example.com, b.example.com） | 模式一 | 最简单，共享 Cookie 即可 |
| 不同域名传统 Web 应用 | 模式二 | 重定向适配性最好 |
| 前后端分离 + 跨域微服务 | **模式三** | 架构最清晰，无 Cookie 依赖 |
| 移动端 App + Web 混合 | 模式三 | Http 调用不依赖浏览器 |

---

## 十一、文件清单

### SSO Server 模块

| 文件 | 说明 |
|------|------|
| `SsoServerApp.java` | SSO Server 启动类（端口 8080） |
| `config/SaTokenSsoServerConfig.java` | SaToken 全局配置、路由拦截器 |
| `controller/SsoServerController.java` | 登录页、登录接口、SSO API |
| `auth/SsoServerAuthService.java` | 用户名/密码校验服务 |
| `application.yml` | Server 端口、SSO 参数配置 |
| `templates/login.html` | 登录页面模板（Thymeleaf） |
| `templates/home.html` | 认证中心首页（已登录状态展示） |

### SSO Client 模块

| 文件 | 说明 |
|------|------|
| `SsoClientApp.java` | SSO Client 启动类（端口 8081/8082） |
| `config/SaTokenSsoClientConfig.java` | SaToken 客户端配置、路由拦截 |
| `controller/HomeController.java` | 受保护的首页 |
| `interceptor/SsoClientInterceptor.java` | SSO 登录拦截器 |
| `application.yml` | Client 端口、SSO 参数配置 |
| `templates/home.html` | 客户端首页（登录后可见） |

### 公共模块

| 文件 | 说明 |
|------|------|
| `SsoConst.java` | 常量定义（SSO Server URL、密钥等） |

---

## 十二、参考资源

- [Sa-Token 官方文档 - SSO 单点登录](https://sa-token.cc/doc.html#/sso/sso-server)
- [Sa-Token Gitee 仓库](https://gitee.com/dromara/sa-token)
- [Sa-Token SSO 三种模式对比](https://sa-token.cc/doc.html#/sso/sso-type)
