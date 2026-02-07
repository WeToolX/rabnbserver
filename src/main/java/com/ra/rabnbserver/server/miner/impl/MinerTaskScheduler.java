package com.ra.rabnbserver.server.miner.impl;

import cn.hutool.core.util.StrUtil;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class MinerTaskScheduler implements SchedulingConfigurer {

    private final MinerServe minerServe;
    private final SystemConfigMapper configMapper;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        // 动态 Cron 任务 - 处理每日收益发放 (processDailyProfit)
        registrar.addTriggerTask(
                () -> {
                    try {
                        log.info("【定时任务】开始执行每日矿机收益发放...");
                        minerServe.processDailyProfit();
                    } catch (Exception e) {
                        log.error("【定时任务】每日收益发放执行异常: ", e);
                    }
                },
                context -> {
                    String cron = getCronFromConfig("profitTime", "00:00:00");
                    return new CronTrigger(cron).nextExecutionTime(context).toInstant();
                }
        );

        // 动态 Cron 任务 - 处理每日电费分成 (processDailyElectricityReward)
        registrar.addTriggerTask(
                () -> {
                    try {
                        log.info("【定时任务】开始执行每日电费分成结算...");
                        minerServe.processDailyElectricityReward();
                    } catch (Exception e) {
                        log.error("【定时任务】每日电费分成结算执行异常: ", e);
                    }
                },
                context -> {
                    // 默认建议在深夜 23:50 执行，确保统计全天数据
                    String cron = getCronFromConfig("electricityRewardTime", "23:50:00");
                    return new CronTrigger(cron).nextExecutionTime(context).toInstant();
                }
        );
    }

    /**
     * 通用的 Cron 获取方法
     * @param jsonKey 配置文件中的 Key (如 profitTime, electricityRewardTime)
     * @param defaultTime 默认时间 (格式 HH:mm:ss)
     * @return Cron 表达式
     */
    private String getCronFromConfig(String jsonKey, String defaultTime) {
        SystemConfig config = configMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "MINER_SYSTEM_SETTINGS"));

        String time = defaultTime;
        if (config != null) {
            try {
                time = com.alibaba.fastjson2.JSON.parseObject(config.getConfigValue()).getString(jsonKey);
                if (StrUtil.isBlank(time)) {
                    time = defaultTime;
                }
            } catch (Exception e) {
                log.warn("解析配置 {} 失败，使用默认值 {}", jsonKey, defaultTime);
            }
        }

        // 简单的 HH:mm:ss 转 Cron 逻辑
        String[] t = time.split(":");
        if (t.length != 3) return "0 0 0 * * ?"; // 格式错误兜底凌晨

        // 转换为 Cron: s m h d m w (秒 分 时 天 月 周)
        return String.format("%s %s %s * * ?", t[2], t[1], t[0]);
    }
}