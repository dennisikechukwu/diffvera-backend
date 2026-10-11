package me.dennislab.diffvera.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Clean OpenAPI 3.0 configuration for Diffvera REST API.
 */
@Configuration
public class OpenApiConfig {

    public static final String COOKIE_AUTH_SCHEME = "cookieAuth";

    @Bean
    public OpenAPI diffveraOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Diffvera API")
                .version("v1"))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Local Development Server")
            ))
            .tags(List.of(
                new Tag().name("Authentication").description("GitHub OAuth 2.0 authentication and session management"),
                new Tag().name("Onboarding").description("GitHub App onboarding and installation status"),
                new Tag().name("Repositories").description("Connected repository management and synchronization"),
                new Tag().name("Webhooks").description("GitHub App webhook receiver and event processing")
            ))
            .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH_SCHEME))
            .schemaRequirement(COOKIE_AUTH_SCHEME, new SecurityScheme()
                .name("diffvera_session")
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .description("Signed HTTP-Only JWT Session Cookie"));
    }
}
