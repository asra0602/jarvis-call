package com.jarvis.call;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Worker latar belakang untuk "Jarvis menelepon saat pekerjaan selesai".
 * <p>
 * Dijadwalkan berkala tiap 15 menit (batas minimum Android untuk
 * PeriodicWork) oleh MainActivity. Setiap jalan, worker mengambil pesan
 * baru dari backend sejak id terakhir yang sudah dilihat. Kalau ada
 * pesan dari jarvis dengan type="call", worker menampilkan notifikasi
 * panggilan full-screen — jadi Asra "ditelepon" saat ada pekerjaan
 * yang selesai, tanpa harus membuka aplikasi.
 * <p>
 * Ini BUKAN FCM instan: jedanya sampai 15 menit, dan Android bisa
 * menundanya lagi saat battery optimization aktif. FCM adalah tahap
 * berikutnya kalau mau seketika.
 */
public class CallCheckWorker extends Worker {

    private static final String BACKEND = "https://jarvis-call-backend.vercel.app/api/messages";
    private static final String PREFS = "jarvis-call";
    private static final String KEY_LAST_SEEN = "last_call_seen_id";

    public CallCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            SharedPreferences prefs = getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            int lastSeen = prefs.getInt(KEY_LAST_SEEN, 0);

            URL url = new URL(BACKEND + "?since=" + lastSeen);
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
                return Result.retry();
            }
            JSONArray arr = json.optJSONArray("messages");
            if (arr == null) {
                return Result.success();
            }
            int maxId = lastSeen;
            boolean foundCall = false;
            String callText = "";
            for (int i = 0; i < arr.length(); i++) {
                JSONObject m = arr.getJSONObject(i);
                int id = m.optInt("id", 0);
                if (id > maxId) {
                    maxId = id;
                }
                if ("jarvis".equals(m.optString("from", ""))
                        && "call".equals(m.optString("type", "chat"))) {
                    foundCall = true;
                    callText = m.optString("text", "");
                }
            }
            // Tandai semua pesan sudah dilihat, supaya panggilan yang sama
            // tidak muncul berulang kali di polling berikutnya.
            prefs.edit().putInt(KEY_LAST_SEEN, maxId).apply();

            if (foundCall) {
                CallNotificationHelper.showIncomingCallNotification(
                        getApplicationContext(),
                        "pemberitahuan selesai",
                        "Jarvis memanggil — pekerjaan selesai",
                        callText.isEmpty() ? "Ketuk untuk menjawab." : callText);
            }
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
