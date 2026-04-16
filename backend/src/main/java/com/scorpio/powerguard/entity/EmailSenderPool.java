package com.scorpio.powerguard.entity;

import lombok.Data;

@Data
public class EmailSenderPool {

    private Long id;
    private String emailAccount;
    private String authCode;
    private Integer status;
}
