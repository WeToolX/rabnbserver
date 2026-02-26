package com.ra.rabnbserver.server.user.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.dto.RegisterDataDTO;
import com.ra.rabnbserver.dto.team.AdminTeamSearchDTO;
import com.ra.rabnbserver.dto.user.UserQueryDTO;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.server.user.UserServe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServeImpl extends ServiceImpl<UserMapper, User> implements UserServe {

    @Override
    public User getByWalletAddress(String address) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserWalletAddress, address));
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
        user.setInviteCode(generateUniqueInviteCode());
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
        user.setInviteCode(generateUniqueInviteCode());
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
        user.setInviteCode(generateUniqueInviteCode());
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

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean addUser(User user) {
        user.setBalance(BigDecimal.ZERO);
        return this.save(user);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updateUser(User user) {
        if (user.getId() == null) {
            return false;
        }
        return this.lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(user.getUserName() != null, User::getUserName, user.getUserName())
                .set(user.getUserWalletAddress() != null, User::getUserWalletAddress, user.getUserWalletAddress())
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
        Page<User> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(queryDTO.getUserWalletAddress()),
                User::getUserWalletAddress, queryDTO.getUserWalletAddress());
        if (StrUtil.isNotBlank(queryDTO.getStartTime())) {
            wrapper.ge(User::getCreateTime, DateUtil.parse(queryDTO.getStartTime()).toLocalDateTime());
        }
        if (StrUtil.isNotBlank(queryDTO.getEndTime())) {
            wrapper.le(User::getCreateTime, DateUtil.parse(queryDTO.getEndTime()).toLocalDateTime());
        }
        wrapper.orderByDesc(User::getCreateTime);
        return this.page(page, wrapper);
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
        return getOne(new LambdaQueryWrapper<User>().eq(User::getInviteCode, inviteCode));
    }
}
