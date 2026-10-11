package me.dennislab.diffvera.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuration providing a shared RestClient bean for external HTTP integrations
 * (GitHub REST APIs, Google Gemini generative AI API).
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }
}
