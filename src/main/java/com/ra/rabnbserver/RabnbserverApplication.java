package com.ra.rabnbserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动入口
 */
@SpringBootApplication(exclude = {
        // 当前阶段不启用数据源自动装配，避免无数据库配置导致启动失败
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class
})
@EnableScheduling
public class RabnbserverApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabnbserverApplication.class, args);
    }

}
