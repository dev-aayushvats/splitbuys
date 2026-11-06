// Mixed, realistic traffic against seeded data. Open model (arrival rate): requests keep arriving at the target rate
// even if the server slows down, which is what real users do. A closed model (fixed VUs) would hide saturation.
import http from 'k6/http';
import { check } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import exec from 'k6/execution';

const BASE = __ENV.BASE_URL || 'http://localhost:8081';
const SECRET = __ENV.JWT_SECRET || 'loadtest-secret-loadtest-secret-loadtest-secret';
// Steady-state steps, e.g. RATES=25,50,75,100,150 (requests/second). Each step = 15s ramp + 45s hold, and every request
// is tagged with its step so latency can be read per load level instead of as one blurred number over the whole ramp.
const RATES = (__ENV.RATES || '25,50,75,100,150').split(',').map(Number);
const STEP_MS = 60000;
const USERS = 200;
const GROUP_SIZE = 5;
const stages = [];
RATES.forEach((rps) => {
  stages.push({ target: rps, duration: '15s' });
  stages.push({ target: rps, duration: '45s' });
});

export const options = {
  scenarios: {
    mixed: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 1000,
      stages,
    },
  },
  // the trivial thresholds exist only to make k6 report these sub-metrics in the summary
  thresholds: Object.fromEntries([
    ['http_req_failed', ['rate<0.01']],
    ['http_req_duration', ['p(95)<500']],
    ...['list_groups', 'balances', 'list_expenses', 'get_group', 'add_expense'].map((e) => [`http_req_duration{endpoint:${e}}`, ['p(95)<1e9']]),
    ...RATES.map((_, i) => [`http_req_duration{step:${i}}`, ['p(95)<1e9']]),
  ]),
};

function b64url(s) {
  return encoding.b64encode(s, 'rawurl');
}

const tokens = {};
function tokenFor(userId) {
  if (!tokens[userId]) {
    const now = Math.floor(Date.now() / 1000);
    const head = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const body = b64url(JSON.stringify({ iss: 'splitbuys', sub: String(userId), iat: now, exp: now + 3600 }));
    tokens[userId] = `${head}.${body}.${crypto.hmac('sha256', SECRET, `${head}.${body}`, 'base64rawurl')}`;
  }
  return tokens[userId];
}

export default function () {
  const user = 1 + Math.floor(Math.random() * USERS);
  const group = 1 + Math.floor((user - 1) / GROUP_SIZE);
  const members = Array.from({ length: GROUP_SIZE }, (_, i) => (group - 1) * GROUP_SIZE + 1 + i);
  const headers = { Authorization: `Bearer ${tokenFor(user)}`, 'Content-Type': 'application/json' };
  const step = Math.min(RATES.length - 1, Math.floor((Date.now() - exec.scenario.startTime) / STEP_MS));
  const r = Math.random();
  let res;

  if (r < 0.35) {
    res = http.get(`${BASE}/api/v1/groups`, { headers, tags: { step: String(step), endpoint: 'list_groups' } });
  } else if (r < 0.6) {
    res = http.get(`${BASE}/api/v1/groups/${group}/balances`, { headers, tags: { step: String(step), endpoint: 'balances' } });
  } else if (r < 0.75) {
    res = http.get(`${BASE}/api/v1/groups/${group}/expenses`, { headers, tags: { step: String(step), endpoint: 'list_expenses' } });
  } else if (r < 0.8) {
    res = http.get(`${BASE}/api/v1/groups/${group}`, { headers, tags: { step: String(step), endpoint: 'get_group' } });
  } else {
    const body = JSON.stringify({
      description: 'Load test dinner',
      amountPaise: 10000 + Math.floor(Math.random() * 90000),
      payerId: user,
      splitType: 'EQUAL',
      splits: members.map((userId) => ({ userId })),
    });
    res = http.post(`${BASE}/api/v1/groups/${group}/expenses`, body, { headers, tags: { step: String(step), endpoint: 'add_expense' } });
  }
  check(res, { 'status is 2xx': (x) => x.status >= 200 && x.status < 300 });
}
