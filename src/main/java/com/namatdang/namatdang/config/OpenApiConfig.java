package com.namatdang.namatdang.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI namatdangOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("남았당 API")
                        .description("동네 베이커리·디저트 매장의 마감 할인 예약 서비스 API")
                        .version("v1"));
    }
}
