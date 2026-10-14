package com.namastute;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// Writes the API's OpenAPI document to apps/web/openapi.json (Learning Notes 52). The web generates its TypeScript
// types from that file, and CI fails if the committed file differs from what this test writes (a drift check).
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class OpenApiExportTest {

    @Autowired MockMvc mvc;

    @Test
    void writesTheApiContractForTheWebApp() throws Exception {
        String spec = mvc.perform(get("/v3/api-docs").with(jwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // Maven runs tests from apps/api, so the web app is next door
        Files.writeString(Path.of("../web/openapi.json"), spec.replace("\r\n", "\n") + "\n", StandardCharsets.UTF_8);
    }
}
