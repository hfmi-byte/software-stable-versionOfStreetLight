package com.group8.streetlight.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    private String time;     // ISO 8601
    private String title;
    /** 接口规范字段名就叫 copy */
    private String copy;
}
