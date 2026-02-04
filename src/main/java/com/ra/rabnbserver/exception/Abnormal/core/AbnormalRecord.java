package com.ra.rabnbserver.exception.Abnormal.core;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Map;

/**
 * 异常记录对象（框架内部使用）
 */
@Data
public class AbnormalRecord {

    private static final Logger log = LoggerFactory.getLogger(AbnormalRecord.class);
    private static final DateTimeFormatter FLEXIBLE_DATE_TIME_FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd HH:mm:ss")
                    .optionalStart()
                    .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
                    .optionalEnd()
                    .toFormatter();

    private Long id;
    private String userValue;
    private Integer errStatus;
    private LocalDateTime errStartTime;
    private Integer errRetryCount;
    private LocalDateTime errNextRetryTime;
    private LocalDateTime errNextRemindStaffTime;
    private Integer errManualNotifyCount;
    private Object statusValue;

    public static AbnormalRecord fromMap(Map<String, Object> row, String idField, String userField, String statusField) {
        AbnormalRecord record = new AbnormalRecord();
        record.setId(toLong(row.get(idField)));
        record.setUserValue(toString(row.get(userField)));
        record.setErrStatus(toInteger(row.get("err_status")));
        record.setErrStartTime(toLocalDateTime(row.get("err_start_time")));
        record.setErrRetryCount(toInteger(row.get("err_retry_count")));
        record.setErrNextRetryTime(toLocalDateTime(row.get("err_next_retry_time")));
        record.setErrNextRemindStaffTime(toLocalDateTime(row.get("err_next_remind_staff_time")));
        record.setErrManualNotifyCount(toInteger(row.get("err_manual_notify_count")));
        record.setStatusValue(row.get(statusField));
        return record;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        if (value instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) value).getTime()).toLocalDateTime();
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay();
        }
        if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).toLocalDateTime();
        }
        if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).toLocalDateTime();
        }
        if (value instanceof byte[]) {
            String text = new String((byte[]) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            LocalDateTime parsed = parseLocalDateTime(text);
            if (parsed != null) {
                return parsed;
            }
            log.debug("err_start_time 解析失败，原始类型=byte[], 原始值={}", text);
            return null;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            LocalDateTime parsed = parseLocalDateTime(text);
            if (parsed != null) {
                return parsed;
            }
            log.debug("err_start_time 解析失败，原始类型=String, 原始值={}", text);
            return null;
        }
        log.debug("err_start_time 解析失败，原始类型={}, 原始值={}", value.getClass().getName(), value);
        return null;
    }

    /**
     * 解析字符串时间（允许带小数秒或 ISO 格式）
     */
    private static LocalDateTime parseLocalDateTime(String text) {
        try {
            return LocalDateTime.parse(text, FLEXIBLE_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // ignored
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String toString(Object value) {
        return value == null ? null : value.toString();
    }
}
