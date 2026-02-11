package com.ra.rabnbserver.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.ra.rabnbserver.enums.AbnormalStatus;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // 1. 获取当前时间 (Asia/Shanghai)
        LocalDateTime beijingTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        // 2. 插入时：同时填充 createTime 和 updateTime
        // 这样数据库中 create_time 和 update_time 都有值，且初始值一致
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, beijingTime);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, beijingTime);

        // 3. 异常框架字段兜底：errStatus 为空则补 2000，errStartTime 为空则补当前时间
        if (metaObject.hasSetter("errStatus") && metaObject.getValue("errStatus") == null) {
            metaObject.setValue("errStatus", AbnormalStatus.NORMAL.getCode());
        }
        if (metaObject.hasSetter("errStartTime") && metaObject.getValue("errStartTime") == null) {
            metaObject.setValue("errStartTime", beijingTime);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 1. 获取当前时间 (Asia/Shanghai)
        LocalDateTime beijingTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        // 2. 更新时：只刷新 updateTime
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, beijingTime);
    }
}
