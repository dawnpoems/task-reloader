package com.yegkim.task_reloader_api.auth.security;

import com.yegkim.task_reloader_api.auth.entity.UserRole;

public record AuthenticatedUser(Long userId, UserRole role) {
}
