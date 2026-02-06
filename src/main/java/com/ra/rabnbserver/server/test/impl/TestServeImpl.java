package com.ra.rabnbserver.server.test.impl;

import com.ra.rabnbserver.exception.Abnormal.annotation.AbnormalRetryConfig;
import com.ra.rabnbserver.exception.Abnormal.core.AbstractAbnormalRetryService;
import com.ra.rabnbserver.exception.Abnormal.core.AbnormalRetryManager;
import com.ra.rabnbserver.server.test.TestServe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * 异常处理框架使用示例（仅示例）
 */
@Slf4j
@Service
@AbnormalRetryConfig(
        table = "test",
        serviceName = "异常处理示例",
        idField = "id",
        userField = "user_value",
        statusField = "biz_status",
        successValue = "SUCCESS",
        failValue = "FAIL",
        minIntervalSeconds = 5,
        timeoutSeconds = 180,
        maxRetryCount = 2,
        manualRemindIntervalSeconds = 10
)
public class TestServeImpl extends AbstractAbnormalRetryService implements TestServe {

    private final JdbcTemplate jdbcTemplate;

    public TestServeImpl(AbnormalRetryManager abnormalRetryManager,
                         JdbcTemplate jdbcTemplate) {
        super(abnormalRetryManager);
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 业务入口示例（必须先检查异常）
     * 必须写这个方法 并且在Controller层 入口加上检查
     *
     * @param userValue 用户标识
     */
    @Override
    public void entryExample(String userValue) {
        checkUserErr(userValue);
        log.info("示例业务入口已通过异常校验，user={}", userValue);
    }

    /**
     * 业务异常落库示例（调用框架标记异常
     * 异常的时候调用这个方法 自动写入异常 包含业务状态也会写入异常(markAbnormal)
     * @param dataId 数据主键
     * @param userValue 用户标识
     */
    @Override
    public void markAbnormalExample(Long dataId, String userValue) {
        if (dataId == null) {
            throw new IllegalArgumentException("dataId 不能为空");
        }
        markAbnormal(dataId);
        log.info("示例异常已标记，dataId={}, user={}", dataId, userValue);
    }

    /**
     * 检查业务状态是否已成功
     * 必须重写这个方法 用于业务层面检查状态
     * @param dataId 业务数据主键
     * @return true 已成功，false 仍异常
     */
    @Override
    public boolean checkStatus(Long dataId) {
        log.info("执行检查业务状态");
        return false;
    }

    /**
     * 异常重试处理方法
     * 必须重写 异常重试的业务代码
     *
     * @param dataId 业务数据主键
     * @return true 处理成功，false 处理失败（不得抛异常）
     */
    @Override
    public boolean ExceptionHandling(Long dataId) {

        if (dataId == null) {
            return false;
        }
        try {
            // TODO: 这里写实际业务重试逻辑
            log.info("示例异常重试执行中，dataId={}", dataId);
            return false;
        } catch (Exception e) {
            log.warn("示例异常重试执行失败，dataId={}, 原因={}", dataId, e.getMessage());
            return false;
        }
    }

    /**
     * 写入一条失败的测试支付数据
     * (仅测试)
     * @param userValue 用户标识
     * @return 数据主键
     */
    @Override
    public Long createFailPayment(String userValue) {
        String finalUser = (userValue == null || userValue.isBlank()) ? "1000" : userValue;
        String sql = "INSERT INTO test (user_value, biz_status) VALUES (?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, finalUser);
            ps.setString(2, "FAIL");
            return ps;
        }, keyHolder);
        if (keyHolder.getKey() == null) {
            throw new IllegalStateException("写入失败数据后未返回主键");
        }
        Long dataId = keyHolder.getKey().longValue();
        markAbnormal(dataId, finalUser);
        log.info("示例失败支付已写入，dataId={}, user={}", dataId, finalUser);
        return dataId;
    }

    @Override
    public String manualSuccessRoute(){
        return "/api/admin/test/payment/manual-success";
    }

}
