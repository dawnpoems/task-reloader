import http from "k6/http";
import { check, sleep } from "k6";

function envNumber(name, defaultValue) {
  const value = Number(__ENV[name]);
  return Number.isFinite(value) && value > 0 ? value : defaultValue;
}

function envDuration(name, defaultValue) {
  const value = __ENV[name];
  return value && value.trim().length > 0 ? value.trim() : defaultValue;
}

function envTaskId() {
  const value = Number(__ENV.TASK_ID);
  return Number.isInteger(value) && value > 0 ? value : null;
}

function completionYearMonth() {
  const now = new Date();

  const envYear = Number(__ENV.COMPLETIONS_YEAR);
  const envMonth = Number(__ENV.COMPLETIONS_MONTH);

  const year = Number.isInteger(envYear) ? envYear : now.getUTCFullYear();
  const month = Number.isInteger(envMonth) && envMonth >= 1 && envMonth <= 12
    ? envMonth
    : now.getUTCMonth() + 1;

  return { year, month };
}

function parseJsonResponse(response) {
  try {
    return response.json();
  } catch (_error) {
    return null;
  }
}

function verifyResponse(endpointTag, response, body) {
  check(response, {
    [`${endpointTag}: status 200`]: (r) => r.status === 200
  });

  check(body, {
    [`${endpointTag}: success true`]: (b) => !!b && b.success === true
  });
}

function getTaskIdFromTaskList(body) {
  const data = body?.data;
  if (Array.isArray(data) && data.length > 0 && typeof data[0]?.id === "number") {
    return data[0].id;
  }
  return null;
}

export const options = {
  vus: envNumber("VUS", 10),
  duration: envDuration("DURATION", "1m"),
  thresholds: {
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
    "http_req_duration{endpoint:tasks_due_now}": ["p(95)<800"],
    "http_req_duration{endpoint:tasks_upcoming}": ["p(95)<800"],
    "http_req_duration{endpoint:insights_dashboard}": ["p(95)<1000"],
    "http_req_duration{endpoint:insights_overview}": ["p(95)<1000"],
    "http_req_duration{endpoint:insights_recent}": ["p(95)<1000"],
    "http_req_duration{endpoint:task_detail}": ["p(95)<1000"],
    "http_req_duration{endpoint:task_completions_monthly}": ["p(95)<1000"]
  }
};

export default function () {
  const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
  const apiBase = `${baseUrl}/api`;
  const sleepSeconds = envNumber("SLEEP_SECONDS", 1);

  const coreBatch = [
    ["GET", `${apiBase}/tasks?status=DUE_NOW`, null, { tags: { endpoint: "tasks_due_now" } }],
    ["GET", `${apiBase}/tasks?status=UPCOMING`, null, { tags: { endpoint: "tasks_upcoming" } }],
    ["GET", `${apiBase}/insights/dashboard`, null, { tags: { endpoint: "insights_dashboard" } }],
    ["GET", `${apiBase}/insights/overview?days=30&top=5`, null, { tags: { endpoint: "insights_overview" } }],
    ["GET", `${apiBase}/insights/recent-completions`, null, { tags: { endpoint: "insights_recent" } }]
  ];

  const [
    dueNowResponse,
    upcomingResponse,
    dashboardResponse,
    overviewResponse,
    recentResponse
  ] = http.batch(coreBatch);

  const dueNowBody = parseJsonResponse(dueNowResponse);
  const upcomingBody = parseJsonResponse(upcomingResponse);
  const dashboardBody = parseJsonResponse(dashboardResponse);
  const overviewBody = parseJsonResponse(overviewResponse);
  const recentBody = parseJsonResponse(recentResponse);

  verifyResponse("tasks_due_now", dueNowResponse, dueNowBody);
  verifyResponse("tasks_upcoming", upcomingResponse, upcomingBody);
  verifyResponse("insights_dashboard", dashboardResponse, dashboardBody);
  verifyResponse("insights_overview", overviewResponse, overviewBody);
  verifyResponse("insights_recent", recentResponse, recentBody);

  const taskIdFromLists = getTaskIdFromTaskList(dueNowBody) ?? getTaskIdFromTaskList(upcomingBody);
  const fallbackTaskId = envTaskId();
  const selectedTaskId = taskIdFromLists ?? fallbackTaskId;

  if (selectedTaskId !== null) {
    const { year, month } = completionYearMonth();

    const [detailResponse, completionsResponse] = http.batch([
      ["GET", `${apiBase}/tasks/${selectedTaskId}`, null, { tags: { endpoint: "task_detail" } }],
      [
        "GET",
        `${apiBase}/tasks/${selectedTaskId}/completions?year=${year}&month=${month}`,
        null,
        { tags: { endpoint: "task_completions_monthly" } }
      ]
    ]);

    verifyResponse("task_detail", detailResponse, parseJsonResponse(detailResponse));
    verifyResponse(
      "task_completions_monthly",
      completionsResponse,
      parseJsonResponse(completionsResponse)
    );
  }

  sleep(sleepSeconds);
}
