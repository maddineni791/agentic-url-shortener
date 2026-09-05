package com.assessment.agentic.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assessment.agentic.AgenticSdlcPlatformApplication;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the asynchronous submission path by toggling {@link OrchestrationProperties} at runtime,
 * so it reuses the shared application context rather than forcing a distinct one.
 */
@SpringBootTest(classes = AgenticSdlcPlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AsyncWorkflowSubmissionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrchestrationProperties orchestrationProperties;

    @AfterEach
    void resetAsync() {
        orchestrationProperties.setAsync(false);
    }

    @Test
    void submissionReturnsBeforeOrchestrationCompletesAndThenReachesTheChangeGate() throws Exception {
        orchestrationProperties.setAsync(true);

        String json = mockMvc.perform(post("/api/workflows")
                .with(httpBasic("operator", "operator-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "scenarioKey": "brownfield-analytics",
                      "requirement": "Add URL creation API and redirect endpoint with PostgreSQL storage, rate limiting, blocked host validation, expiry, retention cleanup, and UTC daily analytics."
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(org.hamcrest.Matchers.oneOf("SUBMITTED", "RUNNING", "AWAITING_CHANGE_APPROVAL")))
            .andReturn().getResponse().getContentAsString();
        String workflowId = JsonPath.read(json, "$.id");

        String status = awaitStatus(workflowId, Duration.ofSeconds(60), "AWAITING_CHANGE_APPROVAL", "FAILED");
        assertThat(status).isEqualTo("AWAITING_CHANGE_APPROVAL");
    }

    private String awaitStatus(String workflowId, Duration timeout, String... terminalStates) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        String last = "UNKNOWN";
        while (Instant.now().isBefore(deadline)) {
            String body = mockMvc.perform(get("/api/workflows/" + workflowId).with(httpBasic("operator", "operator-pass")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            last = JsonPath.read(body, "$.status");
            for (String terminal : terminalStates) {
                if (terminal.equals(last)) {
                    return last;
                }
            }
            Thread.sleep(300);
        }
        return last;
    }
}
