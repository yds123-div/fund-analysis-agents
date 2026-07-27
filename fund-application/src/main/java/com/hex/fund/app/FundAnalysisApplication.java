package com.hex.fund.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 基金分析 Agents 系统启动入口。
 */
@SpringBootApplication(scanBasePackages = "com.hex.fund")
@MapperScan("com.hex.fund.service.mapper")
@EnableScheduling
@EnableAsync
public class FundAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(FundAnalysisApplication.class, args);
    }
}
