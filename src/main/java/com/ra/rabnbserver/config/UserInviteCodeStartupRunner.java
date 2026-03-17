package com.ra.rabnbserver.config;

import com.ra.rabnbserver.server.user.UserServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动后执行用户邀请码自检任务。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserInviteCodeStartupRunner implements ApplicationRunner {

    private final UserServe userServe;

    /**
     * 启动时检查邀请码是否与钱包地址一致，不一致则自动修正。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("启动任务：开始检查用户邀请码是否与钱包地址一致");
            int updatedCount = userServe.syncInviteCodeWithWalletAddress();
            log.info("启动任务：用户邀请码一致性检查完成，本次修正记录数：{}", updatedCount);
        } catch (Exception e) {
            log.error("启动任务：用户邀请码一致性检查执行异常", e);
        }
    }
}
