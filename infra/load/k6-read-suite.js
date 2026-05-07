import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 10,
  duration: "1m",
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

function getJson(apiBase, path, endpointTag) {
  const res = http.get(`${apiBase}${path}`, { tags: { endpoint: endpointTag } });

  check(res, {
    [`${endpointTag}: status 200`]: (r) => r.status === 200
  });

  let body = null;
  try {
    body = res.json();
  } catch (_error) {
    body = null;
  }

  check(body, {
    [`${endpointTag}: success true`]: (b) => !!b && b.success === true
  });

  return body;
}

function pickFirstTaskId(...responses) {
  for (const response of responses) {
    const data = response?.data;
    if (Array.isArray(data) && data.length > 0 && typeof data[0]?.id === "number") {
      return data[0].id;
    }
  }
  return null;
}

export default function () {
  const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
  const apiBase = `${baseUrl}/api`;

  const dueNow = getJson(apiBase, "/tasks?status=DUE_NOW", "tasks_due_now");
  const upcoming = getJson(apiBase, "/tasks?status=UPCOMING", "tasks_upcoming");

  getJson(apiBase, "/insights/dashboard", "insights_dashboard");
  getJson(apiBase, "/insights/overview?days=30&top=5", "insights_overview");
  getJson(apiBase, "/insights/recent-completions", "insights_recent");

  const taskId = pickFirstTaskId(dueNow, upcoming);
  if (taskId !== null) {
    getJson(apiBase, `/tasks/${taskId}`, "task_detail");

    const now = new Date();
    const year = now.getUTCFullYear();
    const month = now.getUTCMonth() + 1;
    getJson(
      apiBase,
      `/tasks/${taskId}/completions?year=${year}&month=${month}`,
      "task_completions_monthly"
    );
  }

  sleep(1);
}
