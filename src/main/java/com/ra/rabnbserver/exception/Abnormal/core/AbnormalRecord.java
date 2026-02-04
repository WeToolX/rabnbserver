package com.ra.rabnbserver.exception.Abnormal.core;

import lombok.Data;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 异常记录对象（框架内部使用）
 */
@Data
public class AbnormalRecord {

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
        return null;
    }

    private static String toString(Object value) {
        return value == null ? null : value.toString();
    }
}
