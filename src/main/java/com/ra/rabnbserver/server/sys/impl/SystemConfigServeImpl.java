package com.ra.rabnbserver.server.sys.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.VO.MinerSettings;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.SystemConfigMapper;
import com.ra.rabnbserver.pojo.SystemConfig;
import com.ra.rabnbserver.server.sys.SystemConfigServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SystemConfigServeImpl extends ServiceImpl<SystemConfigMapper, SystemConfig> implements SystemConfigServe {

    // 使用同步锁防止并发初始化
    private static final Object INIT_LOCK = new Object();

    @Override
    public String getValueByKey(String key) {
        // 1. 检查并初始化默认值
        checkAndInitDefaults();

        SystemConfig config = this.getOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key));
        return config != null ? config.getConfigValue() : null;
    }

    @Override
    public <T> T getConfigObject(String key, Class<T> clazz) {
        String value = this.getValueByKey(key);
        if (StrUtil.isBlank(value)) return null;
        return com.alibaba.fastjson2.JSON.parseObject(value, clazz);
    }

    /**
     * 检查数据库是否为空，若为空则插入默认配置
     */
    @Override
    public void checkAndInitDefaults() {
        // 先快速检查（非锁定），如果已有数据直接返回
        if (this.count() > 0) {
            return;
        }

        synchronized (INIT_LOCK) {
            // 二次检查（双重检查锁定模式）
            if (this.count() == 0) {
                log.info("检测到系统配置表为空，开始初始化默认配置...");
                List<SystemConfig> defaultConfigs = new ArrayList<>();

                // 1. 矿机系统设置
                MinerSettings minerSettings = new MinerSettings();
                minerSettings.setElectricFee(new BigDecimal("10.00"));
                minerSettings.setAccelerationFee(new BigDecimal("50.00"));
                // 设置默认分销比例：1级10%，2级5%，3级2%
                Map<Integer, BigDecimal> ratios = new HashMap<>();
                ratios.put(1, new BigDecimal("0.10"));
                ratios.put(2, new BigDecimal("0.05"));
                ratios.put(3, new BigDecimal("0.02"));
                minerSettings.setDistributionRatios(ratios);

                SystemConfig minerConfig = new SystemConfig();
                minerConfig.setConfigKey("MINER_SYSTEM_SETTINGS");
                minerConfig.setConfigName("矿机全局参数配置");
                minerConfig.setConfigValue(com.alibaba.fastjson2.JSON.toJSONString(minerSettings));
                minerConfig.setRemark("包含电费、加速费、分销比例等");
                defaultConfigs.add(minerConfig);

                // 2. 提现设置 (示例)
                SystemConfig withdrawConfig = new SystemConfig();
                withdrawConfig.setConfigKey("WITHDRAW_SETTINGS");
                withdrawConfig.setConfigName("提现参数设置");
                withdrawConfig.setConfigValue("{\"minAmount\":10, \"feeRate\":0.05}");
                withdrawConfig.setRemark("最小提现金额及手续费率");
                defaultConfigs.add(withdrawConfig);

                // 3. 平台公告 (示例)
                SystemConfig noticeConfig = new SystemConfig();
                noticeConfig.setConfigKey("PLATFORM_NOTICE");
                noticeConfig.setConfigName("全局公告");
                noticeConfig.setConfigValue("欢迎来到 RaBnB 矿机系统！");
                noticeConfig.setRemark("显示在首页的滚动公告");
                defaultConfigs.add(noticeConfig);

                this.saveBatch(defaultConfigs);
                log.info("默认配置初始化完成，共插入 {} 条数据。", defaultConfigs.size());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveOrUpdateConfig(SystemConfig config) {
        SystemConfig existing = this.getOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, config.getConfigKey()));

        if (config.getId() == null) {
            if (existing != null) throw new BusinessException("配置Key已存在");
            this.save(config);
        } else {
            if (existing != null && !existing.getId().equals(config.getId())) {
                throw new BusinessException("配置Key已被其他记录占用");
            }
            this.updateById(config);
        }
    }
}