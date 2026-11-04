package com.jarvis.call;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Penerima FCM untuk panggilan selesai instan.
 * <p>
 * Saat backend mengirim pesan data type="call", service ini langsung
 * menampilkan notifikasi panggilan full-screen — tanpa menunggu worker
 * 15 menit. Worker tetap ada sebagai fallback kalau FCM terlewat.
 * Token perangkat dikirim ke backend (/api/tokens) saat token baru,
 * supaya backend tahu ke perangkat mana FCM harus dikirim.
 */
public class JarvisMessagingService extends FirebaseMessagingService {

    private static final String TAG = "JarvisFCM";
    private static final String BACKEND_TOKENS = "https://jarvis-call-backend.vercel.app/api/tokens";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        String type = message.getData().get("type");
        if (!"call".equals(type)) {
            return;
        }
        String title = message.getData().get("title");
        String body = message.getData().get("body");
        if (title == null || title.isEmpty()) {
            title = "Jarvis memanggil — pekerjaan selesai";
        }
        if (body == null || body.isEmpty()) {
            body = "Ketuk untuk menjawab.";
        }
        CallNotificationHelper.showIncomingCallNotification(
                this, "fcm selesai", title, body);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        sendTokenToBackend(token);
    }

    static void sendTokenToBackend(String token) {
        new Thread(() -> {
            try {
                URL url = new URL(BACKEND_TOKENS);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                String body = "{\"token\":\"" + token.replace("\"", "") + "\"}";
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "Gagal kirim token FCM ke backend", e);
            }
        }).start();
    }
}
