package com.ra.rabnbserver.server.gold.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.VO.gold.GoldQuantCommissionRecordVO;
import com.ra.rabnbserver.VO.gold.GoldQuantCommissionSettingsVO;
import com.ra.rabnbserver.VO.gold.GoldQuantCommissionStatisticsVO;
import com.ra.rabnbserver.VO.gold.GoldQuantTeamAreaVO;
import com.ra.rabnbserver.VO.gold.GoldQuantTeamSummaryVO;
import com.ra.rabnbserver.dto.gold.AdminGoldQuantCommissionQueryDTO;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class GoldQuantCommissionServiceImpl
        extends ServiceImpl<GoldQuantCommissionRecordMapper, GoldQuantCommissionRecord>
        implements GoldQuantCommissionService {
    private static final String SETTINGS_KEY = "GOLD_QUANT_COMMISSION_SETTINGS";

    private final UserMapper userMapper;
    private final GoldQuantWindowMapper goldQuantWindowMapper;
    private final UserBillMapper userBillMapper;
    private final UserBillServe userBillServe;
    private final SystemConfigServe systemConfigServe;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void settleWindowOrder(Long sourceUserId, String sourceOrderId, BigDecimal orderAmount) {
        if (sourceUserId == null || !StrUtil.isNotBlank(sourceOrderId) || orderAmount == null
                || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        long settledCount = this.count(new LambdaQueryWrapper<GoldQuantCommissionRecord>()
                .eq(GoldQuantCommissionRecord::getSourceOrderId, sourceOrderId));
        if (settledCount > 0) {
            log.info("gold quant commission already settled, orderId={}", sourceOrderId);
            return;
        }

        TeamSnapshot snapshot = buildSnapshot();
        User sourceUser = snapshot.userMap.get(sourceUserId);
        if (sourceUser == null) {
            throw new BusinessException("来源用户不存在");
        }
        Long sourceBillId = findBillId(sourceUserId, sourceOrderId);
        GoldQuantCommissionSettingsVO settings = getRules();

        settleReward(sourceUser, sourceOrderId, sourceBillId, orderAmount, settings, snapshot);
        settleDistribution(sourceUser, sourceOrderId, sourceBillId, orderAmount, settings, snapshot);
    }

    @Override
    public GoldQuantTeamSummaryVO getTeamSummary(Long userId) {
        TeamSnapshot snapshot = buildSnapshot();
        User user = snapshot.userMap.get(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        GoldQuantCommissionSettingsVO settings = getRules();
        int directValidBuyerCount = countDirectValidBuyers(user, snapshot);
        int rewardLevel = matchRewardLevel(directValidBuyerCount, settings);
        DistributionLevel distributionLevel = matchDistributionLevel(countTeamValidWindows(user, snapshot), settings);
        List<GoldQuantTeamAreaVO> areas = buildTeamAreas(user, snapshot);

        int bigAreaCount = areas.stream()
                .filter(item -> Boolean.TRUE.equals(item.getBigArea()))
                .map(GoldQuantTeamAreaVO::getValidWindowCount)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0);
        int smallAreaCount = areas.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getBigArea()))
                .map(GoldQuantTeamAreaVO::getValidWindowCount)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);

        GoldQuantTeamSummaryVO vo = new GoldQuantTeamSummaryVO();
        vo.setSelfValidWindowCount(snapshot.ownWindowCountMap.getOrDefault(userId, 0));
        vo.setTeamValidWindowCount(countTeamValidWindows(user, snapshot));
        vo.setBigAreaValidWindowCount(bigAreaCount);
        vo.setSmallAreaValidWindowCount(smallAreaCount);
        vo.setDirectValidBuyerCount(directValidBuyerCount);
        vo.setRewardLevel(rewardLevel);
        vo.setDistributionLevel(distributionLevel.level);
        vo.setRewardGenerationRange(buildRewardGenerationRange(rewardLevel, settings));
        vo.setDistributionRatio(distributionLevel.ratio);
        return vo;
    }

    @Override
    public List<GoldQuantTeamAreaVO> getTeamAreas(Long userId) {
        TeamSnapshot snapshot = buildSnapshot();
        User user = snapshot.userMap.get(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return buildTeamAreas(user, snapshot);
    }

    @Override
    public IPage<GoldQuantCommissionRecordVO> getUserCommissionPage(Long userId, GoldQuantCommissionQueryDTO query) {
        GoldQuantCommissionQueryDTO safeQuery = query == null ? new GoldQuantCommissionQueryDTO() : query;
        LambdaQueryWrapper<GoldQuantCommissionRecord> wrapper = baseQueryByUser(userId, safeQuery);
        wrapper.orderByDesc(GoldQuantCommissionRecord::getCreateTime).orderByDesc(GoldQuantCommissionRecord::getId);
        return this.page(new Page<>(safePage(safeQuery.getPage()), safeSize(safeQuery.getSize())), wrapper)
                .convert(this::toVO);
    }

    @Override
    public IPage<GoldQuantCommissionRecordVO> getAdminCommissionPage(AdminGoldQuantCommissionQueryDTO query) {
        AdminGoldQuantCommissionQueryDTO safeQuery = query == null ? new AdminGoldQuantCommissionQueryDTO() : query;
        LambdaQueryWrapper<GoldQuantCommissionRecord> wrapper = buildAdminQuery(safeQuery);
        wrapper.orderByDesc(GoldQuantCommissionRecord::getCreateTime).orderByDesc(GoldQuantCommissionRecord::getId);
        return this.page(new Page<>(safePage(safeQuery.getPage()), safeSize(safeQuery.getSize())), wrapper)
                .convert(this::toVO);
    }

    @Override
    public GoldQuantCommissionStatisticsVO getAdminCommissionStatistics(AdminGoldQuantCommissionQueryDTO query) {
        AdminGoldQuantCommissionQueryDTO safeQuery = query == null ? new AdminGoldQuantCommissionQueryDTO() : query;
        List<GoldQuantCommissionRecord> records = this.list(buildAdminQuery(safeQuery));
        GoldQuantCommissionStatisticsVO vo = new GoldQuantCommissionStatisticsVO();
        LocalDate today = LocalDate.now();
        for (GoldQuantCommissionRecord record : records) {
            BigDecimal amount = record.getCommissionAmount() == null ? BigDecimal.ZERO : record.getCommissionAmount();
            vo.setTotalAmount(vo.getTotalAmount().add(amount));
            vo.setTotalCount(vo.getTotalCount() + 1);
            if (GoldQuantCommissionType.REWARD.equals(record.getCommissionType())) {
                vo.setRewardAmount(vo.getRewardAmount().add(amount));
            }
            if (GoldQuantCommissionType.DISTRIBUTION.equals(record.getCommissionType())) {
                vo.setDistributionAmount(vo.getDistributionAmount().add(amount));
            }
            if (record.getCreateTime() != null && today.equals(record.getCreateTime().toLocalDate())) {
                vo.setTodayAmount(vo.getTodayAmount().add(amount));
            }
        }
        return vo;
    }

    @Override
    public GoldQuantCommissionSettingsVO getRules() {
        GoldQuantCommissionSettingsVO settings = systemConfigServe.getConfigObject(SETTINGS_KEY, GoldQuantCommissionSettingsVO.class);
        return normalizeSettings(settings);
    }

    private void settleReward(User sourceUser, String sourceOrderId, Long sourceBillId, BigDecimal orderAmount,
                              GoldQuantCommissionSettingsVO settings, TeamSnapshot snapshot) {
        List<User> ancestors = getAncestorsFromNearest(sourceUser, snapshot.userMap);
        for (int i = 0; i < ancestors.size(); i++) {
            User ancestor = ancestors.get(i);
            if (isFromBigArea(ancestor, sourceUser, snapshot)) {
                continue;
            }
            int generation = i + 1;
            int level = matchRewardLevel(countDirectValidBuyers(ancestor, snapshot), settings);
            GoldQuantCommissionSettingsVO.RewardGenerationRule rule = findRewardRule(level, generation, settings);
            if (rule == null || rule.getRatio() == null || rule.getRatio().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal commissionAmount = calculateAmount(orderAmount, rule.getRatio());
            createCommissionAndBill(ancestor, sourceUser, sourceOrderId, sourceBillId, GoldQuantCommissionType.REWARD,
                    level, generation, rule.getRatio(), orderAmount, commissionAmount);
        }
    }

    private void settleDistribution(User sourceUser, String sourceOrderId, Long sourceBillId, BigDecimal orderAmount,
                                    GoldQuantCommissionSettingsVO settings, TeamSnapshot snapshot) {
        int maxGeneration = settings.getDistributionMaxGeneration() == null || settings.getDistributionMaxGeneration() <= 0
                ? 15
                : settings.getDistributionMaxGeneration();
        int maxQualifiedLevel = matchDistributionLevel(countTeamValidWindows(sourceUser, snapshot), settings).level;
        List<User> ancestors = getAncestorsFromNearest(sourceUser, snapshot.userMap);
        for (int i = 0; i < ancestors.size() && i < maxGeneration; i++) {
            User ancestor = ancestors.get(i);
            int generation = i + 1;
            DistributionLevel level = matchDistributionLevel(countTeamValidWindows(ancestor, snapshot), settings);
            boolean canReceive = level.level > 0
                    && level.level > maxQualifiedLevel
                    && !isFromBigArea(ancestor, sourceUser, snapshot);
            if (canReceive) {
                BigDecimal commissionAmount = calculateAmount(orderAmount, level.ratio);
                createCommissionAndBill(ancestor, sourceUser, sourceOrderId, sourceBillId, GoldQuantCommissionType.DISTRIBUTION,
                        level.level, generation, level.ratio, orderAmount, commissionAmount);
            }
            if (level.level > maxQualifiedLevel) {
                maxQualifiedLevel = level.level;
            }
        }
    }

    private void createCommissionAndBill(User beneficiary, User sourceUser, String sourceOrderId, Long sourceBillId,
                                         GoldQuantCommissionType type, Integer level, Integer generation,
                                         BigDecimal ratio, BigDecimal orderAmount, BigDecimal commissionAmount) {
        if (commissionAmount == null || commissionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        long exists = this.count(new LambdaQueryWrapper<GoldQuantCommissionRecord>()
                .eq(GoldQuantCommissionRecord::getSourceOrderId, sourceOrderId)
                .eq(GoldQuantCommissionRecord::getBeneficiaryUserId, beneficiary.getId())
                .eq(GoldQuantCommissionRecord::getCommissionType, type));
        if (exists > 0) {
            return;
        }

        String commissionOrderId = "GQ_COM_" + type.getCode() + "_" + IdWorker.getIdStr();
        String remark = String.format("黄金量化%s，来源用户=%s，来源订单=%s，等级=V%d，代数=%d，比例=%s%%",
                type.getDesc(),
                sourceUser.getUserWalletAddress(),
                sourceOrderId,
                level,
                generation,
                ratio.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString());

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
        this.save(record);

        userBillServe.createBillAndUpdateBalance(
                beneficiary.getId(),
                commissionAmount,
                BillType.PLATFORM,
                FundType.INCOME,
                GoldQuantCommissionType.REWARD.equals(type)
                        ? TransactionType.GOLD_QUANT_REWARD
                        : TransactionType.GOLD_QUANT_DISTRIBUTION,
                remark,
                commissionOrderId,
                null,
                null,
                0,
                null
        );
        Long commissionBillId = findBillId(beneficiary.getId(), commissionOrderId);
        if (commissionBillId != null) {
            this.update(new LambdaUpdateWrapper<GoldQuantCommissionRecord>()
                    .eq(GoldQuantCommissionRecord::getId, record.getId())
                    .set(GoldQuantCommissionRecord::getCommissionBillId, commissionBillId));
        }
    }

    private TeamSnapshot buildSnapshot() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getId, User::getUserWalletAddress, User::getParentId, User::getPath, User::getTeamCount));
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, item -> item, (a, b) -> a, LinkedHashMap::new));

        List<GoldQuantWindow> windows = goldQuantWindowMapper.selectList(new LambdaQueryWrapper<GoldQuantWindow>()
                .select(GoldQuantWindow::getUserId)
                .eq(GoldQuantWindow::getStatus, 1)
                .gt(GoldQuantWindow::getMaintenanceExpireTime, LocalDateTime.now()));
        Map<Long, Integer> ownWindowCountMap = new HashMap<>();
        for (GoldQuantWindow window : windows) {
            if (window.getUserId() != null) {
                ownWindowCountMap.merge(window.getUserId(), 1, Integer::sum);
            }
        }
        return new TeamSnapshot(userMap, ownWindowCountMap);
    }

    private List<GoldQuantTeamAreaVO> buildTeamAreas(User user, TeamSnapshot snapshot) {
        List<User> directChildren = snapshot.userMap.values().stream()
                .filter(item -> Objects.equals(item.getParentId(), user.getId()))
                .collect(Collectors.toList());
        if (directChildren.isEmpty()) {
            return Collections.emptyList();
        }
        List<GoldQuantTeamAreaVO> areas = new ArrayList<>();
        Long bigAreaUserId = null;
        int bigAreaCount = -1;
        for (User child : directChildren) {
            int validWindowCount = countBranchValidWindows(child, snapshot);
            GoldQuantTeamAreaVO vo = new GoldQuantTeamAreaVO();
            vo.setUserId(child.getId());
            vo.setWalletAddress(child.getUserWalletAddress());
            vo.setTeamCount(child.getTeamCount() == null ? 0 : child.getTeamCount());
            vo.setValidWindowCount(validWindowCount);
            vo.setBigArea(false);
            areas.add(vo);
            if (validWindowCount > bigAreaCount || (validWindowCount == bigAreaCount
                    && (bigAreaUserId == null || child.getId() < bigAreaUserId))) {
                bigAreaCount = validWindowCount;
                bigAreaUserId = child.getId();
            }
        }
        Long finalBigAreaUserId = bigAreaUserId;
        areas.forEach(item -> item.setBigArea(Objects.equals(item.getUserId(), finalBigAreaUserId)));
        areas.sort(Comparator.comparing(GoldQuantTeamAreaVO::getBigArea).reversed()
                .thenComparing(GoldQuantTeamAreaVO::getValidWindowCount, Comparator.reverseOrder())
                .thenComparing(GoldQuantTeamAreaVO::getUserId));
        return areas;
    }

    private int countBranchValidWindows(User branchRoot, TeamSnapshot snapshot) {
        int total = snapshot.ownWindowCountMap.getOrDefault(branchRoot.getId(), 0);
        String prefix = pathPrefix(branchRoot);
        for (User user : snapshot.userMap.values()) {
            if (!Objects.equals(user.getId(), branchRoot.getId()) && isInSubtree(user, prefix)) {
                total += snapshot.ownWindowCountMap.getOrDefault(user.getId(), 0);
            }
        }
        return total;
    }

    private int countTeamValidWindows(User user, TeamSnapshot snapshot) {
        int total = 0;
        String prefix = pathPrefix(user);
        for (User item : snapshot.userMap.values()) {
            if (!Objects.equals(item.getId(), user.getId()) && isInSubtree(item, prefix)) {
                total += snapshot.ownWindowCountMap.getOrDefault(item.getId(), 0);
            }
        }
        return total;
    }

    private int countDirectValidBuyers(User user, TeamSnapshot snapshot) {
        int count = 0;
        for (User item : snapshot.userMap.values()) {
            if (Objects.equals(item.getParentId(), user.getId())
                    && snapshot.ownWindowCountMap.getOrDefault(item.getId(), 0) > 0) {
                count++;
            }
        }
        return count;
    }

    private boolean isFromBigArea(User ancestor, User sourceUser, TeamSnapshot snapshot) {
        Long branchChildId = getDirectChildIdUnderAncestor(ancestor, sourceUser);
        if (branchChildId == null) {
            return false;
        }
        Long bigAreaChildId = findBigAreaChildId(ancestor, snapshot);
        return bigAreaChildId != null && bigAreaChildId.equals(branchChildId);
    }

    private Long findBigAreaChildId(User user, TeamSnapshot snapshot) {
        return buildTeamAreas(user, snapshot).stream()
                .filter(item -> Boolean.TRUE.equals(item.getBigArea()))
                .map(GoldQuantTeamAreaVO::getUserId)
                .findFirst()
                .orElse(null);
    }

    private Long getDirectChildIdUnderAncestor(User ancestor, User sourceUser) {
        if (ancestor == null || sourceUser == null || ancestor.getId() == null || sourceUser.getId() == null
                || Objects.equals(ancestor.getId(), sourceUser.getId())) {
            return null;
        }
        List<Long> pathIds = parsePathUserIds(sourceUser.getPath());
        int ancestorIndex = pathIds.indexOf(ancestor.getId());
        if (ancestorIndex >= 0) {
            if (ancestorIndex + 1 < pathIds.size()) {
                return pathIds.get(ancestorIndex + 1);
            }
            return sourceUser.getId();
        }
        if (Objects.equals(ancestor.getId(), sourceUser.getParentId())) {
            return sourceUser.getId();
        }
        return null;
    }

    private List<User> getAncestorsFromNearest(User user, Map<Long, User> userMap) {
        List<Long> ids = parsePathUserIds(user.getPath());
        Collections.reverse(ids);
        if (user.getParentId() != null && user.getParentId() > 0
                && (ids.isEmpty() || !Objects.equals(ids.get(0), user.getParentId()))) {
            ids.add(0, user.getParentId());
        }
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

    private int matchRewardLevel(int directValidBuyerCount, GoldQuantCommissionSettingsVO settings) {
        return settings.getRewardLevels().stream()
                .filter(item -> item.getDirectValidBuyerCount() != null && directValidBuyerCount >= item.getDirectValidBuyerCount())
                .map(GoldQuantCommissionSettingsVO.RewardLevelRule::getLevel)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
    }

    private GoldQuantCommissionSettingsVO.RewardGenerationRule findRewardRule(
            int level, int generation, GoldQuantCommissionSettingsVO settings) {
        return settings.getRewardRules().stream()
                .filter(item -> Objects.equals(item.getLevel(), level))
                .filter(item -> item.getMinGeneration() == null || generation >= item.getMinGeneration())
                .filter(item -> item.getMaxGeneration() == null || generation <= item.getMaxGeneration())
                .findFirst()
                .orElse(null);
    }

    private DistributionLevel matchDistributionLevel(int teamValidWindowCount, GoldQuantCommissionSettingsVO settings) {
        return settings.getDistributionLevels().stream()
                .filter(item -> item.getTeamValidWindowCount() != null && teamValidWindowCount >= item.getTeamValidWindowCount())
                .max(Comparator.comparing(GoldQuantCommissionSettingsVO.DistributionLevelRule::getTeamValidWindowCount))
                .map(item -> new DistributionLevel(item.getLevel() == null ? 0 : item.getLevel(),
                        item.getRatio() == null ? BigDecimal.ZERO : item.getRatio()))
                .orElse(DistributionLevel.none());
    }

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

    private GoldQuantCommissionSettingsVO normalizeSettings(GoldQuantCommissionSettingsVO settings) {
        GoldQuantCommissionSettingsVO result = settings == null ? new GoldQuantCommissionSettingsVO() : settings;
        if (result.getRewardLevels() == null || result.getRewardLevels().isEmpty()) {
            result.setRewardLevels(defaultRewardLevels());
        }
        if (result.getRewardRules() == null || result.getRewardRules().isEmpty()) {
            result.setRewardRules(defaultRewardRules());
        }
        if (result.getDistributionLevels() == null || result.getDistributionLevels().isEmpty()) {
            result.setDistributionLevels(defaultDistributionLevels());
        }
        if (result.getDistributionMaxGeneration() == null || result.getDistributionMaxGeneration() <= 0) {
            result.setDistributionMaxGeneration(15);
        }
        result.getRewardLevels().sort(Comparator.comparing(GoldQuantCommissionSettingsVO.RewardLevelRule::getLevel));
        result.getRewardRules().sort(Comparator.comparing(GoldQuantCommissionSettingsVO.RewardGenerationRule::getLevel));
        result.getDistributionLevels().sort(Comparator.comparing(GoldQuantCommissionSettingsVO.DistributionLevelRule::getTeamValidWindowCount));
        return result;
    }

    private List<GoldQuantCommissionSettingsVO.RewardLevelRule> defaultRewardLevels() {
        List<GoldQuantCommissionSettingsVO.RewardLevelRule> rules = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            GoldQuantCommissionSettingsVO.RewardLevelRule rule = new GoldQuantCommissionSettingsVO.RewardLevelRule();
            rule.setLevel(i);
            rule.setDirectValidBuyerCount(i);
            rules.add(rule);
        }
        return rules;
    }

    private List<GoldQuantCommissionSettingsVO.RewardGenerationRule> defaultRewardRules() {
        List<GoldQuantCommissionSettingsVO.RewardGenerationRule> rules = new ArrayList<>();
        rules.add(rewardRule(1, 1, 1, "0.05"));
        rules.add(rewardRule(2, 2, 2, "0.03"));
        rules.add(rewardRule(3, 3, 10, "0.01"));
        rules.add(rewardRule(4, 11, 14, "0.03"));
        rules.add(rewardRule(5, 15, null, "0.05"));
        return rules;
    }

    private GoldQuantCommissionSettingsVO.RewardGenerationRule rewardRule(
            int level, int minGeneration, Integer maxGeneration, String ratio) {
        GoldQuantCommissionSettingsVO.RewardGenerationRule rule = new GoldQuantCommissionSettingsVO.RewardGenerationRule();
        rule.setLevel(level);
        rule.setMinGeneration(minGeneration);
        rule.setMaxGeneration(maxGeneration);
        rule.setRatio(new BigDecimal(ratio));
        return rule;
    }

    private List<GoldQuantCommissionSettingsVO.DistributionLevelRule> defaultDistributionLevels() {
        List<GoldQuantCommissionSettingsVO.DistributionLevelRule> rules = new ArrayList<>();
        rules.add(distributionRule(1, 50, "0.15"));
        rules.add(distributionRule(2, 100, "0.175"));
        rules.add(distributionRule(3, 300, "0.20"));
        rules.add(distributionRule(4, 500, "0.225"));
        rules.add(distributionRule(5, 1000, "0.24"));
        rules.add(distributionRule(6, 2000, "0.255"));
        rules.add(distributionRule(7, 5000, "0.27"));
        rules.add(distributionRule(8, 10000, "0.285"));
        rules.add(distributionRule(9, 20000, "0.30"));
        return rules;
    }

    private GoldQuantCommissionSettingsVO.DistributionLevelRule distributionRule(int level, int count, String ratio) {
        GoldQuantCommissionSettingsVO.DistributionLevelRule rule = new GoldQuantCommissionSettingsVO.DistributionLevelRule();
        rule.setLevel(level);
        rule.setTeamValidWindowCount(count);
        rule.setRatio(new BigDecimal(ratio));
        return rule;
    }

    private LambdaQueryWrapper<GoldQuantCommissionRecord> baseQueryByUser(Long userId, GoldQuantCommissionQueryDTO query) {
        LambdaQueryWrapper<GoldQuantCommissionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GoldQuantCommissionRecord::getBeneficiaryUserId, userId);
        wrapper.eq(query.getCommissionType() != null, GoldQuantCommissionRecord::getCommissionType, query.getCommissionType());
        applyTimeRange(wrapper, query.getStartTime(), query.getEndTime());
        return wrapper;
    }

    private LambdaQueryWrapper<GoldQuantCommissionRecord> buildAdminQuery(AdminGoldQuantCommissionQueryDTO query) {
        LambdaQueryWrapper<GoldQuantCommissionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getBeneficiaryUserId() != null, GoldQuantCommissionRecord::getBeneficiaryUserId, query.getBeneficiaryUserId());
        wrapper.like(StrUtil.isNotBlank(query.getBeneficiaryWalletAddress()), GoldQuantCommissionRecord::getBeneficiaryWalletAddress, query.getBeneficiaryWalletAddress());
        wrapper.eq(query.getSourceUserId() != null, GoldQuantCommissionRecord::getSourceUserId, query.getSourceUserId());
        wrapper.like(StrUtil.isNotBlank(query.getSourceWalletAddress()), GoldQuantCommissionRecord::getSourceWalletAddress, query.getSourceWalletAddress());
        wrapper.eq(StrUtil.isNotBlank(query.getSourceOrderId()), GoldQuantCommissionRecord::getSourceOrderId, query.getSourceOrderId());
        wrapper.eq(query.getCommissionType() != null, GoldQuantCommissionRecord::getCommissionType, query.getCommissionType());
        wrapper.eq(query.getLevel() != null, GoldQuantCommissionRecord::getLevel, query.getLevel());
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

    private void applyTimeRange(LambdaQueryWrapper<GoldQuantCommissionRecord> wrapper, String startTime, String endTime) {
        if (StrUtil.isNotBlank(startTime)) {
            wrapper.ge(GoldQuantCommissionRecord::getCreateTime, DateUtil.parse(startTime).toLocalDateTime().with(LocalTime.MIN));
        }
        if (StrUtil.isNotBlank(endTime)) {
            wrapper.le(GoldQuantCommissionRecord::getCreateTime, DateUtil.parse(endTime).toLocalDateTime().with(LocalTime.MAX));
        }
    }

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

    private Long findBillId(Long userId, String orderId) {
        UserBill bill = userBillMapper.selectOne(new LambdaQueryWrapper<UserBill>()
                .select(UserBill::getId)
                .eq(UserBill::getUserId, userId)
                .eq(UserBill::getTransactionOrderId, orderId)
                .last("LIMIT 1"));
        return bill == null ? null : bill.getId();
    }

    private BigDecimal calculateAmount(BigDecimal orderAmount, BigDecimal ratio) {
        return orderAmount.multiply(ratio).setScale(18, RoundingMode.DOWN).stripTrailingZeros();
    }

    private String pathPrefix(User user) {
        return StrUtil.blankToDefault(user.getPath(), "0,") + user.getId() + ",";
    }

    private boolean isInSubtree(User user, String prefix) {
        return user != null && StrUtil.isNotBlank(user.getPath()) && user.getPath().startsWith(prefix);
    }

    private List<Long> parsePathUserIds(String path) {
        if (StrUtil.isBlank(path)) {
            return new ArrayList<>();
        }
        return Arrays.stream(path.split(","))
                .filter(item -> StrUtil.isNotBlank(item) && !"0".equals(item))
                .map(Long::parseLong)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private long safePage(Integer page) {
        return page == null || page <= 0 ? 1 : page;
    }

    private long safeSize(Integer size) {
        return size == null || size <= 0 ? 10 : size;
    }

    private record TeamSnapshot(Map<Long, User> userMap, Map<Long, Integer> ownWindowCountMap) {
    }

    private record DistributionLevel(int level, BigDecimal ratio) {
        private static DistributionLevel none() {
            return new DistributionLevel(0, BigDecimal.ZERO);
        }
    }
}
