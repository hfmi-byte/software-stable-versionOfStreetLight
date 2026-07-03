package com.group8.streetlight.config;

import cn.hutool.db.Db;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Hutool Db 统一入口，强制复用 Spring Boot 管理的 HikariDataSource。
 */
@Component
public class HutoolDb {

    private static DataSource dataSource;

    public HutoolDb(DataSource dataSource) {
        HutoolDb.dataSource = dataSource;
    }

    public static Db use() {
        return Db.use(dataSource);
    }
}
