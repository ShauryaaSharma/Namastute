package com.namastute;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// The production containers wait on this check (docker-compose.prod.yml): it must say UP even with no mail password
// set, as on the staging server (Learning Notes 90). The mail library would otherwise add a check of the mail server.
@SpringBootTest
@AutoConfigureMockMvc
class HealthTest {

    @Autowired MockMvc mvc;

    @Test
    void healthIsUpWithoutAMailServer() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
