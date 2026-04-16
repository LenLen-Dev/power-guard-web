package com.scorpio.powerguard.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "external-electricity")
public class ExternalElectricityProperties {

    private String url;
    private String aid;
    private String fixedAccount;
    private String userAgent;
    private String accept;
    private String acceptEncoding;
    private String acceptLanguage;
    private String origin;
    private String referer;
    private String cookie;
    private String area;
    private Integer timeoutMillis;
}
