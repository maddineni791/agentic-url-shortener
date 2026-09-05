package com.assessment.agentic.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assessment.agentic.AgenticSdlcPlatformApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = AgenticSdlcPlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformInfoControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsPlatformInfo() throws Exception {
        mockMvc.perform(get("/api/platform"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("agentic-sdlc-platform"))
            .andExpect(jsonPath("$.checkpoint").value("checkpoint-1"));
    }
}
