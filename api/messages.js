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
      if (!from || !text) {
        return res.status(400).json({ ok: false, error: 'from and text are required' });
      }
      const list = await load();
      const nextId = list.length ? Math.max(...list.map((m) => m.id)) + 1 : 1;
      const message = { id: nextId, from, text, at: new Date().toISOString() };
      list.push(message);
      const trimmed = list.slice(-200);
      await save(trimmed);
      return res.status(200).json({ ok: true, message });
    }

    res.setHeader('Allow', 'GET, POST');
    return res.status(405).json({ ok: false, error: 'Method not allowed' });
  } catch (e) {
    return res.status(500).json({ ok: false, error: 'backend error', storage: 'error' });
  }
}
