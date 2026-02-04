package com.ra.rabnbserver.controller.sys;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ra.rabnbserver.pojo.SystemConfig;
import com.ra.rabnbserver.server.sys.SystemConfigServe;
import com.ra.rabnbserver.server.sys.impl.SystemConfigServeImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员端 -- 系统配置
 */
@RestController
@RequestMapping("/api/admin/system/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigServe systemConfigServe;

    /**
     * 分页查询配置
     */
    @GetMapping("/page")
    public IPage<SystemConfig> getPage(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       String configKey, String configName) {

        systemConfigServe.checkAndInitDefaults();
        Page<SystemConfig> pageParam = new Page<>(page, size);
        return systemConfigServe.page(pageParam, new LambdaQueryWrapper<SystemConfig>()
                .like(StrUtil.isNotBlank(configKey), SystemConfig::getConfigKey, configKey)
                .like(StrUtil.isNotBlank(configName), SystemConfig::getConfigName, configName)
                .orderByDesc(SystemConfig::getId));
    }

    /**
     * 获取详情
     */
    @GetMapping("/{id}")
    public SystemConfig getById(@PathVariable Long id) {
        systemConfigServe.checkAndInitDefaults();
        return systemConfigServe.getById(id);
    }

    /**
     * 根据 Key 直接查询配置值 (常用)
     */
    @GetMapping("/val")
    public String getValueByKey(@RequestParam String key) {
        return systemConfigServe.getValueByKey(key);
    }

    /**
     * 新增或修改
     */
    @PostMapping("/save")
    public void save(@RequestBody SystemConfig config) {
        systemConfigServe.saveOrUpdateConfig(config);
    }

    /**
     * 删除
     */
    @GetMapping("/delete/{id}")
    public void remove(@PathVariable Long id) {
        systemConfigServe.removeById(id);
    }
}