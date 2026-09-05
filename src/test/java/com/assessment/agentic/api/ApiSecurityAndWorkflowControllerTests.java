package com.assessment.agentic.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assessment.agentic.AgenticSdlcPlatformApplication;
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
class ApiSecurityAndWorkflowControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesScenarioCatalogWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/scenarios").header(CorrelationIdFilter.HEADER, "corr-scenarios"))
            .andExpect(status().isOk())
            .andExpect(header().string(CorrelationIdFilter.HEADER, "corr-scenarios"))
            .andExpect(jsonPath("$.items", hasSize(4)))
            .andExpect(jsonPath("$.items[0].key").value("greenfield-url-shortener"));
    }

    @Test
    void submitsWorkflowForOperatorAndPersistsRevision() throws Exception {
        mockMvc.perform(post("/api/workflows")
                .with(httpBasic("operator", "operator-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "scenarioKey": "greenfield-url-shortener",
                      "requirement": "Build a URL shortener API with redirect endpoint, PostgreSQL persistence, rate limiting, blocked host validation, expiry, retention, and UTC analytics."
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.scenarioKey").value("greenfield-url-shortener"))
            .andExpect(jsonPath("$.status").value("AWAITING_RELEASE_APPROVAL"))
            .andExpect(jsonPath("$.currentRevision").value(1));
    }

    @Test
    void submissionRunsAgentsAndExposesGeneratedEvidence() throws Exception {
        String workflowJson = mockMvc.perform(post("/api/workflows")
                .with(httpBasic("operator", "operator-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "scenarioKey": "brownfield-analytics",
                      "requirement": "Add URL creation API and redirect endpoint with PostgreSQL storage, rate limiting, blocked host validation, expiry, retention cleanup, and UTC daily analytics."
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("AWAITING_RELEASE_APPROVAL"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String workflowId = workflowJson.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        mockMvc.perform(get("/api/workflows/" + workflowId + "/tasks")
                .with(httpBasic("operator", "operator-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(12))
            .andExpect(jsonPath("$.items[0].status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.items[?(@.taskKey=='implement-change')].taskType").value("IMPLEMENTER"));

        mockMvc.perform(get("/api/workflows/" + workflowId + "/artifacts")
                .with(httpBasic("operator", "operator-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.name=='normalized-requirement.json')].sha256").exists())
            .andExpect(jsonPath("$.items[?(@.name=='implementation-proposal.json')].sha256").exists())
            .andExpect(jsonPath("$.items[?(@.name=='patch-policy.json')].sha256").exists())
            .andExpect(jsonPath("$.items[?(@.name=='unified-diff.patch')].sha256").exists())
            .andExpect(jsonPath("$.items[?(@.name=='validation-attempt-1.json')].sha256").exists())
            .andExpect(jsonPath("$.items[?(@.name=='release-readiness.json')].sha256").exists());

        mockMvc.perform(get("/api/workflows/" + workflowId + "/artifacts/implementation-proposal.json")
                .with(httpBasic("operator", "operator-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("fileOperations")));

        mockMvc.perform(get("/api/workflows/" + workflowId + "/artifacts/unified-diff.patch")
                .with(httpBasic("operator", "operator-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("GeneratedUrlShortenerSlice")));

        mockMvc.perform(get("/api/workflows/" + workflowId + "/audit-events")
                .with(httpBasic("operator", "operator-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.eventType=='task.completed')]").isNotEmpty())
            .andExpect(jsonPath("$.items[?(@.eventType=='patch.applied')]").isNotEmpty())
            .andExpect(jsonPath("$.items[?(@.eventType=='validation.passed')]").isNotEmpty())
            .andExpect(jsonPath("$.items[?(@.eventType=='workflow.awaiting-release-approval')]").isNotEmpty());

        mockMvc.perform(get("/api/workflows/" + workflowId + "/validation-attempts")
                .with(httpBasic("operator", "operator-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].commandName").value("maven-wrapper-clean-test"))
            .andExpect(jsonPath("$.items[0].exitCode").value(0))
            .andExpect(jsonPath("$.items[0].failureClassification").value("NONE"));
    }

    @Test
    void repairScenarioFailsValidationThenAppliesRepairAndRevalidates() throws Exception {
        String workflowJson = mockMvc.perform(post("/api/workflows")
                .with(httpBasic("operator", "operator-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "scenarioKey": "repair-demonstration",
                      "requirement": "repair scenario: Add URL creation API and redirect endpoint with PostgreSQL storage, rate limiting, blocked host validation, expiry, retention cleanup, and UTC daily analytics."
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("AWAITING_RELEASE_APPROVAL"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String workflowId = workflowJson.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        mockMvc.perform(get("/api/workflows/" + workflowId + "/validation-attempts")
                .with(httpBasic("operator", "operator-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items[0].failureClassification").value("COMPILER"))
            .andExpect(jsonPath("$.items[1].exitCode").value(0));

        mockMvc.perform(get("/api/workflows/" + workflowId + "/artifacts")
                .with(httpBasic("operator", "operator-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.name=='repair-proposal.json')].sha256").exists())
            .andExpect(jsonPath("$.items[?(@.name=='repair-diff.patch')].sha256").exists())
            .andExpect(jsonPath("$.items[?(@.name=='validation-attempt-2.json')].sha256").exists());
    }

    @Test
    void ambiguousSubmissionPausesForClarificationAndSkipsImplementation() throws Exception {
        String workflowJson = mockMvc.perform(post("/api/workflows")
                .with(httpBasic("operator", "operator-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "scenarioKey": "ambiguous-requirement",
                      "requirement": "Make links better."
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("AWAITING_CLARIFICATION"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String workflowId = workflowJson.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        mockMvc.perform(get("/api/workflows/" + workflowId + "/tasks")
                .with(httpBasic("operator", "operator-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[?(@.taskKey=='analyze-ambiguity')].status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/workflows/" + workflowId + "/artifacts")
                .with(httpBasic("operator", "operator-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[?(@.name=='ambiguity-decision.json')].sha256").exists());
    }

    @Test
    void rejectsWorkflowSubmissionWithoutOperatorRole() throws Exception {
        mockMvc.perform(post("/api/workflows")
                .with(httpBasic("change-approver", "change-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "scenarioKey": "greenfield-url-shortener",
                      "requirement": "Build a URL shortener."
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void returnsProblemDetailsForValidationFailure() throws Exception {
        mockMvc.perform(post("/api/workflows")
                .with(httpBasic("operator", "operator-pass"))
                .header(CorrelationIdFilter.HEADER, "corr-validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "scenarioKey": "",
                      "requirement": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("https://agentic-url-shortener.local/problems/validation-failed"))
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.correlationId").value("corr-validation"));
    }

    @Test
    void enforcesReleaseApproverRoleForReleaseApproval() throws Exception {
        String workflowJson = mockMvc.perform(post("/api/workflows")
                .with(httpBasic("operator", "operator-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "scenarioKey": "brownfield-analytics",
                      "requirement": "Add total redirect analytics."
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String workflowId = workflowJson.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        mockMvc.perform(post("/api/workflows/" + workflowId + "/approvals/release")
                .with(httpBasic("change-approver", "change-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "artifactHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "reason": "looks good"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void exposesOpenApiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.paths['/api/workflows']").exists());
    }
}
