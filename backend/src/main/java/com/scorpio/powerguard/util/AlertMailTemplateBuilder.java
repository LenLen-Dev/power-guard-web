package com.scorpio.powerguard.util;

import com.scorpio.powerguard.enums.MailMessageType;
import com.scorpio.powerguard.enums.RoomStatusEnum;
import com.scorpio.powerguard.model.AlertMailMessage;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class AlertMailTemplateBuilder {

    private static final DateTimeFormatter QUERY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String buildSubject(AlertMailMessage message) {
        if (message.getMailType() == MailMessageType.DAILY_SUMMARY) {
            return "宿舍当日用电摘要";
        }
        if (message.getMailType() == MailMessageType.DEFERRED_ALERT) {
            return "宿舍电量夜间延迟提醒";
        }
        return "宿舍电量低电量预警";
    }

    public String buildHtmlContent(String title, AlertMailMessage message) {
        MailTone tone = resolveTone(message);
        String project = fallback(message.getProject());
        String room = fallback(resolveRoom(message));
        String account = fallback(message.getAccount());
        String todayUsage = resolveTodayUsageText(message);
        String apiMessage = fallback(message.getApiMessage());
        String queryTime = message.getFetchTime() == null ? "--" : message.getFetchTime().format(QUERY_TIME_FORMATTER);
        String balanceText = formatKwh(message.getRemain());

        return """
            <div style="font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial; background-color:#f5f7fb; padding:20px;">
              <div style="max-width:600px; margin:auto; background:#ffffff; border-radius:10px; box-shadow:0 4px 12px rgba(0,0,0,0.05); overflow:hidden;">
                <div style="background:%s; color:#ffffff; padding:16px 20px; font-size:18px;">
                  %s %s
                </div>

                <div style="padding:20px;">
                  <p style="margin:0 0 10px; font-size:14px; color:#6b7280;">
                    %s
                  </p>

                  <div style="margin:20px 0; text-align:center;">
                    <div style="font-size:14px; color:#6b7280;">当前剩余电量</div>
                    <div style="font-size:36px; font-weight:bold; color:%s;">
                      %s
                    </div>
                  </div>

                  <table style="width:100%%; font-size:14px; border-collapse:collapse;">
                    <tr>
                      <td style="padding:8px 0; color:#6b7280;">项目</td>
                      <td style="padding:8px 0; text-align:right; color:#111827;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding:8px 0; color:#6b7280;">房间</td>
                      <td style="padding:8px 0; text-align:right;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding:8px 0; color:#6b7280;">账号</td>
                      <td style="padding:8px 0; text-align:right;">%s</td>
                    </tr>
                    %s
                    <tr>
                      <td style="padding:8px 0; color:#6b7280;">接口提示</td>
                      <td style="padding:8px 0; text-align:right;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding:8px 0; color:#6b7280;">查询时间</td>
                      <td style="padding:8px 0; text-align:right;">%s</td>
                    </tr>
                  </table>

                  <div style="margin-top:20px; padding:12px; background:%s; color:%s; border-radius:6px; font-size:13px;">
                    %s
                  </div>
                </div>

                <div style="background:#f9fafb; padding:12px 20px; font-size:12px; color:#9ca3af; text-align:center;">
                  本邮件由 PowerGuard 自动发送
                </div>
              </div>
            </div>
            """.formatted(
            resolveHeaderBg(tone),
            resolveHeaderIcon(message),
            escape(title),
            escape(resolveIntroText(message)),
            resolveBalanceColor(tone),
            escape(balanceText),
            escape(project),
            escape(room),
            escape(account),
            optionalRow("今日耗电量", todayUsage, shouldForceTodayUsageRow(message)),
            escape(apiMessage),
            escape(queryTime),
            resolveNoticeBg(tone),
            resolveNoticeColor(tone),
            escape(resolveNoticeText(message, tone))
        ).trim();
    }

    private MailTone resolveTone(AlertMailMessage message) {
        if (message.getMailType() == MailMessageType.LOW_BALANCE_ALERT) {
            return MailTone.ALERT;
        }
        if (message.getStatus() != null && message.getStatus() == RoomStatusEnum.ALERT.getCode()) {
            return MailTone.ALERT;
        }
        if (message.getStatus() != null && message.getStatus() == RoomStatusEnum.WARNING.getCode()) {
            return MailTone.WARNING;
        }
        return MailTone.NORMAL;
    }

    private String resolveHeaderBg(MailTone tone) {
        return switch (tone) {
            case ALERT -> "#EF4444";
            case WARNING -> "#F59E0B";
            case NORMAL -> "#3B82F6";
        };
    }

    private String resolveBalanceColor(MailTone tone) {
        return switch (tone) {
            case ALERT -> "#EF4444";
            case WARNING -> "#F59E0B";
            case NORMAL -> "#10B981";
        };
    }

    private String resolveNoticeBg(MailTone tone) {
        return switch (tone) {
            case ALERT -> "#FEE2E2";
            case WARNING -> "#FEF3C7";
            case NORMAL -> "#DBEAFE";
        };
    }

    private String resolveNoticeColor(MailTone tone) {
        return switch (tone) {
            case ALERT -> "#991B1B";
            case WARNING -> "#92400E";
            case NORMAL -> "#1E3A8A";
        };
    }

    private String resolveHeaderIcon(AlertMailMessage message) {
        if (message.getMailType() == MailMessageType.DAILY_SUMMARY) {
            return "📊";
        }
        if (message.getMailType() == MailMessageType.DEFERRED_ALERT) {
            return "⏰";
        }
        return "⚠";
    }

    private String resolveIntroText(AlertMailMessage message) {
        if (message.getMailType() == MailMessageType.DAILY_SUMMARY) {
            return "以下为本日用电摘要：";
        }
        if (message.getMailType() == MailMessageType.DEFERRED_ALERT) {
            return "以下为 07:00 延迟提醒：";
        }
        return "以下是最新电量监控信息：";
    }

    private String resolveNoticeText(AlertMailMessage message, MailTone tone) {
        if (message.getMailType() == MailMessageType.DAILY_SUMMARY) {
            return switch (tone) {
                case ALERT -> "⚠ 当前已低于阈值，请尽快处理，避免影响正常用电。";
                case WARNING -> "⚠ 当前电量处于阈值附近，请及时关注，避免继续下降。";
                case NORMAL -> "⚠ 以下为本日用电摘要，请关注剩余电量并合理安排用电。";
            };
        }
        if (message.getMailType() == MailMessageType.DEFERRED_ALERT) {
            return switch (tone) {
                case ALERT -> "⚠ 夜间静默期内曾触发低电量提醒，当前仍低于阈值，现于 07:00 补发。";
                case WARNING -> "⚠ 夜间静默期内曾触发低电量提醒，当前处于阈值附近，现于 07:00 补发。";
                case NORMAL -> "⚠ 夜间静默期提醒已恢复正常。";
            };
        }
        return "⚠ 电量处于低位，请尽快处理，避免影响正常用电。";
    }

    private String resolveRoom(AlertMailMessage message) {
        if (hasText(message.getRoomName())) {
            return message.getRoomName();
        }
        if (hasText(message.getBuildingName())) {
            return message.getBuildingName();
        }
        return null;
    }

    private String formatKwh(BigDecimal value) {
        if (value == null) {
            return "--";
        }
        return value.stripTrailingZeros().toPlainString() + " kWh";
    }

    private String resolveTodayUsageText(AlertMailMessage message) {
        if (message.getTodayUsage() != null) {
            return formatKwh(message.getTodayUsage());
        }
        return shouldForceTodayUsageRow(message) ? "--" : null;
    }

    private boolean shouldForceTodayUsageRow(AlertMailMessage message) {
        return message.getMailType() == MailMessageType.DAILY_SUMMARY;
    }

    private String optionalRow(String label, String value, boolean forceShow) {
        if (forceShow) {
            return """
                <tr>
                  <td style="padding:8px 0; color:#6b7280;">%s</td>
                  <td style="padding:8px 0; text-align:right;">%s</td>
                </tr>
                """.formatted(escape(label), escape(fallback(value))).trim();
        }
        if (!hasText(value)) {
            return "";
        }
        return """
            <tr>
              <td style="padding:8px 0; color:#6b7280;">%s</td>
              <td style="padding:8px 0; text-align:right;">%s</td>
            </tr>
            """.formatted(escape(label), escape(value)).trim();
    }

    private String fallback(String value) {
        return hasText(value) ? value.trim() : "--";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private enum MailTone {
        NORMAL,
        WARNING,
        ALERT
    }
}
