# Getting Started

## 项目结构（完整 Tree）

```
rabnbserver
├── .idea
│   ├── dictionaries
│   │   └── project.xml
│   ├── inspectionProfiles
│   │   └── Project_Default.xml
│   ├── .gitignore
│   ├── ApifoxUploaderProjectSetting.xml
│   ├── compiler.xml
│   ├── encodings.xml
│   ├── jarRepositories.xml
│   ├── misc.xml
│   ├── vcs.xml
│   └── workspace.xml
├── .mvn
│   └── wrapper
│       └── maven-wrapper.properties
├── Document
│   ├── air.md
│   ├── AiRWord_v3.sol
│   ├── card_v2.md
│   └── card_v2.sol
├── logs
│   ├── app.2026-02-04.0.log.gz
│   ├── app.2026-02-05.0.log.gz
│   ├── app.2026-02-05.1.log.gz
│   ├── app.2026-02-06.0.log.gz
│   ├── app.2026-02-07.0.log.gz
│   ├── app.2026-02-08.0.log.gz
│   ├── app.2026-02-09.0.log.gz
│   ├── app.2026-02-10.0.log.gz
│   ├── app.log
│   └── warn-error.log
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── ra
│   │   │           └── rabnbserver
│   │   │               ├── advice
│   │   │               │   └── ResponseEncryptAdvice.java
│   │   │               ├── annotation
│   │   │               │   ├── ColumnComment.java
│   │   │               │   ├── ColumnType.java
│   │   │               │   ├── DefaultValue.java
│   │   │               │   ├── ForeignKey.java
│   │   │               │   ├── ForeignKeys.java
│   │   │               │   ├── Index.java
│   │   │               │   ├── Indexes.java
│   │   │               │   └── TableComment.java
│   │   │               ├── common
│   │   │               │   ├── BaseEntity.java
│   │   │               │   └── Result.java
│   │   │               ├── config
│   │   │               │   ├── CorsConfig.java
│   │   │               │   ├── DatabaseInitConfig.java
│   │   │               │   ├── MybatisPlusConfig.java
│   │   │               │   ├── MyMetaObjectHandler.java
│   │   │               │   ├── SaTokenConfig.java
│   │   │               │   ├── StpInterfaceImpl.java
│   │   │               │   ├── ThreadPoolTaskExecutorConfig.java
│   │   │               │   └── TimeZoneConfig.java
│   │   │               ├── contract
│   │   │               │   ├── service
│   │   │               │   │   └── AionService.java
│   │   │               │   ├── support
│   │   │               │   │   ├── AmountConvertUtils.java
│   │   │               │   │   ├── BlockchainProperties.java
│   │   │               │   │   ├── ContractAddressProperties.java
│   │   │               │   │   ├── ContractAdminProperties.java
│   │   │               │   │   ├── ContractBase.java
│   │   │               │   │   ├── ContractTypeUtils.java
│   │   │               │   │   ├── PrivateKeyCryptoService.java
│   │   │               │   │   └── Web3jConfig.java
│   │   │               │   ├── AionContract.java
│   │   │               │   ├── CardNftContract.java
│   │   │               │   └── PaymentUsdtContract.java
│   │   │               ├── controller
│   │   │               │   ├── abnormal
│   │   │               │   │   └── AdminAbnormalController.java
│   │   │               │   ├── bill
│   │   │               │   │   └── AdminBillController.java
│   │   │               │   ├── card
│   │   │               │   │   ├── admin
│   │   │               │   │   │   └── AdminEtfCardController.java
│   │   │               │   │   └── user
│   │   │               │   │       └── EtfCardController.java
│   │   │               │   ├── sys
│   │   │               │   │   ├── AdminContractController.java
│   │   │               │   │   └── SystemConfigController.java
│   │   │               │   ├── test
│   │   │               │   │   └── TestController.java
│   │   │               │   ├── user
│   │   │               │   │   ├── AdminUserController.java
│   │   │               │   │   ├── MinerController.java
│   │   │               │   │   └── UserController.java
│   │   │               │   ├── AdminCommonController.java
│   │   │               │   ├── AdminRbacController.java
│   │   │               │   ├── AuthMockController.java
│   │   │               │   └── CommonController.java
│   │   │               ├── crypto
│   │   │               │   ├── CryptoConstants.java
│   │   │               │   ├── CryptoEnvelope.java
│   │   │               │   ├── CryptoUtils.java
│   │   │               │   ├── MorseCodec.java
│   │   │               │   ├── RequestCryptoService.java
│   │   │               │   └── ResponseCryptoService.java
│   │   │               ├── db
│   │   │               │   └── DatabaseInitService.java
│   │   │               ├── dto
│   │   │               │   ├── adminMinerAction
│   │   │               │   │   ├── AdminMinerActionDTO.java
│   │   │               │   │   └── FragmentExchangeNftDTO.java
│   │   │               │   ├── contract
│   │   │               │   │   └── TreasuryDTO.java
│   │   │               │   ├── AbnormalQueryDTO.java
│   │   │               │   ├── AdminAmountRequestDTO.java
│   │   │               │   ├── AdminBillQueryDTO.java
│   │   │               │   ├── AdminUserLoginDTO.java
│   │   │               │   ├── AmountRequestDTO.java
│   │   │               │   ├── AuthMockRequest.java
│   │   │               │   ├── BillQueryDTO.java
│   │   │               │   ├── DistributeNftDTO.java
│   │   │               │   ├── EtfCardQueryDTO.java
│   │   │               │   ├── LoginDataDTO.java
│   │   │               │   ├── MinerAccelerationDTO.java
│   │   │               │   ├── MinerElectricityDTO.java
│   │   │               │   ├── MinerProfitRecordQueryDTO.java
│   │   │               │   ├── MinerPurchaseDTO.java
│   │   │               │   ├── MinerQueryDTO.java
│   │   │               │   ├── NFTPurchaseDTO.java
│   │   │               │   ├── RegisterDataDTO.java
│   │   │               │   ├── TeamQueryDTO.java
│   │   │               │   ├── UserQueryDTO.java
│   │   │               │   └── UserUpdateDTO.java
│   │   │               ├── enums
│   │   │               │   ├── AbnormalManualStatus.java
│   │   │               │   ├── AbnormalStatus.java
│   │   │               │   ├── BaseEnum.java
│   │   │               │   ├── BillType.java
│   │   │               │   ├── ForeignKeyAction.java
│   │   │               │   ├── FundType.java
│   │   │               │   ├── IndexType.java
│   │   │               │   ├── MinerType.java
│   │   │               │   ├── OrderType.java
│   │   │               │   ├── TransactionStatus.java
│   │   │               │   └── TransactionType.java
│   │   │               ├── exception
│   │   │               │   ├── Abnormal
│   │   │               │   │   ├── annotation
│   │   │               │   │   │   └── AbnormalRetryConfig.java
│   │   │               │   │   ├── core
│   │   │               │   │   │   ├── AbnormalContext.java
│   │   │               │   │   │   ├── AbnormalMailService.java
│   │   │               │   │   │   ├── AbnormalManualController.java
│   │   │               │   │   │   ├── AbnormalManualEndpointRegistrar.java
│   │   │               │   │   │   ├── AbnormalManualRouteInfo.java
│   │   │               │   │   │   ├── AbnormalManualRouteRegistry.java
│   │   │               │   │   │   ├── AbnormalRecord.java
│   │   │               │   │   │   ├── AbnormalRetryHandler.java
│   │   │               │   │   │   ├── AbnormalRetryManager.java
│   │   │               │   │   │   ├── AbnormalRetryProperties.java
│   │   │               │   │   │   ├── AbnormalRetryScheduler.java
│   │   │               │   │   │   └── AbstractAbnormalRetryService.java
│   │   │               │   │   └── model
│   │   │               │   │       └── AbnormalBaseEntity.java
│   │   │               │   ├── AionContractException.java
│   │   │               │   ├── BusinessException.java
│   │   │               │   ├── ChainCallException.java
│   │   │               │   ├── ContractCallException.java
│   │   │               │   └── GlobalExceptionHandler.java
│   │   │               ├── filter
│   │   │               │   ├── CachedBodyHttpServletRequest.java
│   │   │               │   └── CryptoRequestFilter.java
│   │   │               ├── mapper
│   │   │               │   ├── AdminPermissionMapper.java
│   │   │               │   ├── AdminRoleMapper.java
│   │   │               │   ├── AdminRolePermissionMapper.java
│   │   │               │   ├── AdminUserMapper.java
│   │   │               │   ├── EtfCardMapper.java
│   │   │               │   ├── MinerProfitRecordMapper.java
│   │   │               │   ├── SystemConfigMapper.java
│   │   │               │   ├── UserBillMapper.java
│   │   │               │   ├── UserMapper.java
│   │   │               │   └── UserMinerMapper.java
│   │   │               ├── model
│   │   │               │   └── ApiResponse.java
│   │   │               ├── pojo
│   │   │               │   ├── AdminPermission.java
│   │   │               │   ├── AdminRole.java
│   │   │               │   ├── AdminRolePermission.java
│   │   │               │   ├── AdminUser.java
│   │   │               │   ├── ETFCard.java
│   │   │               │   ├── MinerProfitRecord.java
│   │   │               │   ├── SystemConfig.java
│   │   │               │   ├── TestPojo.java
│   │   │               │   ├── User.java
│   │   │               │   ├── UserBill.java
│   │   │               │   └── UserMiner.java
│   │   │               ├── security
│   │   │               │   └── TokenExtractor.java
│   │   │               ├── server
│   │   │               │   ├── admin
│   │   │               │   │   ├── impl
│   │   │               │   │   │   ├── AdminPermissionServiceImpl.java
│   │   │               │   │   │   ├── AdminRoleServiceImpl.java
│   │   │               │   │   │   └── AdminUserServiceImpl.java
│   │   │               │   │   ├── AdminPermissionService.java
│   │   │               │   │   ├── AdminRoleService.java
│   │   │               │   │   └── AdminUserService.java
│   │   │               │   ├── card
│   │   │               │   │   ├── impl
│   │   │               │   │   │   └── EtfCardServeImpl.java
│   │   │               │   │   └── EtfCardServe.java
│   │   │               │   ├── miner
│   │   │               │   │   ├── impl
│   │   │               │   │   │   ├── MinerProfitRecordServeImpl.java
│   │   │               │   │   │   ├── MinerProfitRetryServeImpl.java
│   │   │               │   │   │   ├── MinerPurchaseRetryServeImpl.java
│   │   │               │   │   │   ├── MinerServeImpl.java
│   │   │               │   │   │   └── MinerTaskScheduler.java
│   │   │               │   │   ├── MinerProfitRecordServe.java
│   │   │               │   │   └── MinerServe.java
│   │   │               │   ├── sys
│   │   │               │   │   ├── impl
│   │   │               │   │   │   └── SystemConfigServeImpl.java
│   │   │               │   │   └── SystemConfigServe.java
│   │   │               │   ├── test
│   │   │               │   │   ├── impl
│   │   │               │   │   │   └── TestServeImpl.java
│   │   │               │   │   └── TestServe.java
│   │   │               │   └── user
│   │   │               │       ├── impl
│   │   │               │       │   ├── UserBillRetryServeImpl.java
│   │   │               │       │   ├── UserBillServeImpl.java
│   │   │               │       │   └── UserServeImpl.java
│   │   │               │       ├── UserBillServe.java
│   │   │               │       └── UserServe.java
│   │   │               ├── utils
│   │   │               │   └── RandomIdGenerator.java
│   │   │               ├── VO
│   │   │               │   ├── AbnormalPageVO.java
│   │   │               │   ├── AdminBillStatisticsVO.java
│   │   │               │   ├── AdminRoleVO.java
│   │   │               │   ├── CreateUserBillVO.java
│   │   │               │   ├── MinerSettings.java
│   │   │               │   └── PaymentUsdtMetaVO.java
│   │   │               ├── PrivateKeyEncryptTool.java
│   │   │               └── RabnbserverApplication.java
│   │   └── resources
│   │       ├── application.yaml
│   │       └── logback-spring.xml
│   └── test
│       └── java
│           └── com
│               └── ra
│                   └── rabnbserver
│                       └── RabnbserverApplicationTests.java
├── .gitattributes
├── .gitignore
├── AGENTS.md
├── contract.md
├── HELP.md
├── mvnw
├── mvnw.cmd
├── pom.xml
└── 异常处理框架文档.md
```

## 文件索引（逐个文件说明）

### 根目录
- `.gitattributes`：Git 属性规则（行尾/二进制处理等）。
- `.gitignore`：Git 忽略规则。
- `AGENTS.md`：项目对话与开发规范。
- `HELP.md`：项目结构与文件索引（本文件）。
- `contract.md`：合约接口与业务说明文档。
- `异常处理框架文档.md`：异常处理框架设计文档。
- `pom.xml`：Maven 依赖与构建配置。
- `mvnw`：Maven Wrapper 启动脚本（Unix）。
- `mvnw.cmd`：Maven Wrapper 启动脚本（Windows）。

### .idea（IDE 配置）
- `.idea/.gitignore`：IDE 文件忽略规则。
- `.idea/ApifoxUploaderProjectSetting.xml`：Apifox 插件配置。
- `.idea/compiler.xml`：IDE 编译配置。
- `.idea/dictionaries/project.xml`：IDE 字典配置。
- `.idea/encodings.xml`：IDE 编码配置。
- `.idea/inspectionProfiles/Project_Default.xml`：IDE 代码检查配置。
- `.idea/jarRepositories.xml`：IDE 依赖仓库配置。
- `.idea/misc.xml`：IDE 通用配置。
- `.idea/vcs.xml`：IDE 版本控制配置。
- `.idea/workspace.xml`：IDE 工作区配置（本地）。

### .mvn
- `.mvn/wrapper/maven-wrapper.properties`：Maven Wrapper 版本与下载地址配置。

### Document（合约文档与源码）
- `Document/air.md`：AION 合约文档（接口/错误码/说明）。
- `Document/AiRWord_v3.sol`：AION 合约源码。
- `Document/card_v2.md`：CardNFT v2 文档（接口/规则/错误说明）。
- `Document/card_v2.sol`：CardNFT v2 合约源码。

### logs（运行日志）
- `logs/app.2026-02-04.0.log.gz`：历史应用日志（压缩）。
- `logs/app.2026-02-05.0.log.gz`：历史应用日志（压缩）。
- `logs/app.2026-02-05.1.log.gz`：历史应用日志（压缩）。
- `logs/app.2026-02-06.0.log.gz`：历史应用日志（压缩）。
- `logs/app.2026-02-07.0.log.gz`：历史应用日志（压缩）。
- `logs/app.2026-02-08.0.log.gz`：历史应用日志（压缩）。
- `logs/app.2026-02-09.0.log.gz`：历史应用日志（压缩）。
- `logs/app.2026-02-10.0.log.gz`：历史应用日志（压缩）。
- `logs/app.log`：当前应用日志。
- `logs/warn-error.log`：告警/错误日志。

### src/main/resources
- `src/main/resources/application.yaml`：全局配置（链/合约/邮件/异常框架/业务配置等）。
- `src/main/resources/logback-spring.xml`：日志输出与滚动策略配置。

### src/main/java/com/ra/rabnbserver
- `src/main/java/com/ra/rabnbserver/RabnbserverApplication.java`：Spring Boot 启动入口。
- `src/main/java/com/ra/rabnbserver/PrivateKeyEncryptTool.java`：私钥加密测试工具（控制台）。

#### advice
- `src/main/java/com/ra/rabnbserver/advice/ResponseEncryptAdvice.java`：响应加密切面（统一封装明文/密文）。

#### annotation
- `src/main/java/com/ra/rabnbserver/annotation/ColumnComment.java`：字段注释注解。
- `src/main/java/com/ra/rabnbserver/annotation/ColumnType.java`：字段类型注解。
- `src/main/java/com/ra/rabnbserver/annotation/DefaultValue.java`：字段默认值注解。
- `src/main/java/com/ra/rabnbserver/annotation/ForeignKey.java`：外键注解。
- `src/main/java/com/ra/rabnbserver/annotation/ForeignKeys.java`：外键集合注解。
- `src/main/java/com/ra/rabnbserver/annotation/Index.java`：索引注解。
- `src/main/java/com/ra/rabnbserver/annotation/Indexes.java`：索引集合注解。
- `src/main/java/com/ra/rabnbserver/annotation/TableComment.java`：表注释注解。

#### common
- `src/main/java/com/ra/rabnbserver/common/BaseEntity.java`：基础实体字段（时间戳等）。
- `src/main/java/com/ra/rabnbserver/common/Result.java`：通用响应结构（历史兼容）。

#### config
- `src/main/java/com/ra/rabnbserver/config/CorsConfig.java`：全局跨域配置。
- `src/main/java/com/ra/rabnbserver/config/DatabaseInitConfig.java`：数据库初始化配置入口。
- `src/main/java/com/ra/rabnbserver/config/MybatisPlusConfig.java`：MyBatis-Plus 配置。
- `src/main/java/com/ra/rabnbserver/config/MyMetaObjectHandler.java`：MyBatis-Plus 自动填充配置。
- `src/main/java/com/ra/rabnbserver/config/SaTokenConfig.java`：Sa-Token 配置。
- `src/main/java/com/ra/rabnbserver/config/StpInterfaceImpl.java`：Sa-Token 权限接口实现。
- `src/main/java/com/ra/rabnbserver/config/ThreadPoolTaskExecutorConfig.java`：线程池配置（异步任务）。
- `src/main/java/com/ra/rabnbserver/config/TimeZoneConfig.java`：全局时区配置（Asia/Shanghai）。

#### contract（合约调用）
- `src/main/java/com/ra/rabnbserver/contract/AionContract.java`：AION 合约调用封装。
- `src/main/java/com/ra/rabnbserver/contract/CardNftContract.java`：CardNFT v2 合约调用封装（多卡牌ID、订单号 keccak）。
- `src/main/java/com/ra/rabnbserver/contract/PaymentUsdtContract.java`：PaymentUSDT 合约调用封装。
- `src/main/java/com/ra/rabnbserver/contract/service/AionService.java`：AION 业务查询服务（锁仓统计/可流通量等）。
- `src/main/java/com/ra/rabnbserver/contract/support/AmountConvertUtils.java`：金额单位转换工具。
- `src/main/java/com/ra/rabnbserver/contract/support/BlockchainProperties.java`：链配置属性。
- `src/main/java/com/ra/rabnbserver/contract/support/ContractAddressProperties.java`：合约地址配置。
- `src/main/java/com/ra/rabnbserver/contract/support/ContractAdminProperties.java`：合约管理员密钥配置。
- `src/main/java/com/ra/rabnbserver/contract/support/ContractBase.java`：合约调用基类。
- `src/main/java/com/ra/rabnbserver/contract/support/ContractTypeUtils.java`：ABI 参数类型工具。
- `src/main/java/com/ra/rabnbserver/contract/support/PrivateKeyCryptoService.java`：私钥加解密工具。
- `src/main/java/com/ra/rabnbserver/contract/support/Web3jConfig.java`：Web3j 连接配置。

#### controller（对外接口）
- `src/main/java/com/ra/rabnbserver/controller/AdminCommonController.java`：后台通用接口集合。
- `src/main/java/com/ra/rabnbserver/controller/AdminRbacController.java`：后台角色/权限接口。
- `src/main/java/com/ra/rabnbserver/controller/AuthMockController.java`：鉴权测试接口。
- `src/main/java/com/ra/rabnbserver/controller/CommonController.java`：公共接口集合。
- `src/main/java/com/ra/rabnbserver/controller/abnormal/AdminAbnormalController.java`：异常处理数据列表接口。
- `src/main/java/com/ra/rabnbserver/controller/bill/AdminBillController.java`：后台账单接口。
- `src/main/java/com/ra/rabnbserver/controller/card/admin/AdminEtfCardController.java`：后台卡牌管理接口。
- `src/main/java/com/ra/rabnbserver/controller/card/user/EtfCardController.java`：用户卡牌查询/购买接口。
- `src/main/java/com/ra/rabnbserver/controller/sys/AdminContractController.java`：合约配置接口（USDT/AION/卡牌）。
- `src/main/java/com/ra/rabnbserver/controller/sys/SystemConfigController.java`：系统配置接口。
- `src/main/java/com/ra/rabnbserver/controller/test/TestController.java`：异常框架测试接口（写入失败/人工处理）。
- `src/main/java/com/ra/rabnbserver/controller/user/AdminUserController.java`：后台用户管理接口（登录/分发）。
- `src/main/java/com/ra/rabnbserver/controller/user/MinerController.java`：矿机业务接口（购买/电费/兑换）。
- `src/main/java/com/ra/rabnbserver/controller/user/UserController.java`：用户接口（初始化/登录/充值/购买）。

#### crypto
- `src/main/java/com/ra/rabnbserver/crypto/CryptoConstants.java`：加解密常量。
- `src/main/java/com/ra/rabnbserver/crypto/CryptoEnvelope.java`：加密封装对象。
- `src/main/java/com/ra/rabnbserver/crypto/CryptoUtils.java`：加解密工具。
- `src/main/java/com/ra/rabnbserver/crypto/MorseCodec.java`：自定义摩斯编码工具。
- `src/main/java/com/ra/rabnbserver/crypto/RequestCryptoService.java`：请求解密逻辑。
- `src/main/java/com/ra/rabnbserver/crypto/ResponseCryptoService.java`：响应加密逻辑。

#### db
- `src/main/java/com/ra/rabnbserver/db/DatabaseInitService.java`：数据库初始化与表结构维护。

#### dto
- `src/main/java/com/ra/rabnbserver/dto/AbnormalQueryDTO.java`：异常列表查询参数。
- `src/main/java/com/ra/rabnbserver/dto/AdminAmountRequestDTO.java`：后台充值/扣款参数。
- `src/main/java/com/ra/rabnbserver/dto/AdminBillQueryDTO.java`：后台账单查询参数。
- `src/main/java/com/ra/rabnbserver/dto/AdminUserLoginDTO.java`：后台登录参数。
- `src/main/java/com/ra/rabnbserver/dto/AmountRequestDTO.java`：用户金额操作参数。
- `src/main/java/com/ra/rabnbserver/dto/AuthMockRequest.java`：鉴权测试请求参数。
- `src/main/java/com/ra/rabnbserver/dto/BillQueryDTO.java`：用户账单查询参数。
- `src/main/java/com/ra/rabnbserver/dto/DistributeNftDTO.java`：后台分发卡牌参数（含 cardId）。
- `src/main/java/com/ra/rabnbserver/dto/EtfCardQueryDTO.java`：卡牌查询参数。
- `src/main/java/com/ra/rabnbserver/dto/LoginDataDTO.java`：登录请求参数。
- `src/main/java/com/ra/rabnbserver/dto/MinerAccelerationDTO.java`：矿机加速参数。
- `src/main/java/com/ra/rabnbserver/dto/MinerElectricityDTO.java`：矿机电费参数。
- `src/main/java/com/ra/rabnbserver/dto/MinerProfitRecordQueryDTO.java`：矿机收益查询参数。
- `src/main/java/com/ra/rabnbserver/dto/MinerPurchaseDTO.java`：矿机购买参数（含 cardId）。
- `src/main/java/com/ra/rabnbserver/dto/MinerQueryDTO.java`：矿机查询参数。
- `src/main/java/com/ra/rabnbserver/dto/NFTPurchaseDTO.java`：NFT 购买参数（含 cardId）。
- `src/main/java/com/ra/rabnbserver/dto/RegisterDataDTO.java`：注册参数。
- `src/main/java/com/ra/rabnbserver/dto/TeamQueryDTO.java`：团队查询参数。
- `src/main/java/com/ra/rabnbserver/dto/UserQueryDTO.java`：用户查询参数。
- `src/main/java/com/ra/rabnbserver/dto/UserUpdateDTO.java`：用户更新参数。
- `src/main/java/com/ra/rabnbserver/dto/contract/TreasuryDTO.java`：收款地址配置参数。
- `src/main/java/com/ra/rabnbserver/dto/adminMinerAction/AdminMinerActionDTO.java`：后台矿机操作参数。
- `src/main/java/com/ra/rabnbserver/dto/adminMinerAction/FragmentExchangeNftDTO.java`：碎片兑换卡牌参数（含 cardId）。

#### enums
- `src/main/java/com/ra/rabnbserver/enums/AbnormalManualStatus.java`：异常人工处理状态枚举。
- `src/main/java/com/ra/rabnbserver/enums/AbnormalStatus.java`：异常状态枚举。
- `src/main/java/com/ra/rabnbserver/enums/BaseEnum.java`：枚举基础接口。
- `src/main/java/com/ra/rabnbserver/enums/BillType.java`：账单类型枚举。
- `src/main/java/com/ra/rabnbserver/enums/ForeignKeyAction.java`：外键动作枚举。
- `src/main/java/com/ra/rabnbserver/enums/FundType.java`：资金方向枚举。
- `src/main/java/com/ra/rabnbserver/enums/IndexType.java`：索引类型枚举。
- `src/main/java/com/ra/rabnbserver/enums/MinerType.java`：矿机类型枚举。
- `src/main/java/com/ra/rabnbserver/enums/OrderType.java`：订单类型枚举。
- `src/main/java/com/ra/rabnbserver/enums/TransactionStatus.java`：交易状态枚举。
- `src/main/java/com/ra/rabnbserver/enums/TransactionType.java`：交易业务类型枚举。

#### exception
- `src/main/java/com/ra/rabnbserver/exception/BusinessException.java`：业务异常。
- `src/main/java/com/ra/rabnbserver/exception/ChainCallException.java`：链上调用异常封装。
- `src/main/java/com/ra/rabnbserver/exception/ContractCallException.java`：合约调用异常基类。
- `src/main/java/com/ra/rabnbserver/exception/AionContractException.java`：AION 合约异常（原始+解码）。
- `src/main/java/com/ra/rabnbserver/exception/GlobalExceptionHandler.java`：全局异常处理。
- `src/main/java/com/ra/rabnbserver/exception/Abnormal/annotation/AbnormalRetryConfig.java`：异常重试注解定义。
- `src/main/java/com/ra/rabnbserver/exception/Abnormal/core/AbnormalContext.java`：异常上下文。
- `src/main/java/com/ra/rabnbserver/exception/Abnormal/core/AbnormalManualController.java`：人工处理动态接口控制器。
- `src/main/java/com/ra/rabnbserver/exception/Abnormal/core/AbnormalManualEndpointRegistrar.java`：人工处理动态路由注册器。
- `src/main/java/com/ra/rabnbserver/exception/Abnormal/core/AbnormalManualRouteInfo.java`：人工处理路由信息模型。
- `src/main/java/com/ra/rabnbserver/exception/Abnormal/core/AbnormalManualRouteRegistry.java`：人工处理路由注册表。
- `src/main/java/com/ra/rabnbserver/exception/Abnormal/core/AbnormalMailService.java`：异常邮件通知服务。
- `src/main/java/com/ra/rabnbserver/exception/Abnormal/core/AbnormalRecord.java`：异常记录模型。
- `src/main/java/com/ra/rabnbserver/exception/Abnormal/core/AbnormalRetryHandler.java`：异常处理接口定义。
- `src/main/java/com/ra/rabnbserver/exception/Abnormal/core/AbnormalRetryManager.java`：异常重试核心逻辑。
- `src/main/java/com/ra/rabnbserver/exception/Abnormal/core/AbnormalRetryProperties.java`：异常重试配置模型。
- `src/main/java/com/ra/rabnbserver/exception/Abnormal/core/AbnormalRetryScheduler.java`：异常重试调度器。
- `src/main/java/com/ra/rabnbserver/exception/Abnormal/core/AbstractAbnormalRetryService.java`：异常重试服务抽象基类。
- `src/main/java/com/ra/rabnbserver/exception/Abnormal/model/AbnormalBaseEntity.java`：异常处理框架基础字段实体。

#### filter
- `src/main/java/com/ra/rabnbserver/filter/CachedBodyHttpServletRequest.java`：可重复读的请求包装器。
- `src/main/java/com/ra/rabnbserver/filter/CryptoRequestFilter.java`：请求解密过滤器。

#### mapper
- `src/main/java/com/ra/rabnbserver/mapper/AdminPermissionMapper.java`：后台权限 Mapper。
- `src/main/java/com/ra/rabnbserver/mapper/AdminRoleMapper.java`：后台角色 Mapper。
- `src/main/java/com/ra/rabnbserver/mapper/AdminRolePermissionMapper.java`：后台角色权限关联 Mapper。
- `src/main/java/com/ra/rabnbserver/mapper/AdminUserMapper.java`：后台用户 Mapper。
- `src/main/java/com/ra/rabnbserver/mapper/EtfCardMapper.java`：卡牌 Mapper。
- `src/main/java/com/ra/rabnbserver/mapper/MinerProfitRecordMapper.java`：矿机收益记录 Mapper。
- `src/main/java/com/ra/rabnbserver/mapper/SystemConfigMapper.java`：系统配置 Mapper。
- `src/main/java/com/ra/rabnbserver/mapper/UserBillMapper.java`：账单 Mapper。
- `src/main/java/com/ra/rabnbserver/mapper/UserMapper.java`：用户 Mapper。
- `src/main/java/com/ra/rabnbserver/mapper/UserMinerMapper.java`：用户矿机 Mapper。

#### model
- `src/main/java/com/ra/rabnbserver/model/ApiResponse.java`：统一响应封装。

#### pojo
- `src/main/java/com/ra/rabnbserver/pojo/AdminPermission.java`：后台权限实体。
- `src/main/java/com/ra/rabnbserver/pojo/AdminRole.java`：后台角色实体。
- `src/main/java/com/ra/rabnbserver/pojo/AdminRolePermission.java`：角色权限关联实体。
- `src/main/java/com/ra/rabnbserver/pojo/AdminUser.java`：后台用户实体。
- `src/main/java/com/ra/rabnbserver/pojo/ETFCard.java`：卡牌实体。
- `src/main/java/com/ra/rabnbserver/pojo/MinerProfitRecord.java`：矿机收益记录实体。
- `src/main/java/com/ra/rabnbserver/pojo/SystemConfig.java`：系统配置实体。
- `src/main/java/com/ra/rabnbserver/pojo/TestPojo.java`：异常框架示例实体。
- `src/main/java/com/ra/rabnbserver/pojo/User.java`：用户实体。
- `src/main/java/com/ra/rabnbserver/pojo/UserBill.java`：账单实体（含 cardId）。
- `src/main/java/com/ra/rabnbserver/pojo/UserMiner.java`：用户矿机实体（含 cardId/订单号）。

#### security
- `src/main/java/com/ra/rabnbserver/security/TokenExtractor.java`：请求头 Token 提取。

#### server
- `src/main/java/com/ra/rabnbserver/server/admin/AdminPermissionService.java`：后台权限服务接口。
- `src/main/java/com/ra/rabnbserver/server/admin/AdminRoleService.java`：后台角色服务接口。
- `src/main/java/com/ra/rabnbserver/server/admin/AdminUserService.java`：后台用户服务接口。
- `src/main/java/com/ra/rabnbserver/server/admin/impl/AdminPermissionServiceImpl.java`：后台权限服务实现。
- `src/main/java/com/ra/rabnbserver/server/admin/impl/AdminRoleServiceImpl.java`：后台角色服务实现。
- `src/main/java/com/ra/rabnbserver/server/admin/impl/AdminUserServiceImpl.java`：后台用户服务实现。
- `src/main/java/com/ra/rabnbserver/server/card/EtfCardServe.java`：卡牌服务接口。
- `src/main/java/com/ra/rabnbserver/server/card/impl/EtfCardServeImpl.java`：卡牌服务实现。
- `src/main/java/com/ra/rabnbserver/server/miner/MinerProfitRecordServe.java`：矿机收益服务接口。
- `src/main/java/com/ra/rabnbserver/server/miner/MinerServe.java`：矿机服务接口。
- `src/main/java/com/ra/rabnbserver/server/miner/impl/MinerProfitRecordServeImpl.java`：矿机收益服务实现。
- `src/main/java/com/ra/rabnbserver/server/miner/impl/MinerProfitRetryServeImpl.java`：矿机收益异常重试服务。
- `src/main/java/com/ra/rabnbserver/server/miner/impl/MinerPurchaseRetryServeImpl.java`：矿机购买异常重试服务。
- `src/main/java/com/ra/rabnbserver/server/miner/impl/MinerServeImpl.java`：矿机服务实现。
- `src/main/java/com/ra/rabnbserver/server/miner/impl/MinerTaskScheduler.java`：矿机相关定时任务。
- `src/main/java/com/ra/rabnbserver/server/sys/SystemConfigServe.java`：系统配置服务接口。
- `src/main/java/com/ra/rabnbserver/server/sys/impl/SystemConfigServeImpl.java`：系统配置服务实现。
- `src/main/java/com/ra/rabnbserver/server/test/TestServe.java`：异常框架测试服务接口。
- `src/main/java/com/ra/rabnbserver/server/test/impl/TestServeImpl.java`：异常框架测试服务实现。
- `src/main/java/com/ra/rabnbserver/server/user/UserBillServe.java`：账单服务接口。
- `src/main/java/com/ra/rabnbserver/server/user/UserServe.java`：用户服务接口。
- `src/main/java/com/ra/rabnbserver/server/user/impl/UserBillRetryServeImpl.java`：账单异常重试服务。
- `src/main/java/com/ra/rabnbserver/server/user/impl/UserBillServeImpl.java`：账单服务实现。
- `src/main/java/com/ra/rabnbserver/server/user/impl/UserServeImpl.java`：用户服务实现。

#### utils
- `src/main/java/com/ra/rabnbserver/utils/RandomIdGenerator.java`：订单号/随机 ID 生成工具。

#### VO
- `src/main/java/com/ra/rabnbserver/VO/AbnormalPageVO.java`：异常列表分页响应。
- `src/main/java/com/ra/rabnbserver/VO/AdminBillStatisticsVO.java`：后台账单统计响应。
- `src/main/java/com/ra/rabnbserver/VO/AdminRoleVO.java`：后台角色响应。
- `src/main/java/com/ra/rabnbserver/VO/CreateUserBillVO.java`：创建账单响应（含 cardId）。
- `src/main/java/com/ra/rabnbserver/VO/MinerSettings.java`：矿机配置响应。
- `src/main/java/com/ra/rabnbserver/VO/PaymentUsdtMetaVO.java`：USDT 合约元数据响应。

### src/test/java
- `src/test/java/com/ra/rabnbserver/RabnbserverApplicationTests.java`：手动测试集合（默认不随打包执行）。
