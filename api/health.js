export default function handler(req, res) {
  res.status(200).json({
    ok: true,
    service: 'jarvis-call-backend',
    version: '0.1-prototype',
    note: 'Health check only. Chat persistence (Vercel KV / database) is the next step, not yet wired in.',
  });
}
