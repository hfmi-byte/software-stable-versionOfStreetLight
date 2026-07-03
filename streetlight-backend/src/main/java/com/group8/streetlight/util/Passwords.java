package com.group8.streetlight.util;

import cn.hutool.crypto.SecureUtil;

/** 简单的 SHA-256 密码哈希。实训项目用，生产请换 BCrypt。 */
public final class Passwords {
    private Passwords() {}

    public static String hash(String raw) {
        return SecureUtil.sha256(raw);
    }

    public static boolean match(String raw, String hash) {
        return hash != null && hash.equals(SecureUtil.sha256(raw));
    }
}
