package com.ra.rabnbserver.server.gold.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.VO.gold.AdminGoldQuantUserStatisticsVO;
import com.ra.rabnbserver.VO.gold.GoldQuantCommissionRecordVO;
import com.ra.rabnbserver.VO.gold.GoldQuantCommissionSettingsVO;
import com.ra.rabnbserver.VO.gold.GoldQuantCommissionStatisticsVO;
import com.ra.rabnbserver.VO.gold.GoldQuantTeamAreaVO;
import com.ra.rabnbserver.VO.gold.GoldQuantTeamSummaryVO;
import com.ra.rabnbserver.dto.gold.AdminGoldQuantCommissionQueryDTO;
import com.ra.rabnbserver.dto.gold.AdminGoldQuantUserStatisticsQueryDTO;
import com.ra.rabnbserver.dto.gold.GoldQuantCommissionQueryDTO;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.GoldQuantCommissionType;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.GoldQuantCommissionRecordMapper;
import com.ra.rabnbserver.mapper.GoldQuantWindowMapper;
import com.ra.rabnbserver.mapper.UserBillMapper;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.pojo.GoldQuantCommissionRecord;
import com.ra.rabnbserver.pojo.GoldQuantWindow;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.pojo.UserBill;
import com.ra.rabnbserver.server.gold.GoldQuantCommissionService;
import com.ra.rabnbserver.server.sys.SystemConfigServe;
import com.ra.rabnbserver.server.user.UserBillServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 黄金量化分佣服务实现类
 * 处理量化订单产生后的直推奖励、团队分润结算，以及相关的统计与后台查询功能
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GoldQuantCommissionServiceImpl
        extends ServiceImpl<GoldQuantCommissionRecordMapper, GoldQuantCommissionRecord>
        implements GoldQuantCommissionService {

    // 系统配置中存储分佣规则设置的键名
    private static final String SETTINGS_KEY = "GOLD_QUANT_COMMISSION_SETTINGS";

    // 依赖注入Mapper和服务层
    private final UserMapper userMapper;
    private final GoldQuantWindowMapper goldQuantWindowMapper;
    private final UserBillMapper userBillMapper;
    private final UserBillServe userBillServe;
    private final SystemConfigServe systemConfigServe;

    /**
     * 结算量化窗口订单的佣金
     *
     * @param sourceUserId 触发分佣的来源用户ID（即购买或续费窗口的用户）
     * @param sourceOrderId 来源订单ID
     * @param orderAmount 订单金额
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void settleWindowOrder(Long sourceUserId, String sourceOrderId, BigDecimal orderAmount) {
        // 参数校验：如果信息不完整或金额小于等于0，则直接跳过不处理
        if (sourceUserId == null || !StrUtil.isNotBlank(sourceOrderId) || orderAmount == null
                || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // 查询数据库中该订单是否已经结算过，防止重复发奖
        long settledCount = this.count(new LambdaQueryWrapper<GoldQuantCommissionRecord>()
                .eq(GoldQuantCommissionRecord::getSourceOrderId, sourceOrderId));
        if (settledCount > 0) {
            log.info("gold quant commission already settled, orderId={}", sourceOrderId);
            return;
        }

        // 构建团队快照：加载所有用户与窗口状态（降低频繁的数据库查询）
        TeamSnapshot snapshot = buildSnapshot();
        User sourceUser = snapshot.userMap.get(sourceUserId);
        if (sourceUser == null) {
            throw new BusinessException("来源用户不存在");
        }

        // 查找来源订单对应的账单ID
        Long sourceBillId = findBillId(sourceUserId, sourceOrderId);
        // 获取最新的分佣规则配置
        GoldQuantCommissionSettingsVO settings = getRules();

        // 结算平级/直推代数奖励
        settleReward(sourceUser, sourceOrderId, sourceBillId, orderAmount, settings, snapshot);
        // 结算级差/团队极差分润
        settleDistribution(sourceUser, sourceOrderId, sourceBillId, orderAmount, settings, snapshot);
    }

    /**
     * 获取用户的团队分佣摘要统计信息
     *
     * @param userId 需要查询的用户ID
     * @return 摘要视图对象，包括大小区有效窗口数、奖金信息等
     */
    @Override
    public GoldQuantTeamSummaryVO getTeamSummary(Long userId) {
        // 获取全量团队快照并查找当前用户
        TeamSnapshot snapshot = buildSnapshot();
        User user = snapshot.userMap.get(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 获取规则配置
        GoldQuantCommissionSettingsVO settings = getRules();

        // 统计直推有效买家数量（拥有有效窗口的直推用户数）
        int directValidBuyerCount = countDirectValidBuyers(user, snapshot);
        // 匹配当前用户的推荐奖励等级（代数奖等级）
        int rewardLevel = matchRewardLevel(directValidBuyerCount, settings);
        // 匹配当前用户的团队分润等级（极差等级）
        DistributionLevel distributionLevel = matchDistributionLevel(countTeamValidWindows(user, snapshot), settings);
        // 获取用户的伞下各个团队区（线）的情况，以区分大小区
        List<GoldQuantTeamAreaVO> areas = buildTeamAreas(user, snapshot);

        // 过滤提取大区的有效窗口数（bigArea 为 true 的线）
        int bigAreaCount = areas.stream()
                .filter(item -> Boolean.TRUE.equals(item.getBigArea()))
                .map(GoldQuantTeamAreaVO::getValidWindowCount)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0);

        // 过滤提取小区（除大区外的其他所有区）的有效窗口总和
        int smallAreaCount = areas.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getBigArea()))
                .map(GoldQuantTeamAreaVO::getValidWindowCount)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);

        // 封装返回 VO 数据
        GoldQuantTeamSummaryVO vo = new GoldQuantTeamSummaryVO();
        vo.setSelfValidWindowCount(snapshot.ownWindowCountMap.getOrDefault(userId, 0)); // 自身有效窗口数
        vo.setTeamValidWindowCount(countTeamValidWindows(user, snapshot)); // 伞下总有效窗口数
        vo.setBigAreaValidWindowCount(bigAreaCount); // 大区有效窗口数
        vo.setSmallAreaValidWindowCount(smallAreaCount); // 小区有效窗口数
        vo.setDirectValidBuyerCount(directValidBuyerCount); // 直推有效用户数
        vo.setRewardLevel(rewardLevel); // 当前代数奖励等级
        vo.setDistributionLevel(distributionLevel.level); // 当前级差分润等级
        vo.setRewardGenerationRange(buildRewardGenerationRange(rewardLevel, settings)); // 格式化能拿多少代奖励的文本
        vo.setDistributionRatio(distributionLevel.ratio); // 分润极差比例

        // 统计用户历史获得的代数奖和极差奖总额
        BigDecimal rewardAmount = sumUserCommission(userId, GoldQuantCommissionType.REWARD);
        BigDecimal distributionAmount = sumUserCommission(userId, GoldQuantCommissionType.DISTRIBUTION);
        vo.setRewardDistributedAmount(rewardAmount);
        vo.setDistributionDistributedAmount(distributionAmount);
        vo.setTotalDistributedAmount(rewardAmount.add(distributionAmount));
        return vo;
    }

    /**
     * 获取用户团队各个分支（线）的明细情况
     *
     * @param userId 用户ID
     * @return 包含多条支线业绩信息的列表
     */
    @Override
    public List<GoldQuantTeamAreaVO> getTeamAreas(Long userId) {
        TeamSnapshot snapshot = buildSnapshot();
        User user = snapshot.userMap.get(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return buildTeamAreas(user, snapshot);
    }

    /**
     * 前台：分页查询当前用户的分佣明细记录
     *
     * @param userId 用户ID
     * @param query 查询条件DTO
     * @return 分页对象
     */
    @Override
    public IPage<GoldQuantCommissionRecordVO> getUserCommissionPage(Long userId, GoldQuantCommissionQueryDTO query) {
        GoldQuantCommissionQueryDTO safeQuery = query == null ? new GoldQuantCommissionQueryDTO() : query;
        // 构建面向普通用户的查询条件
        LambdaQueryWrapper<GoldQuantCommissionRecord> wrapper = baseQueryByUser(userId, safeQuery);
        // 按创建时间和ID倒序排列
        wrapper.orderByDesc(GoldQuantCommissionRecord::getCreateTime).orderByDesc(GoldQuantCommissionRecord::getId);
        return this.page(new Page<>(safePage(safeQuery.getPage()), safeSize(safeQuery.getSize())), wrapper)
                .convert(this::toVO);
    }

    /**
     * 后台：管理端分页查询全站的分佣明细记录
     *
     * @param query 管理端查询条件DTO
     * @return 分页对象
     */
    @Override
    public IPage<GoldQuantCommissionRecordVO> getAdminCommissionPage(AdminGoldQuantCommissionQueryDTO query) {
        AdminGoldQuantCommissionQueryDTO safeQuery = query == null ? new AdminGoldQuantCommissionQueryDTO() : query;
        // 构建复杂的管理员多维度查询条件
        LambdaQueryWrapper<GoldQuantCommissionRecord> wrapper = buildAdminQuery(safeQuery);
        wrapper.orderByDesc(GoldQuantCommissionRecord::getCreateTime).orderByDesc(GoldQuantCommissionRecord::getId);
        return this.page(new Page<>(safePage(safeQuery.getPage()), safeSize(safeQuery.getSize())), wrapper)
                .convert(this::toVO);
    }

    /**
     * 后台：获取管理员分佣面板的宏观统计数据
     *
     * @param query 查询条件（可按时间、类型过滤）
     * @return 统计数据VO
     */
    @Override
    public GoldQuantCommissionStatisticsVO getAdminCommissionStatistics(AdminGoldQuantCommissionQueryDTO query) {
        AdminGoldQuantCommissionQueryDTO safeQuery = query == null ? new AdminGoldQuantCommissionQueryDTO() : query;
        // 根据条件拉取列表记录并进行内存遍历汇总（如果数据量极大可考虑改成 SQL SUM，但当前逻辑采用内存累加）
        List<GoldQuantCommissionRecord> records = this.list(buildAdminQuery(safeQuery));
        GoldQuantCommissionStatisticsVO vo = new GoldQuantCommissionStatisticsVO();
        LocalDate today = LocalDate.now();

        // 遍历累加
        for (GoldQuantCommissionRecord record : records) {
            BigDecimal amount = record.getCommissionAmount() == null ? BigDecimal.ZERO : record.getCommissionAmount();
            vo.setTotalAmount(vo.getTotalAmount().add(amount));
            vo.setTotalCount(vo.getTotalCount() + 1);

            // 累加代数奖励类型
            if (GoldQuantCommissionType.REWARD.equals(record.getCommissionType())) {
                vo.setRewardAmount(vo.getRewardAmount().add(amount));
            }
            // 累加极差分润类型
            if (GoldQuantCommissionType.DISTRIBUTION.equals(record.getCommissionType())) {
                vo.setDistributionAmount(vo.getDistributionAmount().add(amount));
            }
            // 累加今日产生的分佣金额
            if (record.getCreateTime() != null && today.equals(record.getCreateTime().toLocalDate())) {
                vo.setTodayAmount(vo.getTodayAmount().add(amount));
            }
        }
        return vo;
    }

    /**
     * 后台：管理端分页查询用户的分佣统计信息列表
     *
     * @param query 用户统计查询条件
     * @return 包含用户统计数据的分页对象
     */
    @Override
    public IPage<AdminGoldQuantUserStatisticsVO> getAdminUserStatisticsPage(AdminGoldQuantUserStatisticsQueryDTO query) {
        AdminGoldQuantUserStatisticsQueryDTO safeQuery = query == null ? new AdminGoldQuantUserStatisticsQueryDTO() : query;
        int page = (int) safePage(safeQuery.getPage());
        int size = (int) safeSize(safeQuery.getSize());

        // 解析时间区间
        LocalDateTime startTime = parseStartDateTime(safeQuery.getStartTime());
        LocalDateTime endTime = parseEndDateTime(safeQuery.getEndTime());
        boolean timeFiltered = startTime != null || endTime != null;

        // 构建快照，获取各种统计的基础数据
        TeamSnapshot snapshot = buildSnapshot();
        GoldQuantCommissionSettingsVO settings = getRules();

        // 如果按时间过滤，则仅查询该时间段内购买过窗口的用户统计；否则置空
        Map<Long, Integer> paidWindowCountMap = timeFiltered
                ? sumGoldQuantPaidWindowCount(startTime, endTime)
                : Collections.emptyMap();
        // 统计各个用户的全部窗口总数（含非激活状态）
        Map<Long, Integer> totalWindowCountMap = countTotalWindows();
        // 统计该时间段内各用户的代数奖励总和
        Map<Long, BigDecimal> rewardAmountMap = sumGoldQuantCommissionAmount(startTime, endTime, GoldQuantCommissionType.REWARD);
        // 统计该时间段内各用户的极差分润总和
        Map<Long, BigDecimal> distributionAmountMap = sumGoldQuantCommissionAmount(startTime, endTime, GoldQuantCommissionType.DISTRIBUTION);

        // 遍历系统中所有用户，并计算符合条件的结果（在内存中进行聚合过滤和排序）
        List<AdminGoldQuantUserStatisticsVO> records = snapshot.userMap.values().stream()
                // 根据钱包地址过滤
                .filter(user -> StrUtil.isBlank(safeQuery.getWalletAddress())
                        || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(user.getUserWalletAddress()), safeQuery.getWalletAddress()))
                // 转换成统计 VO 模型
                .map(user -> buildGoldQuantUserStatistics(user, snapshot, settings, paidWindowCountMap, totalWindowCountMap,
                        rewardAmountMap, distributionAmountMap, timeFiltered))
                // 根据传入的代数等级过滤
                .filter(vo -> safeQuery.getRewardLevel() == null || Objects.equals(vo.getRewardLevel(), safeQuery.getRewardLevel()))
                // 根据传入的极差等级过滤
                .filter(vo -> safeQuery.getDistributionLevel() == null || Objects.equals(vo.getDistributionLevel(), safeQuery.getDistributionLevel()))
                .collect(Collectors.toList());

        // 执行内存分页并返回
        return pageGoldQuantUserStatistics(records, page, size);
    }

    /**
     * 获取量化分佣规则，如果数据库未配置则返回默认规则结构
     *
     * @return 分佣配置VO
     */
    @Override
    public GoldQuantCommissionSettingsVO getRules() {
        GoldQuantCommissionSettingsVO settings = systemConfigServe.getConfigObject(SETTINGS_KEY, GoldQuantCommissionSettingsVO.class);
        // 格式化、补全配置项
        return normalizeSettings(settings);
    }

    /**
     * 处理代数奖励（直推/间推等平级推荐奖励）的逻辑
     *
     * @param sourceUser 来源（触发结算）用户
     * @param sourceOrderId 来源订单编号
     * @param sourceBillId 来源订单对应的账单ID
     * @param orderAmount 订单金额
     * @param settings 结算规则
     * @param snapshot 团队快照数据
     */
    private void settleReward(User sourceUser, String sourceOrderId, Long sourceBillId, BigDecimal orderAmount,
                              GoldQuantCommissionSettingsVO settings, TeamSnapshot snapshot) {
        // 从近到远获取所有直系上级（推荐链上的祖先节点）
        List<User> ancestors = getAncestorsFromNearest(sourceUser, snapshot.userMap);
        for (int i = 0; i < ancestors.size(); i++) {
            User ancestor = ancestors.get(i);

            // 核心业务规则：大区的业绩不参与该项代数奖励的分润，跳过大区用户
            if (isFromBigArea(ancestor, sourceUser, snapshot)) {
                continue;
            }

            // i=0表示1代，i=1表示2代
            int generation = i + 1;
            // 计算当前祖先拥有的代数奖励资格等级（受直推有效用户数限制）
            int level = matchRewardLevel(countDirectValidBuyers(ancestor, snapshot), settings);

            // 根据等级和代数匹配对应的发奖比例规则
            GoldQuantCommissionSettingsVO.RewardGenerationRule rule = findRewardRule(level, generation, settings);
            if (rule == null || rule.getRatio() == null || rule.getRatio().compareTo(BigDecimal.ZERO) <= 0) {
                // 如果拿不到该代的奖励，则忽略该用户并继续向上找
                continue;
            }

            // 计算最终可得的佣金金额
            BigDecimal commissionAmount = calculateAmount(orderAmount, rule.getRatio());
            // 生成具体的佣金记录并写库、变更余额
            createCommissionAndBill(ancestor, sourceUser, sourceOrderId, sourceBillId, GoldQuantCommissionType.REWARD,
                    level, generation, rule.getRatio(), orderAmount, commissionAmount);
        }
    }

    /**
     * 处理级差团队分润（无限代极差奖）的逻辑
     *
     * @param sourceUser 来源（触发结算）用户
     * @param sourceOrderId 来源订单编号
     * @param sourceBillId 来源订单对应的账单ID
     * @param orderAmount 订单金额
     * @param settings 结算规则
     * @param snapshot 团队快照数据
     */
    private void settleDistribution(User sourceUser, String sourceOrderId, Long sourceBillId, BigDecimal orderAmount,
                                    GoldQuantCommissionSettingsVO settings, TeamSnapshot snapshot) {
        // 最大拨出代数必须显式配置；没有配置则不执行分销分成。
        if (settings.getDistributionMaxGeneration() == null || settings.getDistributionMaxGeneration() <= 0) {
            return;
        }
        int maxGeneration = settings.getDistributionMaxGeneration();

        // 获取触发用户的级差等级，以便往上计算级差时扣除已被拿走的极差等级（拨差逻辑）
        int maxQualifiedLevel = matchDistributionLevel(countTeamValidWindows(sourceUser, snapshot), settings).level;

        List<User> ancestors = getAncestorsFromNearest(sourceUser, snapshot.userMap);
        for (int i = 0; i < ancestors.size() && i < maxGeneration; i++) {
            User ancestor = ancestors.get(i);
            int generation = i + 1;

            // 匹配当前祖先节点的团队级差等级（基于其伞下总有效窗口）
            DistributionLevel level = matchDistributionLevel(countTeamValidWindows(ancestor, snapshot), settings);

            // 级差奖的发放条件：等级必须大于0，且大于下级的等级（有极差），而且不能来自大区
            boolean canReceive = level.level > 0
                    && level.level > maxQualifiedLevel
                    && !isFromBigArea(ancestor, sourceUser, snapshot);

            if (canReceive) {
                // 如果满足条件，则按该等级拨出比例全额（或差额）发奖
                // 注意此处的业务逻辑似乎是直接取对应level的ratio，如果是严格级差这里通常是 (当前比例 - 下级比例)
                // 按照原逻辑：计算比例发奖。
                BigDecimal commissionAmount = calculateAmount(orderAmount, level.ratio);
                createCommissionAndBill(ancestor, sourceUser, sourceOrderId, sourceBillId, GoldQuantCommissionType.DISTRIBUTION,
                        level.level, generation, level.ratio, orderAmount, commissionAmount);
            }

            // 动态提升已截留（封锁）的最大等级，即上级必须比这个等级高才能继续拿极差
            if (level.level > maxQualifiedLevel) {
                maxQualifiedLevel = level.level;
            }
        }
    }

    /**
     * 生成一条具体的佣金记录，更新用户账单（发钱操作）
     *
     * @param beneficiary 最终获得奖励的用户
     * @param sourceUser 触发奖励的来源用户
     * @param sourceOrderId 来源的外部订单号
     * @param sourceBillId 来源相关的系统流水账单ID
     * @param type 奖励类型（代数 REWARD 或级差 DISTRIBUTION）
     * @param level 用户当前匹配的对应等级
     * @param generation 用户与触发用户的代数关系（层级差）
     * @param ratio 计算使用的比例
     * @param orderAmount 订单原始金额
     * @param commissionAmount 计算得出的奖励金额
     */
    private void createCommissionAndBill(User beneficiary, User sourceUser, String sourceOrderId, Long sourceBillId,
                                         GoldQuantCommissionType type, Integer level, Integer generation,
                                         BigDecimal ratio, BigDecimal orderAmount, BigDecimal commissionAmount) {
        // 不发金额<=0的无效佣金
        if (commissionAmount == null || commissionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // 再次兜底检查：是否已经给该用户发放过当前来源单号、同类型的佣金，防止并发重复发奖
        long exists = this.count(new LambdaQueryWrapper<GoldQuantCommissionRecord>()
                .eq(GoldQuantCommissionRecord::getSourceOrderId, sourceOrderId)
                .eq(GoldQuantCommissionRecord::getBeneficiaryUserId, beneficiary.getId())
                .eq(GoldQuantCommissionRecord::getCommissionType, type));
        if (exists > 0) {
            return;
        }

        // 生成发佣系统内部流水单号
        String commissionOrderId = "GQ_COM_" + type.getCode() + "_" + IdWorker.getIdStr();

        // 拼设备注说明信息，用于账单展示
        String remark = String.format("黄金量化%s，来源用户=%s，来源订单=%s，等级=V%d，代数=%d，比例=%s%%",
                type.getDesc(),
                sourceUser.getUserWalletAddress(),
                sourceOrderId,
                level,
                generation,
                ratio.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString());

        // 初始化佣金记录对象实体
        GoldQuantCommissionRecord record = new GoldQuantCommissionRecord();
        record.setBeneficiaryUserId(beneficiary.getId());
        record.setBeneficiaryWalletAddress(beneficiary.getUserWalletAddress());
        record.setSourceUserId(sourceUser.getId());
        record.setSourceWalletAddress(sourceUser.getUserWalletAddress());
        record.setSourceOrderId(sourceOrderId);
        record.setSourceBillId(sourceBillId);
        record.setCommissionType(type);
        record.setLevel(level);
        record.setGeneration(generation);
        record.setRatio(ratio);
        record.setOrderAmount(orderAmount);
        record.setCommissionAmount(commissionAmount);
        record.setRemark(remark);

        // 保存分佣记录至数据库
        this.save(record);

        // 调用账单模块为获利者增加余额，生成对应的进账流水
        userBillServe.createBillAndUpdateBalance(
                beneficiary.getId(),
                commissionAmount,
                BillType.PLATFORM, // 平台发放
                FundType.INCOME, // 收入
                GoldQuantCommissionType.REWARD.equals(type)
                        ? TransactionType.GOLD_QUANT_REWARD // 代数奖类型
                        : TransactionType.GOLD_QUANT_DISTRIBUTION, // 级差奖类型
                remark,
                commissionOrderId,
                null,
                null,
                0,
                null
        );

        // 关联刚刚生成的账单流水ID到当前的分佣记录中
        Long commissionBillId = findBillId(beneficiary.getId(), commissionOrderId);
        if (commissionBillId != null) {
            this.update(new LambdaUpdateWrapper<GoldQuantCommissionRecord>()
                    .eq(GoldQuantCommissionRecord::getId, record.getId())
                    .set(GoldQuantCommissionRecord::getCommissionBillId, commissionBillId));
        }
    }

    /**
     * 构建团队数据的全量缓存快照
     * 一次性查出所需的用户和窗口信息，避免后续递归查库出现 N+1 性能问题
     *
     * @return TeamSnapshot 快照数据容器记录
     */
    private TeamSnapshot buildSnapshot() {
        // 1. 查询所需的用户树形基础字段并转换成Map
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getId, User::getUserWalletAddress, User::getParentId, User::getPath, User::getTeamCount));
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, item -> item, (a, b) -> a, LinkedHashMap::new));

        // 2. 查询当前正在运行且未到期的有效量化窗口列表
        List<GoldQuantWindow> windows = goldQuantWindowMapper.selectList(new LambdaQueryWrapper<GoldQuantWindow>()
                .select(GoldQuantWindow::getUserId, GoldQuantWindow::getCreateTime)
                .eq(GoldQuantWindow::getStatus, 1)
                .gt(GoldQuantWindow::getMaintenanceExpireTime, LocalDateTime.now()));

        // 每个用户持有的有效窗口数统计
        Map<Long, Integer> ownWindowCountMap = new HashMap<>();
        // 每个用户最后一次创建窗口的时间（用于大小区业绩相同时的排序降级）
        Map<Long, LocalDateTime> lastWindowTimeMap = new HashMap<>();

        for (GoldQuantWindow window : windows) {
            if (window.getUserId() != null) {
                // 累计窗口数
                ownWindowCountMap.merge(window.getUserId(), 1, Integer::sum);
                // 更新最后操作时间
                if (window.getCreateTime() != null) {
                    lastWindowTimeMap.merge(window.getUserId(), window.getCreateTime(),
                            (oldValue, newValue) -> newValue.isAfter(oldValue) ? newValue : oldValue);
                }
            }
        }
        return new TeamSnapshot(userMap, ownWindowCountMap, lastWindowTimeMap);
    }

    /**
     * 获取指定用户的直推线（分支区）集合，并标出其中业绩最大的区（大区）
     *
     * @param user 要查询的用户
     * @param snapshot 预加载的快照
     * @return 该用户伞下的多条线的业绩统计列表
     */
    private List<GoldQuantTeamAreaVO> buildTeamAreas(User user, TeamSnapshot snapshot) {
        String prefix = pathPrefix(user);

        // 找出所有在伞下的子级用户
        List<User> downlineUsers = snapshot.userMap.values().stream()
                .filter(item -> !Objects.equals(item.getId(), user.getId()) && isInSubtree(item, prefix))
                .collect(Collectors.toList());
        if (downlineUsers.isEmpty()) {
            return Collections.emptyList();
        }

        // 构建各个子级线的区域对象（把直推下级当作一条线处理，由于原逻辑看似是所有下级，其实是记录每个下线自己的有效窗口作为区业绩。具体需要结合产品逻辑定义）
        List<GoldQuantTeamAreaVO> areas = new ArrayList<>();
        for (User downline : downlineUsers) {
            int validWindowCount = snapshot.ownWindowCountMap.getOrDefault(downline.getId(), 0);
            GoldQuantTeamAreaVO vo = new GoldQuantTeamAreaVO();
            vo.setUserId(downline.getId());
            vo.setWalletAddress(downline.getUserWalletAddress());
            vo.setTeamCount(1);
            vo.setValidWindowCount(validWindowCount);
            vo.setBigArea(false); // 默认先置为不是大区
            areas.add(vo);
        }
        // 根据业绩等规则排序：第一名就是大区
        areas.sort(teamAreaComparator(snapshot));
        areas.get(0).setBigArea(true); // 将排名第一的设置成大区
        return areas;
    }

    /**
     * 自定义比较器：用于排序确定伞下的“大区”
     * 比较规则：有效窗口数(降序) -> 最后开窗时间(升序/先发优势) -> ID兜底
     *
     * @param snapshot 快照信息
     * @return 比较器对象
     */
    private Comparator<GoldQuantTeamAreaVO> teamAreaComparator(TeamSnapshot snapshot) {
        return Comparator
                .comparing(GoldQuantTeamAreaVO::getValidWindowCount, Comparator.nullsFirst(Integer::compareTo))
                .reversed() // 有效窗口数多的大区排前面
                .thenComparing(item -> snapshot.lastWindowTimeMap.get(item.getUserId()),
                        Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(GoldQuantTeamAreaVO::getUserId);
    }

    /**
     * 统计指定用户伞下团队的所有有效窗口总数（含本身或不含依据isInSubtree的逻辑）
     *
     * @param user 指定用户
     * @param snapshot 快照数据
     * @return 伞下有效窗口总数
     */
    private int countTeamValidWindows(User user, TeamSnapshot snapshot) {
        int total = 0;
        String prefix = pathPrefix(user);
        // 遍历所有快照用户，计算路径前缀匹配的下级累加
        for (User item : snapshot.userMap.values()) {
            if (!Objects.equals(item.getId(), user.getId()) && isInSubtree(item, prefix)) {
                total += snapshot.ownWindowCountMap.getOrDefault(item.getId(), 0);
            }
        }
        return total;
    }

    /**
     * 统计直推有效买家的数量（直系下级中拥有>0有效窗口的用户数）
     *
     * @param user 当前用户
     * @param snapshot 快照数据
     * @return 满足条件的直推下属个数
     */
    private int countDirectValidBuyers(User user, TeamSnapshot snapshot) {
        int count = 0;
        for (User item : snapshot.userMap.values()) {
            // parentId 匹配且拥有至少一个活动量化窗口
            if (Objects.equals(item.getParentId(), user.getId())
                    && snapshot.ownWindowCountMap.getOrDefault(item.getId(), 0) > 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * 判定来源用户（产生业绩者）是否属于某祖先的“大区”之中
     *
     * @param ancestor 待领奖的祖先节点
     * @param sourceUser 触发分佣逻辑的底层节点
     * @param snapshot 团队快照
     * @return 若该底层节点正是大区的那条线，返回true
     */
    private boolean isFromBigArea(User ancestor, User sourceUser, TeamSnapshot snapshot) {
        if (ancestor == null || sourceUser == null || ancestor.getId() == null || sourceUser.getId() == null
                || Objects.equals(ancestor.getId(), sourceUser.getId())) {
            return false;
        }
        // 找出该祖先节点的大区用户ID
        Long bigAreaUserId = findBigAreaUserId(ancestor, snapshot);
        // 如果触发人正好是该大区线用户本身（或者归属在这个线），依照目前代码逻辑这里直接等于
        return bigAreaUserId != null && bigAreaUserId.equals(sourceUser.getId());
    }

    /**
     * 寻找指定用户伞下业绩最大的区域根节点ID
     *
     * @param user 用户
     * @param snapshot 快照
     * @return 大区用户的ID
     */
    private Long findBigAreaUserId(User user, TeamSnapshot snapshot) {
        return buildTeamAreas(user, snapshot).stream()
                .filter(item -> Boolean.TRUE.equals(item.getBigArea()))
                .map(GoldQuantTeamAreaVO::getUserId)
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据用户的path字段，从下往上依次获取推荐链路上的祖先用户节点列表
     *
     * @param user 出发点用户
     * @param userMap 全局用户字典字典映射
     * @return 排好序的祖先列表（第一个是直推上级，第二个是直推的直推，依此类推）
     */
    private List<User> getAncestorsFromNearest(User user, Map<Long, User> userMap) {
        List<Long> ids = parsePathUserIds(user.getPath());
        // 翻转顺序，使得从最近的上级开始
        Collections.reverse(ids);

        // 兼容数据：如果path里没写明parentId，手动把parentId补在最前面
        if (user.getParentId() != null && user.getParentId() > 0
                && (ids.isEmpty() || !Objects.equals(ids.get(0), user.getParentId()))) {
            ids.add(0, user.getParentId());
        }

        // 去重防环路死循环（防脏数据）
        Set<Long> seen = new HashSet<>();
        List<User> result = new ArrayList<>();
        for (Long id : ids) {
            if (id == null || id <= 0 || Objects.equals(id, user.getId()) || !seen.add(id)) {
                continue;
            }
            User ancestor = userMap.get(id);
            if (ancestor != null) {
                result.add(ancestor);
            }
        }
        return result;
    }

    /**
     * 根据“直推有效买家数”去匹配该用户所属的代数奖励（直推平级奖）级别
     *
     * @param directValidBuyerCount 直推且活跃下线人数
     * @param settings 分佣设置规则
     * @return 符合的最高级别数值 (Level)，不符合返回0
     */
    private int matchRewardLevel(int directValidBuyerCount, GoldQuantCommissionSettingsVO settings) {
        return settings.getRewardLevels().stream()
                .filter(item -> item.getDirectValidBuyerCount() != null && directValidBuyerCount >= item.getDirectValidBuyerCount())
                .map(GoldQuantCommissionSettingsVO.RewardLevelRule::getLevel)
                .filter(Objects::nonNull)
                .max(Integer::compareTo) // 满足条件的里面取Level最高的
                .orElse(0);
    }

    /**
     * 在规则中查找某级别可以拿下属多少代范围内的对应拨出比例参数
     *
     * @param level 用户当前的级别
     * @param generation 用户与触发下属的代数间隔（差值）
     * @param settings 设置规则
     * @return 匹配到的奖励代数规则配置对象，无则为null
     */
    private GoldQuantCommissionSettingsVO.RewardGenerationRule findRewardRule(
            int level, int generation, GoldQuantCommissionSettingsVO settings) {
        return settings.getRewardRules().stream()
                .filter(item -> Objects.equals(item.getLevel(), level)) // 匹配等级
                // 检查该代数是否落在规则配置的最小与最大代数区间之中
                .filter(item -> item.getMinGeneration() == null || generation >= item.getMinGeneration())
                .filter(item -> item.getMaxGeneration() == null || generation <= item.getMaxGeneration())
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据伞下“团队有效窗口总数”来匹配该用户的级差（极差团队分红）级别
     *
     * @param teamValidWindowCount 团队全量有效窗口数量
     * @param settings 配置规则
     * @return 极差等级对象 (DistributionLevel)
     */
    private DistributionLevel matchDistributionLevel(int teamValidWindowCount, GoldQuantCommissionSettingsVO settings) {
        return settings.getDistributionLevels().stream()
                // 窗口数满足等级门槛要求
                .filter(item -> item.getTeamValidWindowCount() != null && teamValidWindowCount >= item.getTeamValidWindowCount())
                // 取门槛要求最高的那个级别
                .max(Comparator.comparing(GoldQuantCommissionSettingsVO.DistributionLevelRule::getTeamValidWindowCount))
                .map(item -> new DistributionLevel(item.getLevel() == null ? 0 : item.getLevel(),
                        item.getRatio() == null ? BigDecimal.ZERO : item.getRatio()))
                .orElse(DistributionLevel.none()); // 不满足则没有任何级别
    }

    /**
     * 生成供前台UI显示的奖励能拿几代范围文字文本说明
     *
     * @param level 当前代数等级
     * @param settings 规则
     * @return 文字描述如 "第1-2代"
     */
    private String buildRewardGenerationRange(int level, GoldQuantCommissionSettingsVO settings) {
        GoldQuantCommissionSettingsVO.RewardGenerationRule rule = settings.getRewardRules().stream()
                .filter(item -> Objects.equals(item.getLevel(), level))
                .findFirst()
                .orElse(null);
        if (rule == null || level <= 0) {
            return "无";
        }
        if (rule.getMaxGeneration() == null) {
            return "第" + rule.getMinGeneration() + "代及以上";
        }
        if (Objects.equals(rule.getMinGeneration(), rule.getMaxGeneration())) {
            return "第" + rule.getMinGeneration() + "代";
        }
        return "第" + rule.getMinGeneration() + "-" + rule.getMaxGeneration() + "代";
    }

    /**
     * 标准化及规范化分佣配置对象。若无则赋初始默认业务参数。
     *
     * @param settings 原始从配置系统拉取的配置信息
     * @return 归一化的配置对象
     */
    private GoldQuantCommissionSettingsVO normalizeSettings(GoldQuantCommissionSettingsVO settings) {
        GoldQuantCommissionSettingsVO result = settings == null ? new GoldQuantCommissionSettingsVO() : settings;
        // 规则列表为null时规整为空列表；空列表表示未配置规则，不再补默认规则。
        if (result.getRewardLevels() == null) {
            result.setRewardLevels(new ArrayList<>());
        }
        if (result.getRewardRules() == null) {
            result.setRewardRules(new ArrayList<>());
        }
        if (result.getDistributionLevels() == null) {
            result.setDistributionLevels(new ArrayList<>());
        }
        // 保证内部规则列表是有序的，方便上游处理逻辑
        result.getRewardLevels().sort(Comparator.comparing(GoldQuantCommissionSettingsVO.RewardLevelRule::getLevel,
                Comparator.nullsLast(Integer::compareTo)));
        result.getRewardRules().sort(Comparator.comparing(GoldQuantCommissionSettingsVO.RewardGenerationRule::getLevel,
                Comparator.nullsLast(Integer::compareTo)));
        result.getDistributionLevels().sort(Comparator.comparing(GoldQuantCommissionSettingsVO.DistributionLevelRule::getTeamValidWindowCount,
                Comparator.nullsLast(Integer::compareTo)));
        return result;
    }

    /**
     * 构建针对普通用户查询的前端展示用的Lambda查询器
     *
     * @param userId 锁定当前用户的受益人ID
     * @param query 查询参数
     * @return QueryWrapper
     */
    private LambdaQueryWrapper<GoldQuantCommissionRecord> baseQueryByUser(Long userId, GoldQuantCommissionQueryDTO query) {
        LambdaQueryWrapper<GoldQuantCommissionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GoldQuantCommissionRecord::getBeneficiaryUserId, userId); // 只允许查自己的奖金记录
        wrapper.eq(query.getCommissionType() != null, GoldQuantCommissionRecord::getCommissionType, query.getCommissionType());
        // 加入时间过滤条件
        applyTimeRange(wrapper, query.getStartTime(), query.getEndTime());
        return wrapper;
    }

    /**
     * 构建供管理员后台系统查询的多维度综合Lambda查询器
     *
     * @param query 多功能查询DTO
     * @return QueryWrapper
     */
    private LambdaQueryWrapper<GoldQuantCommissionRecord> buildAdminQuery(AdminGoldQuantCommissionQueryDTO query) {
        LambdaQueryWrapper<GoldQuantCommissionRecord> wrapper = new LambdaQueryWrapper<>();
        // 精确或模糊匹配各个字段
        wrapper.eq(query.getBeneficiaryUserId() != null, GoldQuantCommissionRecord::getBeneficiaryUserId, query.getBeneficiaryUserId());
        wrapper.like(StrUtil.isNotBlank(query.getBeneficiaryWalletAddress()), GoldQuantCommissionRecord::getBeneficiaryWalletAddress, query.getBeneficiaryWalletAddress());
        wrapper.eq(query.getSourceUserId() != null, GoldQuantCommissionRecord::getSourceUserId, query.getSourceUserId());
        wrapper.like(StrUtil.isNotBlank(query.getSourceWalletAddress()), GoldQuantCommissionRecord::getSourceWalletAddress, query.getSourceWalletAddress());
        wrapper.eq(StrUtil.isNotBlank(query.getSourceOrderId()), GoldQuantCommissionRecord::getSourceOrderId, query.getSourceOrderId());
        wrapper.eq(query.getCommissionType() != null, GoldQuantCommissionRecord::getCommissionType, query.getCommissionType());
        wrapper.eq(query.getLevel() != null, GoldQuantCommissionRecord::getLevel, query.getLevel());

        // 范围匹配
        wrapper.ge(query.getMinGeneration() != null, GoldQuantCommissionRecord::getGeneration, query.getMinGeneration());
        wrapper.le(query.getMaxGeneration() != null, GoldQuantCommissionRecord::getGeneration, query.getMaxGeneration());
        wrapper.ge(query.getMinRatio() != null, GoldQuantCommissionRecord::getRatio, query.getMinRatio());
        wrapper.le(query.getMaxRatio() != null, GoldQuantCommissionRecord::getRatio, query.getMaxRatio());
        wrapper.ge(query.getMinOrderAmount() != null, GoldQuantCommissionRecord::getOrderAmount, query.getMinOrderAmount());
        wrapper.le(query.getMaxOrderAmount() != null, GoldQuantCommissionRecord::getOrderAmount, query.getMaxOrderAmount());
        wrapper.ge(query.getMinCommissionAmount() != null, GoldQuantCommissionRecord::getCommissionAmount, query.getMinCommissionAmount());
        wrapper.le(query.getMaxCommissionAmount() != null, GoldQuantCommissionRecord::getCommissionAmount, query.getMaxCommissionAmount());

        applyTimeRange(wrapper, query.getStartTime(), query.getEndTime());
        return wrapper;
    }

    /**
     * 为查询添加标准的开始时间和结束时间的SQL区间边界约束
     *
     * @param wrapper 现有wrapper
     * @param startTime 格式化开始时间
     * @param endTime 格式化结束时间
     */
    private void applyTimeRange(LambdaQueryWrapper<GoldQuantCommissionRecord> wrapper, String startTime, String endTime) {
        if (StrUtil.isNotBlank(startTime)) {
            // 时间处理：将传入日期转换成当天最早的00:00:00
            wrapper.ge(GoldQuantCommissionRecord::getCreateTime, DateUtil.parse(startTime).toLocalDateTime().with(LocalTime.MIN));
        }
        if (StrUtil.isNotBlank(endTime)) {
            // 时间处理：将传入日期转换成当天最晚的23:59:59.999
            wrapper.le(GoldQuantCommissionRecord::getCreateTime, DateUtil.parse(endTime).toLocalDateTime().with(LocalTime.MAX));
        }
    }

    /**
     * 将实体DO(Data Object)转换成发往客户端的展示层VO对象
     *
     * @param record 实体对象
     * @return 转换后的VO模型
     */
    private GoldQuantCommissionRecordVO toVO(GoldQuantCommissionRecord record) {
        GoldQuantCommissionRecordVO vo = new GoldQuantCommissionRecordVO();
        vo.setId(record.getId());
        vo.setBeneficiaryUserId(record.getBeneficiaryUserId());
        vo.setBeneficiaryWalletAddress(record.getBeneficiaryWalletAddress());
        vo.setSourceUserId(record.getSourceUserId());
        vo.setSourceWalletAddress(record.getSourceWalletAddress());
        vo.setSourceOrderId(record.getSourceOrderId());
        vo.setSourceBillId(record.getSourceBillId());
        vo.setCommissionBillId(record.getCommissionBillId());
        vo.setCommissionType(record.getCommissionType());
        vo.setLevel(record.getLevel());
        vo.setGeneration(record.getGeneration());
        vo.setRatio(record.getRatio());
        vo.setOrderAmount(record.getOrderAmount());
        vo.setCommissionAmount(record.getCommissionAmount());
        vo.setRemark(record.getRemark());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    /**
     * 根据业务单号，反查生成的用户明细账单流水ID
     *
     * @param userId 所属用户ID
     * @param orderId 交易单号
     * @return 返回账单ID，没有则返回null
     */
    private Long findBillId(Long userId, String orderId) {
        UserBill bill = userBillMapper.selectOne(new LambdaQueryWrapper<UserBill>()
                .select(UserBill::getId)
                .eq(UserBill::getUserId, userId)
                .eq(UserBill::getTransactionOrderId, orderId)
                .last("LIMIT 1")); // 只要查到第一条就返回，提高性能
        return bill == null ? null : bill.getId();
    }

    /**
     * 计算佣金得出结果，统一精度舍入规则
     * 按照原金额乘以提成比例，保留18位小数并做向下取整（抹零）处理。最终去除尾部的0输出。
     *
     * @param orderAmount 订单源头金额
     * @param ratio 计算比例
     * @return 奖励金额结果
     */
    private BigDecimal calculateAmount(BigDecimal orderAmount, BigDecimal ratio) {
        return orderAmount.multiply(ratio).setScale(18, RoundingMode.DOWN).stripTrailingZeros();
    }

    /**
     * 在数据库中按类型进行全量求和某一用户历史上拿到的佣金总额
     *
     * @param userId 获奖用户ID
     * @param type 佣金分类枚举
     * @return 求和总金
     */
    private BigDecimal sumUserCommission(Long userId, GoldQuantCommissionType type) {
        List<GoldQuantCommissionRecord> records = this.list(new LambdaQueryWrapper<GoldQuantCommissionRecord>()
                .select(GoldQuantCommissionRecord::getCommissionAmount)
                .eq(GoldQuantCommissionRecord::getBeneficiaryUserId, userId)
                .eq(GoldQuantCommissionRecord::getCommissionType, type));
        // 将所有相关的金额字段规约累加
        return records.stream()
                .map(GoldQuantCommissionRecord::getCommissionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 将用户统计的相关快照及维度数据拼接融合成后台所需展示的用户行数据对象
     *
     * @param user 当前用户
     * @param snapshot 团队关联快照
     * @param settings 发奖规则设置
     * @param paidWindowCountMap 时间段内有购买记录的映射表
     * @param totalWindowCountMap 全部窗口数量的映射表
     * @param rewardAmountMap 时间段内代数收益映射表
     * @param distributionAmountMap 时间段内级差收益映射表
     * @param timeFiltered 是否启用时间过滤模式
     * @return 封装好的单行统计对象
     */
    private AdminGoldQuantUserStatisticsVO buildGoldQuantUserStatistics(User user,
                                                                        TeamSnapshot snapshot,
                                                                        GoldQuantCommissionSettingsVO settings,
                                                                        Map<Long, Integer> paidWindowCountMap,
                                                                        Map<Long, Integer> totalWindowCountMap,
                                                                        Map<Long, BigDecimal> rewardAmountMap,
                                                                        Map<Long, BigDecimal> distributionAmountMap,
                                                                        boolean timeFiltered) {
        // 重用判断函数：分析该用户等级情况
        int directValidBuyerCount = countDirectValidBuyers(user, snapshot);
        int rewardLevel = matchRewardLevel(directValidBuyerCount, settings);
        int distributionLevel = matchDistributionLevel(countTeamValidWindows(user, snapshot), settings).level;

        // 分离获取数量状态
        int totalWindowCount = totalWindowCountMap.getOrDefault(user.getId(), 0);
        int activeWindowCount = snapshot.ownWindowCountMap.getOrDefault(user.getId(), 0);
        int paidWindowCount = paidWindowCountMap.getOrDefault(user.getId(), 0);

        AdminGoldQuantUserStatisticsVO vo = new AdminGoldQuantUserStatisticsVO();
        vo.setUserId(user.getId());
        vo.setWalletAddress(user.getUserWalletAddress());
        vo.setWindowCount(totalWindowCount);
        vo.setRewardLevel(rewardLevel);
        vo.setDistributionLevel(distributionLevel);
        // 获取预先按时间计算好的聚合统计收益
        vo.setRewardDistributedAmount(rewardAmountMap.getOrDefault(user.getId(), BigDecimal.ZERO));
        vo.setDistributionDistributedAmount(distributionAmountMap.getOrDefault(user.getId(), BigDecimal.ZERO));

        // 根据查询情况，适配不同语境下"购买"和"有效"的定义展示
        if (timeFiltered) {
            vo.setPurchasedCount(paidWindowCount);
            vo.setActiveCount(paidWindowCount);
        } else {
            vo.setPurchasedCount(totalWindowCount);
            vo.setActiveCount(activeWindowCount);
        }
        return vo;
    }

    /**
     * 将全网有效的量化窗口依据用户进行分组统计（Group By）
     *
     * @return key为userId，value为有效窗口数量的Map映射
     */
    private Map<Long, Integer> countTotalWindows() {
        return goldQuantWindowMapper.selectList(new LambdaQueryWrapper<GoldQuantWindow>()
                        .select(GoldQuantWindow::getUserId)
                        .eq(GoldQuantWindow::getStatus, 1)) // 只查询状态激活的窗口
                .stream()
                .filter(window -> window.getUserId() != null)
                .collect(Collectors.groupingBy(
                        GoldQuantWindow::getUserId,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    /**
     * 依据系统流水（账单），统计某个时间段内发生过“购买”或“续期”的量化窗口次数和件数
     *
     * @param startTime 统计开始时间
     * @param endTime 统计结束时间
     * @return 分组计数的Map映射
     */
    private Map<Long, Integer> sumGoldQuantPaidWindowCount(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<UserBill> wrapper = new LambdaQueryWrapper<UserBill>()
                .select(UserBill::getUserId, UserBill::getNum)
                .eq(UserBill::getBillType, BillType.PLATFORM)
                .eq(UserBill::getFundType, FundType.EXPENSE) // 支出类型（用户花钱买）
                .eq(UserBill::getTransactionType, TransactionType.GOLD_QUANT)
                // 筛选特定业务字冠前缀的流水：购买/续费/批量
                .and(order -> order
                        .likeRight(UserBill::getTransactionOrderId, "GQ_BUY_")
                        .or()
                        .likeRight(UserBill::getTransactionOrderId, "GQ_RENEW_")
                        .or()
                        .likeRight(UserBill::getTransactionOrderId, "GQ_BATCH_"));

        if (startTime != null) {
            wrapper.ge(UserBill::getTransactionTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(UserBill::getTransactionTime, endTime);
        }

        // 执行SQL按用户分组计算购买数量总和
        return userBillMapper.selectList(wrapper).stream()
                .filter(bill -> bill.getUserId() != null)
                .collect(Collectors.groupingBy(
                        UserBill::getUserId,
                        Collectors.summingInt(bill -> bill.getNum() == null ? 0 : bill.getNum())));
    }

    /**
     * 按时间段和特定类型，对发放的佣金金额按用户做聚合求和
     *
     * @param startTime 起始时间
     * @param endTime 截止时间
     * @param type 佣金奖项类型
     * @return 返回分组汇总后的用户ID与金额的Map映射
     */
    private Map<Long, BigDecimal> sumGoldQuantCommissionAmount(LocalDateTime startTime, LocalDateTime endTime, GoldQuantCommissionType type) {
        LambdaQueryWrapper<GoldQuantCommissionRecord> wrapper = new LambdaQueryWrapper<GoldQuantCommissionRecord>()
                .select(GoldQuantCommissionRecord::getBeneficiaryUserId, GoldQuantCommissionRecord::getCommissionAmount)
                .eq(GoldQuantCommissionRecord::getCommissionType, type);

        if (startTime != null) {
            wrapper.ge(GoldQuantCommissionRecord::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(GoldQuantCommissionRecord::getCreateTime, endTime);
        }

        // 查出明细后在内存里用Reduce函数做按用户的金额大数累加
        return this.list(wrapper).stream()
                .filter(record -> record.getBeneficiaryUserId() != null && record.getCommissionAmount() != null)
                .collect(Collectors.groupingBy(
                        GoldQuantCommissionRecord::getBeneficiaryUserId,
                        Collectors.reducing(BigDecimal.ZERO, GoldQuantCommissionRecord::getCommissionAmount, BigDecimal::add)));
    }

    /**
     * 辅助工具方法：将内存组装好的List记录数据手动执行分页切片逻辑
     * 用于那些必须经过复合计算，无法直接通过数据库LIMIT进行分页的复杂业务。
     *
     * @param records 完整的内存列表数据集
     * @param page 请求显示的页码
     * @param size 请求每页的数量大小
     * @return 标准的MybatisPlus Page分页容器对象
     */
    private IPage<AdminGoldQuantUserStatisticsVO> pageGoldQuantUserStatistics(List<AdminGoldQuantUserStatisticsVO> records,
                                                                              int page,
                                                                              int size) {
        Page<AdminGoldQuantUserStatisticsVO> result = new Page<>(page, size);
        int fromIndex = Math.max((page - 1) * size, 0); // 计算起始索引下标
        // 边界防护：如果没有数据或者已经超出最大页，返回空壳
        if (records == null || fromIndex >= records.size()) {
            result.setRecords(Collections.emptyList());
            result.setTotal(records == null ? 0 : records.size());
            return result;
        }
        // 计算结束截断索引下标，防止数组越界
        int toIndex = Math.min(fromIndex + size, records.size());
        // 生成分页数据的副本并设置回结果集中
        result.setRecords(new ArrayList<>(records.subList(fromIndex, toIndex)));
        result.setTotal(records.size());
        return result;
    }

    /**
     * 辅助解析字符串日期并转为当日凌晨（最小时间）的方法
     */
    private LocalDateTime parseStartDateTime(String value) {
        return StrUtil.isBlank(value) ? null : DateUtil.parse(value).toLocalDateTime().with(LocalTime.MIN);
    }

    /**
     * 辅助解析字符串日期并转为当日最晚（最大时间）的方法
     */
    private LocalDateTime parseEndDateTime(String value) {
        return StrUtil.isBlank(value) ? null : DateUtil.parse(value).toLocalDateTime().with(LocalTime.MAX);
    }

    /**
     * 根据当前用户构建用于子树匹配的Path前缀
     * 例如该用户的路径是 "0,1," ID是2，则生成前缀为 "0,1,2,"
     *
     * @param user 用户实体
     * @return 路径字符串
     */
    private String pathPrefix(User user) {
        return StrUtil.blankToDefault(user.getPath(), "0,") + user.getId() + ",";
    }

    /**
     * 判断某个用户是否是在给定前缀（prefix）描述的伞下（子节点）
     *
     * @param user 需要进行判断的下属节点用户
     * @param prefix 根节点的path路径约束前缀
     * @return true表示属于子网伞下，false反之
     */
    private boolean isInSubtree(User user, String prefix) {
        return user != null && StrUtil.isNotBlank(user.getPath()) && user.getPath().startsWith(prefix);
    }

    /**
     * 把形如 "0,1,5,23," 这样以逗号分隔的用户树形路径字符串拆解为具体的ID数组集合
     *
     * @param path 路径字段值
     * @return 解析后的ID集合列表（滤除了根节点的0号位）
     */
    private List<Long> parsePathUserIds(String path) {
        if (StrUtil.isBlank(path)) {
            return new ArrayList<>();
        }
        return Arrays.stream(path.split(","))
                .filter(item -> StrUtil.isNotBlank(item) && !"0".equals(item)) // 剔除空串和无效根
                .map(Long::parseLong) // 将其转化成长整型
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 安全保证分页的页码参数（不低于第一页）
     */
    private long safePage(Integer page) {
        return page == null || page <= 0 ? 1 : page;
    }

    /**
     * 安全保证分页的页数参数（默认展示10条）
     */
    private long safeSize(Integer size) {
        return size == null || size <= 0 ? 10 : size;
    }

    /**
     * 内部记录(Record)类：用于暂存一次性查询的大数据集合，充当内存缓存容器
     * 包含所有用户的散列表、每个用户的有效窗口总数、最后开窗时间标记。
     */
    private record TeamSnapshot(Map<Long, User> userMap,
                                Map<Long, Integer> ownWindowCountMap,
                                Map<Long, LocalDateTime> lastWindowTimeMap) {
    }

    /**
     * 内部记录(Record)类：保存一个等级和对应比例结构体的数据载体
     */
    private record DistributionLevel(int level, BigDecimal ratio) {
        // 创建一个用于表示没有任何权限/不符合要求的静态默认实例
        private static DistributionLevel none() {
            return new DistributionLevel(0, BigDecimal.ZERO);
        }
    }
}
