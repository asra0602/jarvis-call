package com.jarvis.call;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.messaging.FirebaseMessaging;

import java.util.concurrent.TimeUnit;

/**
 * Layar utama v1.
 * <p>
 * Tiga aksi: simulasi panggilan langsung, tes notifikasi panggilan
 * full-screen, dan buka chat lokal. Tidak ada koneksi ke server di v1.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATIONS = 1001;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.status_text);
        Button simulateButton = findViewById(R.id.btn_simulate_call);
        Button notifyButton = findViewById(R.id.btn_notify_call);
        Button chatButton = findViewById(R.id.btn_open_chat);

        simulateButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, CallActivity.class);
            intent.putExtra(CallActivity.EXTRA_SOURCE, "simulasi langsung");
            startActivity(intent);
        });

        notifyButton.setOnClickListener(v -> {
            if (!hasNotificationPermission()) {
                requestNotificationPermission();
                return;
            }
            CallNotificationHelper.showIncomingCallNotification(this);
            Toast.makeText(this,
                    "Notifikasi panggilan dikirim. Kalau layar panggilan tidak muncul sendiri, ketuk notifikasinya.",
                    Toast.LENGTH_LONG).show();
        });

        chatButton.setOnClickListener(v ->
                startActivity(new Intent(this, ChatActivity.class)));

        // Worker latar belakang: cek backend tiap 15 menit (minimum Android)
        // untuk pesan type="call" dari Jarvis, lalu tampilkan layar panggilan.
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest callCheck = new PeriodicWorkRequest.Builder(
                CallCheckWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "jarvis-call-check", ExistingPeriodicWorkPolicy.KEEP, callCheck);

        // Daftarkan token FCM perangkat ini ke backend, supaya backend
        // bisa mengirim panggilan selesai instan lewat FCM.
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    JarvisMessagingService.sendTokenToBackend(task.getResult());
                }
            });
        } catch (Exception ignored) {
            // Tanpa FCM pun worker 15 menit tetap jalan sebagai fallback.
        }

        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        String status = "Izin notifikasi: "
                + (hasNotificationPermission() ? "AKTIF" : "belum — ketuk Tes Notifikasi untuk meminta izin")
                + "\n\nPanggilan selesai: AKTIF (worker cek backend tiap 15 menit; "
                + "saat Jarvis mengirim pesan type=call, layar panggilan akan muncul). "
                + "Bukan instan seperti FCM — itu tahap berikutnya kalau mau seketika.";
        statusText.setText(status);
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                CallNotificationHelper.showIncomingCallNotification(this);
            } else {
                Toast.makeText(this,
                        "Izin notifikasi ditolak, jadi tes notifikasi tidak bisa jalan. "
                                + "Pakai tombol Simulasi Panggilan saja untuk sementara.",
                        Toast.LENGTH_LONG).show();
            }
            refreshStatus();
        }
    }
}
