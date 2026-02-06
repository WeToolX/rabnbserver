package com.ra.rabnbserver.VO;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 异常记录分页结果
 */
@Data
public class AbnormalPageVO {

    /**
     * 记录列表
     */
    private List<Map<String, Object>> records;

    /**
     * 总数
     */
    private long total;

    /**
     * 页码
     */
    private int pageNum;

    /**
     * 每页数量
     */
    private int pageSize;

    /**
     * 服务名列表（用于前端筛选）
     */
    private List<String> serviceNames;

    /**
     * 字段注释映射（table -> (column -> comment)）
     */
    private Map<String, Map<String, String>> columnComments;
}
