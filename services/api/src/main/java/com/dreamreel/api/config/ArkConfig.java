package com.dreamreel.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ArkProperties.class)
public class ArkConfig {

    @Bean
    RestClient arkRestClient(ArkProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(300_000);

        var baseUrl = properties.baseUrl() != null && !properties.baseUrl().isBlank()
                ? properties.baseUrl()
                : "https://ark.cn-beijing.volces.com";

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
