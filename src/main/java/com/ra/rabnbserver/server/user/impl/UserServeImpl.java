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
import com.ra.rabnbserver.dto.UserQueryDTO;
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
