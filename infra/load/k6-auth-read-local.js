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

function envBoolean(name, defaultValue = false) {
  const value = __ENV[name];
  if (!value) return defaultValue;
  return ["1", "true", "yes", "on"].includes(value.trim().toLowerCase());
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
  const month =
    Number.isInteger(envMonth) && envMonth >= 1 && envMonth <= 12
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

function loginAndGetAccessToken(baseUrl, email, password) {
  const response = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({ email, password }),
    {
      headers: {
        "Content-Type": "application/json",
      },
      tags: { endpoint: "auth_login_bootstrap" },
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
    "http_req_duration{endpoint:task_completions_monthly}": ["p(95)<1000"],
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
    throw new Error(
      "ACCESS_TOKEN 또는 AUTH_EMAIL/AUTH_PASSWORD가 필요합니다. " +
      "Cloudflare 우회 부하테스트는 홈서버 localhost(BASE_URL) 대상을 권장합니다."
    );
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
    reloginOn401: envBoolean("RELOGIN_ON_401", false),
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
  };
}

export default function (setupData) {
  const accessToken = ensureVuToken(setupData);
  const baseUrl = setupData.baseUrl;
  const apiBase = `${baseUrl}/api`;
  const sleepSeconds = envNumber("SLEEP_SECONDS", 1);

  const baseParams = {
    headers: authHeaders(accessToken),
  };

  const coreBatch = [
    ["GET", `${apiBase}/tasks?status=DUE_NOW`, null, { ...baseParams, tags: { endpoint: "tasks_due_now" } }],
    ["GET", `${apiBase}/tasks?status=UPCOMING`, null, { ...baseParams, tags: { endpoint: "tasks_upcoming" } }],
    ["GET", `${apiBase}/insights/dashboard`, null, { ...baseParams, tags: { endpoint: "insights_dashboard" } }],
    ["GET", `${apiBase}/insights/overview?days=30&top=5`, null, { ...baseParams, tags: { endpoint: "insights_overview" } }],
    ["GET", `${apiBase}/insights/recent-completions`, null, { ...baseParams, tags: { endpoint: "insights_recent" } }],
  ];

  const [
    dueNowResponse,
    upcomingResponse,
    dashboardResponse,
    overviewResponse,
    recentResponse,
  ] = http.batch(coreBatch);

  if (dueNowResponse.status === 401 || upcomingResponse.status === 401) {
    check(dueNowResponse, {
      "auth: no unauthorized during load": (r) => r.status !== 401,
    });
  }

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
  const selectedTaskId = taskIdFromLists ?? envTaskId();

  if (selectedTaskId !== null) {
    const { year, month } = completionYearMonth();
    const [detailResponse, completionsResponse] = http.batch([
      [
        "GET",
        `${apiBase}/tasks/${selectedTaskId}`,
        null,
        { ...baseParams, tags: { endpoint: "task_detail" } },
      ],
      [
        "GET",
        `${apiBase}/tasks/${selectedTaskId}/completions?year=${year}&month=${month}`,
        null,
        { ...baseParams, tags: { endpoint: "task_completions_monthly" } },
      ],
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
