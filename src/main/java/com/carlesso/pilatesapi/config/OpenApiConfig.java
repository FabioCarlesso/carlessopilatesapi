package com.carlesso.pilatesapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .info(new Info()
                        .title("Carlesso Pilates API")
                        .description("""
                                API para gestão de pacientes do estúdio Carlesso Pilates.

                                Para testar endpoints protegidos no Swagger UI:
                                1. Execute POST /auth/login com um dos usuários abaixo.
                                2. Copie o campo accessToken retornado.
                                3. Clique em Authorize e cole somente o token, sem o prefixo Bearer.

                                Usuários iniciais para teste:
                                - admin@carlessopilates.com / senha1234 / ADMIN
                                - operacional@carlessopilates.com / senha1234 / ADMIN
                                - recepcao@carlessopilates.com / senha1234 / USER
                                - financeiro@carlessopilates.com / senha1234 / USER
                                - consulta@carlessopilates.com / senha1234 / USER
                                """)
                        .version("v1.0")
                        .contact(new Contact()
                                .name("Carlesso Pilates")
                                .email("contato@carlessopilates.com.br")));
    }
}
