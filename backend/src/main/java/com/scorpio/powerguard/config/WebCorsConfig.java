package com.scorpio.powerguard.config;

/**
 * CORS 配置已统一收口到 {@link CorsConfig}。
 * 保留这个类只是为了避免重复启用第二份全局 CORS 映射。
 */
@Deprecated
public final class WebCorsConfig {

    private WebCorsConfig() {
    }
}
