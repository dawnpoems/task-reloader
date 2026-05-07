package com.yegkim.task_reloader_api.auth.dto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record AuthCsrfConfig(
        @Value("${auth.csrf.cookie-name:csrf_token}") String cookieName,
        @Value("${auth.csrf.cookie-path:/}") String cookiePath,
        @Value("${auth.csrf.header-name:X-CSRF-Token}") String headerName,
        @Value("${auth.csrf.secure:true}") boolean secure,
        @Value("${auth.csrf.same-site:Lax}") String sameSite,
        @Value("${auth.csrf.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOrigins
) {
}
