// 백엔드 내부 콜백의 모의 서버. 서명·시각을 ProcessingCallbackAuthenticator와 같은 규칙으로 검증한다.
// 시험에서 가져다 쓰고, EC2 확인 때는 직접 실행한다.
//   node test/support/mock-backend.js --environment dev --port 8787 [--start 503|skip] [--complete 400] [--failed 500]
// 시크릿은 환경 변수 IMAGE_PROCESSING_API_SECRET. 요청마다 JSON 한 줄을 표준 출력에 쓴다.
import { createServer } from 'node:http';
import { randomUUID, timingSafeEqual } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { signCallback } from '../../src/worker/callback-client.js';

const ROUTE = /^\/internal\/v1\/filter-probes\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/(start|complete|failed)$/;
const MAX_AGE_SECONDS = 300;
const MAX_FUTURE_SKEW_SECONDS = 30;

function verifySignature({ secret, environment, path, rawBody, timestamp, signature, nowSeconds }) {
  if (typeof timestamp !== 'string' || !/^-?\d+$/.test(timestamp)) return false;
  const age = nowSeconds - Number(timestamp);
  if (age > MAX_AGE_SECONDS || age < -MAX_FUTURE_SKEW_SECONDS) return false;
  if (typeof signature !== 'string' || !signature.startsWith('v1=') || !/^[0-9a-f]*$/i.test(signature.slice(3))) return false;
  const expected = Buffer.from(signCallback({ secret, timestamp, path, body: rawBody, environment }).slice(3), 'hex');
  const provided = Buffer.from(signature.slice(3), 'hex');
  return expected.length === provided.length && timingSafeEqual(expected, provided);
}

// behavior.start: 'proceed'(기본) | 'skip' | 상태 코드. behavior.complete·failed: 204(기본) | 상태 코드.
// behavior는 시험 도중 바꿀 수 있도록 같은 객체를 그대로 읽는다.
export async function startMockBackend({ secret, environment, port = 0, host = '127.0.0.1', behavior = {}, now = Date.now, onRequest = () => {} } = {}) {
  const requests = [];
  const recipeIds = new Map();

  const server = createServer((req, res) => {
    const chunks = [];
    req.on('data', (chunk) => chunks.push(chunk));
    req.on('end', () => {
      const rawBody = Buffer.concat(chunks);
      const respond = (status, payload) => {
        if (payload === undefined) {
          res.writeHead(status, { 'Content-Length': '0' }).end();
        } else {
          const text = JSON.stringify(payload);
          res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(text) }).end(text);
        }
      };

      const url = new URL(req.url, 'http://mock');
      const match = ROUTE.exec(url.pathname);
      if (req.method !== 'POST' || !match || url.search) {
        respond(req.method !== 'POST' && match ? 405 : 404);
        return;
      }
      const [, probeId, action] = match;
      const signatureValid = verifySignature({
        secret,
        environment,
        path: url.pathname,
        rawBody,
        timestamp: req.headers['x-chalkak-callback-timestamp'],
        signature: req.headers['x-chalkak-callback-signature'],
        nowSeconds: Math.floor(now() / 1000),
      });
      let body = null;
      if (rawBody.length > 0) {
        try {
          body = JSON.parse(rawBody.toString('utf8'));
        } catch {
          body = null;
        }
      }
      const record = { action, probeId, signatureValid, body, rawBody, contentType: req.headers['content-type'] ?? null };
      requests.push(record);
      onRequest(record);
      if (!signatureValid) {
        respond(401, { message: '유효하지 않은 이미지 처리 콜백입니다.' });
        return;
      }

      const configured = behavior[action];
      if (action === 'start') {
        if (typeof configured === 'number') return respond(configured);
        if (configured === 'skip') return respond(200, { proceed: false, recipeId: null });
        if (!recipeIds.has(probeId)) recipeIds.set(probeId, randomUUID());
        return respond(200, { proceed: true, recipeId: recipeIds.get(probeId) });
      }
      return respond(typeof configured === 'number' ? configured : 204);
    });
  });

  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, host, resolve);
  });
  const { port: boundPort } = server.address();

  return {
    port: boundPort,
    baseUrl: `http://${host}:${boundPort}/internal/v1`,
    requests,
    recipeIds,
    close: () => new Promise((resolve) => {
      server.closeAllConnections();
      server.close(resolve);
    }),
  };
}

function parseCliOptions(argv) {
  const options = {};
  for (let i = 0; i < argv.length; i += 2) {
    const name = argv[i]?.replace(/^--/, '');
    const value = argv[i + 1];
    if (!['environment', 'port', 'start', 'complete', 'failed'].includes(name) || value === undefined) {
      throw new Error(`알 수 없는 인자입니다: ${argv[i]}`);
    }
    options[name] = value;
  }
  return options;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const options = parseCliOptions(process.argv.slice(2));
  const secret = process.env.IMAGE_PROCESSING_API_SECRET;
  if (!secret || !options.environment) {
    console.error('IMAGE_PROCESSING_API_SECRET 환경 변수와 --environment가 필요합니다.');
    process.exit(2);
  }
  const asBehavior = (value) => (value === undefined || value === 'skip' ? value : Number(value));
  const behavior = { start: asBehavior(options.start), complete: asBehavior(options.complete), failed: asBehavior(options.failed) };
  const onRequest = ({ rawBody, ...record }) => {
    console.log(JSON.stringify({ time: new Date().toISOString(), environment: options.environment, ...record }));
  };
  const mock = await startMockBackend({ secret, environment: options.environment, port: Number(options.port ?? 0), behavior, onRequest });
  console.log(JSON.stringify({ event: 'mock_backend_started', environment: options.environment, baseUrl: mock.baseUrl }));
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => process.exit(0));
}
