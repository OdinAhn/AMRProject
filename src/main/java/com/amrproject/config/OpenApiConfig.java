package com.amrproject.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI amrProjectOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AMR/AGV 관제 시스템 API")
                        .description("물류센터 AMR/AGV 관제 시스템 백엔드 API")
                        .version("v0"));
    }
}
