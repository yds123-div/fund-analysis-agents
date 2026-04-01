package com.hex.fund.service.notification;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.service.entity.Notification;
import com.hex.fund.service.entity.NotificationChannel;
import com.hex.fund.service.mapper.NotificationChannelMapper;
import com.hex.fund.service.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 通知推送服务，支持多 Bark 设备和多邮箱渠道。
 * 优先从数据库读取渠道配置，兼容 application.properties 配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationChannelMapper channelMapper;
    private final JavaMailSender mailSender;
    @Value("${notification.bark.server-url:https://api.day.app}")
    private String defaultBarkServerUrl;
    @Value("${notification.bark.device-key:}")
    private String defaultBarkDeviceKey;
    @Value("${spring.mail.username:}")
    private String mailFrom;

    /** 渠道类型 -> 分发策略 */
    private final Map<String, BiConsumer<JSONObject, DispatchContext>> channelDispatchers = Map.of(
            "BARK", (config, ctx) -> sendBarkRecord(ctx.userId, ctx.reportId, ctx.title, ctx.content,
                    config.getStr("serverUrl", defaultBarkServerUrl), config.getStr("deviceKey")),
            "EMAIL", (config, ctx) -> sendEmailRecord(ctx.userId, ctx.reportId,
                    config.getStr("email"), ctx.title, ctx.content)
    );

    /** 渠道类型 -> 测试策略 */
    private final Map<String, java.util.function.Function<JSONObject, String>> channelTesters = Map.of(
            "BARK", config -> {
                sendBarkToDevice(config.getStr("serverUrl", defaultBarkServerUrl),
                        config.getStr("deviceKey"), "测试通知", "Fund Agents 通知渠道测试成功");
                return "Bark 推送成功";
            },
            "EMAIL", config -> {
                sendEmailTo(config.getStr("email"), "测试通知", "Fund Agents 通知渠道测试成功");
                return "邮件发送成功";
            }
    );

    /** 分析完成后向用户所有启用渠道推送通知 */
    @Async
    public void notifyAnalysisComplete(Long userId, Long reportId, String fundCode, String summary) {
        String title = "基金分析完成 - " + fundCode;
        // Bark 支持长文本，保留 4000 字符以展示完整分析摘要
        String content = summary.length() > 4000
                ? summary.substring(0, 4000) + "\n\n... (内容过长已截断)" : summary;
        sendToAllChannels(userId, reportId, title, content);
    }

    /** 向用户所有启用的通知渠道发送消息 */
    public void sendToAllChannels(Long userId, Long reportId, String title, String content) {
        List<NotificationChannel> channels = getEnabledChannels(userId);
        if (channels.isEmpty()) sendBarkDefault(userId, reportId, title, content);
        else channels.forEach(ch -> dispatchToChannel(ch, userId, reportId, title, content));
    }

    /** 测试通知渠道连通性 */
    public String testChannel(NotificationChannel channel) {
        try {
            JSONObject config = JSONUtil.parseObj(channel.getConfigJson());
            var tester = channelTesters.get(channel.getChannelType());
            if (tester == null) return "不支持的渠道类型: " + channel.getChannelType();
            return tester.apply(config);
        } catch (Exception e) {
            return "测试失败: " + e.getMessage();
        }
    }

    private void dispatchToChannel(NotificationChannel ch, Long userId, Long reportId,
                                   String title, String content) {
        JSONObject config = JSONUtil.parseObj(ch.getConfigJson());
        var dispatcher = channelDispatchers.get(ch.getChannelType());
        if (dispatcher != null) dispatcher.accept(config, new DispatchContext(userId, reportId, title, content));
    }

    private List<NotificationChannel> getEnabledChannels(Long userId) {
        return channelMapper.selectList(new LambdaQueryWrapper<NotificationChannel>()
                .eq(NotificationChannel::getUserId, userId).eq(NotificationChannel::getEnabled, 1));
    }

    private void sendBarkDefault(Long userId, Long reportId, String title, String content) {
        if (defaultBarkDeviceKey == null || defaultBarkDeviceKey.isBlank()) return;
        sendBarkRecord(userId, reportId, title, content, defaultBarkServerUrl, defaultBarkDeviceKey);
    }

    private void sendBarkRecord(Long userId, Long reportId, String title, String content,
                                String serverUrl, String deviceKey) {
        Notification record = buildRecord(userId, reportId, "BARK", title, content);
        try {
            sendBarkToDevice(serverUrl, deviceKey, title, content);
            record.setStatus("SENT");
            record.setSentTime(LocalDateTime.now());
            log.info("Bark推送成功: {}", title);
        } catch (Exception e) {
            record.setStatus("FAILED");
            record.setErrorMessage(e.getMessage());
            log.error("Bark推送失败: {}", e.getMessage());
        }
        notificationMapper.insert(record);
    }

    private void sendBarkToDevice(String serverUrl, String deviceKey, String title, String content) {
        String url = deviceKey.startsWith("http") ? deviceKey : serverUrl + "/" + deviceKey + "/";
        String json = JSONUtil.toJsonStr(Map.of("title", title, "body", content, "group", "fund-analysis"));
        log.debug("Bark request URL: {}, body: {}", url, json);
        var resp = cn.hutool.http.HttpRequest.post(url)
                .contentType("application/json").body(json).timeout(10000).execute();
        log.debug("Bark response: status={}, body={}", resp.getStatus(), resp.body());
        if (!resp.isOk()) throw new RuntimeException("Bark API error: " + resp.getStatus() + " - " + resp.body());
    }

    private void sendEmailRecord(Long userId, Long reportId, String to, String title, String content) {
        if (mailFrom == null || mailFrom.isBlank()) return;
        Notification record = buildRecord(userId, reportId, "EMAIL", title, content);
        try {
            sendEmailTo(to, title, content);
            record.setStatus("SENT");
            record.setSentTime(LocalDateTime.now());
            log.info("邮件发送成功: 收件人={}", to);
        } catch (Exception e) {
            record.setStatus("FAILED");
            record.setErrorMessage(e.getMessage());
            log.error("邮件发送失败: {}", e.getMessage());
        }
        notificationMapper.insert(record);
    }

    private void sendEmailTo(String to, String title, String content) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(mailFrom);
        msg.setTo(to);
        msg.setSubject(title);
        msg.setText(content);
        mailSender.send(msg);
    }

    private Notification buildRecord(Long userId, Long reportId, String channel, String title, String content) {
        return Notification.builder().userId(userId).reportId(reportId)
                .channel(channel).title(title).content(content)
                .status("PENDING").createdAt(LocalDateTime.now()).build();
    }

    private record DispatchContext(Long userId, Long reportId, String title, String content) {
    }
}
