package com.story.game.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI storyGameOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Story Game API")
                        .description("인터랙티브 스토리 게임 플레이어 API 문서")
                        .version("1.0")
                        .contact(new Contact()
                                .name("Story Game Team")
                                .url("https://github.com/skRookies2team")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("개발 서버")
                ));
    }
}
