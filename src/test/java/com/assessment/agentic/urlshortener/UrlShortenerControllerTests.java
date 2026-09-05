package com.assessment.agentic.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assessment.agentic.AgenticSdlcPlatformApplication;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AgenticSdlcPlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UrlShortenerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsInspectsRedirectsAndIncrementsAnalytics() throws Exception {
        String code = create("https://example.com/a?q=1");

        mockMvc.perform(get("/api/urls/" + code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.originalUrl").value("https://example.com/a?q=1"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.redirectCount").value(0));

        mockMvc.perform(get("/r/" + code))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/a?q=1"));

        mockMvc.perform(get("/api/urls/" + code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redirectCount").value(1));
    }

    @Test
    void deactivatesShortUrlAndRejectsFurtherRedirects() throws Exception {
        String code = create("https://example.org/resource");

        mockMvc.perform(patch("/api/urls/" + code + "/deactivate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/r/" + code))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("SHORT_URL_DEACTIVATED"));
    }

    @Test
    void rejectsExpiredUrlsAtCreationAndRedirectTime() throws Exception {
        mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "url": "https://example.com",
                      "expiresAt": "2020-01-01T00:00:00Z"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String code = create("https://example.com/soon", Instant.now().plusSeconds(1));
        Thread.sleep(1_200);
        mockMvc.perform(get("/r/" + code))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("SHORT_URL_EXPIRED"));
    }

    @Test
    void returnsProblemDetailsForUnknownCode() throws Exception {
        mockMvc.perform(get("/api/urls/no-such-code"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("https://agentic-url-shortener.local/problems/url-shortener"))
            .andExpect(jsonPath("$.title").value("URL shortener request rejected"))
            .andExpect(jsonPath("$.code").value("SHORT_CODE_NOT_FOUND"));
    }

    @Test
    void rejectsMalformedUnsupportedAndUserInfoUrls() throws Exception {
        assertRejectedUrl("notaurl", "UNSUPPORTED_URL_SCHEME");
        assertRejectedUrl("ftp://example.com/file", "UNSUPPORTED_URL_SCHEME");
        assertRejectedUrl("https://user@example.com/secret", "URL_USERINFO_REJECTED");
    }

    private String create(String url) throws Exception {
        return create(url, null);
    }

    private String create(String url, Instant expiresAt) throws Exception {
        String expiresAtJson = expiresAt == null ? "null" : "\"" + expiresAt + "\"";
        String body = """
            {
              "url": "%s",
              "expiresAt": %s
            }
            """.formatted(url, expiresAtJson);
        String response = mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.shortCode").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String code = response.replaceAll(".*\\\"shortCode\\\":\\\"([^\\\"]+)\\\".*", "$1");
        assertThat(code).hasSize(8);
        return code;
    }

    private void assertRejectedUrl(String url, String code) throws Exception {
        mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "url": "%s"
                    }
                    """.formatted(url)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("https://agentic-url-shortener.local/problems/url-shortener"))
            .andExpect(jsonPath("$.code").value(code));
    }
}
