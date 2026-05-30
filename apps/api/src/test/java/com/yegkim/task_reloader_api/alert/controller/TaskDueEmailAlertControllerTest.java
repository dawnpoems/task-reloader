package com.yegkim.task_reloader_api.alert.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yegkim.task_reloader_api.alert.dto.AddTaskDueEmailAlertRecipientRequest;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertRecipientResponse;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertSettingsResponse;
import com.yegkim.task_reloader_api.alert.dto.UpdateTaskDueEmailAlertSettingsRequest;
import com.yegkim.task_reloader_api.alert.exception.TaskDueEmailAlertException;
import com.yegkim.task_reloader_api.alert.service.TaskDueEmailAlertService;
import com.yegkim.task_reloader_api.auth.jwt.JwtTokenProvider;
import com.yegkim.task_reloader_api.auth.security.AuthRateLimitGuard;
import com.yegkim.task_reloader_api.auth.security.SecurityErrorResponseWriter;
import com.yegkim.task_reloader_api.common.web.RequestIdLoggingFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskDueEmailAlertController.class)
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class
})
@Import(RequestIdLoggingFilter.class)
@DisplayName("TaskDueEmailAlertController 단위테스트")
class TaskDueEmailAlertControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return mapper;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TaskDueEmailAlertService taskDueEmailAlertService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private SecurityErrorResponseWriter securityErrorResponseWriter;

    @MockitoBean
    private AuthRateLimitGuard authRateLimitGuard;

    @Test
    @DisplayName("설정 조회 - 로그인 이메일 추천값을 포함한다")
    void getSettings() throws Exception {
        when(taskDueEmailAlertService.getSettings()).thenReturn(new TaskDueEmailAlertSettingsResponse(
                false,
                LocalTime.of(9, 0),
                "Asia/Seoul",
                null,
                "user@example.com",
                5
        ));

        mockMvc.perform(get("/api/alerts/task-due-email/settings")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.enabled", is(false)))
                .andExpect(jsonPath("$.data.sendTime", is("09:00:00")))
                .andExpect(jsonPath("$.data.timezone", is("Asia/Seoul")))
                .andExpect(jsonPath("$.data.suggestedEmail", is("user@example.com")))
                .andExpect(jsonPath("$.data.maxRecipientCount", is(5)));
    }

    @Test
    @DisplayName("설정 수정 - 요청 본문을 서비스에 전달한다")
    void updateSettings() throws Exception {
        UpdateTaskDueEmailAlertSettingsRequest request =
                new UpdateTaskDueEmailAlertSettingsRequest(true, LocalTime.of(8, 30), "Asia/Seoul");
        when(taskDueEmailAlertService.updateSettings(any(UpdateTaskDueEmailAlertSettingsRequest.class)))
                .thenReturn(new TaskDueEmailAlertSettingsResponse(
                        true,
                        LocalTime.of(8, 30),
                        "Asia/Seoul",
                        LocalDate.of(2026, 5, 23),
                        "user@example.com",
                        5
                ));

        mockMvc.perform(patch("/api/alerts/task-due-email/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.enabled", is(true)))
                .andExpect(jsonPath("$.data.sendTime", is("08:30:00")))
                .andExpect(jsonPath("$.data.lastSentLocalDate", is("2026-05-23")));
    }

    @Test
    @DisplayName("수신 이메일 목록 조회")
    void findRecipients() throws Exception {
        when(taskDueEmailAlertService.findRecipients()).thenReturn(List.of(
                new TaskDueEmailAlertRecipientResponse(
                        10L,
                        "owner@example.com",
                        OffsetDateTime.parse("2026-05-24T09:00:00+09:00")
                )
        ));

        mockMvc.perform(get("/api/alerts/task-due-email/recipients")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(10)))
                .andExpect(jsonPath("$.data[0].email", is("owner@example.com")));
    }

    @Test
    @DisplayName("수신 이메일 추가 - 생성 응답")
    void addRecipient() throws Exception {
        AddTaskDueEmailAlertRecipientRequest request =
                new AddTaskDueEmailAlertRecipientRequest("owner@example.com");
        when(taskDueEmailAlertService.addRecipient(any(AddTaskDueEmailAlertRecipientRequest.class)))
                .thenReturn(new TaskDueEmailAlertRecipientResponse(
                        10L,
                        "owner@example.com",
                        OffsetDateTime.parse("2026-05-24T09:00:00+09:00")
                ));

        mockMvc.perform(post("/api/alerts/task-due-email/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(10)))
                .andExpect(jsonPath("$.data.email", is("owner@example.com")));
    }

    @Test
    @DisplayName("수신 이메일 추가 - 형식 오류면 400")
    void addRecipientInvalidEmail() throws Exception {
        AddTaskDueEmailAlertRecipientRequest request =
                new AddTaskDueEmailAlertRecipientRequest("not-email");

        mockMvc.perform(post("/api/alerts/task-due-email/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("수신 이메일 추가 - 서비스 예외를 표준 에러로 응답")
    void addRecipientLimitExceeded() throws Exception {
        AddTaskDueEmailAlertRecipientRequest request =
                new AddTaskDueEmailAlertRecipientRequest("owner@example.com");
        when(taskDueEmailAlertService.addRecipient(any(AddTaskDueEmailAlertRecipientRequest.class)))
                .thenThrow(new TaskDueEmailAlertException(
                        HttpStatus.CONFLICT,
                        "TASK_DUE_EMAIL_ALERT_RECIPIENT_LIMIT_EXCEEDED",
                        "수신 이메일은 최대 5개까지 등록할 수 있습니다."
                ));

        mockMvc.perform(post("/api/alerts/task-due-email/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("TASK_DUE_EMAIL_ALERT_RECIPIENT_LIMIT_EXCEEDED")));
    }

    @Test
    @DisplayName("수신 이메일 삭제 - 성공")
    void deleteRecipient() throws Exception {
        mockMvc.perform(delete("/api/alerts/task-due-email/recipients/10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(taskDueEmailAlertService).deleteRecipient(eq(10L));
    }
}
