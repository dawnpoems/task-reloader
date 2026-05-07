import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 10,
  duration: "1m",
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<800"],
    checks: ["rate>0.99"]
  }
};

export default function () {
  const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
  const res = http.get(`${baseUrl}/api/tasks?status=DUE_NOW`);

  check(res, {
    "status is 200": (r) => r.status === 200
  });

  sleep(1);
}
