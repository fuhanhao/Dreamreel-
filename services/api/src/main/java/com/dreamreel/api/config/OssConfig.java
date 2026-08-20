package com.dreamreel.api.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "dreamreel.oss", name = "enabled", havingValue = "true")
    OSS ossClient(OssProperties properties) {
        return new OSSClientBuilder().build(
                properties.normalizedEndpoint(),
                properties.accessKeyId(),
                properties.accessKeySecret()
        );
    }
}
