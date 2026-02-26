package com.ra.rabnbserver.server.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.dto.RegisterDataDTO;
import com.ra.rabnbserver.dto.team.AdminTeamSearchDTO;
import com.ra.rabnbserver.dto.user.UserQueryDTO;
import com.ra.rabnbserver.pojo.User;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface UserServe extends IService<User> {
    User getByWalletAddress(String address);

    @Transactional(rollbackFor = Exception.class)
    User handleRegister(String walletAddress, String referrerWalletAddress);

    @Transactional(rollbackFor = Exception.class)
    User loginOrRegister(String walletAddress, String referrerWalletAddress);

    @Transactional(rollbackFor = Exception.class)
    User register(RegisterDataDTO registerDataDTO);

    @Transactional(rollbackFor = Exception.class)
    void bindTeamBatch(List<Long> userIds, Long targetParentId);

    @Transactional(rollbackFor = Exception.class)
    void unbindTeamBatch(List<Long> userIds);

    IPage<User> selectComplexTeamPage(AdminTeamSearchDTO queryDTO);

    @Transactional(rollbackFor = Exception.class)
    boolean addUser(User user);

    @Transactional(rollbackFor = Exception.class)
    boolean updateUser(User user);

    @Transactional(rollbackFor = Exception.class)
    boolean deleteUserWithCascade(Long id);

    IPage<User> selectUserPage(UserQueryDTO queryDTO);
}
