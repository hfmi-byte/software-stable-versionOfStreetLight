package com.group8.streetlight.huawei;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.group8.streetlight.config.HuaweiIotProperties;
import com.group8.streetlight.service.CommandSender;
import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.auth.ICredential;
import com.huaweicloud.sdk.core.exception.ConnectionException;
import com.huaweicloud.sdk.core.exception.RequestTimeoutException;
import com.huaweicloud.sdk.core.exception.ServiceResponseException;
import com.huaweicloud.sdk.core.http.HttpConfig;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.CreateCommandRequest;
import com.huaweicloud.sdk.iotda.v5.model.CreateCommandResponse;
import com.huaweicloud.sdk.iotda.v5.model.DeviceCommandRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 通过华为云 IoTDA SDK 下发命令 (支持 AK/SK 和 IAM Token 两种方式)
 */
@Slf4j
@Component
@ConditionalOnExpression("'${streetlight.mode}'.equalsIgnoreCase('huawei') || '${streetlight.mode}'.equalsIgnoreCase('both')")
public class HuaweiCommandSender implements CommandSender {

    @Autowired
    private HuaweiIotProperties props;

    @Autowired
    private HuaweiIamTokenHolder tokenHolder;

    private IoTDAClient sdkClient;
    // SDK 断路器：收到 401 后置 true，后续请求直接走 IAM，不再浪费一次 SDK 调用
    private volatile boolean sdkAuthFailed = false;

    @PostConstruct
    public void init() {
        if (useAkSk()) {
            try {
                initSdkClient();
                log.info("[huawei-sdk] 使用 AK/SK 方式初始化华为云 SDK 客户端");
            } catch (Exception e) {
                log.error("[huawei-sdk] SDK 客户端初始化失败", e);
            }
        }
    }

    @Override
    public boolean send(String deviceId, String huaweiDeviceId, String action) {
        if (StrUtil.isBlank(huaweiDeviceId)) {
            log.warn("[huawei-cmd] 设备 {} 未绑定 huawei_device_id，跳过下发", deviceId);
            return false;
        }
        if (StrUtil.isBlank(props.getProjectId()) || props.getProjectId().startsWith("请到")) {
            log.warn("[huawei-cmd] projectId 未配置");
            return false;
        }

        // AK/SK 优先；401 断路后直接走 IAM，不再重试 SDK
        if (useAkSk() && sdkClient != null && !sdkAuthFailed) {
            boolean ok = sendViaSDK(deviceId, huaweiDeviceId, action);
            if (ok) return true;
            if (tokenHolder.isConfigured()) {
                log.warn("[huawei-cmd] SDK 下发失败，尝试 IAM token 降级");
                return sendViaHttp(deviceId, huaweiDeviceId, action);
            }
            return false;
        }

        if (tokenHolder.isConfigured()) {
            return sendViaHttp(deviceId, huaweiDeviceId, action);
        }

        log.warn("[huawei-cmd] 无可用华为云下发认证方式，请检查 IAM 或 AK/SK 配置");
        return false;
    }

    /**
     * 使用华为云 SDK 发送命令 (AK/SK 方式)
     */
    private boolean sendViaSDK(String deviceId, String huaweiDeviceId, String action) {
        try {
            String led = "turn_on".equalsIgnoreCase(action) ? "ON" : "OFF";
            
            // 构建命令参数
            Map<String, Object> paras = new HashMap<>();
            paras.put("Led", led);

            DeviceCommandRequest commandBody = new DeviceCommandRequest()
                    .withServiceId(props.getServiceId())
                    .withCommandName(props.getCommandName())
                    .withParas(paras);

            CreateCommandRequest request = new CreateCommandRequest()
                    .withDeviceId(huaweiDeviceId)
                    .withBody(commandBody);
            if (StrUtil.isNotBlank(props.getInstanceId())) {
                request.withInstanceId(props.getInstanceId());
            }

            // 发送命令
            CreateCommandResponse response = sdkClient.createCommand(request);
            
            log.info("[huawei-cmd] {} → {} status=success commandId={}", 
                    deviceId, action, response.getCommandId());
            return true;
            
        } catch (ConnectionException e) {
            log.error("[huawei-cmd] SDK 连接失败: {}", e.getMessage());
            return false;
        } catch (RequestTimeoutException e) {
            log.error("[huawei-cmd] SDK 请求超时: {}", e.getMessage());
            return false;
        } catch (ServiceResponseException e) {
            log.error("[huawei-cmd] SDK 调用失败 status={} errorCode={} errorMsg={}",
                    e.getHttpStatusCode(), e.getErrorCode(), e.getErrorMsg());
            // 401 鉴权失败：AK/SK 无效，触发断路器，后续请求直接走 IAM，不再重试 SDK
            if (e.getHttpStatusCode() == 401) {
                sdkAuthFailed = true;
                log.warn("[huawei-sdk] AK/SK 鉴权持续失败，已禁用 SDK 路径，后续全部走 IAM token");
            }
            return false;
        } catch (Exception e) {
            log.error("[huawei-cmd] SDK 调用异常", e);
            return false;
        }
    }

    /**
     * 使用 HTTP + IAM Token 方式发送命令 (降级方案)
     */
    private boolean sendViaHttp(String deviceId, String huaweiDeviceId, String action) {
        String token = tokenHolder.getToken();
        if (StrUtil.isBlank(token)) {
            log.warn("[huawei-cmd] 无可用 IAM token 且未配置 AK/SK，跳过下发");
            return false;
        }

        String url = props.getEndpoint()
                + "/v5/iot/" + props.getProjectId()
                + "/devices/" + huaweiDeviceId
                + "/commands";

        String led = "turn_on".equalsIgnoreCase(action) ? "ON" : "OFF";
        JSONObject body = new JSONObject()
                .set("service_id", props.getServiceId())
                .set("command_name", props.getCommandName())
                .set("paras", new JSONObject().set("Led", led));

        try {
            cn.hutool.http.HttpRequest httpRequest = cn.hutool.http.HttpRequest.post(url)
                    .header("Content-Type", "application/json")
                    .header("X-Auth-Token", token)
                    .body(body.toString())
                    .timeout(15000); // 华为云 API 偶发慢响应，从 8s 放宽到 15s

            if (StrUtil.isNotBlank(props.getInstanceId())) {
                httpRequest.header("Instance-Id", props.getInstanceId());
            }

            try (cn.hutool.http.HttpResponse resp = httpRequest.execute()) {
                int st = resp.getStatus();
                log.info("[huawei-cmd] {} → {} status={} body={}", deviceId, action, st, resp.body());
                if (st == 403 && StrUtil.containsIgnoreCase(resp.body(), "device is not online")) {
                    log.warn("[huawei-cmd] 华为云认为设备不在线，等待设备重新上报后再重试 deviceId={}", deviceId);
                }
                return st >= 200 && st < 300;
            }
        } catch (Exception e) {
            log.error("[huawei-cmd] HTTP 调用失败", e);
            return false;
        }
    }

    /**
     * 判断是否使用 AK/SK 方式
     */
    private boolean useAkSk() {
        return StrUtil.isNotBlank(props.getAccessKey()) && StrUtil.isNotBlank(props.getSecretKey());
    }

    /**
     * 初始化华为云 SDK 客户端
     */
    private void initSdkClient() {
        // 配置认证信息
        ICredential auth = new BasicCredentials()
                .withAk(props.getAccessKey())
                .withSk(props.getSecretKey())
                .withProjectId(props.getProjectId());

        // 配置 HTTP 参数
        HttpConfig config = HttpConfig.getDefaultHttpConfig()
                .withTimeout(8);

        // 创建客户端 (修正 Builder 用法)
        sdkClient = IoTDAClient.newBuilder()
                .withHttpConfig(config)
                .withCredential(auth)
                .withEndpoint(props.getEndpoint())
                .build();
    }
}
