package com.cjh.mqtt.api;


import com.cjh.mqtt.OpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Phaser;

// 可以把输出转化为json格式
@CrossOrigin
@RestController
public class DataOpApi {


    /**
     * 用户登陆
     * 输入：用户名和密码
     * 输出：
     */
    @PostMapping("/login")
    public Map userLogin(@RequestBody Map map) throws SQLException {
        // 1.数据能够被接收的情况下面
        System.out.println(map.get("username"));
        String username = map.get("username").toString();
        System.out.println(map.get("password"));
        String password = map.get("password").toString();
        // 2.实现业务（需求需要进行控制）需要到数据库里面去做查找（封装）（接口---实现）
        return  OpService.loginByuserNameAndPassword(username,password);
    }

    @GetMapping("/fetchStudents")
    public Map fetchStudents() throws SQLException {
        // 连接数据库，写查询语句

        //
        Map map = new HashMap();
        map.put("code","");
        map.put("msg","");
        map.put("data","");
        return   map;
    }
}
