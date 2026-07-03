package com.group8.streetlight.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/** 记录数据库配置。实际 Hutool Db 入口见 {@link HutoolDb}，统一复用 Spring HikariDataSource。 */
@Slf4j
@Component
public class HutoolDbBridge {

    @Value("${spring.datasource.url}")
    private String url;
    @Value("${spring.datasource.username}")
    private String username;
    @Value("${spring.datasource.password}")
    private String password;
    @Value("${spring.datasource.driver-class-name:com.mysql.jdbc.Driver}")
    private String driver;

    @PostConstruct
    public void bridge() {
        log.info("Hutool Db 将复用 Spring HikariDataSource: {} (user={}, driver={})", url, username, driver);
    }
}
