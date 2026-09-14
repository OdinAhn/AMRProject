package com.amrproject.controller.event;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.RecordComponent;

/**
 * 37단계: 생성 API(POST)는 경로에 대상 id가 없어, 응답 DTO(record)의 {@code id}를 요청 속성에 담아 감사 로그가 "무엇을 만들었는지"
 * 남길 수 있게 한다. 응답 본문은 바꾸지 않는다.
 */
@RestControllerAdvice
public class CreatedIdCapturingAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (body instanceof Record record && request.getMethod() == HttpMethod.POST && request instanceof ServletServerHttpRequest servletRequest) {
            for (RecordComponent component : record.getClass().getRecordComponents()) {
                if (component.getName().equals("id")) {
                    try {
                        servletRequest.getServletRequest().setAttribute(AuditLogInterceptor.CREATED_ID_ATTRIBUTE, component.getAccessor().invoke(record));
                    } catch (ReflectiveOperationException ignored) {
                        // 감사 로그 부가 정보일 뿐이라 id를 못 읽으면 남기지 않는다
                    }
                    break;
                }
            }
        }
        return body;
    }
}
