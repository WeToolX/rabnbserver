package com.ra.rabnbserver.server.miner.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ra.rabnbserver.mapper.SystemConfigMapper;
import com.ra.rabnbserver.pojo.SystemConfig;
import com.ra.rabnbserver.server.miner.MinerServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class MinerTaskScheduler implements SchedulingConfigurer {

    private final MinerServe minerServe;
    private final SystemConfigMapper configMapper;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        // 动态 Cron 任务：处理每日收益发放
        registrar.addTriggerTask(
                () -> {
                    try {
                        log.info("开始执行每日矿机收益发放任务...");
                        minerServe.processDailyProfit();
                    } catch (Exception e) {
                        log.error("每日收益发放任务执行异常: ", e);
                    }
                },
                context -> {
                    String cron = getProfitCronFromConfig();
                    return new CronTrigger(cron).nextExecutionTime(context).toInstant();
                }
        );
    }

    /**
     * 从数据库配置中读取时间并转换为 Cron 表达式
     */
    private String getProfitCronFromConfig() {
        SystemConfig config = configMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "MINER_SYSTEM_SETTINGS"));

        String profitTime = "00:00:00"; // 默认凌晨执行
        if (config != null) {
            try {
                profitTime = com.alibaba.fastjson2.JSON.parseObject(config.getConfigValue()).getString("profitTime");
            } catch (Exception e) {
                log.warn("解析收益发放时间配置失败，使用默认值 00:00:00");
            }
        }

        String[] t = profitTime.split(":");
        // 转换为 Cron: s m h d m w (秒 分 时 天 月 周)
        return String.format("%s %s %s * * ?", t[2], t[1], t[0]);
    }
}