package com.scorpio.powerguard.enums;

import lombok.Getter;

@Getter
public enum RoomStatusEnum {

    NORMAL(0, "正常"),
    WARNING(1, "警告"),
    ALERT(2, "告警");

    private final int code;
    private final String desc;

    RoomStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static String getDescByCode(Integer code) {
        if (code == null) {
            return NORMAL.desc;
        }
        for (RoomStatusEnum statusEnum : values()) {
            if (statusEnum.code == code) {
                return statusEnum.desc;
            }
        }
        return NORMAL.desc;
    }
}
