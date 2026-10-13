package com.designloop.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI designLoopOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("DesignLoop API")
                .version("1.0.0")
                .description("LLD practice API for text, document, diagram and combined design submissions. Evaluation is asynchronous; Gemini feedback is only reported when a real model call succeeds.")
                .contact(new Contact().name("DesignLoop Prototype")));
    }
}
