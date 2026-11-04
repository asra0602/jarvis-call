// Menyimpan token FCM perangkat Asra (satu perangkat) di KV.
// POST {token} saja; tidak ada GET yang mengembalikan token, supaya
// token perangkat tidak terekspos lewat endpoint publik ini.
// Token dipakai server-side oleh api/messages.js saat mengirim FCM
// untuk pesan type="call". Tidak ada rahasia lain di file ini.
const KV_URL = process.env.KV_REST_API_URL;
const KV_TOKEN = process.env.KV_REST_API_TOKEN;
const KEY = 'jarvis-fcm-token';

export default async function handler(req, res) {
  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST');
    return res.status(405).json({ ok: false, error: 'Method not allowed' });
  }
  try {
    const body = typeof req.body === 'object' && req.body ? req.body : {};
    const token = String(body.token || '').slice(0, 500);
    if (!token) {
      return res.status(400).json({ ok: false, error: 'token is required' });
    }
    if (KV_URL && KV_TOKEN) {
      const r = await fetch(`${KV_URL}/set/${KEY}/${encodeURIComponent(token)}`, {
        headers: { Authorization: `Bearer ${KV_TOKEN}` },
      });
      if (!r.ok) throw new Error('kv set failed');
    }
    return res.status(200).json({ ok: true, stored: Boolean(KV_URL && KV_TOKEN) });
  } catch (e) {
    return res.status(500).json({ ok: false, error: 'backend error' });
  }
}
