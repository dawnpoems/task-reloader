package com.yegkim.task_reloader_api.task.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yegkim.task_reloader_api.common.exception.TaskInactiveException;
import com.yegkim.task_reloader_api.common.exception.TaskNotFoundException;
import com.yegkim.task_reloader_api.common.exception.TaskRecentlyCompletedException;
import com.yegkim.task_reloader_api.common.web.RequestIdLoggingFilter;
import com.yegkim.task_reloader_api.task.dto.CreateTaskRequest;
import com.yegkim.task_reloader_api.task.dto.DashboardSummaryResponse;
import com.yegkim.task_reloader_api.task.dto.InsightsOverviewResponse;
import com.yegkim.task_reloader_api.task.dto.RecentTaskCompletionResponse;
import com.yegkim.task_reloader_api.task.dto.TaskCompletionResponse;
import com.yegkim.task_reloader_api.task.dto.TaskResponse;
import com.yegkim.task_reloader_api.task.dto.TaskTrendInsightResponse;
import com.yegkim.task_reloader_api.task.dto.UpdateTaskRequest;
import com.yegkim.task_reloader_api.task.entity.TaskStatus;
import com.yegkim.task_reloader_api.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@WebMvcTest({TaskController.class, TaskInsightsController.class})
@ImportAutoConfiguration({
	JacksonAutoConfiguration.class,
	HttpMessageConvertersAutoConfiguration.class
})
@Import(RequestIdLoggingFilter.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("TaskController 단위테스트")
class TaskControllerTest {

	@TestConfiguration
	static class TestConfig {
		@Bean
		public ObjectMapper objectMapper() {
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
    private TaskService taskService;

    private TaskResponse taskResponse;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();
        taskResponse = TaskResponse.builder()
                .id(1L)
                .name("Test Task")
                .everyNDays(7)
                .timezone("Asia/Seoul")
                .status(TaskStatus.UPCOMING)
                .nextDueAt(now.plusDays(7))
                .lastCompletedAt(now.minusDays(1))
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    @DisplayName("전체 작업 목록 조회 - 성공")
    void testFindAllSuccess() throws Exception {
        // given
        List<TaskResponse> responses = List.of(taskResponse);
        when(taskService.findAll()).thenReturn(responses);

        // when & then
        mockMvc.perform(get("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(1)))
                .andExpect(jsonPath("$.data[0].name", is("Test Task")))
                .andExpect(jsonPath("$.data[0].everyNDays", is(7)))
                .andExpect(jsonPath("$.data[0].status", is("UPCOMING")))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(taskService, times(1)).findAll();
    }

    @Test
    @DisplayName("Request ID 헤더 미전달 시 응답 헤더에 자동 생성")
    void testRequestIdGeneratedWhenMissing() throws Exception {
        when(taskService.findAll()).thenReturn(List.of(taskResponse));

        mockMvc.perform(get("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdLoggingFilter.REQUEST_ID_HEADER))
                .andExpect(header().string(RequestIdLoggingFilter.REQUEST_ID_HEADER, not(isEmptyString())));
    }

    @Test
    @DisplayName("Request ID 헤더 전달 시 동일 값을 응답 헤더와 access log에 사용")
    void testRequestIdPropagatedAndLogged(CapturedOutput output) throws Exception {
        when(taskService.findAll()).thenReturn(List.of(taskResponse));
        String requestId = "test-req-123";

        mockMvc.perform(get("/api/tasks")
                        .header(RequestIdLoggingFilter.REQUEST_ID_HEADER, requestId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdLoggingFilter.REQUEST_ID_HEADER, requestId));

        String logs = output.getOut();
        org.assertj.core.api.Assertions.assertThat(logs).contains("access method=GET uri=/api/tasks status=200");
        org.assertj.core.api.Assertions.assertThat(logs).contains("requestId=" + requestId);
    }

    @Test
    @DisplayName("전체 작업 목록 조회 - status=ALL이면 findAll() 호출")
    void testFindAllWithStatusAll() throws Exception {
        // given
        when(taskService.findAll()).thenReturn(List.of(taskResponse));

        // when & then
        mockMvc.perform(get("/api/tasks").param("status", "ALL")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        verify(taskService, times(1)).findAll();
        verify(taskService, never()).findAll(any(TaskStatus.class));
    }

    @Test
    @DisplayName("전체 작업 목록 조회 - status=TODAY이면 findAll(TODAY) 호출")
    void testFindAllWithStatusToday() throws Exception {
        // given
        when(taskService.findAll(TaskStatus.TODAY)).thenReturn(List.of(taskResponse));

        // when & then
        mockMvc.perform(get("/api/tasks").param("status", "TODAY")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)));

        verify(taskService, times(1)).findAll(TaskStatus.TODAY);
        verify(taskService, never()).findAll();
    }

    @Test
    @DisplayName("전체 작업 목록 조회 - status=OVERDUE이면 findAll(OVERDUE) 호출")
    void testFindAllWithStatusOverdue() throws Exception {
        // given
        when(taskService.findAll(TaskStatus.OVERDUE)).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/tasks").param("status", "OVERDUE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(0)));

        verify(taskService, times(1)).findAll(TaskStatus.OVERDUE);
    }

    @Test
    @DisplayName("전체 작업 목록 조회 - status=DUE_NOW이면 findDueNow() 호출")
    void testFindAllWithStatusDueNow() throws Exception {
        when(taskService.findDueNow()).thenReturn(List.of(taskResponse));

        mockMvc.perform(get("/api/tasks").param("status", "DUE_NOW")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)));

        verify(taskService, times(1)).findDueNow();
        verify(taskService, never()).findAll(any(TaskStatus.class));
    }

    @Test
    @DisplayName("전체 작업 목록 조회 - 잘못된 status 값이면 400")
    void testFindAllWithInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/tasks").param("status", "INVALID")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("BAD_REQUEST")));

        verify(taskService, never()).findAll();
        verify(taskService, never()).findAll(any(TaskStatus.class));
    }

    @Test
    @DisplayName("에러 응답에 requestId가 포함되고 헤더와 연계된다")
    void testErrorResponseContainsRequestId() throws Exception {
        String requestId = "err-req-777";

        mockMvc.perform(get("/api/tasks").param("status", "INVALID")
                        .header(RequestIdLoggingFilter.REQUEST_ID_HEADER, requestId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestIdLoggingFilter.REQUEST_ID_HEADER, requestId))
                .andExpect(jsonPath("$.error.code", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.error.requestId", is(requestId)));
    }

    @Test
    @DisplayName("작업 단건 조회 - 성공")
    void testFindByIdSuccess() throws Exception {
        // given
        when(taskService.findById(1L)).thenReturn(taskResponse);

        // when & then
        mockMvc.perform(get("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(1)))
                .andExpect(jsonPath("$.data.name", is("Test Task")))
                .andExpect(jsonPath("$.data.status", is("UPCOMING")))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(taskService, times(1)).findById(1L);
    }

    @Test
    @DisplayName("작업 완료 이력 조회 - 성공")
    void testFindCompletionsSuccess() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        TaskCompletionResponse response = TaskCompletionResponse.builder()
                .id(10L)
                .completedAt(now.minusDays(1))
                .previousDueAt(now.minusDays(2))
                .nextDueAt(now.plusDays(6))
                .build();

        when(taskService.findCompletions(1L, null, null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/tasks/1/completions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(10)))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(taskService, times(1)).findCompletions(1L, null, null);
    }

    @Test
    @DisplayName("작업 완료 이력 조회 - year/month 파라미터 전달")
    void testFindCompletionsWithYearMonth() throws Exception {
        when(taskService.findCompletions(1L, 2026, 3)).thenReturn(List.of());

        mockMvc.perform(get("/api/tasks/1/completions")
                        .param("year", "2026")
                        .param("month", "3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(0)));

        verify(taskService, times(1)).findCompletions(1L, 2026, 3);
    }

    @Test
    @DisplayName("작업 완료 이력 조회 - year만 전달하면 400")
    void testFindCompletionsWithOnlyYearReturnsBadRequest() throws Exception {
        when(taskService.findCompletions(1L, 2026, null))
                .thenThrow(new IllegalArgumentException("year와 month는 함께 전달해야 합니다."));

        mockMvc.perform(get("/api/tasks/1/completions")
                        .param("year", "2026")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("대시보드 요약 조회 - 성공")
    void testGetDashboardSuccess() throws Exception {
        DashboardSummaryResponse response = DashboardSummaryResponse.builder()
                .totalTasks(12)
                .overdueTasks(2)
                .todayTasks(4)
                .upcomingTasks(6)
                .completedToday(3)
                .completedLast7Days(9)
                .build();

        when(taskService.getDashboardSummary()).thenReturn(response);

        mockMvc.perform(get("/api/insights/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalTasks", is(12)))
                .andExpect(jsonPath("$.data.completedToday", is(3)))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(taskService, times(1)).getDashboardSummary();
    }

    @Test
    @DisplayName("최근 완료 작업 조회 - 성공")
    void testGetRecentCompletionsSuccess() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        RecentTaskCompletionResponse response = RecentTaskCompletionResponse.builder()
                .id(100L)
                .taskId(1L)
                .taskName("Test Task")
                .completedAt(now.minusHours(1))
                .previousDueAt(now.minusDays(1))
                .nextDueAt(now.plusDays(6))
                .build();

        when(taskService.findRecentCompletions()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/insights/recent-completions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].taskId", is(1)))
                .andExpect(jsonPath("$.data[0].taskName", is("Test Task")))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(taskService, times(1)).findRecentCompletions();
    }

    @Test
    @DisplayName("인사이트 overview 조회 - 성공")
    void testGetInsightsOverviewSuccess() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        InsightsOverviewResponse response = InsightsOverviewResponse.builder()
                .periodDays(30)
                .periodStart(now.minusDays(30))
                .periodEnd(now)
                .timezone("Asia/Seoul")
                .activeTaskCount(10)
                .completedTaskCount(8)
                .completionCount(20)
                .delayedCompletionCount(5)
                .completionRatePct(80.0)
                .delayRatePct(25.0)
                .averageDelayMinutes(120.5)
                .riskyTaskCount(2)
                .taskTrends(List.of(
                        TaskTrendInsightResponse.builder()
                                .taskId(1L)
                                .taskName("Test Task")
                                .completionCount(7)
                                .delayedCount(2)
                                .delayRatePct(28.6)
                                .build()
                ))
                .build();

        when(taskService.getInsightsOverview(30, 3)).thenReturn(response);

        mockMvc.perform(get("/api/insights/overview")
                        .param("days", "30")
                        .param("top", "3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.periodDays", is(30)))
                .andExpect(jsonPath("$.data.timezone", is("Asia/Seoul")))
                .andExpect(jsonPath("$.data.completionRatePct", is(80.0)))
                .andExpect(jsonPath("$.data.delayRatePct", is(25.0)))
                .andExpect(jsonPath("$.data.taskTrends", hasSize(1)))
                .andExpect(jsonPath("$.data.taskTrends[0].taskId", is(1)));

        verify(taskService, times(1)).getInsightsOverview(30, 3);
    }

    @Test
    @DisplayName("인사이트 overview 조회 - 유효하지 않은 days면 400")
    void testGetInsightsOverviewInvalidDays() throws Exception {
        when(taskService.getInsightsOverview(0, 5))
                .thenThrow(new IllegalArgumentException("days는 1~365 사이여야 합니다."));

        mockMvc.perform(get("/api/insights/overview")
                        .param("days", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("작업 단건 조회 - 존재하지 않음")
    void testFindByIdNotFound() throws Exception {
        // given
        when(taskService.findById(999L))
                .thenThrow(new IllegalArgumentException("Task not found: id=999"));

        // when & then
        mockMvc.perform(get("/api/tasks/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.error.message", containsString("Task not found")))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(taskService, times(1)).findById(999L);
    }

    @Test
    @DisplayName("작업 생성 - 성공")
    void testCreateSuccess() throws Exception {
        // given
        CreateTaskRequest request = CreateTaskRequest.builder()
                .name("New Task")
                .everyNDays(5)
                .build();

        TaskResponse createdResponse = TaskResponse.builder()
                .id(2L)
                .name("New Task")
                .everyNDays(5)
                .timezone("Asia/Seoul")
                .status(TaskStatus.UPCOMING)
                .nextDueAt(OffsetDateTime.now().plusDays(5))
                .lastCompletedAt(null)
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(taskService.create(any(CreateTaskRequest.class))).thenReturn(createdResponse);

        // when & then
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(2)))
                .andExpect(jsonPath("$.data.name", is("New Task")))
                .andExpect(jsonPath("$.data.everyNDays", is(5)))
                .andExpect(jsonPath("$.data.status", is("UPCOMING")))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(taskService, times(1)).create(any(CreateTaskRequest.class));
    }

    @Test
    @DisplayName("작업 생성 - name이 빈 값이면 400")
    void testCreateFailWhenNameIsBlank() throws Exception {
        // given
        CreateTaskRequest request = CreateTaskRequest.builder()
                .name("")
                .everyNDays(5)
                .build();

        // when & then
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(taskService, never()).create(any());
    }

    @Test
    @DisplayName("작업 생성 - everyNDays가 0이면 400")
    void testCreateFailWhenEveryNDaysIsZero() throws Exception {
        // given
        CreateTaskRequest request = CreateTaskRequest.builder()
                .name("Task")
                .everyNDays(0)
                .build();

        // when & then
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(taskService, never()).create(any());
    }

    @Test
    @DisplayName("작업 생성 - everyNDays가 null이면 400")
    void testCreateFailWhenEveryNDaysIsNull() throws Exception {
        // given
        CreateTaskRequest request = CreateTaskRequest.builder()
                .name("Task")
                .build();

        // when & then
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(taskService, never()).create(any());
    }

    @Test
    @DisplayName("작업 생성 - startDate 포함 시 요청/응답 반영")
    void testCreateSuccessWithStartDate() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        OffsetDateTime initialDueAt = OffsetDateTime.parse("2026-04-01T00:00:00+09:00");

        CreateTaskRequest request = CreateTaskRequest.builder()
                .name("New Task")
                .everyNDays(5)
                .startDate(startDate)
                .build();

        TaskResponse createdResponse = TaskResponse.builder()
                .id(2L)
                .name("New Task")
                .everyNDays(5)
                .timezone("Asia/Seoul")
                .startDate(startDate)
                .status(TaskStatus.TODAY)
                .nextDueAt(initialDueAt)
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(taskService.create(any(CreateTaskRequest.class))).thenReturn(createdResponse);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.startDate", is("2026-04-01")))
                .andExpect(jsonPath("$.data.nextDueAt", org.hamcrest.Matchers.startsWith("2026-04-01T00:00:00+09:00")));

        verify(taskService, times(1)).create(argThat(req -> startDate.equals(req.getStartDate())));
    }

    @Test
    @DisplayName("작업 수정 - 성공")
    void testUpdateSuccess() throws Exception {
        // given
        UpdateTaskRequest request = UpdateTaskRequest.builder()
                .name("Updated Task")
                .everyNDays(3)
                .isActive(false)
                .build();

        TaskResponse updatedResponse = TaskResponse.builder()
                .id(1L)
                .name("Updated Task")
                .everyNDays(3)
                .timezone("Asia/Seoul")
                .nextDueAt(OffsetDateTime.now().plusDays(3))
                .isActive(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(taskService.update(eq(1L), any(UpdateTaskRequest.class))).thenReturn(updatedResponse);

        // when & then
        mockMvc.perform(patch("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(1)))
                .andExpect(jsonPath("$.data.name", is("Updated Task")))
                .andExpect(jsonPath("$.data.everyNDays", is(3)))
                .andExpect(jsonPath("$.data.isActive", is(false)))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(taskService, times(1)).update(eq(1L), any(UpdateTaskRequest.class));
    }

    @Test
    @DisplayName("작업 수정 - everyNDays가 0이면 400")
    void testUpdateFailWhenEveryNDaysIsZero() throws Exception {
        // given
        UpdateTaskRequest request = UpdateTaskRequest.builder()
                .everyNDays(0)
                .build();

        // when & then
        mockMvc.perform(patch("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(taskService, never()).update(any(), any());
    }

    @Test
    @DisplayName("작업 수정 - 존재하지 않음")
    void testUpdateNotFound() throws Exception {
        // given
        UpdateTaskRequest request = UpdateTaskRequest.builder()
                .name("Updated Task")
                .build();

        when(taskService.update(eq(999L), any(UpdateTaskRequest.class)))
                .thenThrow(new IllegalArgumentException("Task not found: id=999"));

        // when & then
        mockMvc.perform(patch("/api/tasks/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.error.message", containsString("Task not found")));

        verify(taskService, times(1)).update(eq(999L), any(UpdateTaskRequest.class));
    }

    @Test
    @DisplayName("작업 수정 - startDate 포함 시 요청/응답 반영")
    void testUpdateSuccessWithStartDate() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 5, 10);
        OffsetDateTime expectedDueAt = OffsetDateTime.parse("2026-05-10T00:00:00+09:00");

        UpdateTaskRequest request = UpdateTaskRequest.builder()
                .startDate(startDate)
                .build();

        TaskResponse updatedResponse = TaskResponse.builder()
                .id(1L)
                .name("Updated Task")
                .everyNDays(3)
                .timezone("Asia/Seoul")
                .startDate(startDate)
                .nextDueAt(expectedDueAt)
                .isActive(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(taskService.update(eq(1L), any(UpdateTaskRequest.class))).thenReturn(updatedResponse);

        mockMvc.perform(patch("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.startDate", is("2026-05-10")))
                .andExpect(jsonPath("$.data.nextDueAt", org.hamcrest.Matchers.startsWith("2026-05-10T00:00:00+09:00")));

        verify(taskService, times(1))
                .update(eq(1L), argThat(req -> startDate.equals(req.getStartDate())));
    }

    @Test
    @DisplayName("작업 삭제 - 성공")
    void testDeleteSuccess() throws Exception {
        // given
        doNothing().when(taskService).delete(1L);

        // when & then
        mockMvc.perform(delete("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(taskService, times(1)).delete(1L);
    }

    @Test
    @DisplayName("작업 삭제 - 존재하지 않음")
    void testDeleteNotFound() throws Exception {
        // given
        doThrow(new IllegalArgumentException("Task not found: id=999"))
                .when(taskService).delete(999L);

        // when & then
        mockMvc.perform(delete("/api/tasks/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("BAD_REQUEST")));

        verify(taskService, times(1)).delete(999L);
    }

    @Test
    @DisplayName("작업 완료 - 성공 (200 OK + TaskResponse)")
    void testCompleteSuccess() throws Exception {
        // given
        OffsetDateTime now = OffsetDateTime.now();
        TaskResponse completedResponse = TaskResponse.builder()
                .id(1L)
                .name("Test Task")
                .everyNDays(7)
                .timezone("Asia/Seoul")
                .status(TaskStatus.UPCOMING)
                .nextDueAt(now.plusDays(7))
                .completedAt(now)
                .lastCompletedAt(now)
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(taskService.complete(1L)).thenReturn(completedResponse);

        // when & then
        mockMvc.perform(post("/api/tasks/1/complete")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(1)))
                .andExpect(jsonPath("$.data.status", is("UPCOMING")))
                .andExpect(jsonPath("$.data.completedAt").exists())
                .andExpect(jsonPath("$.data.lastCompletedAt").exists())
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(taskService, times(1)).complete(1L);
    }

    @Test
    @DisplayName("작업 완료 - 존재하지 않음 (404)")
    void testCompleteNotFound() throws Exception {
        // given
        when(taskService.complete(999L)).thenThrow(new TaskNotFoundException(999L));

        // when & then
        mockMvc.perform(post("/api/tasks/999/complete")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("TASK_NOT_FOUND")))
                .andExpect(jsonPath("$.error.message", containsString("999")))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(taskService, times(1)).complete(999L);
    }

    @Test
    @DisplayName("작업 완료 - 비활성 Task (409)")
    void testCompleteInactiveTask() throws Exception {
        // given
        when(taskService.complete(1L)).thenThrow(new TaskInactiveException(1L));

        // when & then
        mockMvc.perform(post("/api/tasks/1/complete")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("TASK_INACTIVE")))
                .andExpect(jsonPath("$.error.message", containsString("1")))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(taskService, times(1)).complete(1L);
    }

    @Test
    @DisplayName("작업 완료 - 2초 이내 중복 완료 (409)")
    void testCompleteRecentlyCompleted() throws Exception {
        // given
        when(taskService.complete(1L)).thenThrow(new TaskRecentlyCompletedException(1L));

        // when & then
        mockMvc.perform(post("/api/tasks/1/complete")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("TASK_RECENTLY_COMPLETED")))
                .andExpect(jsonPath("$.error.message", containsString("1")))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(taskService, times(1)).complete(1L);
    }
}
