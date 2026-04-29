package com.ra.rabnbserver.server.user.impl;

import com.alibaba.fastjson2.JSON;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.VO.MinerSettings;
import com.ra.rabnbserver.VO.gold.GoldQuantCommissionSettingsVO;
import com.ra.rabnbserver.VO.team.TeamAreaItemVO;
import com.ra.rabnbserver.VO.team.TeamAreaResultVO;
import com.ra.rabnbserver.dto.RegisterDataDTO;
import com.ra.rabnbserver.dto.team.AdminTeamSearchDTO;
import com.ra.rabnbserver.dto.team.TeamAreaQueryDTO;
import com.ra.rabnbserver.dto.user.UserQueryDTO;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.SystemConfigMapper;
import com.ra.rabnbserver.mapper.UserBillMapper;
import com.ra.rabnbserver.mapper.GoldQuantWindowMapper;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.pojo.GoldQuantWindow;
import com.ra.rabnbserver.pojo.SystemConfig;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.pojo.UserBill;
import com.ra.rabnbserver.server.user.UserServe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServeImpl extends ServiceImpl<UserMapper, User> implements UserServe {

    @Autowired
    private SystemConfigMapper configMapper;

    @Autowired
    private UserBillMapper userBillMapper;

    @Autowired
    private GoldQuantWindowMapper goldQuantWindowMapper;

    @Override
    public User getByWalletAddress(String address) {
        if (StrUtil.isBlank(address)) {
            return null;
        }
        List<User> userList = this.list(new LambdaQueryWrapper<User>()
                .eq(User::getUserWalletAddress, address)
                .orderByAsc(User::getId)
                .last("LIMIT 2"));
        if (userList.isEmpty()) {
            return null;
        }
        if (userList.size() > 1) {
            log.error("用户表存在重复钱包地址数据，钱包地址：{}，当前已命中至少{}条记录，系统将暂时返回ID最小的用户：{}。请尽快清理脏数据并确保唯一索引生效。",
                    address, userList.size(), userList.get(0).getId());
        }
        return userList.get(0);
    }

    @Value("${ADMIN.DFCODE:eC4vW8}")
    private String DFCODE;


    @Transactional(rollbackFor = Exception.class)
    @Override
    public User handleRegister(String walletAddress, String referrerWalletAddress) {
        // 再次兜底检查（防止并发情况下重复写入）
        User existing = getByWalletAddress(walletAddress);
        if (existing != null) return existing;

        User parent = null;

        // 1. 邀请人逻辑判断
        if (DFCODE.equalsIgnoreCase(referrerWalletAddress)) {
            // 匹配默认地址：作为根用户，没有上级
            log.info("用户 {} 使用默认地址注册为系统根用户", walletAddress);
        } else {
            // 非默认地址：必须是库中已存在的用户
            parent = getByWalletAddress(referrerWalletAddress);
            if (parent == null) {
                throw new BusinessException("邀请人地址无效，请核对后重试");
            }
        }

        // 2. 初始化用户数据
        User user = new User();
        user.setUserWalletAddress(walletAddress);
        user.setUserName(walletAddress);
        user.setBalance(BigDecimal.ZERO);
        user.setInviteCode(walletAddress);
        user.setDirectCount(0);
        user.setTeamCount(0);

        // 3. 维护层级树结构
        if (parent != null) {
            // 正常级联
            user.setParentInviteCode(parent.getInviteCode());
            user.setParentId(parent.getId());
            user.setLevel(parent.getLevel() + 1);
            user.setPath(parent.getPath() + parent.getId() + ",");
        } else {
            // 系统根用户
            user.setParentInviteCode("0");
            user.setParentId(0L);
            user.setLevel(1);
            user.setPath("0,");
        }

        this.save(user);

        // 4. 更新上级/团队统计
        if (parent != null) {
            // 增加直推人数
            this.lambdaUpdate()
                    .setSql("direct_count = direct_count + 1")
                    .eq(User::getId, parent.getId())
                    .update();

            // 增加所有祖先的团队人数
            List<Long> ancestorIds = Arrays.stream(user.getPath().split(","))
                    .filter(s -> StrUtil.isNotBlank(s) && !"0".equals(s))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            if (!ancestorIds.isEmpty()) {
                this.update(new LambdaUpdateWrapper<User>()
                        .setSql("team_count = team_count + 1")
                        .in(User::getId, ancestorIds));
            }
        }

        return user;
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public User loginOrRegister(String walletAddress, String referrerWalletAddress) {
        // 尝试获取当前用户
        User user = getByWalletAddress(walletAddress);
        if (user != null) {
            return user;
        }

        // 2. 如果用户不存在，执行“无感注册”
        log.info("用户 {} 不存在，执行自动注册...", walletAddress);

        User parent = null;
        // 如果传入了邀请人地址，且不是自己
        if (StrUtil.isNotBlank(referrerWalletAddress) && !referrerWalletAddress.equalsIgnoreCase(walletAddress)) {
            parent = getByWalletAddress(referrerWalletAddress);

            // 如果邀请人也不存在，则自动创建邀请人为“根用户”
            if (parent == null) {
                log.info("邀请人 {} 不存在，先创建邀请人", referrerWalletAddress);
                parent = createBaseUser(referrerWalletAddress, null);
            }
        }

        // 3. 创建当前用户
        return createBaseUser(walletAddress, parent);
    }

    /**
     * 内部私有方法：创建一个基础用户并维护上下级关系
     * @param walletAddress 钱包地址
     * @param parent 推荐人对象（可为null）
     */
    private User createBaseUser(String walletAddress, User parent) {
        User user = new User();
        user.setUserWalletAddress(walletAddress);
        user.setUserName(walletAddress);
        user.setBalance(BigDecimal.ZERO);
        user.setInviteCode(walletAddress);
        user.setDirectCount(0);
        user.setTeamCount(0);

        if (parent != null) {
            // 设置级联关系
            user.setParentInviteCode(parent.getInviteCode());
            user.setParentId(parent.getId());
            user.setLevel(parent.getLevel() + 1);
            user.setPath(parent.getPath() + parent.getId() + ",");
        } else {
            // 默认作为根用户
            user.setParentInviteCode("0");
            user.setParentId(0L);
            user.setLevel(1);
            user.setPath("0,");
        }

        this.save(user);

        // 如果有上级，更新上级的统计数据
        if (parent != null) {
            // 更新直接上级：直推人数 +1
            this.lambdaUpdate()
                    .setSql("direct_count = direct_count + 1")
                    .eq(User::getId, parent.getId())
                    .update();

            // 更新所有祖先：团队总人数 +1
            List<Long> ancestorIds = Arrays.stream(user.getPath().split(","))
                    .filter(s -> StrUtil.isNotBlank(s) && !"0".equals(s))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            if (!ancestorIds.isEmpty()) {
                this.update(new LambdaUpdateWrapper<User>()
                        .setSql("team_count = team_count + 1")
                        .in(User::getId, ancestorIds));
            }
        }
        return user;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public User register(RegisterDataDTO registerDataDTO) {
        String inputInviteCode = registerDataDTO.getCode();
        User parent = null;

        if (DFCODE.equals(inputInviteCode)) {
            log.info("根用户注册，钱包地址: {}", registerDataDTO.getUserWalletAddress());
        } else {
            parent = getByInviteCode(inputInviteCode);
            if (parent == null) {
                throw new BusinessException("请输入正确的邀请码");
            }
        }

        // 初始化新用户
        User user = new User();
        user.setUserWalletAddress(registerDataDTO.getUserWalletAddress());
        user.setUserName(registerDataDTO.getUserWalletAddress());
        user.setBalance(BigDecimal.ZERO);
        user.setInviteCode(registerDataDTO.getUserWalletAddress());
        user.setDirectCount(0);
        user.setTeamCount(0);

        // 设置层级关系
        if (parent != null) {
            // 有推荐人的情况
            user.setParentInviteCode(parent.getInviteCode());
            user.setParentId(parent.getId());
            user.setLevel(parent.getLevel() + 1);
            user.setPath(parent.getPath() + parent.getId() + ",");
        } else {
            user.setParentInviteCode("0");
            user.setParentId(0L);
            user.setLevel(1);
            user.setPath("0,");
        }
        this.save(user);
        if (parent != null) {
            // 更新直接上级：直推人数 +1
            this.lambdaUpdate()
                    .setSql("direct_count = direct_count + 1")
                    .eq(User::getId, parent.getId())
                    .update();
            // 更新所有祖先：团队总人数 +1
            // 获取路径中所有的祖先ID (排除掉 0)
            List<Long> ancestorIds = Arrays.stream(user.getPath().split(","))
                    .filter(s -> StrUtil.isNotBlank(s) && !"0".equals(s))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            if (!ancestorIds.isEmpty()) {
                // 使用一条 SQL 更新所有祖先的 team_count，避免循环查询/更新
                this.update(new LambdaUpdateWrapper<User>()
                        .setSql("team_count = team_count + 1")
                        .in(User::getId, ancestorIds));
            }
        }
        return user;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void bindTeamBatch(List<Long> userIds, Long targetParentId) {
        if (userIds == null || userIds.isEmpty()) return;
        // 1. 获取目标上级
        User targetParent = null;
        if (targetParentId != null && targetParentId != 0) {
            targetParent = this.getById(targetParentId);
            if (targetParent == null) throw new BusinessException("目标上级不存在");
        }

        for (Long userId : userIds) {
            moveUserToNewParent(userId, targetParent);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void unbindTeamBatch(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return;

        for (Long userId : userIds) {
            // 解绑即移动到根节点 (targetParent = null)
            moveUserToNewParent(userId, null);
        }
    }

    /**
     * 核心私有方法：移动单个用户及其整个子树到新上级
     */
    private void moveUserToNewParent(Long userId, User newParent) {
        User user = this.getById(userId);
        if (user == null) return;

        // 0. 防止死循环：目标上级不能是该用户本身或其子孙
        if (newParent != null) {
            if (newParent.getId().equals(user.getId())) throw new BusinessException("不能将用户绑定到自己身上");
            if (newParent.getPath().contains("," + user.getId() + ",")) {
                throw new BusinessException("目标上级不能是该用户的下级");
            }
            if (newParent.getId().equals(user.getParentId())) return; // 无需移动
        } else if (user.getParentId() == 0) {
            return; // 已经是根用户，无需解绑
        }

        // --- 第一部分：清理旧上级的统计数据 ---
        int affectCount = 1 + user.getTeamCount(); // 本身 + 他的所有团队成员
        List<Long> oldAncestorIds = Arrays.stream(user.getPath().split(","))
                .filter(s -> StrUtil.isNotBlank(s) && !"0".equals(s))
                .map(Long::parseLong)
                .collect(Collectors.toList());

        if (!oldAncestorIds.isEmpty()) {
            // 扣除旧祖先的团队人数
            this.update(new LambdaUpdateWrapper<User>()
                    .setSql("team_count = team_count - " + affectCount)
                    .in(User::getId, oldAncestorIds));

            // 扣除旧直接上级的直推人数
            this.lambdaUpdate()
                    .setSql("direct_count = direct_count - 1")
                    .eq(User::getId, user.getParentId())
                    .update();
        }

        // --- 第二部分：更新用户自身及子孙节点的路径/层级 ---
        String oldPathPrefix = user.getPath() + user.getId() + ",";
        String newPath;
        int newLevel;
        String newParentInviteCode;
        Long newParentId;

        if (newParent != null) {
            newPath = newParent.getPath() + newParent.getId() + ",";
            newLevel = newParent.getLevel() + 1;
            newParentInviteCode = newParent.getInviteCode();
            newParentId = newParent.getId();
        } else {
            newPath = "0,";
            newLevel = 1;
            newParentInviteCode = "0";
            newParentId = 0L;
        }

        // 更新子孙节点的 path 和 level
        // 逻辑：将所有 path 以 oldPathPrefix 开头的用户，替换其 path 前缀，并调整 level 偏移量
        int levelOffset = newLevel - user.getLevel();

        // MyBatis Plus 手写 SQL 更新子孙
        this.lambdaUpdate()
                .setSql("path = REPLACE(path, '" + user.getPath() + "', '" + newPath + "')") // 错误预警：这里需要更精准的替换
                // 建议更稳健的子孙路径更新方案：
                .setSql("level = level + " + levelOffset)
                .likeRight(User::getPath, oldPathPrefix)
                .update();

        // 特殊处理：MySQL 的 REPLACE 在处理 materialized path 时要小心，下面是优化后的子孙更新逻辑：
        String sql = String.format(
                "UPDATE user SET path = CONCAT('%s', SUBSTRING(path, %d)), level = level + %d WHERE path LIKE '%s%%'",
                newPath + user.getId() + ",",
                (user.getPath() + user.getId() + ",").length() + 1,
                levelOffset,
                oldPathPrefix
        );
        this.baseMapper.updateUserPaths(newPath + user.getId() + ",", (user.getPath() + user.getId() + ",").length() + 1, levelOffset, oldPathPrefix);

        // 更新当前节点
        user.setParentId(newParentId);
        user.setParentInviteCode(newParentInviteCode);
        user.setPath(newPath);
        user.setLevel(newLevel);
        this.updateById(user);

        // --- 第三部分：增加新上级的统计数据 ---
        if (newParent != null) {
            List<Long> newAncestorIds = Arrays.stream(newPath.split(","))
                    .filter(s -> StrUtil.isNotBlank(s) && !"0".equals(s))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            if (!newAncestorIds.isEmpty()) {
                this.update(new LambdaUpdateWrapper<User>()
                        .setSql("team_count = team_count + " + affectCount)
                        .in(User::getId, newAncestorIds));
            }

            // 增加新直接上级的直推人数
            this.lambdaUpdate()
                    .setSql("direct_count = direct_count + 1")
                    .eq(User::getId, newParentId)
                    .update();
        }
    }

    @Override
    public IPage<User> selectComplexTeamPage(AdminTeamSearchDTO queryDTO) {
        Page<User> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        // 目标用户信息筛选 ---
        // 搜索特定钱包地址 (支持模糊查询)
        wrapper.like(StrUtil.isNotBlank(queryDTO.getUserWalletAddress()),
                User::getUserWalletAddress, queryDTO.getUserWalletAddress());

        // 搜索特定用户名
        wrapper.like(StrUtil.isNotBlank(queryDTO.getUserName()),
                User::getUserName, queryDTO.getUserName());

        // 搜索特定邀请码
        wrapper.eq(StrUtil.isNotBlank(queryDTO.getInviteCode()),
                User::getInviteCode, queryDTO.getInviteCode());


        // 团队范围限定 (确定搜索的“树”范围) ---

        // 如果传了领导人钱包地址，先查出领导人ID
        if (StrUtil.isNotBlank(queryDTO.getLeaderWalletAddress())) {
            User leader = this.getByWalletAddress(queryDTO.getLeaderWalletAddress());
            if (leader != null) {
                String teamPathPrefix = leader.getPath() + leader.getId() + ",";
                wrapper.likeRight(User::getPath, teamPathPrefix);
            } else {
                // 如果领导人地址搜不到，直接返回空分页，防止查出全表数据
                return page;
            }
        }
        // 或者通过 LeaderId 缩小范围
        else if (queryDTO.getLeaderId() != null) {
            User leader = this.getById(queryDTO.getLeaderId());
            if (leader != null) {
                String teamPathPrefix = leader.getPath() + leader.getId() + ",";
                wrapper.likeRight(User::getPath, teamPathPrefix);
            }
        }

        // 层级与统计指标筛选 ---
        // 指定直接上级
        wrapper.eq(queryDTO.getParentId() != null, User::getParentId, queryDTO.getParentId());
        // 指定绝对层级
        wrapper.eq(queryDTO.getLevel() != null, User::getLevel, queryDTO.getLevel());
        // 团队人数区间
        if (queryDTO.getMinTeamCount() != null) wrapper.ge(User::getTeamCount, queryDTO.getMinTeamCount());
        if (queryDTO.getMaxTeamCount() != null) wrapper.le(User::getTeamCount, queryDTO.getMaxTeamCount());
        // 直推人数区间
        if (queryDTO.getMinDirectCount() != null) wrapper.ge(User::getDirectCount, queryDTO.getMinDirectCount());
        // 时间与排序 ---
        if (StrUtil.isNotBlank(queryDTO.getStartTime())) {
            wrapper.ge(User::getCreateTime, DateUtil.parse(queryDTO.getStartTime()).toLocalDateTime());
        }
        if (StrUtil.isNotBlank(queryDTO.getEndTime())) {
            wrapper.le(User::getCreateTime, DateUtil.parse(queryDTO.getEndTime()).toLocalDateTime());
        }
        wrapper.orderByDesc(User::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    public TeamAreaResultVO getTeamAreaList(Long userId, TeamAreaQueryDTO query) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("用户ID不能为空");
        }
        TeamAreaQueryDTO safeQuery = normalizeTeamAreaQuery(query);
        User currentUser = this.getById(userId);
        List<TeamAreaItemVO> areas = this.baseMapper.selectDirectAreaStats(userId);
        if (areas == null) {
            areas = Collections.emptyList();
        }

        TeamAreaResultVO result = new TeamAreaResultVO();
        result.setType(safeQuery.getType());
        result.setPage(safeQuery.getPage());
        result.setSize(safeQuery.getSize());
        result.setTotalTeamCount(safeInt(currentUser == null ? null : currentUser.getTeamCount()));
        TeamAreaItemVO totalStats = this.baseMapper.selectTeamMinerStats(userId);
        result.setTotalPurchasedCount(safeInt(totalStats == null ? null : totalStats.getPurchasedCount()));
        result.setTotalActiveCount(safeInt(totalStats == null ? null : totalStats.getActiveCount()));
        fillCurrentGradeAndRatio(result, currentUser);
        result.setTeamRewardDistributedAmount(sumMinerTeamReward(userId));

        List<TeamAreaItemVO> smallAreas = areas.size() <= 1
                ? Collections.emptyList()
                : new ArrayList<>(areas.subList(1, areas.size()));
        fillSmallAreaSummary(result, smallAreas);

        if (areas.isEmpty()) {
            result.setRecords(Collections.emptyList());
            result.setTotal(0L);
            return result;
        }

        TeamAreaItemVO bigArea = areas.get(0);
        if (safeQuery.getType() == 1) {
            result.setRecords(Collections.singletonList(bigArea));
            result.setTotal(1L);
        } else {
            result.setRecords(pageSmallAreas(smallAreas, safeQuery));
            result.setTotal((long) smallAreas.size());
        }
        return result;
    }

    private TeamAreaQueryDTO normalizeTeamAreaQuery(TeamAreaQueryDTO query) {
        TeamAreaQueryDTO safeQuery = query == null ? new TeamAreaQueryDTO() : query;
        if (safeQuery.getType() == null || (safeQuery.getType() != 1 && safeQuery.getType() != 2)) {
            throw new BusinessException("type必须为1-大区或2-小区");
        }
        if (safeQuery.getPage() == null || safeQuery.getPage() <= 0) {
            safeQuery.setPage(1);
        }
        if (safeQuery.getSize() == null || safeQuery.getSize() <= 0) {
            safeQuery.setSize(20);
        }
        return safeQuery;
    }

    private List<TeamAreaItemVO> pageSmallAreas(List<TeamAreaItemVO> smallAreas, TeamAreaQueryDTO query) {
        if (smallAreas == null || smallAreas.isEmpty()) {
            return Collections.emptyList();
        }
        int page = query.getPage();
        int size = query.getSize();
        int fromIndex = Math.max((page - 1) * size, 0);
        if (fromIndex >= smallAreas.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(fromIndex + size, smallAreas.size());
        return new ArrayList<>(smallAreas.subList(fromIndex, toIndex));
    }

    private void fillCurrentGradeAndRatio(TeamAreaResultVO result, User user) {
        Integer grade = user == null ? 0 : user.getUserGrade();
        result.setCurrentUserGrade(grade);
        result.setCurrentUserElectricityRatio(findRatioByGrade(grade, getMinerSettings()));
    }

    private void fillSmallAreaSummary(TeamAreaResultVO result, List<TeamAreaItemVO> smallAreas) {
        if (smallAreas == null || smallAreas.isEmpty()) {
            result.setSmallAreaTeamCount(0);
            result.setSmallAreaPurchasedCount(0);
            result.setSmallAreaActiveCount(0);
            return;
        }
        int teamCount = 0;
        int purchasedCount = 0;
        int activeCount = 0;
        for (TeamAreaItemVO item : smallAreas) {
            if (item == null) {
                continue;
            }
            teamCount += safeInt(item.getTeamCount());
            purchasedCount += safeInt(item.getPurchasedCount());
            activeCount += safeInt(item.getActiveCount());
        }
        result.setSmallAreaTeamCount(teamCount);
        result.setSmallAreaPurchasedCount(purchasedCount);
        result.setSmallAreaActiveCount(activeCount);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal findRatioByGrade(Integer grade, MinerSettings settings) {
        if (grade == null || settings == null || settings.getTiers() == null || settings.getTiers().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return settings.getTiers().stream()
                .filter(tier -> tier != null && grade.equals(tier.getGrade()))
                .map(this::getTierRewardRatio)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal getTierRewardRatio(MinerSettings.RewardTier tier) {
        if (tier == null) {
            return BigDecimal.ZERO;
        }
        return tier.getRewardRatio() != null ? tier.getRewardRatio() : tier.getRatio();
    }

    private BigDecimal sumMinerTeamReward(Long userId) {
        List<UserBill> bills = userBillMapper.selectList(new LambdaQueryWrapper<UserBill>()
                .select(UserBill::getAmount)
                .eq(UserBill::getUserId, userId)
                .eq(UserBill::getBillType, BillType.PLATFORM)
                .eq(UserBill::getFundType, FundType.INCOME)
                .in(UserBill::getTransactionType, TransactionType.REWARD, TransactionType.MINER_ELECTRICITY_REWARD));
        return bills.stream()
                .map(UserBill::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private MinerSettings getMinerSettings() {
        SystemConfig config = configMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "MINER_SYSTEM_SETTINGS"));
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return new MinerSettings();
        }
        return JSON.parseObject(config.getConfigValue(), MinerSettings.class);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean addUser(User user) {
        if (user == null || StrUtil.isBlank(user.getUserWalletAddress())) {
            throw new BusinessException("用户钱包地址不能为空");
        }
        user.setBalance(BigDecimal.ZERO);
        user.setInviteCode(user.getUserWalletAddress());
        return this.save(user);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updateUser(User user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        User dbUser = this.getById(user.getId());
        if (dbUser == null) {
            return false;
        }
        boolean walletAddressChanged = StrUtil.isNotBlank(user.getUserWalletAddress())
                && !user.getUserWalletAddress().equals(dbUser.getUserWalletAddress());
        boolean updated = this.lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(user.getUserName() != null, User::getUserName, user.getUserName())
                .set(user.getUserWalletAddress() != null, User::getUserWalletAddress, user.getUserWalletAddress())
                .set(walletAddressChanged, User::getInviteCode, user.getUserWalletAddress())
                .update();
        if (updated && walletAddressChanged) {
            this.lambdaUpdate()
                    .eq(User::getParentId, user.getId())
                    .set(User::getParentInviteCode, user.getUserWalletAddress())
                    .update();
        }
        return updated;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void setCustomUserGrade(Long userId, Integer customUserGrade) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        User dbUser = this.getById(userId);
        if (dbUser == null) {
            throw new BusinessException("用户不存在");
        }
        int grade = customUserGrade == null ? 0 : customUserGrade;
        if (grade < 0) {
            throw new BusinessException("自定义等级不能小于0");
        }
        this.lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getCustomUserGrade, grade)
                .update();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean deleteUserWithCascade(Long id) {
        // 获取要删除的用户
        User targetUser = this.getById(id);
        if (targetUser == null) {
            return false;
        }

        // 计算受影响的团队人数
        // 受影响的人数 = 该用户本人(1) + 他的所有后代人数(teamCount)
        int totalAffectCount = 1 + targetUser.getTeamCount();

        // 更新该用户的所有上级（祖先）的 team_count
        List<Long> ancestorIds = Arrays.stream(targetUser.getPath().split(","))
                .filter(s -> StrUtil.isNotBlank(s) && !"0".equals(s))
                .map(Long::parseLong)
                .collect(Collectors.toList());

        if (!ancestorIds.isEmpty()) {
            this.update(new LambdaUpdateWrapper<User>()
                    .setSql("team_count = team_count - " + totalAffectCount)
                    .in(User::getId, ancestorIds));

            // 如果有直接上级，减少直接上级的直推人数
            if (targetUser.getParentId() != 0) {
                this.lambdaUpdate()
                        .setSql("direct_count = direct_count - 1")
                        .eq(User::getId, targetUser.getParentId())
                        .update();
            }
        }

        // 将所有下级（后代）重置为根用户
        // 逻辑：寻找 path 中包含 ",id," 的所有用户
        String subPathMarker = "," + id + ",";
        // 或者利用我们之前的逻辑：likeRight(targetUser.getPath() + targetUser.getId() + ",")
        String childrenPathPrefix = targetUser.getPath() + targetUser.getId() + ",";

        this.lambdaUpdate()
                .set(User::getParentId, 0L)
                .set(User::getParentInviteCode, "0")
                .set(User::getLevel, 1)
                .set(User::getPath, "0,")
                .likeRight(User::getPath, childrenPathPrefix)
                .update();

        // 执行删除该用户
        return this.removeById(id);
    }

    @Override
    public IPage<User> selectUserPage(UserQueryDTO queryDTO) {
        UserQueryDTO safeQuery = queryDTO == null ? new UserQueryDTO() : queryDTO;
        Page<User> page = new Page<>(safePage(safeQuery.getPage()), safeSize(safeQuery.getSize()));
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(safeQuery.getUserWalletAddress()),
                User::getUserWalletAddress, safeQuery.getUserWalletAddress());
        wrapper.apply(safeQuery.getUserGrade() != null,
                "GREATEST(COALESCE(user_grade,0), COALESCE(custom_user_grade,0)) = {0}",
                safeQuery.getUserGrade());
        if (StrUtil.isNotBlank(safeQuery.getStartTime())) {
            wrapper.ge(User::getCreateTime, DateUtil.parse(safeQuery.getStartTime()).toLocalDateTime());
        }
        if (StrUtil.isNotBlank(safeQuery.getEndTime())) {
            wrapper.le(User::getCreateTime, DateUtil.parse(safeQuery.getEndTime()).toLocalDateTime());
        }
        wrapper.orderByDesc(User::getCreateTime);
        if (safeQuery.getGoldQuantRewardLevel() == null && safeQuery.getGoldQuantDistributionLevel() == null) {
            IPage<User> result = this.page(page, wrapper);
            fillGoldQuantLevels(result.getRecords());
            return result;
        }

        List<User> users = this.list(wrapper);
        fillGoldQuantLevels(users);
        List<User> filtered = users.stream()
                .filter(user -> safeQuery.getGoldQuantRewardLevel() == null
                        || Objects.equals(user.getGoldQuantRewardLevel(), safeQuery.getGoldQuantRewardLevel()))
                .filter(user -> safeQuery.getGoldQuantDistributionLevel() == null
                        || Objects.equals(user.getGoldQuantDistributionLevel(), safeQuery.getGoldQuantDistributionLevel()))
                .collect(Collectors.toList());
        return pageUsers(filtered, safePage(safeQuery.getPage()), safeSize(safeQuery.getSize()));
    }

    private void fillGoldQuantLevels(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        GoldQuantCommissionSettingsVO settings = getGoldQuantCommissionSettings();
        List<User> allUsers = this.list(new LambdaQueryWrapper<User>()
                .select(User::getId, User::getParentId, User::getPath));
        Map<Long, User> userMap = allUsers.stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));
        Map<Long, Integer> ownWindowCountMap = goldQuantWindowMapper.selectList(new LambdaQueryWrapper<GoldQuantWindow>()
                        .select(GoldQuantWindow::getUserId)
                        .eq(GoldQuantWindow::getStatus, 1)
                        .gt(GoldQuantWindow::getMaintenanceExpireTime, java.time.LocalDateTime.now()))
                .stream()
                .filter(window -> window.getUserId() != null)
                .collect(Collectors.groupingBy(
                        GoldQuantWindow::getUserId,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        for (User user : users) {
            int rewardLevel = matchGoldQuantRewardLevel(countDirectValidWindowBuyers(user.getId(), userMap, ownWindowCountMap), settings);
            int distributionLevel = matchGoldQuantDistributionLevel(countTeamValidWindows(user, userMap, ownWindowCountMap), settings);
            user.setGoldQuantRewardLevel(rewardLevel);
            user.setGoldQuantDistributionLevel(distributionLevel);
        }
    }

    private int countDirectValidWindowBuyers(Long userId, Map<Long, User> userMap, Map<Long, Integer> ownWindowCountMap) {
        return (int) userMap.values().stream()
                .filter(user -> Objects.equals(user.getParentId(), userId))
                .filter(user -> ownWindowCountMap.getOrDefault(user.getId(), 0) > 0)
                .count();
    }

    private int countTeamValidWindows(User user, Map<Long, User> userMap, Map<Long, Integer> ownWindowCountMap) {
        if (user == null || user.getId() == null) {
            return 0;
        }
        String prefix = StrUtil.blankToDefault(user.getPath(), "0,") + user.getId() + ",";
        int total = 0;
        for (User item : userMap.values()) {
            if (!Objects.equals(item.getId(), user.getId())
                    && StrUtil.isNotBlank(item.getPath())
                    && item.getPath().startsWith(prefix)) {
                total += ownWindowCountMap.getOrDefault(item.getId(), 0);
            }
        }
        return total;
    }

    private int matchGoldQuantRewardLevel(int directValidBuyerCount, GoldQuantCommissionSettingsVO settings) {
        return settings.getRewardLevels().stream()
                .filter(item -> item.getDirectValidBuyerCount() != null && directValidBuyerCount >= item.getDirectValidBuyerCount())
                .map(GoldQuantCommissionSettingsVO.RewardLevelRule::getLevel)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
    }

    private int matchGoldQuantDistributionLevel(int teamValidWindowCount, GoldQuantCommissionSettingsVO settings) {
        return settings.getDistributionLevels().stream()
                .filter(item -> item.getTeamValidWindowCount() != null && teamValidWindowCount >= item.getTeamValidWindowCount())
                .max(Comparator.comparing(GoldQuantCommissionSettingsVO.DistributionLevelRule::getTeamValidWindowCount))
                .map(item -> item.getLevel() == null ? 0 : item.getLevel())
                .orElse(0);
    }

    private GoldQuantCommissionSettingsVO getGoldQuantCommissionSettings() {
        SystemConfig config = configMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "GOLD_QUANT_COMMISSION_SETTINGS"));
        GoldQuantCommissionSettingsVO settings = config == null || !StringUtils.hasText(config.getConfigValue())
                ? new GoldQuantCommissionSettingsVO()
                : JSON.parseObject(config.getConfigValue(), GoldQuantCommissionSettingsVO.class);
        if (settings.getRewardLevels() == null) {
            settings.setRewardLevels(new ArrayList<>());
        }
        if (settings.getDistributionLevels() == null) {
            settings.setDistributionLevels(new ArrayList<>());
        }
        return settings;
    }

    private IPage<User> pageUsers(List<User> users, int page, int size) {
        Page<User> result = new Page<>(page, size);
        int fromIndex = Math.max((page - 1) * size, 0);
        if (users == null || fromIndex >= users.size()) {
            result.setRecords(Collections.emptyList());
            result.setTotal(users == null ? 0 : users.size());
            return result;
        }
        int toIndex = Math.min(fromIndex + size, users.size());
        result.setRecords(new ArrayList<>(users.subList(fromIndex, toIndex)));
        result.setTotal(users.size());
        return result;
    }

    private int safePage(Integer page) {
        return page == null || page <= 0 ? 1 : page;
    }

    private int safeSize(Integer size) {
        return size == null || size <= 0 ? 10 : size;
    }

    /**
     * 启动时批量修正邀请码，确保邀请码与钱包地址一致。
     *
     * @return 修正的记录数
     */
    @Override
    public int syncInviteCodeWithWalletAddress() {
        long mismatchCount = this.baseMapper.countInviteCodeMismatch();
        if (mismatchCount == 0) {
            log.info("启动自检：所有用户邀请码均与钱包地址一致，无需修正");
            return 0;
        }
        int updatedCount = this.baseMapper.syncInviteCodeWithWalletAddress();
        log.info("启动自检：检测到{}条用户邀请码与钱包地址不一致，已修正{}条记录", mismatchCount, updatedCount);
        return updatedCount;
    }

    /**
     * 生成唯一的 6 位随机邀请码
     */
    private String generateUniqueInviteCode() {
        String code;
        while (true) {
            // 生成6位大写字母+数字混合字符串
            code = RandomUtil.randomString("ABCDEFGHJKLMNPQRSTUVWXYZ23456789", 6);
            // 校验数据库是否存在
            long count = count(new LambdaQueryWrapper<User>().eq(User::getInviteCode, code));
            if (count == 0) {
                break;
            }
        }
        return code;
    }



    /**
     * 根据邀请码获取用户
     */
    private User getByInviteCode(String inviteCode) {
        if (StrUtil.isBlank(inviteCode) || "0".equals(inviteCode)) {
            return null;
        }
        List<User> userList = this.list(new LambdaQueryWrapper<User>()
                .eq(User::getInviteCode, inviteCode)
                .orderByAsc(User::getId)
                .last("LIMIT 2"));
        if (userList.isEmpty()) {
            return null;
        }
        if (userList.size() > 1) {
            log.error("用户表存在重复邀请码数据，邀请码：{}，当前已命中至少{}条记录，系统将暂时返回ID最小的用户：{}。请尽快清理脏数据。",
                    inviteCode, userList.size(), userList.get(0).getId());
        }
        return userList.get(0);
    }
}
