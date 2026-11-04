// Chat bridge Jarvis Call, deployed on Vercel (project jarvis-call-backend).
// Storage: Vercel KV (Upstash REST) when KV_REST_API_URL + KV_REST_API_TOKEN
// are linked to the project; otherwise it falls back to in-memory storage
// and says so in the response, so a missing KV link is visible, not silent.
// Auth note (honest limitation, 2026-11-04): the Android app is built from
// a PUBLIC repo, so a secret embedded in the app would not be a secret.
// This endpoint therefore does not pretend to be authenticated yet.
// Do not send sensitive personal data through it; proper user login is
// a later stage. No tokens, phone numbers, or secrets are stored here.
const KV_URL = process.env.KV_REST_API_URL;
const KV_TOKEN = process.env.KV_REST_API_TOKEN;
const KEY = 'jarvis-messages';
let memoryMessages = [];
let memoryNextId = 1;

async function kvGet() {
  const r = await fetch(`${KV_URL}/get/${KEY}`, {
    headers: { Authorization: `Bearer ${KV_TOKEN}` },
  });
  if (!r.ok) throw new Error('kv get failed');
  const data = await r.json();
  return data.result ? JSON.parse(data.result) : [];
}

async function kvSet(list) {
  const r = await fetch(`${KV_URL}/set/${KEY}/${encodeURIComponent(JSON.stringify(list))}`, {
    headers: { Authorization: `Bearer ${KV_TOKEN}` },
  });
  if (!r.ok) throw new Error('kv set failed');
}

async function load() {
  if (KV_URL && KV_TOKEN) return kvGet();
  return memoryMessages;
}

async function save(list) {
  if (KV_URL && KV_TOKEN) return kvSet(list);
  memoryMessages = list;
}

// Kirim FCM instan untuk pesan type="call". Kredensial service account
// dibaca dari env FIREBASE_SERVICE_ACCOUNT_JSON (disetel di Vercel, bukan
// di repo). JWT RS256 dibuat dengan Node crypto bawaan, tanpa dependensi.
// Kalau env/token tidak ada atau FCM gagal, fungsi ini mengembalikan
// false dan worker 15 menit tetap menjadi fallback — pesan tetap tersimpan.
async function sendFcmCall(title, body) {
  try {
    const saJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
    if (!saJson || !KV_URL || !KV_TOKEN) return false;
    const sa = JSON.parse(saJson);
    if (!sa.project_id || !sa.client_email || !sa.private_key) return false;
    const tokenRes = await fetch(`${KV_URL}/get/jarvis-fcm-token`, {
      headers: { Authorization: `Bearer ${KV_TOKEN}` },
    });
    const tokenData = await tokenRes.json();
    const deviceToken = tokenData.result;
    if (!deviceToken) return false;

    const { createSign } = await import('node:crypto');
    const b64url = (s) => Buffer.from(s).toString('base64url');
    const now = Math.floor(Date.now() / 1000);
    const header = b64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
    const claims = b64url(JSON.stringify({
      iss: sa.client_email,
      sub: sa.client_email,
      aud: 'https://oauth2.googleapis.com/token',
      iat: now,
      exp: now + 3600,
      scope: 'https://www.googleapis.com/auth/firebase.messaging',
    }));
    const unsigned = `${header}.${claims}`;
    const signer = createSign('RSA-SHA256');
    signer.update(unsigned);
    const signature = signer.sign(sa.private_key, 'base64url');
    const jwt = `${unsigned}.${signature}`;

    const oauthRes = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${encodeURIComponent(jwt)}`,
    });
    const oauth = await oauthRes.json();
    if (!oauth.access_token) return false;

    const fcmRes = await fetch(
      `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`,
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${oauth.access_token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          message: {
            token: deviceToken,
            data: { type: 'call', title, body },
            android: { priority: 'high' },
            notification: { title, body },
          },
        }),
      },
    );
    return fcmRes.ok;
  } catch (e) {
    return false;
  }
}

export default async function handler(req, res) {
  try {
    if (req.method === 'GET') {
      const list = await load();
      // Lightweight status for polling (no message text): ?count=1
      if (req.query.count === '1') {
        const last = list[list.length - 1] || null;
        return res.status(200).json({
          ok: true,
          storage: KV_URL && KV_TOKEN ? 'kv' : 'memory',
          count: list.length,
          lastId: last ? last.id : 0,
          lastFrom: last ? last.from : null,
          lastType: last ? (last.type || 'chat') : null,
        });
      }
      const since = Number(req.query.since || 0);
      return res.status(200).json({
        ok: true,
        storage: KV_URL && KV_TOKEN ? 'kv' : 'memory',
        messages: list.filter((m) => m.id > since).slice(-50),
      });
    }

    if (req.method === 'POST') {
      const body = typeof req.body === 'object' && req.body ? req.body : {};
      const from = String(body.from || '').slice(0, 32);
      const text = String(body.text || '').slice(0, 2000);
      // type: "chat" (default) or "call" — a "call" message from jarvis is a
      // completion call: the app's background worker shows the call screen
      // for it, so Asra gets "called" when work is done.
      const type = body.type === 'call' ? 'call' : 'chat';
      if (!from || !text) {
        return res.status(400).json({ ok: false, error: 'from and text are required' });
      }
      const list = await load();
      const nextId = list.length ? Math.max(...list.map((m) => m.id)) + 1 : 1;
      const message = { id: nextId, from, text, type, at: new Date().toISOString() };
      list.push(message);
      const trimmed = list.slice(-200);
      await save(trimmed);
      // Pesan type=call dari jarvis juga memicu FCM instan (worker 15 menit
      // tetap jadi fallback kalau FCM tidak terkirim).
      let fcm = null;
      if (type === 'call' && from === 'jarvis') {
        fcm = await sendFcmCall('Jarvis memanggil — pekerjaan selesai', text);
      }
      return res.status(200).json({ ok: true, message, fcm });
    }

    res.setHeader('Allow', 'GET, POST');
    return res.status(405).json({ ok: false, error: 'Method not allowed' });
  } catch (e) {
    return res.status(500).json({ ok: false, error: 'backend error', storage: 'error' });
  }
}
