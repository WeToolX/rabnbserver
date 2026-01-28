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
│  │  │     │  └── UserInitController.java          # 用户初始化/登录接口
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
│  │  │     │  └── CryptoRequestFilter.java         # 请求体解密与初始化处理
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

- 请求流程：/api/user/init 先进入 Controller 生成明文与登录态，再由 CryptoRequestFilter 进行密文输出；其余接口按内容类型解密后进入 Controller
- 响应流程：Controller 返回 String 后由 ResponseEncryptAdvice 直接返回密文字符串（/admin/** 与 /api/user/init 返回明文）
- Sa-Token：token-name 为 Account-token，初始化登录在 Controller 完成，密文在拦截器生成
- 鉴权策略：放行 /api/user/init、/api/user/login、/api/user/register、/admin/** 与 OPTIONS 预检，其余接口需登录
- 加解密：/admin/** 不做请求解密与响应加密，其余接口基于 Sa-Token token 参与 key 派生
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
