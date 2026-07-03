package com.group8.streetlight.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistoryPoint {
    private String time;     // ISO 8601
    private String device;
    private Integer light;
    private Boolean lamp;
    /** MQTT上报 | 自动联动 | 手动控制 */
    private String source;
}
