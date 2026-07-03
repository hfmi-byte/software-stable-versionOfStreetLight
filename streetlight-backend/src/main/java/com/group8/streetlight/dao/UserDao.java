package com.group8.streetlight.dao;

import cn.hutool.db.Entity;
import com.group8.streetlight.config.HutoolDb;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;

@Slf4j
@Repository
public class UserDao {

    public Entity findByUsername(String username) throws SQLException {
        return HutoolDb.use().queryOne("SELECT * FROM users WHERE username = ?", username);
    }

    public int insert(String username, String passwordHash, String role) throws SQLException {
        return HutoolDb.use().insert(Entity.create("users")
                .set("username", username)
                .set("password_hash", passwordHash)
                .set("role", role));
    }
}
