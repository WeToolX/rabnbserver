package com.ra.rabnbserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动入口
 */
@SpringBootApplication
@EnableScheduling
public class RabnbserverApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabnbserverApplication.class, args);
    }

}
