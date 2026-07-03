package com.group8.streetlight.util;

import cn.hutool.core.util.StrUtil;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/** ISO-8601 ↔ java.util.Date 转换工具。后端对外统一 ISO-8601（接口规范 §1.5）。 */
public final class Times {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private Times() {}

    public static String toIso(Date d) {
        if (d == null) return null;
        return d.toInstant().atZone(ZONE).format(ISO);
    }

    public static Date fromIso(String s) {
        if (StrUtil.isBlank(s)) return null;
        try {
            // 兼容 Huawei IoT 的 yyyyMMdd'T'HHmmss'Z' 格式
            if (s.length() == 16 && s.charAt(8) == 'T' && s.endsWith("Z")) {
                LocalDateTime ldt = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
                return Date.from(ldt.atZone(ZoneOffset.UTC).toInstant());
            }
            OffsetDateTime odt = OffsetDateTime.parse(s, ISO);
            return Date.from(odt.toInstant());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static String nowIso() {
        return toIso(new Date());
    }
}
