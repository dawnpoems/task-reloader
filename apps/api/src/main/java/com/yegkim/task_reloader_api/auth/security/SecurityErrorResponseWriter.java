package com.yegkim.task_reloader_api.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yegkim.task_reloader_api.common.response.ApiResponse;
import com.yegkim.task_reloader_api.common.response.ErrorResponse;
import com.yegkim.task_reloader_api.common.web.RequestIdLoggingFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String requestId = MDC.get(RequestIdLoggingFilter.REQUEST_ID_MDC_KEY);
        ApiResponse<Void> body = ApiResponse.error(ErrorResponse.of(code, message, requestId));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
