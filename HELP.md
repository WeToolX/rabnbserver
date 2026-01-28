# Getting Started

## 项目结构（Tree）

```
rabnbserver
├─ pom.xml                           # Maven 依赖与构建配置
├─ src
│  ├─ main
│  │  ├─ java
│  │  │  └─ com/ra/rabnbserver
│  │  │     ├─ RabnbserverApplication.java         # 应用入口
│  │  │     ├─ advice
│  │  │     │  └─ ResponseEncryptAdvice.java       # 响应加密拦截
│  │  │     ├─ controller
│  │  │     │  ├─ AuthMockController.java          # 鉴权测试接口
│  │  │     │  └─ UserInitController.java          # 用户初始化接口
│  │  │     ├─ config
│  │  │     │  ├─ CorsConfig.java                  # 全局跨域配置
│  │  │     │  └─ SecurityConfig.java              # 安全链路配置（解密 + JWT）
│  │  │     ├─ crypto
│  │  │     │  ├─ CryptoConstants.java             # 加解密常量
│  │  │     │  ├─ CryptoEnvelope.java              # 密文包裹解析/封装
│  │  │     │  ├─ CryptoUtils.java                 # 加解密基础工具
│  │  │     │  ├─ MorseCodec.java                  # Base64->自定义摩斯
│  │  │     │  ├─ RequestCryptoService.java        # 请求解密与验签
│  │  │     │  └─ ResponseCryptoService.java       # 响应加密与摩斯编码
│  │  │     ├─ filter
│  │  │     │  ├─ CachedBodyHttpServletRequest.java # 可重复读取请求体
│  │  │     │  └─ CryptoRequestFilter.java         # 请求体解密过滤器
│  │  │     ├─ dto
│  │  │     │  └─ AuthMockRequest.java             # 测试接口入参
│  │  │     ├─ model
│  │  │     │  └─ ApiResponse.java                 # 统一响应结构
│  │  │     ├─ security
│  │  │        ├─ JwtAuthenticationFilter.java     # JWT 鉴权过滤器
│  │  │        ├─ JwtProperties.java               # JWT 配置属性
│  │  │        ├─ JwtService.java                  # JWT 生成与解析
│  │  │        ├─ RestAccessDeniedHandler.java     # 无权限响应
│  │  │        ├─ RestAuthenticationEntryPoint.java# 未登录响应
│  │  │        ├─ TokenCleanupScheduler.java       # 定时清理过期 token
│  │  │        ├─ TokenExtractor.java              # 请求头 Token 提取
│  │  │        └─ TokenStore.java                  # 内存白名单
│  │  │     └─ utils
│  │  │        └─ RandomIdGenerator.java           # 随机ID生成
│  │  └─ resources
│  │     ├─ application.properties                # 应用配置
│  │     └─ logback-spring.xml                    # 日志配置（按大小+日期滚动）
│  └─ test
│     └─ java
└─ HELP.md
```

## 架构摘要

- 请求流程：CryptoRequestFilter 解密 -> Sa-Token 校验登录态 -> 业务 Controller
- 响应流程：Controller 返回 String -> ResponseEncryptAdvice 统一封装“明文+密文”（已登录生成密文）
- Sa-Token：token-name 为 Account-token，初始化接口写入响应头
- 白名单：内存缓存模块保留但当前未启用（改用 Sa-Token 登录态）
- 初始化：/api/user/init 生成 token + Key，返回加密后的密文字符串
- 测试接口：/auth/mock 需要 JWT，返回 subject 与 userId
- 加解密：沿用旧协议（包裹结构、MD5 逻辑、盐值 456787415adfdfsdf）
- 数据源：当前默认禁用自动装配，需数据库时再开启并配置连接

### Reference Documentation

For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.2/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.0.2/maven-plugin/build-image.html)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.0.2/reference/using/devtools.html)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the
parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.
