package com.dreamreel.api.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dreamreel.api.config.ArkApiKeyResolver;
import com.dreamreel.api.config.ArkProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ArkSeedreamClientTest {

    @Test
    void createsSeedreamFiveProImageWithReferences() {
        var builder = RestClient.builder().baseUrl("https://ark.example.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = new ArkProperties(
                "https://ark.example.test",
                "test-key",
                "seedance-model",
                "seedance-fast-model",
                "doubao-seedream-5-0-260128",
                false);
        var client = new ArkSeedreamClient(
                builder.build(),
                new ObjectMapper(),
                properties,
                new ArkApiKeyResolver(properties));

        server.expect(requestTo("https://ark.example.test/api/v3/images/generations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json("""
                        {
                          "model": "doubao-seedream-5-0-260128",
                          "prompt": "cinematic storyboard",
                          "size": "2K",
                          "aspect_ratio": "16:9",
                          "response_format": "url",
                          "output_format": "jpeg",
                          "watermark": false,
                          "image": ["https://example.test/reference.jpg"]
                        }
                        """))
                .andRespond(withSuccess("""
                        {"data":[{"url":"https://example.test/generated.jpg"}]}
                        """, MediaType.APPLICATION_JSON));

        var result = client.createImage(new ArkSeedreamClient.CreateImagePayload(
                null,
                "cinematic storyboard",
                "16:9",
                "https://example.test/reference.jpg",
                List.of()));

        assertThat(result.outputUrl()).isEqualTo("https://example.test/generated.jpg");
        server.verify();
    }
}
