package com.namatdang.namatdang.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocsAreAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("남았당 API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/stores'].get.summary")
                        .value("매장 목록 조회 및 검색"))
                .andExpect(jsonPath("$.paths['/api/v1/owner/stores'].post.summary")
                        .value("매장 등록"))
                .andExpect(jsonPath("$.paths['/api/v1/favorites'].get.summary")
                        .value("즐겨찾기 매장 목록 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/favorites/{storeId}'].put.summary")
                        .value("매장 즐겨찾기 등록"))
                .andExpect(jsonPath("$.components.schemas.UserUpdateRequestDto.properties.empty")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.StoreUpdateRequestDto.properties.empty")
                        .doesNotExist());
    }

    @Test
    void swaggerUiIsAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
