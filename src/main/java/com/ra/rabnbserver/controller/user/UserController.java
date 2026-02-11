package com.ra.rabnbserver.controller.user;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ra.rabnbserver.crypto.CryptoConstants;
import com.ra.rabnbserver.crypto.CryptoUtils;
import com.ra.rabnbserver.dto.*;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.pojo.UserBill;
import com.ra.rabnbserver.server.user.UserServe;
import com.ra.rabnbserver.server.user.impl.UserBillRetryServeImpl;
import com.ra.rabnbserver.utils.RandomIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import com.ra.rabnbserver.server.user.UserBillServe;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户端 - 用户接口（初始化、登录）
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final String INIT_ATTR_TOKEN = "initToken";
    private static final String INIT_ATTR_TS6 = "initTs6";
    private static final String INIT_ATTR_PLAIN = "initPlainJson";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserServe userService;
    private final UserBillServe billService;
    private final UserBillRetryServeImpl billRetryServe;
    public UserController(UserServe userService, UserBillServe billService, UserBillRetryServeImpl billRetryServe) {
        this.userService = userService;
        this.billService = billService;
        this.billRetryServe = billRetryServe;
    }

    @Value("${ADMIN.DFCODE:eC4vW8}")
    private String DFCODE;


    /**
     * 初始化接口（返回明文，密文由拦截器处理）
     */
    @PostMapping("/init")
    public String init(HttpServletRequest request, @RequestBody String data) throws Exception {
        log.info("用户初始化接口收到请求，请求体：{}", data);
        String userAgent = request.getHeader("User-Agent");
        String timestamp = String.valueOf(System.currentTimeMillis());
        String shortTimestamp = timestamp.substring(0, Math.min(7, timestamp.length()));
        log.info("用户初始化接口UA: {}, 时间戳: {}", userAgent, shortTimestamp);
        String subject = RandomIdGenerator.generateRandom16ByteHexString();
        StpUtil.login(subject, new SaLoginParameter()
                .setIsLastingCookie(true)
                .setTimeout(60 * 60 * 24)
                .setIsConcurrent(true)
                .setIsShare(true)
                .setMaxLoginCount(12)
                .setMaxTryTimes(12)
                .setIsWriteHeader(true)
                .setTerminalExtra("Key", CryptoUtils.md5Hex(StpUtil.getTokenValue() + CryptoConstants.TOKEN_SALT))
        );
        String token = StpUtil.getTokenValue();
        String key = CryptoUtils.md5Hex(token + CryptoConstants.TOKEN_SALT);
        StpUtil.getTokenSession().set("Key", key);
        Map<String, Object> plainMap = new HashMap<>();
        plainMap.put("token", token);
        plainMap.put("Key", key);
        String ts6 = timestamp.substring(0, Math.min(6, timestamp.length()));
        String plainJson = objectMapper.writeValueAsString(plainMap);
        request.setAttribute(INIT_ATTR_TOKEN, token);
        request.setAttribute(INIT_ATTR_TS6, ts6);
        request.setAttribute(INIT_ATTR_PLAIN, plainJson);
        log.info("初始化明文数据：{}", plainJson);
        return plainJson;
    }
    /**
     * 用户登录接口
     */
    @PostMapping("/login")
    public String login(HttpServletRequest request, @RequestBody LoginDataDTO loginDataDTO) throws Exception {
        log.info("登录请求：{}", loginDataDTO);
        String walletAddress = loginDataDTO.getUserWalletAddress();
        if (StrUtil.isBlank(walletAddress)) {
            return ApiResponse.error("钱包地址不能为空");
        }
        User user = userService.getByWalletAddress(walletAddress);
        if (user == null) {
            return ApiResponse.error("用户不存在");
        }
        upgradeToUserSession(user.getId().toString());
        return ApiResponse.success("登录成功", user);
    }



    /**
     * 用户注册接口
     */
    @PostMapping("/register")
    public String register(HttpServletRequest request, @RequestBody RegisterDataDTO registerDataDTO) throws Exception {
        log.info("注册请求：{}", registerDataDTO);
        String walletAddress = registerDataDTO.getUserWalletAddress();
        if (StrUtil.isBlank(walletAddress)) {
            return ApiResponse.error("钱包地址不能为空");
        }
        if (StrUtil.isBlank(registerDataDTO.getCode())) {
            log.info("传入邀请码为空，自动填入默认邀请码");
            registerDataDTO.setCode(DFCODE);
        }
        User existingUser = userService.getByWalletAddress(walletAddress);
        if (existingUser != null) {
            return ApiResponse.error("该地址已注册");
        }
        User newUser = userService.register(registerDataDTO);
        upgradeToUserSession(newUser.getId().toString());
        return ApiResponse.success("注册成功", newUser);
    }

    /**
     * 统一登录/注册接口
     * 数据库中是否存在             数据库中是否存在
     * 提交的 userWalletAddress	提交的 code(邀请人地址（默认地址（默认邀请码））)	库中是否存在该用户	    结果
     * 为空	                    任意	                                        -	                报错：钱包地址不能为空
     * 已存在地址	                任意（传或不传）	                            是	                直接登录成功
     * 未注册地址	                为空	                                        否	                报错：用户未注册，请提供邀请人地址
     * 未注册地址	                等于系统默认地址	                            否	                注册成功：作为根用户
     * 未注册地址	                等于库中已有地址	                            否	                注册成功：作为该地址的下级
     * 未注册地址	                库中不存在且非默认	                            否	                报错：邀请人地址无效
     */
    @PostMapping("/access")
    public String login(@RequestBody LoginDataDTO loginDataDTO) throws Exception {
        log.info("访问请求：{}", loginDataDTO);

        String walletAddress = loginDataDTO.getUserWalletAddress();
        String referrer = loginDataDTO.getCode();

        // 1. 校验：钱包地址不能为空 (Code: 4001)
        if (StrUtil.isBlank(walletAddress)) {
            return ApiResponse.error(1001, "钱包地址不能为空");
        }

        // 2. 检查用户是否存在
        User user = userService.getByWalletAddress(walletAddress);
        if (user != null) {
            // 只要地址存在，直接登录 (Code: 200)
            upgradeToUserSession(user.getId().toString());
            return ApiResponse.success("登录成功", user);
        }

        // 3. 用户不存在，准备注册
        // 3.1 校验：未注册地址必须传入推荐人 (Code: 4002)
        if (StrUtil.isBlank(referrer)) {
            return ApiResponse.error(1002, "用户未注册，请提供邀请人地址");
        }

        // 3.2 校验：不能邀请自己 (Code: 4004)
        if (walletAddress.equalsIgnoreCase(referrer)) {
            return ApiResponse.error(1003, "无效的邀请地址（不能邀请自己）");
        }

        try {
            // 执行注册
            User newUser = userService.handleRegister(walletAddress, referrer);
            upgradeToUserSession(newUser.getId().toString());
            return ApiResponse.success("注册成功", newUser);
        } catch (BusinessException e) {
            // 推荐人无效等业务异常 (Code: 4003)邀请人地址无效，请核对后重试
            return ApiResponse.error(1004, e.getMessage());
        } catch (Exception e) {
            log.error("系统注册异常", e);
            return ApiResponse.error(500, "系统繁忙，请稍后再试");
        }
    }

    /**
     * 获取当前登录用户信息
     */
    @SaCheckLogin
    @GetMapping("/info")
    public String getUserInfo() throws Exception {
        try {
            Long userId = getFormalUserId();
            User user = userService.getById(userId);
            if (user == null) {
                return ApiResponse.error("用户不存在");
            }
            log.info("获取用户信息成功: {}", user);
            return ApiResponse.success("获取成功", user);
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 查询我的团队列表
     * @param query (包..)
     */
    @SaCheckLogin
    @PostMapping("/team/list")
    public String getTeamList(@RequestBody TeamQueryDTO query) {
        Long userId = getFormalUserId();
        User currentUser = userService.getById(userId);
        Page<User> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        // 核心逻辑：查询 path 以 "我的Path + 我的ID," 开头的用户
        String subPath = currentUser.getPath() + currentUser.getId() + ",";
        wrapper.likeRight(User::getPath, subPath);
        // 如果传了 targetLevel，则计算绝对层级
        // 比如：我的 level 是 5，我想看我的第一层(targetLevel=1)，那就是查 level = 6 的人
        if (query.getTargetLevel() != null && query.getTargetLevel() > 0) {
            wrapper.eq(User::getLevel, currentUser.getLevel() + query.getTargetLevel());
        }
        wrapper.orderByDesc(User::getCreateTime);
        IPage<User> result = userService.page(page, wrapper);
        log.info("查询团队列表：{}",result.toString());
        return ApiResponse.success("获取成功", result);
    }

    /**
     * 核心复用方法：将当前的临时 Token 绑定到正式用户 ID
     * @param realUserId 真实的数据库用户ID
     */
    private void upgradeToUserSession(String realUserId) {
        String currentToken = StpUtil.getTokenValue();
        Object cryptoKey = StpUtil.getTokenSession().get("Key");
        StpUtil.login(realUserId, new SaLoginParameter()
                .setToken(currentToken) // 强制指定 Token
                .setIsLastingCookie(true)
                .setTimeout(60 * 60 * 24)
        );
        StpUtil.getTokenSession().set("Key", cryptoKey);
        log.info("用户ID {} 已成功绑定到原有 Token {}", realUserId, currentToken);
    }

    /**
     * 用户平台余额充值
     */
    @SaCheckLogin
    @PostMapping("/amount/deposit")
    public String deposit(@RequestBody AmountRequestDTO dto) throws Exception {
        Long userId;
        try {
            userId = getFormalUserId();
        } catch (BusinessException e) {
            return ApiResponse.error(402,"操作失败：需登录正式账号");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(dto.getAmount());
        } catch (Exception e) {
            return ApiResponse.error("金额格式错误");
        }
        if (amount.compareTo(new BigDecimal("50")) < 0) {
            return ApiResponse.error("充值金额必须大于等于50");
        }
        log.info("用户 {} 尝试通过链上充值: {}", userId, amount);
        try {
            billService.rechargeFromChain(userId, amount);
            return ApiResponse.success("充值处理成功");
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("充值接口未知异常", e);
            return ApiResponse.error("服务器繁忙，请稍后再试");
        }
    }

    /**
     * 用户查询账单列表（条件筛选，分页）
     * @param query
     * @return
     * @throws Exception
     */
    @SaCheckLogin
    @PostMapping("/bill/list")
    public String getBillList(@RequestBody(required = false) BillQueryDTO query) throws Exception {
        if (query == null) {
            query = new BillQueryDTO();
        }
        Long userId;
        try {
            userId = getFormalUserId();
        } catch (BusinessException e) {
            return ApiResponse.error(402,"操作失败：需登录正式账号");
        }
        log.info("用户 {} 查询账单列表，条件: {}", userId, query);
        try {
            IPage<UserBill> result = billService.getUserBillPage(userId, query);
            log.info("获取成功:{}", result);
            return ApiResponse.success("获取成功", result);
        } catch (java.time.format.DateTimeParseException e) {
            return ApiResponse.error("日期格式错误，请使用 yyyy-MM-dd 或者 yyyy-MM-dd 00:00:00 格式");
        } catch (Exception e) {
            log.error("查询账单失败", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 用户平台余额扣款
     */
    @SaCheckLogin
    @PostMapping("/amount/deduct")
    public String deduct(@RequestBody AmountRequestDTO dto) throws Exception {
        Long userId;
        try {
            userId = getFormalUserId();
        } catch (BusinessException e) {
            return ApiResponse.error(402,"操作失败：需登录正式账号");
        }
        BigDecimal amount = new BigDecimal(dto.getAmount()) ;
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.error("扣款金额必须大于0");
        }
        try {
            billService.createBillAndUpdateBalance(
                    userId,
                    amount,
                    BillType.PLATFORM,
                    FundType.EXPENSE,
                    TransactionType.PURCHASE,
                    dto.getRemark() == null ? "" : dto.getRemark(),
                    null,
                    null,
                    null,
                    0,
                    null
            );
            return ApiResponse.success("扣款成功");
        } catch (BusinessException e) {
            log.error("扣款失败: {}", e.getMessage());
            return ApiResponse.error("操作失败: " + e.getMessage());
        }
    }


    /**
     * 获取当前登录的正式用户ID
     * @return 成功则返回Long类型的UserId，如果是临时会话或未登录则抛出异常
     */
    private Long getFormalUserId() {
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();
        if (!StrUtil.isNumeric(loginId)) {
            log.warn("检测到临时会话访问受限接口: {}", loginId);
            throw new BusinessException("请先完成登录或注册");
        }
        return Long.parseLong(loginId);
    }

    /**
     * 购买 NFT 卡牌资产
     */
    @SaCheckLogin
    @PostMapping("/nft/purchase")
    public String purchaseNft(@RequestBody NFTPurchaseDTO nftPurchaseDTO) throws Exception {
        Long userId;
        try {
            userId = getFormalUserId();
        } catch (BusinessException e) {
            return ApiResponse.error(402,"操作失败：需登录正式账号");
        }

        // 1. 获取并解析参数
        Integer cardId = nftPurchaseDTO.getCardId();
        if (cardId == null) {
            return ApiResponse.error("卡牌ID不能为空");
        }
        Object quantityObj = nftPurchaseDTO.getNumber();
        if (quantityObj == null) {
            return ApiResponse.error("请输入购买数量");
        }

        int quantity;
        try {
            quantity = Integer.parseInt(quantityObj.toString());
        } catch (NumberFormatException e) {
            return ApiResponse.error("数量格式不正确");
        }

        log.info("用户 {} 请求购买 {} 张 NFT 卡牌，卡牌ID={}", userId, quantity, cardId);

        try {
            // 2. 调用服务层逻辑
            billService.purchaseNftCard(userId, quantity, cardId);
            return ApiResponse.success("购买成功，卡牌已发放到您的钱包");
        } catch (BusinessException e) {
            log.warn("购买 NFT 失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("购买 NFT 接口异常", e);
            return ApiResponse.error("购买服务暂时不可用");
        }
    }

//    /**
//     * 管理员人工核销账单（补发成功）
//     */
//    @PostMapping("/admin/manual-bill-success")
//    public String manualBillSuccess(@RequestParam("billId") Long billId) {
//        log.info("管理员人工干预账单成功，ID: {}", billId);
//        billRetryServe.ProcessingSuccessful(billId);
//        return ApiResponse.success("核销成功");
//    }



}
