package com.yegkim.task_reloader_api.auth.security;

import com.yegkim.task_reloader_api.auth.exception.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserProvider {

    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 정보가 없습니다.");
        }
        return user.userId();
    }
}
