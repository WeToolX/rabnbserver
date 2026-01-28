# Getting Started

## 项目结构（Tree）

```
rabnbserver
├── pom.xml                           # Maven 依赖与构建配置
├── src
│  ├── main
│  │  ├── java
│  │  │  └── com/ra/rabnbserver
│  │  │     ├── RabnbserverApplication.java         # 应用入口
│  │  │     ├── advice
│  │  │     │  └── ResponseEncryptAdvice.java       # 响应加密拦截
│  │  │     ├── controller
│  │  │     │  ├── AuthMockController.java          # 鉴权测试接口
│  │  │     │  └── UserInitController.java          # 用户初始化接口
│  │  │     ├── db
│  │  │     │  └── DatabaseInitService.java         # 数据库初始化与表结构维护
│  │  │     ├── config
│  │  │     │  ├── CorsConfig.java                  # 全局跨域配置
│  │  │     │  └── SaTokenConfig.java               # Sa-Token 鉴权配置
│  │  │     ├── crypto
│  │  │     │  ├── CryptoConstants.java             # 加解密常量
│  │  │     │  ├── CryptoEnvelope.java              # 密文封装与解析
│  │  │     │  ├── CryptoUtils.java                 # 加解密基础工具
│  │  │     │  ├── MorseCodec.java                  # Base64 -> 自定义摩斯
│  │  │     │  ├── RequestCryptoService.java        # 请求解密与验签
│  │  │     │  └── ResponseCryptoService.java       # 响应加密与摩斯编码
│  │  │     ├── filter
│  │  │     │  ├── CachedBodyHttpServletRequest.java # 可重复读取请求体
│  │  │     │  └── CryptoRequestFilter.java         # 请求体解密过滤器
│  │  │     ├── dto
│  │  │     │  └── AuthMockRequest.java             # 测试接口入参
│  │  │     ├── model
│  │  │     │  └── ApiResponse.java                 # 统一响应结构
│  │  │     ├── security
│  │  │     │  └── TokenExtractor.java              # Sa-Token 请求头提取
│  │  │     └── utils
│  │  │        └── RandomIdGenerator.java           # 随机 ID 生成
│  │  └── resources
│  │     ├── application.yaml                       # 应用配置
│  │     └── logback-spring.xml                      # 日志配置（按大小+日期滚动）
│  └── test
│     └── java
└── HELP.md
```

## 架构摘要

- 请求流程：CryptoRequestFilter 解密 -> Sa-Token 拦截器校验登录 -> 业务 Controller
- 响应流程：Controller 返回 String -> ResponseEncryptAdvice 统一封装“明文/密文”（登录态才加密）
- Sa-Token：token-name 为 Account-token，/api/user/init 负责登录并返回 token + Key
- 鉴权策略：放行 /api/user/init、/api/user/login、/api/user/register 与 OPTIONS 预检，其余接口需登录（由 Sa-Token 拦截器统一处理）
- 加解密：使用 Sa-Token 的 token 参与加解密 key 派生与响应加密
- 数据源：已启用 Spring Boot 数据源自动装配，使用 application.yaml 中的 MySQL 配置

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
