package com.amrproject.config;

import com.amrproject.controller.event.AuditLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 37단계: 상태를 바꾸는 API 호출을 감사 로그로 남기는 인터셉터 등록. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuditLogInterceptor auditLogInterceptor;

    public WebMvcConfig(AuditLogInterceptor auditLogInterceptor) {
        this.auditLogInterceptor = auditLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditLogInterceptor).addPathPatterns("/api/**");
    }
}
