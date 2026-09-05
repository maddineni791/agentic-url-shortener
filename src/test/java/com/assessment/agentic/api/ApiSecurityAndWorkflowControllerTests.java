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
                      "requirement": "Build a URL shortener with redirect analytics."
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.scenarioKey").value("greenfield-url-shortener"))
            .andExpect(jsonPath("$.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.currentRevision").value(1));
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
