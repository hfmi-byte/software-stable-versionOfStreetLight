package com.cjh.mqtt;

import cn.hutool.db.Db;
import cn.hutool.db.Entity;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

public  class OpService {

    public static Map loginByuserNameAndPassword(String username,String password) throws SQLException {
        Map msg = new HashMap();
        // 做逻辑操作，需要到数据库里面去做查找 (业务需求)
        //SELECT  * FROM
        //info
        //WHERE `name`="" AND sex = ""
        //查询
        List<Entity> result = Db.use().query("SELECT  * FROM info WHERE name = ? AND sex = ?", username,password);
        // 如果有这个人
        if(result.size() > 0){
            msg.put("reuslt","ok");
        }else {
            msg.put("reuslt","no");
        }
        return msg;
    }
}
