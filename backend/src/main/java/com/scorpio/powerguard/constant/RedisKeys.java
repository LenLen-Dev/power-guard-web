package com.scorpio.powerguard.constant;

public final class RedisKeys {

    private RedisKeys() {
    }

    public static final String MAIL_QUEUE_KEY = "power:mail:queue";
    public static final String MAIL_DEAD_LETTER_KEY = "power:mail:dead-letter";
    public static final String MAIL_SENDER_COUNT_PREFIX = "power:mail:sender:count";
    public static final String ROOM_MAIL_STATE_PREFIX = "power:mail:room";
    public static final String ALERT_SENT_SUFFIX = "alert-sent";
    public static final String SUMMARY_SENT_SUFFIX = "summary-sent";
    public static final String QUIET_PENDING_SUFFIX = "quiet-pending";
}
