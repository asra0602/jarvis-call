// Prototype chat bridge for Jarvis Call, deployed on Vercel.
// WARNING: this store is in-memory per serverless instance, so messages
// do NOT persist reliably across cold starts or redeploys. It exists to
// prove the Vercel deployment pipeline end-to-end. The next step before
// real chat use is a persistent store (Vercel KV or a database) plus a
// shared secret, so chat messages are not public on an unauthenticated URL.
// No tokens, phone numbers, or secrets are stored in this file.
let messages = [];
let nextId = 1;

export default function handler(req, res) {
  if (req.method === 'GET') {
    const since = Number(req.query.since || 0);
    return res.status(200).json({
      ok: true,
      prototype: true,
      messages: messages.filter((m) => m.id > since).slice(-50),
    });
  }

  if (req.method === 'POST') {
    const body = typeof req.body === 'object' && req.body ? req.body : {};
    const from = String(body.from || '').slice(0, 32);
    const text = String(body.text || '').slice(0, 2000);
    if (!from || !text) {
      return res.status(400).json({ ok: false, error: 'from and text are required' });
    }
    const message = { id: nextId++, from, text, at: new Date().toISOString() };
    messages.push(message);
    messages = messages.slice(-200);
    return res.status(200).json({ ok: true, message });
  }

  res.setHeader('Allow', 'GET, POST');
  return res.status(405).json({ ok: false, error: 'Method not allowed' });
}
