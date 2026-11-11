// Friends and Activity under load. Needs seed.sql then seed-heavy.sql (see run-feeds.sh).
//
// Two kinds of user: "typical" (ids 1-200: one group, 4 friends, 40 expenses) and "heavy" (ids 201-210: 50 groups, 209 friends,
// about 10,000 expenses and 200 payments in the feed). Phases run one after another so each can be read on its own:
//   warmup   discarded; the JVM must be compiled before anything is measured (see README: a cold start dominates otherwise)
//   latency  one request at a time per user kind, so the cost of a single request is not mixed with queueing
//   deep     one heavy user pages through the WHOLE feed with the cursor, tagged by depth: does page 300 cost more than page 1?
//   load     open model (arrival rate) steps, 90% typical / 10% heavy visits
import http from 'k6/http';
import { check } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import exec from 'k6/execution';

const BASE = __ENV.BASE_URL || 'http://localhost:8081';
const SECRET = __ENV.JWT_SECRET || 'loadtest-secret-loadtest-secret-loadtest-secret';
const RATES = (__ENV.RATES || '10,25,50').split(',').map(Number); // visits per second
const STEP_MS = 60000;
const PAGE = 30; // the web app's page size
const TYPICAL_USERS = 200;
const HEAVY_FIRST = 201;
const HEAVY_USERS = 10;

const stages = [];
RATES.forEach((rps) => { stages.push({ target: rps, duration: '15s' }); stages.push({ target: rps, duration: '45s' }); });

export const options = {
  scenarios: {
    warmup: { executor: 'constant-arrival-rate', rate: 15, timeUnit: '1s', duration: '30s', preAllocatedVUs: 20, maxVUs: 100, exec: 'warmup', startTime: '0s' },
    latency_typical: { executor: 'per-vu-iterations', vus: 1, iterations: 150, maxDuration: '40s', exec: 'latencyTypical', startTime: '35s' },
    latency_heavy: { executor: 'per-vu-iterations', vus: 1, iterations: 150, maxDuration: '40s', exec: 'latencyHeavy', startTime: '80s' },
    deep_walk: { executor: 'per-vu-iterations', vus: 1, iterations: 3, maxDuration: '150s', exec: 'deepWalk', startTime: '125s' },
    load: { executor: 'ramping-arrival-rate', startRate: 2, timeUnit: '1s', preAllocatedVUs: 100, maxVUs: 1000, stages, exec: 'load', startTime: '285s' },
  },
  // the trivial thresholds exist only to make k6 report these sub-metrics in the summary
  thresholds: Object.fromEntries([
    ['http_req_failed', ['rate<0.01']],
    ...['typical', 'heavy'].flatMap((w) => ['friends', 'activity', 'activity_next'].map((e) => `http_req_duration{phase:latency,who:${w},endpoint:${e}}`)),
    ...['p1-10', 'p11-100', 'p101+'].map((d) => `http_req_duration{phase:deep,depth:${d}}`),
    ...RATES.flatMap((_, i) => ['typical', 'heavy'].map((w) => `http_req_duration{phase:load,step:${i},who:${w}}`)),
    ...['friends', 'activity', 'activity_next'].map((e) => `http_req_duration{phase:load,endpoint:${e}}`),
  ].map((m) => (Array.isArray(m) ? m : [m, ['p(95)<1e9']]))),
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

const typicalUser = () => 1 + Math.floor(Math.random() * TYPICAL_USERS);
const heavyUser = () => HEAVY_FIRST + Math.floor(Math.random() * HEAVY_USERS);

function call(path, user, tags) {
  const res = http.get(`${BASE}/api/v1${path}`, { headers: { Authorization: `Bearer ${tokenFor(user)}` }, tags });
  check(res, { 'status is 200': (r) => r.status === 200 });
  return res;
}
const friends = (user, tags) => call('/me/friends', user, { ...tags, endpoint: 'friends' });
const activity = (user, cursor, tags) =>
  call(`/me/activity?limit=${PAGE}${cursor ? `&cursor=${cursor}` : ''}`, user, { ...tags, endpoint: cursor ? 'activity_next' : 'activity' });
const nextCursor = (res) => (res.status === 200 ? res.json('nextCursor') : null);

/** What opening the tab and scrolling a little looks like: the first page, then (sometimes) one more. */
function visitActivity(user, tags, scrollOn) {
  const first = activity(user, null, tags);
  const cursor = nextCursor(first);
  if (cursor && scrollOn) activity(user, cursor, tags);
}

export function warmup() {
  const user = Math.random() < 0.9 ? typicalUser() : heavyUser();
  if (Math.random() < 0.5) friends(user, { phase: 'warmup' }); else visitActivity(user, { phase: 'warmup' }, true);
}

function latency(user, who) {
  const tags = { phase: 'latency', who };
  friends(user, tags);
  visitActivity(user, tags, true);
}
export function latencyTypical() { latency(typicalUser(), 'typical'); }
export function latencyHeavy() { latency(heavyUser(), 'heavy'); }

export function deepWalk() {
  const user = HEAVY_FIRST + (exec.scenario.iterationInTest % HEAVY_USERS);
  let cursor = null;
  for (let page = 1; page <= 1000; page++) {
    const depth = page <= 10 ? 'p1-10' : page <= 100 ? 'p11-100' : 'p101+';
    const res = call(`/me/activity?limit=${PAGE}${cursor ? `&cursor=${cursor}` : ''}`, user, { phase: 'deep', depth, endpoint: 'activity_deep' });
    cursor = nextCursor(res);
    if (!cursor) break;
  }
}

export function load() {
  const step = Math.min(RATES.length - 1, Math.floor((Date.now() - exec.scenario.startTime) / STEP_MS));
  const heavy = Math.random() < 0.1;
  const user = heavy ? heavyUser() : typicalUser();
  const tags = { phase: 'load', step: String(step), who: heavy ? 'heavy' : 'typical' };
  if (Math.random() < 0.5) friends(user, tags); else visitActivity(user, tags, Math.random() < 0.3);
}
