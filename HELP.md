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
│  │  │     ├── annotation                          # 表结构注解（字段/索引/注释）
│  │  │     ├── common
│  │  │     │  ├── BaseEntity.java                  # 通用基础实体
│  │  │     │  └── Result.java                      # 通用返回结构
│  │  │     ├── config
│  │  │     │  ├── CorsConfig.java                  # 全局跨域配置
│  │  │     │  ├── DatabaseInitConfig.java          # 初始化建表配置
│  │  │     │  ├── MyMetaObjectHandler.java         # MyBatis-Plus 自动填充
│  │  │     │  ├── MybatisPlusConfig.java           # MyBatis-Plus 配置
│  │  │     │  ├── SaTokenConfig.java               # Sa-Token 鉴权配置
│  │  │     │  ├── StpInterfaceImpl.java            # Sa-Token 权限实现
│  │  │     │  ├── ThreadPoolTaskExecutorConfig.java # 线程池配置
│  │  │     │  └── TimeZoneConfig.java              # 全局时区配置（Asia/Shanghai）
│  │  │     ├── controller
│  │  │     │  ├── AdminCommonController.java       # 后台通用接口
│  │  │     │  ├── AdminRbacController.java         # 后台权限接口
│  │  │     │  ├── AuthMockController.java          # 鉴权测试接口
│  │  │     │  ├── CommonController.java            # 公共接口
│  │  │     │  ├── test
│  │  │     │  │  └── TestController.java           # 异常框架测试接口
│  │  │     │  ├── bill
│  │  │     │  │  └── AdminBillController.java      # 后台账单接口
│  │  │     │  ├── card
│  │  │     │  │  ├── admin
│  │  │     │  │  │  └── AdminEtfCardController.java # 后台卡牌管理
│  │  │     │  │  └── user
│  │  │     │  │     └── EtfCardController.java     # 卡牌购买/查询
│  │  │     │  ├── sys
│  │  │     │  │  ├── AdminContractController.java  # 合约管理接口
│  │  │     │  │  └── SystemConfigController.java   # 系统配置接口
│  │  │     │  └── user
│  │  │     │     ├── AdminUserController.java      # 后台用户接口
│  │  │     │     ├── MinerController.java          # 矿机业务接口
│  │  │     │     └── UserController.java           # 用户接口（登录/充值等）
│  │  │     ├── contract
│  │  │     │  ├── AionContract.java                # AION 管理员合约调用
│  │  │     │  ├── CardNftContract.java             # 卡牌合约管理员调用
│  │  │     │  ├── PaymentUsdtContract.java         # USDT 收款合约管理员调用
│  │  │     │  ├── service
│  │  │     │  │  └── AionService.java              # AION 业务查询（可流通量/兑换记录）
│  │  │     │  └── support
│  │  │     │     ├── AmountConvertUtils.java       # 金额转换工具（最小单位 -> 人类可读）
│  │  │     │     ├── ContractBase.java             # 合约调用基座
│  │  │     │     ├── ContractTypeUtils.java        # ABI 参数类型转换
│  │  │     │     ├── BlockchainProperties.java     # 链配置
│  │  │     │     ├── ContractAdminProperties.java  # 管理员私钥密文配置
│  │  │     │     ├── ContractAddressProperties.java# 合约地址配置
│  │  │     │     ├── PrivateKeyCryptoService.java  # 私钥加解密
│  │  │     │     └── Web3jConfig.java              # Web3j 配置
│  │  │     ├── crypto
│  │  │     │  ├── CryptoConstants.java             # 加解密常量
│  │  │     │  ├── CryptoEnvelope.java              # 密文封装与解析
│  │  │     │  ├── CryptoUtils.java                 # 加解密基础工具
│  │  │     │  ├── MorseCodec.java                  # Base64 -> 自定义摩斯
│  │  │     │  ├── RequestCryptoService.java        # 请求解密与验签
│  │  │     │  └── ResponseCryptoService.java       # 响应加密与摩斯编码
│  │  │     ├── db
│  │  │     │  └── DatabaseInitService.java         # 数据库初始化与表结构维护
│  │  │     ├── dto                                 # 请求 DTO（登录/账单/购买/配置等）
│  │  │     ├── enums                               # 账单/订单/状态等枚举
│  │  │     ├── exception
│  │  │     │  ├── BusinessException.java           # 业务异常
│  │  │     │  ├── GlobalExceptionHandler.java      # 全局异常处理
│  │  │     │  └── Abnormal
│  │  │     │     ├── annotation
│  │  │     │     │  └── AbnormalRetryConfig.java   # 异常重试注解
│  │  │     │     ├── core
│  │  │     │     │  ├── AbnormalContext.java       # 异常重试上下文
│  │  │     │     │  ├── AbnormalMailService.java   # 异常通知邮件
│  │  │     │     │  ├── AbnormalRecord.java        # 异常记录模型
│  │  │     │     │  ├── AbnormalRetryHandler.java  # 业务处理接口
│  │  │     │     │  ├── AbnormalRetryManager.java  # 框架核心管理器
│  │  │     │     │  ├── AbnormalRetryProperties.java # 框架配置
│  │  │     │     │  ├── AbnormalRetryScheduler.java # 轮询调度
│  │  │     │     │  └── AbstractAbnormalRetryService.java # 业务基类
│  │  │     │     └── model
│  │  │     │        └── AbnormalBaseEntity.java    # 异常字段基类
│  │  │     ├── filter
│  │  │     │  ├── CachedBodyHttpServletRequest.java # 可重复读取请求体
│  │  │     │  └── CryptoRequestFilter.java         # 请求体解密过滤器
│  │  │     ├── mapper                              # MyBatis-Plus Mapper
│  │  │     ├── model
│  │  │     │  └── ApiResponse.java                 # 统一响应结构
│  │  │     ├── pojo                                # 业务实体（用户/账单/卡牌/矿机等）
│  │  │     ├── security
│  │  │     │  └── TokenExtractor.java              # Sa-Token 请求头提取
│  │  │     ├── server
│  │  │     │  ├── admin                            # 后台用户/角色/权限服务
│  │  │     │  ├── card                             # 卡牌业务服务
│  │  │     │  ├── miner                            # 矿机业务服务
│  │  │     │  ├── sys                              # 系统配置服务
│  │  │     │  ├── test                             # 异常框架示例服务
│  │  │     │  └── user                             # 用户/账单服务
│  │  │     ├── utils
│  │  │     │  └── RandomIdGenerator.java           # 随机 ID 生成
│  │  │     └── VO                                  # 返回 VO
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
- 合约调用：合约类在 contract 包，依赖基座与配置在 contract/support
- CardNFT：单 ID（id=1）合约，提供余额/销毁/供应量查询与分发、销毁校验流程
- 回执轮询：使用 blockchain.tx-poll-interval-ms 与 blockchain.tx-timeout-ms 控制轮询间隔与超时
- 主币符号：blockchain.currency-symbol 用于前端展示（如 tBNB）
- 合约地址校验：contract.address.* 启动时校验 0x 地址格式并打印配置
- 生产配置：prod profile 关闭 springdoc 的 api-docs 与 swagger-ui
- 合约返回说明：交易方法返回 TransactionReceipt，并在方法注释中给出 JSON 字段示例与含义
- 测试策略：Maven 默认跳过测试（skipTests=true），如需执行请用 -DskipTests=false；IDE 手动运行不受影响
- USDT 精度：统一使用 18 位（AmountConvertUtils.Currency.USDT=18）
- PaymentUSDT：minAmount 可配置（链上读取 minAmount()）
- 异常重试框架：扫描 @AbnormalRetryConfig，自动补齐异常字段并定时轮询，行锁使用 FOR UPDATE SKIP LOCKED，支持自动重试/人工通知/err_manual_notify_count，超时或达上限会升级人工并持续通知直至人工处理成功（人工提醒间隔线性递增）
- 邮件配置：application.yaml 中 spring.mail.* 与 abnormal.retry.* 控制通知
- 时区统一：JVM/Jackson/JDBC/日志均使用 Asia/Shanghai
- 调试日志：异常重试框架包（com.ra.rabnbserver.exception.Abnormal）默认 DEBUG 输出
- 异常自愈：err_start_time 为空会自动补当前时间，业务状态成功但 err_status 未同步会自动修复为 2001

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
