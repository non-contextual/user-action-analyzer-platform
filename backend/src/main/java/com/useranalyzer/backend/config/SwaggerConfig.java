package com.useranalyzer.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("User Action Analytics API")
                        .version("1.0")
                        .description("Spring Boot REST API for ECharts Visualization")
                        .contact(new Contact()
                                .name("User Action Analyzer Team")
                                .email("support@example.com")))
                .externalDocs(new io.swagger.v3.oas.models.ExternalDocumentation()
                        .description("Spring Boot Wiki")
                        .url("https://spring.io/projects/spring-boot"));
    }
}
