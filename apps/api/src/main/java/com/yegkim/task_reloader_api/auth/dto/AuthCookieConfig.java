package com.yegkim.task_reloader_api.auth.dto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record AuthCookieConfig(
        @Value("${auth.refresh-cookie.name:refresh_token}") String name,
        @Value("${auth.refresh-cookie.path:/api/auth}") String path,
        @Value("${auth.refresh-cookie.secure:true}") boolean secure,
        @Value("${auth.refresh-cookie.same-site:Lax}") String sameSite
) {
}
