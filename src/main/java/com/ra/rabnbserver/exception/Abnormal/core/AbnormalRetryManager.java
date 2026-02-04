package com.ra.rabnbserver.exception.Abnormal.core;

import com.ra.rabnbserver.exception.Abnormal.annotation.AbnormalRetryConfig;
import com.ra.rabnbserver.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

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
        String sql = "SELECT COUNT(1) FROM " + wrapTable(config.table())
                + " WHERE (" + wrapColumn("err_status") + " = ? OR " + wrapColumn(config.statusField()) + " = ?)"
                + " AND " + wrapColumn(config.userField()) + " = ?";
        Object failValue = parseValue(config.failValue());
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, 4000, failValue, userValue);
        if (count != null && count > 0) {
            throw new BusinessException("当前操作存在异常，请过一会再试试或联系客服");
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
        jdbcTemplate.update(sql, 2002, 4002, parseValue(config.successValue()), dataId);
        log.info("人工处理成功已回写，服务={}, 数据ID={}", config.serviceName(), dataId);
    }

    /**
     * 业务异常落库入口
     *
     * @param serviceClass 服务类
     * @param dataId 数据主键
     */
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
    public void markAbnormal(Class<?> serviceClass, Long dataId, String userValue) {
        AbnormalContext context = classContextMap.get(serviceClass);
        if (context == null) {
            throw new BusinessException("异常框架未初始化或未注册服务");
        }
        AbnormalRetryConfig config = context.getConfig();
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
        params.add(4000);
        params.add(parseValue(config.failValue()));
        params.add(config.minIntervalSeconds());
        params.add(config.timeoutSeconds());
        if (userValue != null) {
            sql.append(", ").append(wrapColumn(config.userField())).append(" = ?");
            params.add(userValue);
        }
        sql.append(" WHERE ").append(wrapColumn(config.idField())).append(" = ?");
        params.add(dataId);
        jdbcTemplate.update(sql.toString(), params.toArray());
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
                + " AND " + wrapColumn("err_start_time") + " IS NOT NULL"
                + " AND TIMESTAMPDIFF(SECOND, " + wrapColumn("err_start_time") + ", NOW()) < ?"
                + " AND COALESCE(" + wrapColumn("err_retry_count") + ", 0) < ?"
                + " AND " + wrapColumn(config.statusField()) + " = ?";
        Object failValue = parseValue(config.failValue());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql,
                4000,
                config.timeoutSeconds(),
                config.maxRetryCount(),
                failValue
        );
        List<AbnormalRecord> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            records.add(AbnormalRecord.fromMap(row, config.idField(), config.userField(), config.statusField()));
        }
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
                + " WHERE " + wrapColumn("err_status") + " IN (4000, 4001)"
                + " AND " + wrapColumn("err_start_time") + " IS NOT NULL"
                + " AND TIMESTAMPDIFF(SECOND, " + wrapColumn("err_start_time") + ", NOW()) > ?"
                + " AND COALESCE(" + wrapColumn("err_retry_count") + ", 0) >= ?"
                + " AND (" + wrapColumn("err_next_remind_staff_time") + " IS NULL OR " + wrapColumn("err_next_remind_staff_time") + " < NOW())"
                + " AND " + wrapColumn(config.statusField()) + " = ?";
        Object failValue = parseValue(config.failValue());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql,
                config.timeoutSeconds(),
                config.maxRetryCount(),
                failValue
        );
        List<AbnormalRecord> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            records.add(AbnormalRecord.fromMap(row, config.idField(), config.userField(), config.statusField()));
        }
        return records;
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
        jdbcTemplate.update(sql, 2001, parseValue(config.successValue()), dataId);
        log.info("自动重试成功，服务={}, 数据ID={}", config.serviceName(), dataId);
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
        if (record.getErrStatus() == null || record.getErrStatus() != 4001) {
            String sql = "UPDATE " + wrapTable(config.table()) + " SET "
                    + wrapColumn("err_status") + " = ? "
                    + "WHERE " + wrapColumn(config.idField()) + " = ?";
            jdbcTemplate.update(sql, 4001, record.getId());
        }
        if (record.getErrNextRemindStaffTime() != null && record.getErrNextRemindStaffTime().isAfter(now)) {
            log.info("人工通知未到提醒时间，服务={}, 数据ID={}, 下次提醒时间={}",
                    config.serviceName(), record.getId(), record.getErrNextRemindStaffTime());
            return;
        }
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
        boolean mailOk = false;
        try {
            mailService.sendErrToMail(config, record, fullData);
            mailOk = true;
        } catch (Exception e) {
            log.error("发送异常通知失败，服务={}, 数据ID={}, 原因={}",
                    config.serviceName(), record.getId(), e.getMessage());
        }
        LocalDateTime nextRemindTime = calcNextRemindTime(config, record);
        String sql = "UPDATE " + wrapTable(config.table()) + " SET "
                + wrapColumn("err_next_remind_staff_time") + " = ?, "
                + wrapColumn("err_submit_manual_status") + " = ?, "
                + wrapColumn("err_manual_notify_count") + " = COALESCE(" + wrapColumn("err_manual_notify_count") + ", 0) + 1 "
                + "WHERE " + wrapColumn(config.idField()) + " = ?";
        jdbcTemplate.update(sql, Timestamp.valueOf(nextRemindTime), mailOk ? 2000 : 4000, record.getId());
        log.info("人工通知已记录，服务={}, 数据ID={}, 下次提醒时间={}",
                config.serviceName(), record.getId(), nextRemindTime);
    }

    /**
     * 根据重试次数和间隔计算下一个提醒时间
     */
    private LocalDateTime calcNextRemindTime(AbnormalRetryConfig config, AbnormalRecord record) {
        LocalDateTime now = LocalDateTime.now();
        if (record.getErrStartTime() == null) {
            return now.plusSeconds(config.manualRemindIntervalSeconds());
        }
        long seconds = Duration.between(record.getErrStartTime(), now).getSeconds();
        if (seconds <= 0) {
            seconds = config.manualRemindIntervalSeconds();
        }
        return now.plusSeconds(seconds);
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
                            + " INT DEFAULT 2000 COMMENT '异常主状态'";
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
        String sql = "SELECT * FROM " + wrapTable(config.table())
                + " WHERE " + wrapColumn(config.idField()) + " = ? FOR UPDATE SKIP LOCKED";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, dataId);
        if (rows.isEmpty()) {
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
            return false;
        }
        AbnormalRetryConfig config = context.getConfig();
        if (record.getErrStatus() == null || record.getErrStatus() != 4000) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (record.getErrNextRetryTime() != null && record.getErrNextRetryTime().isAfter(now)) {
            return false;
        }
        if (record.getErrStartTime() == null) {
            return false;
        }
        long duration = Duration.between(record.getErrStartTime(), now).getSeconds();
        if (duration >= config.timeoutSeconds()) {
            return false;
        }
        int retryCount = Optional.ofNullable(record.getErrRetryCount()).orElse(0);
        if (retryCount >= config.maxRetryCount()) {
            return false;
        }
        Object failValue = parseValue(config.failValue());
        return valueEquals(record.getStatusValue(), failValue);
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
            return false;
        }
        AbnormalRetryConfig config = context.getConfig();
        if (record.getErrStatus() == null || (record.getErrStatus() != 4000 && record.getErrStatus() != 4001)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (record.getErrStartTime() == null) {
            return false;
        }
        long duration = Duration.between(record.getErrStartTime(), now).getSeconds();
        if (duration <= config.timeoutSeconds()) {
            return false;
        }
        int retryCount = Optional.ofNullable(record.getErrRetryCount()).orElse(0);
        if (retryCount < config.maxRetryCount()) {
            return false;
        }
        if (record.getErrNextRemindStaffTime() != null && record.getErrNextRemindStaffTime().isAfter(now)) {
            return false;
        }
        Object failValue = parseValue(config.failValue());
        return valueEquals(record.getStatusValue(), failValue);
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
        if (value.matches("^-?\\d+$")) {
            try {
                return Long.parseLong(value);
            } catch (Exception e) {
                return value;
            }
        }
        return value;
    }

    private boolean valueEquals(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.toString().equals(right.toString());
    }
}
