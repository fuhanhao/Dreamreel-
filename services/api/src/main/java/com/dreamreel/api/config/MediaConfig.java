package com.dreamreel.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MediaStorageProperties.class)
public class MediaConfig {

    @Bean
    RestClient mediaDownloadRestClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(300_000);

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
