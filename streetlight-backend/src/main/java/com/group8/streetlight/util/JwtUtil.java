package com.group8.streetlight.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.group8.streetlight.config.StreetlightProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 简易 JWT（HS256）实现，避免引入额外依赖。
 * 仅用于实训项目；生产环境换用 jjwt / Nimbus 等成熟库。
 */
@Slf4j
@Component
public class JwtUtil {

    @Autowired
    private StreetlightProperties props;

    public String issue(String username, String role) {
        long now = System.currentTimeMillis() / 1000L;
        long exp = now + props.getJwt().getExpireHours() * 3600L;

        JSONObject header = new JSONObject().set("alg", "HS256").set("typ", "JWT");
        JSONObject payload = new JSONObject()
                .set("sub", username)
                .set("role", role)
                .set("iat", now)
                .set("exp", exp);

        String h = base64UrlEncode(header.toString().getBytes(StandardCharsets.UTF_8));
        String p = base64UrlEncode(payload.toString().getBytes(StandardCharsets.UTF_8));
        String signingInput = h + "." + p;
        String sig = sign(signingInput);
        return signingInput + "." + sig;
    }

    /** @return 校验通过则返回 payload；不通过返回 null */
    public JSONObject verify(String token) {
        if (StrUtil.isBlank(token)) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;
        String signingInput = parts[0] + "." + parts[1];
        String expected = sign(signingInput);
        if (!expected.equals(parts[2])) {
            log.debug("jwt 签名不匹配");
            return null;
        }
        String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
        JSONObject payload = JSONUtil.parseObj(payloadJson);
        long exp = payload.getLong("exp", 0L);
        if (exp > 0 && exp < System.currentTimeMillis() / 1000L) {
            log.debug("jwt 已过期");
            return null;
        }
        return payload;
    }

    private String sign(String signingInput) {
        byte[] key = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        HMac hmac = new HMac(HmacAlgorithm.HmacSHA256, key);
        byte[] mac = hmac.digest(signingInput);
        return base64UrlEncode(mac);
    }

    private static String base64UrlEncode(byte[] b) {
        String s = Base64.encode(b);
        return s.replace('+', '-').replace('/', '_').replace("=", "");
    }

    private static byte[] base64UrlDecode(String s) {
        String t = s.replace('-', '+').replace('_', '/');
        int mod = t.length() % 4;
        if (mod > 0) t = t + StrUtil.repeat('=', 4 - mod);
        return Base64.decode(t);
    }
}
