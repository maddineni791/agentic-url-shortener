package com.assessment.agentic.urlshortener;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UrlShortenerWebConfiguration implements WebMvcConfigurer {

    private final UrlShortenerRateLimitInterceptor interceptor;

    public UrlShortenerWebConfiguration(UrlShortenerRateLimitInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/urls");
    }
}
