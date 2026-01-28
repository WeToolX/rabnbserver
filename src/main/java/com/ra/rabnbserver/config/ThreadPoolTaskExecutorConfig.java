package com.ra.rabnbserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
public class ThreadPoolTaskExecutorConfig {

    @Bean(name = "taskExecutor")
    public AsyncTaskExecutor taskExecutor() {
        // 1. 创建一个带名称的虚拟线程工厂
        ThreadFactory factory = Thread.ofVirtual().name("vt-task-", 0).factory();
        // 2. 使用该工厂创建执行器
        return new TaskExecutorAdapter(Executors.newThreadPerTaskExecutor(factory));
    }
}