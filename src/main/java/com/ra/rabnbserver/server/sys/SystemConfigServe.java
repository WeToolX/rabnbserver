package com.ra.rabnbserver.server.sys;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.pojo.SystemConfig;
import org.springframework.transaction.annotation.Transactional;

public interface SystemConfigServe extends IService<SystemConfig> {
    String getValueByKey(String key);

    <T> T getConfigObject(String key, Class<T> clazz);

    void checkAndInitDefaults();

    @Transactional(rollbackFor = Exception.class)
    void saveOrUpdateConfig(SystemConfig config);
}
