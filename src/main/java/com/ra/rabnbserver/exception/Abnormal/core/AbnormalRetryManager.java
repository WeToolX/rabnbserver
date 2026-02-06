package com.ra.rabnbserver.exception.Abnormal.core;

import com.ra.rabnbserver.VO.AbnormalPageVO;
import com.ra.rabnbserver.dto.AbnormalQueryDTO;
import com.ra.rabnbserver.enums.AbnormalManualStatus;
import com.ra.rabnbserver.enums.AbnormalStatus;
import com.ra.rabnbserver.exception.Abnormal.annotation.AbnormalRetryConfig;
import com.ra.rabnbserver.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异常重试框架核心管理器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbnormalRetryManager {

    private final ApplicationContext applicationContext;
    private final AbnormalRetryProperties properties;
    private final AbnormalMailService mailService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private final Map<String, AbnormalContext> tableContextMap = new ConcurrentHashMap<>();
    private final Map<Class<?>, AbnormalContext> classContextMap = new ConcurrentHashMap<>();

    /**
     * 初始化扫描（由调度器首次触发）
     */
    public synchronized void initializeIfNeeded() {
        if (!tableContextMap.isEmpty()) {
            return;
        }
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(AbnormalRetryConfig.class);
        log.debug("异常重试框架扫描 @AbnormalRetryConfig，数量={}", beans.size());
        if (beans.isEmpty()) {
            log.warn("未发现 @AbnormalRetryConfig 注解服务，异常重试框架未启动");
            return;
        }
        for (Object bean : beans.values()) {
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            AbnormalRetryConfig config = targetClass.getAnnotation(AbnormalRetryConfig.class);
            if (config == null) {
                continue;
            }
            if (!(bean instanceof AbnormalRetryHandler handler)) {
                log.error("异常重试服务未实现 AbnormalRetryHandler: {}", targetClass.getName());
                continue;
            }
            if (!tableExists(config.table())) {
                log.error("异常重试框架未找到表，已跳过注册：服务={}, 表={}", config.serviceName(), config.table());
                continue;
            }
            AbnormalContext context = new AbnormalContext(config, handler, targetClass);
            ensureTableFields(config.table());
            tableContextMap.put(config.table(), context);
            classContextMap.put(targetClass, context);
            log.info("异常重试服务已注册：服务={}, 表={}", config.serviceName(), config.table());
            log.debug("异常重试服务配置加载完成，服务={}, 表={}, 状态字段={}, 成功值={}, 失败值={}, 最小间隔={}, 超时={}, 最大重试={}, 人工提醒间隔={}",
                    config.serviceName(),
                    config.table(),
                    config.statusField(),
                    config.successValue(),
                    config.failValue(),
                    config.minIntervalSeconds(),
                    config.timeoutSeconds(),
                    config.maxRetryCount(),
                    config.manualRemindIntervalSeconds());
        }
    }

    /**
     * 获取全部上下文
     *
     * @return 上下文集合
     */
    public Collection<AbnormalContext> getAllContexts() {
        initializeIfNeeded();
        return tableContextMap.values();
    }

    /**
     * 业务入口校验异常
     *
     * @param serviceClass 服务类
     * @param userValue 用户标识值
     */
    public void checkUserErr(Class<?> serviceClass, String userValue) {
        AbnormalContext context = classContextMap.get(serviceClass);
        if (context == null) {
            throw new BusinessException("异常框架未初始化或未注册服务");
        }
        AbnormalRetryConfig config = context.getConfig();
        log.debug("异常校验开始，服务={}, 用户={}", config.serviceName(), userValue);
        String sql = "SELECT COUNT(1) FROM " + wrapTable(config.table())
                + " WHERE (" + wrapColumn("err_status") + " = ? OR " + wrapColumn(config.statusField()) + " = ?)"
                + " AND " + wrapColumn(config.userField()) + " = ?";
        Object failValue = parseValue(config.failValue());
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class,
                AbnormalStatus.WAIT_AUTO.getCode(),
                failValue,
                userValue);
        log.debug("异常校验完成，服务={}, 用户={}, 命中数={}", config.serviceName(), userValue, count);
        if (count != null && count > 0) {
            throw new BusinessException("当前操作存在异常，请过一会再试试或联系客服");
        }
    }

    /**
     * 自动修复异常表关键字段（补 err_start_time 与修复成功状态）
     *
     * @param context 上下文
     */
    public void healAbnormalData(AbnormalContext context) {
        AbnormalRetryConfig config = context.getConfig();
        Object successValue = parseValue(config.successValue());

        // 1) err_status=WAIT_AUTO/WAIT_MANUAL 且 err_start_time 为空时补当前时间
        String fillStartTimeSql = "UPDATE " + wrapTable(config.table()) + " SET "
                + wrapColumn("err_start_time") + " = NOW() "
                + "WHERE " + wrapColumn("err_status") + " IN (?, ?)"
                + " AND " + wrapColumn("err_start_time") + " IS NULL";
        int filled = jdbcTemplate.update(fillStartTimeSql,
                AbnormalStatus.WAIT_AUTO.getCode(),
                AbnormalStatus.WAIT_MANUAL.getCode());
        if (filled > 0) {
            log.warn("异常数据开始时间为空，已自动补全，服务={}, 数量={}", config.serviceName(), filled);
        }

        // 2) 业务状态已成功但 err_status 未同步时自动修复
        String fixSuccessSql = "UPDATE " + wrapTable(config.table()) + " SET "
                + wrapColumn("err_status") + " = ? "
                + "WHERE " + wrapColumn(config.statusField()) + " = ?"
                + " AND (" + wrapColumn("err_status") + " IS NULL OR " + wrapColumn("err_status") + " NOT IN (?, ?))";
        int fixed = jdbcTemplate.update(fixSuccessSql,
                AbnormalStatus.AUTO_SUCCESS.getCode(),
                successValue,
                AbnormalStatus.AUTO_SUCCESS.getCode(),
                AbnormalStatus.MANUAL_SUCCESS.getCode());
        if (fixed > 0) {
            log.warn("异常数据业务状态已成功但未同步 err_status，已自动修复，服务={}, 数量={}",
                    config.serviceName(), fixed);
        }
    }

    /**
     * 人工处理成功回调
     *
     * @param serviceClass 服务类
     * @param dataId 数据主键
     */
    public void processingSuccessful(Class<?> serviceClass, Long dataId) {
        AbnormalContext context = classContextMap.get(serviceClass);
        if (context == null) {
            throw new BusinessException("异常框架未初始化或未注册服务");
        }
        AbnormalRetryConfig config = context.getConfig();
        String sql = "UPDATE " + wrapTable(config.table()) + " SET "
                + wrapColumn("err_status") + " = ?, "
                + wrapColumn("err_submit_manual_status") + " = ?, "
                + wrapColumn(config.statusField()) + " = ? "
                + "WHERE " + wrapColumn(config.idField()) + " = ?";
        jdbcTemplate.update(sql,
                AbnormalStatus.MANUAL_SUCCESS.getCode(),
                AbnormalManualStatus.MANUAL_SUCCESS.getCode(),
                parseValue(config.successValue()),
                dataId);
        log.info("人工处理成功已回写，服务={}, 数据ID={}", config.serviceName(), dataId);
    }

    /**
     * 业务异常落库入口
     *
     * @param serviceClass 服务类
     * @param dataId 数据主键
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAbnormal(Class<?> serviceClass, Long dataId) {
        markAbnormal(serviceClass, dataId, null);
    }

    /**
     * 业务异常落库入口（可写入用户标识）
     *
     * @param serviceClass 服务类
     * @param dataId 数据主键
     * @param userValue 用户标识值（可空）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAbnormal(Class<?> serviceClass, Long dataId, String userValue) {
        AbnormalContext context = classContextMap.get(serviceClass);
        if (context == null) {
            throw new BusinessException("异常框架未初始化或未注册服务");
        }
        AbnormalRetryConfig config = context.getConfig();
        log.debug("异常落库开始，服务={}, 数据ID={}, 用户={}", config.serviceName(), dataId, userValue);
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(wrapTable(config.table())).append(" SET ")
                .append(wrapColumn("err_status")).append(" = ?, ")
                .append(wrapColumn(config.statusField())).append(" = ?, ")
                .append(wrapColumn("err_start_time")).append(" = COALESCE(")
                .append(wrapColumn("err_start_time")).append(", NOW()), ")
                .append(wrapColumn("err_retry_count")).append(" = 0, ")
                .append(wrapColumn("err_next_retry_time")).append(" = NULL, ")
                .append(wrapColumn("err_min_interval")).append(" = ?, ")
                .append(wrapColumn("err_timeout")).append(" = ?, ")
                .append(wrapColumn("err_submit_manual_status")).append(" = NULL, ")
                .append(wrapColumn("err_next_remind_staff_time")).append(" = NULL, ")
                .append(wrapColumn("err_manual_notify_count")).append(" = 0");
        List<Object> params = new ArrayList<>();
        params.add(AbnormalStatus.WAIT_AUTO.getCode());
        params.add(parseValue(config.failValue()));
        params.add(config.minIntervalSeconds());
        params.add(config.timeoutSeconds());
        if (userValue != null) {
            sql.append(", ").append(wrapColumn(config.userField())).append(" = ?");
            params.add(userValue);
        }
        sql.append(" WHERE ").append(wrapColumn(config.idField())).append(" = ?");
        params.add(dataId);
        int updated = jdbcTemplate.update(sql.toString(), params.toArray());
        if (updated == 0) {
            log.warn("异常落库未命中记录，服务={}, 数据ID={}, 请确认业务数据已提交或未被回滚",
                    config.serviceName(), dataId);
            return;
        }
        log.info("异常落库完成，服务={}, 数据ID={}", config.serviceName(), dataId);
    }

    /**
     * 获取可自动处理异常数据
     *
     * @param context 上下文
     * @return 异常记录列表
     */
    public List<AbnormalRecord> getAllAbnormalData(AbnormalContext context) {
        AbnormalRetryConfig config = context.getConfig();
        String sql = "SELECT * FROM " + wrapTable(config.table())
                + " WHERE " + wrapColumn("err_status") + " = ?"
                + " AND (" + wrapColumn("err_next_retry_time") + " IS NULL OR " + wrapColumn("err_next_retry_time") + " <= NOW())"
                + " AND " + wrapColumn(config.statusField()) + " = ?";
        Object failValue = parseValue(config.failValue());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql,
                AbnormalStatus.WAIT_AUTO.getCode(),
                failValue
        );
        List<AbnormalRecord> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            records.add(AbnormalRecord.fromMap(row, config.idField(), config.userField(), config.statusField()));
        }
        log.debug("自动重试扫描完成，服务={}, 命中数量={}", config.serviceName(), records.size());
        return records;
    }

    /**
     * 获取需要人工通知的数据
     *
     * @param context 上下文
     * @return 异常记录列表
     */
    public List<AbnormalRecord> getAllAbnormalDataNoticeManually(AbnormalContext context) {
        AbnormalRetryConfig config = context.getConfig();
        String sql = "SELECT * FROM " + wrapTable(config.table())
                + " WHERE " + wrapColumn("err_status") + " = ?"
                + " AND (" + wrapColumn("err_next_remind_staff_time") + " IS NULL OR " + wrapColumn("err_next_remind_staff_time") + " < NOW())"
                + " AND " + wrapColumn(config.statusField()) + " = ?";
        Object failValue = parseValue(config.failValue());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql,
                AbnormalStatus.WAIT_MANUAL.getCode(),
                failValue
        );
        List<AbnormalRecord> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            records.add(AbnormalRecord.fromMap(row, config.idField(), config.userField(), config.statusField()));
        }
        log.debug("人工通知扫描完成，服务={}, 命中数量={}", config.serviceName(), records.size());
        return records;
    }

    /**
     * 超时但未达到最大重试次数的数据，直接升级为人工处理
     *
     * @param context 上下文
     * @return 升级数量
     */
    public int promoteTimeoutToManual(AbnormalContext context) {
        AbnormalRetryConfig config = context.getConfig();
        String sql = "UPDATE " + wrapTable(config.table()) + " SET "
                + wrapColumn("err_status") + " = ?, "
                + wrapColumn("err_retry_count") + " = ?, "
                + wrapColumn("err_next_retry_time") + " = NULL, "
                + wrapColumn("err_submit_manual_status") + " = NULL, "
                + wrapColumn("err_next_remind_staff_time") + " = NULL "
                + "WHERE " + wrapColumn("err_status") + " = ?"
                + " AND TIMESTAMPDIFF(SECOND, " + wrapColumn("err_start_time") + ", NOW()) > ?"
                + " AND COALESCE(" + wrapColumn("err_retry_count") + ", 0) < ?"
                + " AND " + wrapColumn(config.statusField()) + " = ?";
        Object failValue = parseValue(config.failValue());
        int updated = jdbcTemplate.update(
                sql,
                AbnormalStatus.WAIT_MANUAL.getCode(),
                config.maxRetryCount(),
                AbnormalStatus.WAIT_AUTO.getCode(),
                config.timeoutSeconds(),
                config.maxRetryCount(),
                failValue
        );
        log.debug("超时升级人工执行完成，服务={}, 更新数量={}", config.serviceName(), updated);
        if (updated > 0) {
            log.info("异常超时升级人工完成，服务={}, 数量={}", config.serviceName(), updated);
        }
        return updated;
    }

    /**
     * 重试成功处理
     *
     * @param context 上下文
     * @param dataId 数据主键
     */
    public void retrySuccessful(AbnormalContext context, Long dataId) {
        AbnormalRetryConfig config = context.getConfig();
        String sql = "UPDATE " + wrapTable(config.table()) + " SET "
                + wrapColumn("err_status") + " = ?, "
                + wrapColumn(config.statusField()) + " = ? "
                + "WHERE " + wrapColumn(config.idField()) + " = ?";
        jdbcTemplate.update(sql,
                AbnormalStatus.AUTO_SUCCESS.getCode(),
                parseValue(config.successValue()),
                dataId);
        log.info("自动重试成功，服务={}, 数据ID={}", config.serviceName(), dataId);
        log.debug("自动重试成功回写完成，服务={}, 数据ID={}", config.serviceName(), dataId);
    }

    /**
     * 自动处理失败（更新重试或升级人工）
     *
     * @param context 上下文
     * @param record 异常记录
     */
    public void retryFailed(AbnormalContext context, AbnormalRecord record) {
        AbnormalRetryConfig config = context.getConfig();
        LocalDateTime now = LocalDateTime.now();
        Integer retryCount = Optional.ofNullable(record.getErrRetryCount()).orElse(0);
        boolean timeout = false;
        if (record.getErrStartTime() != null) {
            long duration = Duration.between(record.getErrStartTime(), now).getSeconds();
            timeout = duration > config.timeoutSeconds();
        }
        boolean overMax = retryCount >= config.maxRetryCount();
        log.debug("自动重试失败判断，服务={}, 数据ID={}, 已重试={}, 超时={}, 达上限={}",
                config.serviceName(), record.getId(), retryCount, timeout, overMax);
        if (timeout || overMax) {
            upgradeToManual(context, record);
            return;
        }
        int nextRetryCount = retryCount + 1;
        int nextInterval = config.minIntervalSeconds() * nextRetryCount;
        LocalDateTime nextTime = now.plusSeconds(nextInterval);
        String sql = "UPDATE " + wrapTable(config.table()) + " SET "
                + wrapColumn("err_retry_count") + " = ?, "
                + wrapColumn("err_next_retry_time") + " = ?, "
                + wrapColumn("err_min_interval") + " = ?, "
                + wrapColumn("err_timeout") + " = ? "
                + "WHERE " + wrapColumn(config.idField()) + " = ?";
        jdbcTemplate.update(sql,
                nextRetryCount,
                Timestamp.valueOf(nextTime),
                config.minIntervalSeconds(),
                config.timeoutSeconds(),
                record.getId()
        );
        log.info("自动重试失败，已安排下次重试，服务={}, 数据ID={}, 次数={}, 下次时间={}",
                config.serviceName(), record.getId(), nextRetryCount, nextTime);
    }

    /**
     * 升级为人工处理并通知
     *
     * @param context 上下文
     * @param record 异常记录
     */
    public void upgradeToManual(AbnormalContext context, AbnormalRecord record) {
        AbnormalRetryConfig config = context.getConfig();
        if (record.getId() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        log.debug("升级人工开始，服务={}, 数据ID={}, 当前状态={}, 已重试={}",
                config.serviceName(), record.getId(), record.getErrStatus(), record.getErrRetryCount());
        if (record.getErrStatus() == null || record.getErrStatus() != AbnormalStatus.WAIT_MANUAL.getCode()) {
            String sql = "UPDATE " + wrapTable(config.table()) + " SET "
                    + wrapColumn("err_status") + " = ?, "
                    + wrapColumn("err_retry_count") + " = ?, "
                    + wrapColumn("err_next_retry_time") + " = NULL, "
                    + wrapColumn("err_submit_manual_status") + " = NULL, "
                    + wrapColumn("err_next_remind_staff_time") + " = NULL "
                    + "WHERE " + wrapColumn(config.idField()) + " = ?";
            jdbcTemplate.update(sql,
                    AbnormalStatus.WAIT_MANUAL.getCode(),
                    config.maxRetryCount(),
                    record.getId());
            record.setErrStatus(AbnormalStatus.WAIT_MANUAL.getCode());
            record.setErrRetryCount(config.maxRetryCount());
            record.setErrNextRetryTime(null);
            record.setErrNextRemindStaffTime(null);
        }
        if (record.getErrNextRemindStaffTime() != null && record.getErrNextRemindStaffTime().isAfter(now)) {
            log.info("人工通知未到提醒时间，服务={}, 数据ID={}, 下次提醒时间={}",
                    config.serviceName(), record.getId(), record.getErrNextRemindStaffTime());
            return;
        }
        log.debug("升级人工完成，准备发送通知，服务={}, 数据ID={}", config.serviceName(), record.getId());
        notifyManual(context, record);
    }

    /**
     * 发送人工通知并更新提醒时间
     *
     * @param context 上下文
     * @param record 异常记录
     */
    public void notifyManual(AbnormalContext context, AbnormalRecord record) {
        AbnormalRetryConfig config = context.getConfig();
        Map<String, Object> fullData = loadFullData(config, record.getId());
        int currentCount = Optional.ofNullable(record.getErrManualNotifyCount()).orElse(0);
        boolean mailOk = false;
        try {
            mailService.sendErrToMail(config, record, fullData);
            mailOk = true;
        } catch (Exception e) {
            log.error("发送异常通知失败，服务={}, 数据ID={}, 原因={}",
                    config.serviceName(), record.getId(), e.getMessage());
        }
        int intervalMultiplier = mailOk ? (currentCount + 1) : 1;
        long nextSeconds = (long) config.manualRemindIntervalSeconds() * intervalMultiplier;
        LocalDateTime nextRemindTime = LocalDateTime.now().plusSeconds(nextSeconds);
        log.debug("人工通知间隔计算，服务={}, 数据ID={}, 当前通知次数={}, 本次成功={}, 下次间隔秒={}, 下次提醒时间={}",
                config.serviceName(), record.getId(), currentCount, mailOk, nextSeconds, nextRemindTime);
        if (mailOk) {
            String sql = "UPDATE " + wrapTable(config.table()) + " SET "
                    + wrapColumn("err_next_remind_staff_time") + " = ?, "
                    + wrapColumn("err_submit_manual_status") + " = ?, "
                    + wrapColumn("err_manual_notify_count") + " = ? "
                    + "WHERE " + wrapColumn(config.idField()) + " = ?";
            int updated = jdbcTemplate.update(sql,
                    Timestamp.valueOf(nextRemindTime),
                    AbnormalManualStatus.SUBMITTED.getCode(),
                    currentCount + 1,
                    record.getId());
            log.info("人工通知发送成功，服务={}, 数据ID={}, 下次提醒时间={}",
                    config.serviceName(), record.getId(), nextRemindTime);
            log.debug("人工通知成功回写完成，服务={}, 数据ID={}, 下次提醒时间={}",
                    config.serviceName(), record.getId(), nextRemindTime);
            log.debug("人工通知回写行数，服务={}, 数据ID={}, 更新行数={}", config.serviceName(), record.getId(), updated);
        } else {
            String sql = "UPDATE " + wrapTable(config.table()) + " SET "
                    + wrapColumn("err_next_remind_staff_time") + " = ?, "
                    + wrapColumn("err_submit_manual_status") + " = ? "
                    + "WHERE " + wrapColumn(config.idField()) + " = ?";
            int updated = jdbcTemplate.update(sql,
                    Timestamp.valueOf(nextRemindTime),
                    AbnormalManualStatus.SUBMIT_FAILED.getCode(),
                    record.getId());
            log.warn("人工通知发送失败，将继续重试，服务={}, 数据ID={}, 下次提醒时间={}",
                    config.serviceName(), record.getId(), nextRemindTime);
            log.debug("人工通知失败回写完成，服务={}, 数据ID={}, 下次提醒时间={}",
                    config.serviceName(), record.getId(), nextRemindTime);
            log.debug("人工通知回写行数，服务={}, 数据ID={}, 更新行数={}", config.serviceName(), record.getId(), updated);
        }
    }

    /**
     * 根据重试次数和间隔计算下一个提醒时间
     */
    private LocalDateTime calcNextRemindTime(AbnormalRetryConfig config, AbnormalRecord record) {
        LocalDateTime now = LocalDateTime.now();
        int notifyCount = Optional.ofNullable(record.getErrManualNotifyCount()).orElse(0);
        long nextSeconds = (long) config.manualRemindIntervalSeconds() * (notifyCount + 1L);
        return now.plusSeconds(nextSeconds);
    }

    /**
     * 加载给定ID的完整数据，失败时回退
     */
    private Map<String, Object> loadFullData(AbnormalRetryConfig config, Long dataId) {
        if (dataId == null) {
            return Collections.emptyMap();
        }
        String sql = "SELECT * FROM " + wrapTable(config.table())
                + " WHERE " + wrapColumn(config.idField()) + " = ?";
        try {
            return jdbcTemplate.queryForMap(sql, dataId);
        } catch (Exception e) {
            log.warn("加载完整数据失败，表={}, 数据ID={}, 原因={}", config.table(), dataId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private void ensureTableFields(String tableName) {
        List<String> requiredColumns = List.of(
                "err_status",
                "err_start_time",
                "err_retry_count",
                "err_next_retry_time",
                "err_min_interval",
                "err_timeout",
                "err_submit_manual_status",
                "err_next_remind_staff_time",
                "err_manual_notify_count"
        );
        Set<String> existing = getExistingColumns(tableName);
        for (String column : requiredColumns) {
            if (!existing.contains(column.toLowerCase())) {
                String sql = buildAlterSql(tableName, column);
                try {
                    jdbcTemplate.execute(sql);
                    log.info("异常框架新增字段成功：表={}, 字段={}", tableName, column);
                } catch (Exception e) {
                    log.error("异常框架新增字段失败：表={}, 字段={}, 原因={}", tableName, column, e.getMessage());
                }
            }
        }
    }

    private Set<String> getExistingColumns(String tableName) {
        String sql = "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        List<String> columnList = jdbcTemplate.queryForList(sql, String.class, tableName);
        Set<String> result = new HashSet<>();
        for (String col : columnList) {
            result.add(col.toLowerCase());
        }
        return result;
    }

    private boolean tableExists(String tableName) {
        String sql = "SELECT COUNT(1) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
        return count != null && count > 0;
    }

    private String buildAlterSql(String tableName, String column) {
        return switch (column) {
            case "err_status" ->
                    "ALTER TABLE " + wrapTable(tableName) + " ADD COLUMN " + wrapColumn(column)
                            + " INT DEFAULT " + AbnormalStatus.NORMAL.getCode() + " COMMENT '异常主状态'";
            case "err_start_time" ->
                    "ALTER TABLE " + wrapTable(tableName) + " ADD COLUMN " + wrapColumn(column)
                            + " DATETIME NULL COMMENT '首次异常时间'";
            case "err_retry_count" ->
                    "ALTER TABLE " + wrapTable(tableName) + " ADD COLUMN " + wrapColumn(column)
                            + " INT DEFAULT 0 COMMENT '已重试次数'";
            case "err_next_retry_time" ->
                    "ALTER TABLE " + wrapTable(tableName) + " ADD COLUMN " + wrapColumn(column)
                            + " DATETIME NULL COMMENT '下次允许自动重试时间'";
            case "err_min_interval" ->
                    "ALTER TABLE " + wrapTable(tableName) + " ADD COLUMN " + wrapColumn(column)
                            + " INT NULL COMMENT '最小重试间隔(秒)'";
            case "err_timeout" ->
                    "ALTER TABLE " + wrapTable(tableName) + " ADD COLUMN " + wrapColumn(column)
                            + " INT NULL COMMENT '最大处理窗口(秒)'";
            case "err_submit_manual_status" ->
                    "ALTER TABLE " + wrapTable(tableName) + " ADD COLUMN " + wrapColumn(column)
                            + " INT NULL COMMENT '人工处理状态'";
            case "err_next_remind_staff_time" ->
                    "ALTER TABLE " + wrapTable(tableName) + " ADD COLUMN " + wrapColumn(column)
                            + " DATETIME NULL COMMENT '下次提醒人工时间'";
            case "err_manual_notify_count" ->
                    "ALTER TABLE " + wrapTable(tableName) + " ADD COLUMN " + wrapColumn(column)
                            + " INT DEFAULT 0 COMMENT '通知人工处理次数'";
            default -> throw new IllegalArgumentException("未知字段: " + column);
        };
    }

    /**
     * 行锁加载记录（FOR UPDATE）
     *
     * @param context 上下文
     * @param dataId 数据主键
     * @return 锁定后的异常记录
     */
    public AbnormalRecord lockRecordForUpdate(AbnormalContext context, Long dataId) {
        AbnormalRetryConfig config = context.getConfig();
        log.debug("尝试行锁读取，服务={}, 数据ID={}", config.serviceName(), dataId);
        String sql = "SELECT * FROM " + wrapTable(config.table())
                + " WHERE " + wrapColumn(config.idField()) + " = ? FOR UPDATE SKIP LOCKED";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, dataId);
        if (rows.isEmpty()) {
            log.debug("行锁读取未命中，服务={}, 数据ID={}", config.serviceName(), dataId);
            return null;
        }
        return AbnormalRecord.fromMap(rows.get(0), config.idField(), config.userField(), config.statusField());
    }

    /**
     * 重试条件校验（锁内再次校验）
     *
     * @param context 上下文
     * @param record 异常记录
     * @return 是否满足重试条件
     */
    public boolean isRetryEligible(AbnormalContext context, AbnormalRecord record) {
        if (record == null) {
            log.debug("重试条件校验失败：记录为空");
            return false;
        }
        AbnormalRetryConfig config = context.getConfig();
        if (record.getErrStatus() == null || record.getErrStatus() != AbnormalStatus.WAIT_AUTO.getCode()) {
            log.debug("重试条件校验失败：状态不匹配，服务={}, 数据ID={}, err_status={}",
                    config.serviceName(), record.getId(), record.getErrStatus());
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (record.getErrNextRetryTime() != null && record.getErrNextRetryTime().isAfter(now)) {
            log.debug("重试条件校验失败：未到重试时间，服务={}, 数据ID={}, 下次时间={}",
                    config.serviceName(), record.getId(), record.getErrNextRetryTime());
            return false;
        }
        if (record.getErrStartTime() == null) {
            log.debug("重试条件校验失败：开始时间为空，服务={}, 数据ID={}", config.serviceName(), record.getId());
            return false;
        }
        Object failValue = parseValue(config.failValue());
        boolean ok = valueEquals(record.getStatusValue(), failValue);
        if (!ok) {
            log.debug("重试条件校验失败：业务状态不匹配，服务={}, 数据ID={}, 当前值={}, 失败值={}",
                    config.serviceName(), record.getId(), record.getStatusValue(), failValue);
        }
        return ok;
    }

    /**
     * 通知条件校验（锁内再次校验）
     *
     * @param context 上下文
     * @param record 异常记录
     * @return 是否满足通知条件
     */
    public boolean isManualNotifyEligible(AbnormalContext context, AbnormalRecord record) {
        if (record == null) {
            log.debug("人工通知校验失败：记录为空");
            return false;
        }
        AbnormalRetryConfig config = context.getConfig();
        if (record.getErrStatus() == null || record.getErrStatus() != AbnormalStatus.WAIT_MANUAL.getCode()) {
            log.debug("人工通知校验失败：状态不匹配，服务={}, 数据ID={}, err_status={}",
                    config.serviceName(), record.getId(), record.getErrStatus());
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (record.getErrStartTime() == null) {
            log.debug("人工通知校验失败：开始时间为空，服务={}, 数据ID={}", config.serviceName(), record.getId());
            return false;
        }
        if (record.getErrNextRemindStaffTime() != null && record.getErrNextRemindStaffTime().isAfter(now)) {
            log.debug("人工通知校验失败：未到提醒时间，服务={}, 数据ID={}, 下次时间={}",
                    config.serviceName(), record.getId(), record.getErrNextRemindStaffTime());
            return false;
        }
        Object failValue = parseValue(config.failValue());
        boolean ok = valueEquals(record.getStatusValue(), failValue);
        if (!ok) {
            log.debug("人工通知校验失败：业务状态不匹配，服务={}, 数据ID={}, 当前值={}, 失败值={}",
                    config.serviceName(), record.getId(), record.getStatusValue(), failValue);
        }
        return ok;
    }

    /**
     * 是否需要直接升级为人工处理
     *
     * @param context 上下文
     * @param record 异常记录
     * @return true 需要升级
     */
    public boolean shouldUpgradeToManual(AbnormalContext context, AbnormalRecord record) {
        if (record == null) {
            return false;
        }
        if (record.getErrStatus() == null || record.getErrStatus() != AbnormalStatus.WAIT_AUTO.getCode()) {
            return false;
        }
        AbnormalRetryConfig config = context.getConfig();
        LocalDateTime now = LocalDateTime.now();
        if (record.getErrStartTime() != null) {
            long duration = Duration.between(record.getErrStartTime(), now).getSeconds();
            if (duration > config.timeoutSeconds()) {
                log.debug("满足升级人工条件：已超时，服务={}, 数据ID={}, 持续秒数={}",
                        config.serviceName(), record.getId(), duration);
                return true;
            }
        }
        int retryCount = Optional.ofNullable(record.getErrRetryCount()).orElse(0);
        if (retryCount >= config.maxRetryCount()) {
            log.debug("满足升级人工条件：达到最大重试次数，服务={}, 数据ID={}, 已重试={}",
                    config.serviceName(), record.getId(), retryCount);
            return true;
        }
        return false;
    }

    private String wrapTable(String table) {
        return "`" + table + "`";
    }

    private String wrapColumn(String column) {
        return "`" + column + "`";
    }

    private Object parseValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return Boolean.FALSE;
        }
        if (trimmed.matches("^-?\\d+$")) {
            try {
                return Long.parseLong(trimmed);
            } catch (Exception e) {
                return trimmed;
            }
        }
        return trimmed;
    }

    private boolean valueEquals(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof Number && right instanceof Number) {
            return ((Number) left).longValue() == ((Number) right).longValue();
        }
        Boolean leftBool = toBoolean(left);
        Boolean rightBool = toBoolean(right);
        if (leftBool != null || rightBool != null) {
            return Objects.equals(leftBool, rightBool);
        }
        return left.toString().equals(right.toString());
    }

    /**
     * 异常记录分页查询
     *
     * @param query 查询参数
     * @return 分页结果
     */
    public AbnormalPageVO queryAbnormalPage(AbnormalQueryDTO query) {
        initializeIfNeeded();
        AbnormalQueryDTO safeQuery = query == null ? new AbnormalQueryDTO() : query;
        int pageNum = Optional.ofNullable(safeQuery.getPageNum()).orElse(1);
        int pageSize = Optional.ofNullable(safeQuery.getPageSize()).orElse(20);
        if (pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize < 1) {
            pageSize = 20;
        }

        Set<String> allServiceNames = new LinkedHashSet<>();
        List<AbnormalContext> contexts = new ArrayList<>(getAllContexts());
        for (AbnormalContext context : contexts) {
            allServiceNames.add(context.getConfig().serviceName());
        }

        Set<String> serviceFilter = resolveServiceNameFilter(safeQuery);
        Set<Integer> statusFilter = resolveErrStatusFilter(safeQuery);

        List<Map<String, Object>> allRecords = new ArrayList<>();
        for (AbnormalContext context : contexts) {
            AbnormalRetryConfig config = context.getConfig();
            String serviceName = config.serviceName();
            if (serviceFilter != null && !serviceFilter.contains(serviceName)) {
                continue;
            }
            List<Map<String, Object>> records = queryAbnormalByContext(config, statusFilter);
            for (Map<String, Object> record : records) {
                record.put("table", config.table());
                record.put("serviceName", serviceName);
                allRecords.add(record);
            }
        }

        allRecords.sort((left, right) -> {
            LocalDateTime leftTime = toLocalDateTime(left.get("err_start_time"));
            LocalDateTime rightTime = toLocalDateTime(right.get("err_start_time"));
            if (leftTime == null && rightTime == null) {
                return 0;
            }
            if (leftTime == null) {
                return 1;
            }
            if (rightTime == null) {
                return -1;
            }
            return rightTime.compareTo(leftTime);
        });

        int total = allRecords.size();
        int fromIndex = Math.min((pageNum - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Map<String, Object>> pageRecords = allRecords.subList(fromIndex, toIndex);

        AbnormalPageVO result = new AbnormalPageVO();
        result.setRecords(pageRecords);
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setServiceNames(new ArrayList<>(allServiceNames));
        return result;
    }

    private List<Map<String, Object>> queryAbnormalByContext(AbnormalRetryConfig config, Set<Integer> statusFilter) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();
        sql.append("SELECT * FROM ").append(wrapTable(config.table())).append(" WHERE 1=1");
        if (statusFilter == null || statusFilter.isEmpty()) {
            sql.append(" AND ").append(wrapColumn("err_status")).append(" IS NOT NULL");
            sql.append(" AND ").append(wrapColumn("err_status")).append(" <> ?");
            params.add(AbnormalStatus.NORMAL.getCode());
        } else {
            sql.append(" AND ").append(wrapColumn("err_status")).append(" IN (");
            int index = 0;
            for (Integer status : statusFilter) {
                if (index > 0) {
                    sql.append(", ");
                }
                sql.append("?");
                params.add(status);
                index++;
            }
            sql.append(")");
        }
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private Set<String> resolveServiceNameFilter(AbnormalQueryDTO query) {
        Set<String> result = new LinkedHashSet<>();
        if (query == null) {
            return null;
        }
        if (StringUtils.hasText(query.getServiceName())) {
            result.add(query.getServiceName().trim());
        }
        if (query.getServiceNameList() != null) {
            for (String name : query.getServiceNameList()) {
                if (StringUtils.hasText(name)) {
                    result.add(name.trim());
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    private Set<Integer> resolveErrStatusFilter(AbnormalQueryDTO query) {
        Set<Integer> result = new LinkedHashSet<>();
        if (query == null) {
            return null;
        }
        if (query.getErrStatus() != null) {
            result.add(query.getErrStatus());
        }
        if (query.getErrStatusList() != null) {
            result.addAll(query.getErrStatusList());
        }
        return result.isEmpty() ? null : result;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return new Timestamp(date.getTime()).toLocalDateTime();
        }
        return null;
    }

    private Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String text = value.toString().trim();
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        if (text.matches("^-?\\d+$")) {
            try {
                return Long.parseLong(text) != 0L;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
