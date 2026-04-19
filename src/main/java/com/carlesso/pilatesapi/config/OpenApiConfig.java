package com.carlesso.pilatesapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Carlesso Pilates API")
                        .description("API para gestão de pacientes do estúdio Carlesso Pilates")
                        .version("v1.0")
                        .contact(new Contact()
                                .name("Carlesso Pilates")
                                .email("contato@carlessopilates.com.br")));
    }
}
