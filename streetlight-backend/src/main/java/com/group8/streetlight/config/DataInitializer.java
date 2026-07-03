package com.group8.streetlight.config;

import com.group8.streetlight.dao.UserDao;
import com.group8.streetlight.util.Passwords;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * 启动初始化：
 *  1. 跑 src/main/resources/sql/schema.sql（IF NOT EXISTS，已存在则跳过）
 *  2. 把 application.yml 配的 default-accounts 写进 users 表
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired private StreetlightProperties props;
    @Autowired private UserDao userDao;
    @Autowired private DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        runSchema();
        initUsers();
    }

    private void runSchema() {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("sql/schema.sql"));
            populator.setSqlScriptEncoding("UTF-8");  // Windows 默认编码是 GBK，必须显式指定
            populator.setContinueOnError(true);   // IF NOT EXISTS 等语句失败时不中断
            populator.setIgnoreFailedDrops(true);
            populator.setSeparator(";");
            populator.execute(dataSource);
            log.info("schema.sql 执行完毕");
        } catch (Exception e) {
            log.error("schema.sql 执行失败", e);
        }
    }

    private void initUsers() throws SQLException {
        if (props.getDefaultAccounts() == null) return;
        for (StreetlightProperties.DefaultAccount a : props.getDefaultAccounts()) {
            if (a.getUsername() == null || a.getPassword() == null) continue;
            if (userDao.findByUsername(a.getUsername()) == null) {
                userDao.insert(a.getUsername(), Passwords.hash(a.getPassword()), a.getRole());
                log.info("初始化账号 {} (role={})", a.getUsername(), a.getRole());
            }
        }
    }
}
