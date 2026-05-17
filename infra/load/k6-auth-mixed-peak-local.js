import http from "k6/http";
import { check, sleep } from "k6";

function envNumber(name, defaultValue) {
  const value = Number(__ENV[name]);
  return Number.isFinite(value) && value >= 0 ? value : defaultValue;
}

function envDuration(name, defaultValue) {
  const value = __ENV[name];
  return value && value.trim().length > 0 ? value.trim() : defaultValue;
}

function envBoolean(name, defaultValue = false) {
  const value = __ENV[name];
  if (!value) return defaultValue;
  return ["1", "true", "yes", "on"].includes(value.trim().toLowerCase());
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
    [`${endpointTag}: status 200`]: (r) => r.status === 200,
  });
  check(body, {
    [`${endpointTag}: success true`]: (b) => !!b && b.success === true,
  });
}

function getTaskIdFromTaskList(body) {
  const data = body?.data;
  if (Array.isArray(data) && data.length > 0 && typeof data[0]?.id === "number") {
    return data[0].id;
  }
  return null;
}

function completionYearMonth() {
  const now = new Date();
  const envYear = Number(__ENV.COMPLETIONS_YEAR);
  const envMonth = Number(__ENV.COMPLETIONS_MONTH);

  const year = Number.isInteger(envYear) ? envYear : now.getUTCFullYear();
  const month =
    Number.isInteger(envMonth) && envMonth >= 1 && envMonth <= 12
      ? envMonth
      : now.getUTCMonth() + 1;

  return { year, month };
}

function loginAndGetAccessToken(baseUrl, email, password) {
  const response = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({ email, password }),
    {
      headers: {
        "Content-Type": "application/json",
      },
      tags: { endpoint: "auth_login_bootstrap", flow: "auth" },
    }
  );

  const body = parseJsonResponse(response);
  const accessToken = body?.data?.accessToken;
  const expiresInSeconds = Number(body?.data?.expiresInSeconds || 0);

  check(response, {
    "auth bootstrap: status 200": (r) => r.status === 200,
  });
  check(body, {
    "auth bootstrap: success true": (b) => !!b && b.success === true,
  });
  check(accessToken, {
    "auth bootstrap: accessToken exists": (token) => typeof token === "string" && token.length > 0,
  });

  return {
    accessToken: typeof accessToken === "string" ? accessToken : null,
    expiresInSeconds: Number.isFinite(expiresInSeconds) && expiresInSeconds > 0 ? expiresInSeconds : 0,
  };
}

export const options = {
  stages: [
    { duration: envDuration("WARMUP_DURATION", "5m"), target: envNumber("WARMUP_VUS", 20) },
    { duration: envDuration("RAMP_TO_PEAK_DURATION", "5m"), target: envNumber("PEAK_VUS", 50) },
    { duration: envDuration("PEAK_HOLD_DURATION", "20m"), target: envNumber("PEAK_VUS", 50) },
    { duration: envDuration("RAMP_DOWN_DURATION", "5m"), target: 0 },
  ],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
    "http_req_duration{flow:read}": ["p(95)<1000"],
    "http_req_duration{flow:write}": ["p(95)<1500"],
    "http_req_duration{endpoint:tasks_due_now}": ["p(95)<1000"],
    "http_req_duration{endpoint:tasks_upcoming}": ["p(95)<1000"],
    "http_req_duration{endpoint:insights_dashboard}": ["p(95)<1200"],
    "http_req_duration{endpoint:insights_overview}": ["p(95)<1200"],
    "http_req_duration{endpoint:insights_recent}": ["p(95)<1200"],
    "http_req_duration{endpoint:task_create}": ["p(95)<1200"],
    "http_req_duration{endpoint:task_update}": ["p(95)<1200"],
    "http_req_duration{endpoint:task_complete}": ["p(95)<1200"],
  },
};

export function setup() {
  const baseUrl = (__ENV.BASE_URL || "http://127.0.0.1:3000").trim();
  const preIssuedToken = (__ENV.ACCESS_TOKEN || "").trim();
  if (preIssuedToken.length > 0) {
    return {
      baseUrl,
      accessToken: preIssuedToken,
      tokenExpiresAtMs: 0,
      email: "",
      password: "",
      reloginOn401: false,
    };
  }

  const email = (__ENV.AUTH_EMAIL || "").trim();
  const password = __ENV.AUTH_PASSWORD || "";
  if (!email || !password) {
    throw new Error("ACCESS_TOKEN 또는 AUTH_EMAIL/AUTH_PASSWORD가 필요합니다.");
  }

  const loginResult = loginAndGetAccessToken(baseUrl, email, password);
  if (!loginResult.accessToken) {
    throw new Error("초기 로그인(access token 발급)에 실패했습니다.");
  }

  const safetyBufferSec = 30;
  const tokenExpiresAtMs =
    loginResult.expiresInSeconds > safetyBufferSec
      ? Date.now() + (loginResult.expiresInSeconds - safetyBufferSec) * 1000
      : 0;

  return {
    baseUrl,
    accessToken: loginResult.accessToken,
    tokenExpiresAtMs,
    email,
    password,
    reloginOn401: envBoolean("RELOGIN_ON_401", true),
  };
}

let cachedAccessToken = null;
let cachedTokenExpiresAtMs = 0;
let baseUrlCache = "";
let credentialsCache = null;
let reloginOn401Cache = false;

function ensureVuToken(setupData) {
  if (!baseUrlCache) {
    baseUrlCache = setupData.baseUrl;
    credentialsCache = {
      email: setupData.email,
      password: setupData.password,
    };
    reloginOn401Cache = !!setupData.reloginOn401;
  }

  if (!cachedAccessToken) {
    cachedAccessToken = setupData.accessToken;
    cachedTokenExpiresAtMs = setupData.tokenExpiresAtMs || 0;
    return cachedAccessToken;
  }

  const canRelogin =
    reloginOn401Cache &&
    credentialsCache &&
    credentialsCache.email &&
    credentialsCache.password;

  if (cachedTokenExpiresAtMs > 0 && Date.now() >= cachedTokenExpiresAtMs && canRelogin) {
    const loginResult = loginAndGetAccessToken(
      baseUrlCache,
      credentialsCache.email,
      credentialsCache.password
    );
    if (loginResult.accessToken) {
      cachedAccessToken = loginResult.accessToken;
      cachedTokenExpiresAtMs =
        loginResult.expiresInSeconds > 30
          ? Date.now() + (loginResult.expiresInSeconds - 30) * 1000
          : 0;
    }
  }

  return cachedAccessToken;
}

function authHeaders(accessToken) {
  return {
    Authorization: `Bearer ${accessToken}`,
    "Content-Type": "application/json",
  };
}

function runReadFlow(baseUrl, accessToken) {
  const apiBase = `${baseUrl}/api`;
  const baseParams = {
    headers: authHeaders(accessToken),
  };

  const coreBatch = [
    ["GET", `${apiBase}/tasks?status=DUE_NOW`, null, { ...baseParams, tags: { endpoint: "tasks_due_now", flow: "read" } }],
    ["GET", `${apiBase}/tasks?status=UPCOMING`, null, { ...baseParams, tags: { endpoint: "tasks_upcoming", flow: "read" } }],
    ["GET", `${apiBase}/insights/dashboard`, null, { ...baseParams, tags: { endpoint: "insights_dashboard", flow: "read" } }],
    ["GET", `${apiBase}/insights/overview?days=30&top=5`, null, { ...baseParams, tags: { endpoint: "insights_overview", flow: "read" } }],
    ["GET", `${apiBase}/insights/recent-completions`, null, { ...baseParams, tags: { endpoint: "insights_recent", flow: "read" } }],
  ];

  const [
    dueNowResponse,
    upcomingResponse,
    dashboardResponse,
    overviewResponse,
    recentResponse,
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

  const selectedTaskId = getTaskIdFromTaskList(dueNowBody) ?? getTaskIdFromTaskList(upcomingBody);
  if (selectedTaskId === null) return;

  const { year, month } = completionYearMonth();
  const [detailResponse, completionsResponse] = http.batch([
    [
      "GET",
      `${apiBase}/tasks/${selectedTaskId}`,
      null,
      { ...baseParams, tags: { endpoint: "task_detail", flow: "read" } },
    ],
    [
      "GET",
      `${apiBase}/tasks/${selectedTaskId}/completions?year=${year}&month=${month}`,
      null,
      { ...baseParams, tags: { endpoint: "task_completions_monthly", flow: "read" } },
    ],
  ]);

  verifyResponse("task_detail", detailResponse, parseJsonResponse(detailResponse));
  verifyResponse(
    "task_completions_monthly",
    completionsResponse,
    parseJsonResponse(completionsResponse)
  );
}

function runWriteFlow(baseUrl, accessToken) {
  const apiBase = `${baseUrl}/api`;
  const requestBase = {
    headers: authHeaders(accessToken),
  };

  const seed = `${__VU}-${__ITER}-${Date.now()}`;
  const createPayload = {
    name: `k6-mixed-${seed}`,
    everyNDays: 3,
  };

  const createResponse = http.post(
    `${apiBase}/tasks`,
    JSON.stringify(createPayload),
    { ...requestBase, tags: { endpoint: "task_create", flow: "write" } }
  );
  const createBody = parseJsonResponse(createResponse);

  check(createResponse, {
    "task_create: status 201": (r) => r.status === 201,
  });
  check(createBody, {
    "task_create: success true": (b) => !!b && b.success === true,
    "task_create: id exists": (b) => typeof b?.data?.id === "number",
  });

  const createdTaskId = createBody?.data?.id;
  if (typeof createdTaskId !== "number") return;

  const updateResponse = http.patch(
    `${apiBase}/tasks/${createdTaskId}`,
    JSON.stringify({
      name: `k6-mixed-updated-${seed}`,
      everyNDays: 4,
    }),
    { ...requestBase, tags: { endpoint: "task_update", flow: "write" } }
  );
  verifyResponse("task_update", updateResponse, parseJsonResponse(updateResponse));

  const completeResponse = http.post(
    `${apiBase}/tasks/${createdTaskId}/complete`,
    null,
    { ...requestBase, tags: { endpoint: "task_complete", flow: "write" } }
  );
  verifyResponse("task_complete", completeResponse, parseJsonResponse(completeResponse));

  const deleteResponse = http.del(
    `${apiBase}/tasks/${createdTaskId}`,
    null,
    { ...requestBase, tags: { endpoint: "task_delete", flow: "write" } }
  );
  check(deleteResponse, {
    "task_delete: status 204": (r) => r.status === 204 || r.status === 200,
  });
}

export default function (setupData) {
  const accessToken = ensureVuToken(setupData);
  const writeRatioPercent = envNumber("WRITE_RATIO_PERCENT", 30);
  const sleepSeconds = envNumber("SLEEP_SECONDS", 0.5);

  if ((Math.random() * 100) < writeRatioPercent) {
    runWriteFlow(setupData.baseUrl, accessToken);
  } else {
    runReadFlow(setupData.baseUrl, accessToken);
  }

  sleep(sleepSeconds);
}
