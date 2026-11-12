// The newer read endpoints, one request at a time (so the cost of a single request is not mixed with queueing):
//   /me/spending (per-month shares), /groups/{id}/summary (per-category totals), group search, activity search.
// Needs seed.sql then seed-heavy.sql (see run-reads.sh). "typical": ids 1-200 (one group, 40 expenses). "heavy": ids 201-210
// (50 groups, about 10,000 expenses each in the feed; heavy group ids 41-90 hold 200 expenses each).
import http from 'k6/http';
import { check } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

const BASE = __ENV.BASE_URL || 'http://localhost:8081';
const SECRET = __ENV.JWT_SECRET || 'loadtest-secret-loadtest-secret-loadtest-secret';
const N = 100;

const WHO = ['typical', 'heavy'];
const ENDPOINTS = ['spending', 'group_summary', 'group_search', 'group_search_category', 'activity', 'activity_search', 'activity_search_category'];

export const options = {
  scenarios: {
    warmup: { executor: 'constant-arrival-rate', rate: 15, timeUnit: '1s', duration: '30s', preAllocatedVUs: 20, maxVUs: 100, exec: 'warmup' },
    ...Object.fromEntries(WHO.map((w, i) => [`latency_${w}`, { executor: 'per-vu-iterations', vus: 1, iterations: N, maxDuration: '120s', exec: `latency_${w}`, startTime: `${35 + i * 60}s` }])),
  },
  thresholds: Object.fromEntries(WHO.flatMap((w) => ENDPOINTS.map((e) => [`http_req_duration{phase:latency,who:${w},endpoint:${e}}`, ['p(95)<1e9']]))),
};

const b64url = (s) => encoding.b64encode(s, 'rawurl');
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
const typicalUser = () => 1 + Math.floor(Math.random() * 200);
const heavyUser = () => 201 + Math.floor(Math.random() * 10);
const typicalGroup = (u) => 1 + Math.floor((u - 1) / 5);
const heavyGroup = () => 41 + Math.floor(Math.random() * 50);

function get(path, user, tags) {
  const res = http.get(`${BASE}/api/v1${path}`, { headers: { Authorization: `Bearer ${tokenFor(user)}` }, tags });
  check(res, { 'status is 200': (r) => r.status === 200 });
  return res;
}

function visit(user, group, tags) {
  const t = (endpoint) => ({ ...tags, endpoint });
  get('/me/spending', user, t('spending'));
  get(`/groups/${group}/summary`, user, t('group_summary'));
  get(`/groups/${group}/expenses?limit=30&q=expense%201`, user, t('group_search'));
  get(`/groups/${group}/expenses?limit=30&category=FOOD`, user, t('group_search_category')); // matches nothing: the whole group is still read
  get('/me/activity?limit=30', user, t('activity'));
  get('/me/activity?limit=30&q=expense%201', user, t('activity_search'));
  get('/me/activity?limit=30&category=FOOD', user, t('activity_search_category'));
}

export function warmup() { const h = Math.random() < 0.3; const u = h ? heavyUser() : typicalUser(); visit(u, h ? heavyGroup() : typicalGroup(u), { phase: 'warmup' }); }
export function latency_typical() { const u = typicalUser(); visit(u, typicalGroup(u), { phase: 'latency', who: 'typical' }); }
export function latency_heavy() { visit(heavyUser(), heavyGroup(), { phase: 'latency', who: 'heavy' }); }
