package com.jarvis.call;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Chat v2: terhubung ke backend Vercel jarvis-call-backend.
 * App mengirim pesan sebagai "user", lalu mem-poll /api/messages
 * setiap 10 detik untuk balasan dari "jarvis" (yang diposting Muse
 * lewat polling 1 menit di sisinya). Bukan chat live: ada jeda
 * store-and-forward. Kalau backend tidak terjangkau, pesan lokal
 * tetap ditampilkan dengan keterangan offline, bukan balasan palsu.
 */
public class ChatActivity extends AppCompatActivity {

    private static final String BACKEND = "https://jarvis-call-backend.vercel.app/api/messages";

    private LinearLayout messagesLayout;
    private ScrollView scrollView;
    private EditText inputText;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Set<Integer> shownIds = new HashSet<>();
    private int lastId = 0;
    private boolean backendOk = false;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            fetchMessages();
            pollHandler.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        messagesLayout = findViewById(R.id.messages_layout);
        scrollView = findViewById(R.id.messages_scroll);
        inputText = findViewById(R.id.input_text);
        Button sendButton = findViewById(R.id.btn_send);

        addMessage("Jarvis",
                "Halo, ini chat Jarvis Call v2. Pesanmu sekarang dikirim ke backend "
                        + "Vercel, dan aku membalasnya lewat polling tiap 1 menit — jadi "
                        + "balasan bisa butuh waktu sebentar, bukan instan.");

        sendButton.setOnClickListener(v -> {
            String text = inputText.getText().toString().trim();
            if (text.isEmpty()) {
                return;
            }
            inputText.setText("");
            postMessage(text);
        });

        pollHandler.post(pollRunnable);
    }

    @Override
    protected void onDestroy() {
        pollHandler.removeCallbacks(pollRunnable);
        super.onDestroy();
    }

    private void postMessage(String text) {
        new Thread(() -> {
            try {
                URL url = new URL(BACKEND);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                JSONObject body = new JSONObject();
                body.put("from", "user");
                body.put("text", text);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code == 200) {
                    backendOk = true;
                    // Jangan tampilkan di sini: fetchMessages() di bawah
                    // akan menampilkan pesan ini sekali, dari server.
                    fetchMessages();
                } else {
                    runOnUiThread(() -> addMessage("Sistem",
                            "Gagal kirim ke backend (HTTP " + code + "). Cek koneksi, lalu kirim lagi."));
                }
            } catch (Exception e) {
                runOnUiThread(() -> addMessage("Sistem",
                        "Backend tidak terjangkau saat ini. Pesan belum terkirim — coba lagi nanti."));
            }
        }).start();
    }

    private void fetchMessages() {
        new Thread(() -> {
            try {
                URL url = new URL(BACKEND + "?since=" + lastId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                conn.disconnect();
                JSONObject json = new JSONObject(sb.toString());
                if (!json.optBoolean("ok", false)) {
                    return;
                }
                backendOk = true;
                JSONArray arr = json.optJSONArray("messages");
                if (arr == null) {
                    return;
                }
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.getJSONObject(i);
                    int id = m.optInt("id", 0);
                    String from = m.optString("from", "");
                    String text = m.optString("text", "");
                    if (id <= 0 || text.isEmpty() || shownIds.contains(id)) {
                        continue;
                    }
                    shownIds.add(id);
                    if (id > lastId) {
                        lastId = id;
                    }
                    String sender = "user".equals(from) ? "Kamu"
                            : "jarvis".equals(from) ? "Jarvis" : from;
                    runOnUiThread(() -> addMessage(sender, text));
                }
            } catch (Exception ignored) {
                // Poll berikutnya akan mencoba lagi; jangan ganggu layar.
            }
        }).start();
    }

    private void addMessage(String sender, String body) {
        TextView view = new TextView(this);
        String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        view.setText(sender + " · " + time + "\n" + body);
        view.setTextSize(14);
        view.setPadding(16, 16, 16, 16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        view.setLayoutParams(params);
        messagesLayout.addView(view);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
