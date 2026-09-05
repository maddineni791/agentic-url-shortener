package com.assessment.agentic.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "agentic.model")
public class ModelProperties {

    @NotNull
    private ModelProviderType provider = ModelProviderType.DETERMINISTIC;

    @NotNull
    private Duration timeout = Duration.ofSeconds(20);

    @Min(1000)
    private int maxInputChars = 20_000;

    @Min(1000)
    private int maxOutputChars = 20_000;

    @Valid
    private final OpenAi openai = new OpenAi();

    public ModelProviderType getProvider() {
        return provider;
    }

    public void setProvider(ModelProviderType provider) {
        this.provider = provider;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxInputChars() {
        return maxInputChars;
    }

    public void setMaxInputChars(int maxInputChars) {
        this.maxInputChars = maxInputChars;
    }

    public int getMaxOutputChars() {
        return maxOutputChars;
    }

    public void setMaxOutputChars(int maxOutputChars) {
        this.maxOutputChars = maxOutputChars;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public static class OpenAi {
        private String baseUrl = "https://api.openai.com";
        private String apiKey = "";
        private String model = "gpt-5.6-luna";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
