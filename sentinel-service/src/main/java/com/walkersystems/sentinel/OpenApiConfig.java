package com.walkersystems.sentinel;

import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sentinelOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sentinel Rate Limiter API")
                        .description("Uses Redis/Lua for atomic implementation of Token Bucket to control request volume.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Justin Walker")
                                .email("justinwalker.contact@gmail.com")
                                .url("https://github.com/walker-systems/sentinel-service"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
