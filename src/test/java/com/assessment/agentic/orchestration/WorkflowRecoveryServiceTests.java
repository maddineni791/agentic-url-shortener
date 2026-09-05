package com.assessment.agentic.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assessment.agentic.AgenticSdlcPlatformApplication;
import com.assessment.agentic.persistence.WorkflowStateStore;
import com.assessment.agentic.persistence.WorkflowStatus;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AgenticSdlcPlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowRecoveryServiceTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkflowStateStore store;

    @Autowired
    private WorkflowRecoveryService recoveryService;

    @Test
    void recoveryRePausesInterruptedWorkflowAtChangeGate() throws Exception {
        UUID workflowId = submitBrownfield();

        // Simulate an instance that stopped right after the design phase, before the pause was persisted.
        store.updateWorkflowStatus(workflowId, WorkflowStatus.RUNNING);

        int recovered = recoveryService.recoverInterruptedRevisions();
        assertThat(recovered).isGreaterThanOrEqualTo(1);

        mockMvc.perform(get("/api/workflows/" + workflowId).with(httpBasic("operator", "operator-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("AWAITING_CHANGE_APPROVAL"));

        String audit = mockMvc.perform(get("/api/workflows/" + workflowId + "/audit-events")
                .with(httpBasic("operator", "operator-pass")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(audit).contains("workflow.recovery-resumed");
    }

    @Test
    void recoveryResumesInterruptedWorkflowToReleaseGate() throws Exception {
        UUID workflowId = submitBrownfield();
        approveChangeGate(workflowId.toString());

        // Interrupted after the outcome artifact was written but before the release-approval pause was recorded.
        store.updateWorkflowStatus(workflowId, WorkflowStatus.RUNNING);

        recoveryService.recoverInterruptedRevisions();

        mockMvc.perform(get("/api/workflows/" + workflowId).with(httpBasic("operator", "operator-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("AWAITING_RELEASE_APPROVAL"));
    }

    private UUID submitBrownfield() throws Exception {
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
            .andExpect(jsonPath("$.status").value("AWAITING_CHANGE_APPROVAL"))
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private void approveChangeGate(String workflowId) throws Exception {
        String planJson = mockMvc.perform(get("/api/workflows/" + workflowId + "/artifacts/engineering-plan.json")
                .with(httpBasic("change-approver", "change-pass")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String planHash = JsonPath.read(planJson, "$.sha256");
        mockMvc.perform(post("/api/workflows/" + workflowId + "/approvals/change")
                .with(httpBasic("change-approver", "change-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"artifactHash\":\"" + planHash + "\",\"reason\":\"reviewed\"}"))
            .andExpect(status().isAccepted());
    }
}
