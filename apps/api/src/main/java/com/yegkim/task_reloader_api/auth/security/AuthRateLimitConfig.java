package com.yegkim.task_reloader_api.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record AuthRateLimitConfig(
        @Value("${auth.rate-limit.enabled:true}") boolean enabled,

        @Value("${auth.rate-limit.login.ip.limit:30}") int loginIpLimit,
        @Value("${auth.rate-limit.login.ip.window-seconds:60}") long loginIpWindowSeconds,
        @Value("${auth.rate-limit.login.ip-email.limit:5}") int loginIpEmailLimit,
        @Value("${auth.rate-limit.login.ip-email.window-seconds:300}") long loginIpEmailWindowSeconds,

        @Value("${auth.rate-limit.signup.ip.limit:10}") int signupIpLimit,
        @Value("${auth.rate-limit.signup.ip.window-seconds:300}") long signupIpWindowSeconds,
        @Value("${auth.rate-limit.signup.ip-email.limit:3}") int signupIpEmailLimit,
        @Value("${auth.rate-limit.signup.ip-email.window-seconds:600}") long signupIpEmailWindowSeconds,

        @Value("${auth.rate-limit.refresh.ip.limit:60}") int refreshIpLimit,
        @Value("${auth.rate-limit.refresh.ip.window-seconds:60}") long refreshIpWindowSeconds
) {
}
