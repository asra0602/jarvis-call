package com.jarvis.call;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/**
 * Layar panggilan v1: masuk → dalam panggilan → selesai.
 * <p>
 * Dering memakai ringtone sistem, getar memakai pola berulang.
 * Mute / Speaker di v1 hanya mengubah status tampilan dan, untuk
 * speaker, AudioManager speakerphone — belum ada audio call beneran.
 */
public class CallActivity extends AppCompatActivity {

    public static final String EXTRA_SOURCE = "source";

    private TextView stateText;
    private TextView timerText;
    private TextView sourceText;
    private Button acceptButton;
    private Button declineButton;
    private Button messageButton;
    private LinearLayout ringingControls;
    private LinearLayout inCallControls;
    private float acceptDownY = 0f;
    private Button muteButton;
    private Button speakerButton;
    private Button endButton;
    private Button openChatButton;

    private Ringtone ringtone;
    private Vibrator vibrator;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long callStartMillis = 0;
    private boolean inCall = false;
    private boolean muted = false;
    private boolean speakerOn = false;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!inCall) {
                return;
            }
            long seconds = (System.currentTimeMillis() - callStartMillis) / 1000;
            timerText.setText(String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60));
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        // Tampil di atas lock screen, nyalakan layar, tetap menyala
        // selama panggilan (penting saat dibuka sebagai overlay di atas
        // aplikasi lain).
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        stateText = findViewById(R.id.call_state);
        timerText = findViewById(R.id.call_timer);
        sourceText = findViewById(R.id.call_source);
        acceptButton = findViewById(R.id.btn_accept);
        declineButton = findViewById(R.id.btn_decline);
        messageButton = findViewById(R.id.btn_message);
        ringingControls = findViewById(R.id.ringing_controls);
        inCallControls = findViewById(R.id.in_call_controls);
        muteButton = findViewById(R.id.btn_mute);
        speakerButton = findViewById(R.id.btn_speaker);
        endButton = findViewById(R.id.btn_end);
        openChatButton = findViewById(R.id.btn_open_chat_from_call);

        String source = getIntent().getStringExtra(EXTRA_SOURCE);
        sourceText.setText("Dipicu lewat: " + (source == null ? "tidak diketahui" : source));

        acceptButton.setOnClickListener(v -> acceptCall());
        // Usap ke atas pada tombol hijau juga menerima panggilan (gaya WhatsApp).
        // Ketukan biasa tetap bekerja lewat OnClickListener di atas.
        acceptButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                acceptDownY = event.getY();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (acceptDownY - event.getY() > 100) {
                    acceptCall();
                }
            }
            return false;
        });
        declineButton.setOnClickListener(v -> finishCall("Panggilan ditolak."));
        messageButton.setOnClickListener(v -> {
            startActivity(new Intent(this, ChatActivity.class));
            finish();
        });
        endButton.setOnClickListener(v -> finishCall("Panggilan berakhir."));
        openChatButton.setOnClickListener(v -> {
            startActivity(new Intent(this, ChatActivity.class));
            finish();
        });
        muteButton.setOnClickListener(v -> {
            muted = !muted;
            muteButton.setText(muted ? "Unmute" : "Mute");
            stateText.setText(muted ? "Dalam panggilan — dimute (v1, tampilan saja)"
                    : "Dalam panggilan dengan Jarvis (simulasi)");
        });
        speakerButton.setOnClickListener(v -> {
            speakerOn = !speakerOn;
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setSpeakerphoneOn(speakerOn);
            }
            speakerButton.setText(speakerOn ? "Speaker: ON" : "Speaker");
        });

        startRinging();
    }

    private void startRinging() {
        stateText.setText("Jarvis memanggil…");
        timerText.setText("00:00");
        try {
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), ringtoneUri);
            if (ringtone != null) {
                ringtone.play();
            }
        } catch (Exception ignored) {
            // Tanpa dering pun layar panggilan tetap harus jalan.
        }
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 800, 800, 800};
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception ignored) {
            // Tanpa getar pun layar panggilan tetap harus jalan.
        }
    }

    private void stopRinging() {
        try {
            if (ringtone != null && ringtone.isPlaying()) {
                ringtone.stop();
            }
        } catch (Exception ignored) {
        }
        try {
            if (vibrator != null) {
                vibrator.cancel();
            }
        } catch (Exception ignored) {
        }
    }

    private void acceptCall() {
        stopRinging();
        CallNotificationHelper.cancelNotification(this);
        inCall = true;
        callStartMillis = System.currentTimeMillis();
        stateText.setText("Dalam panggilan dengan Jarvis (simulasi)");
        acceptButton.setVisibility(View.GONE);
        declineButton.setVisibility(View.GONE);
        if (ringingControls != null) {
            ringingControls.setVisibility(View.GONE);
        }
        inCallControls.setVisibility(View.VISIBLE);
        openChatButton.setVisibility(View.VISIBLE);
        timerHandler.post(timerRunnable);
    }

    private void finishCall(String message) {
        stopRinging();
        CallNotificationHelper.cancelNotification(this);
        inCall = false;
        timerHandler.removeCallbacks(timerRunnable);
        stateText.setText(message);
        timerText.setText("");
        acceptButton.setVisibility(View.GONE);
        declineButton.setVisibility(View.GONE);
        if (ringingControls != null) {
            ringingControls.setVisibility(View.GONE);
        }
        inCallControls.setVisibility(View.GONE);
        openChatButton.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        stopRinging();
        timerHandler.removeCallbacks(timerRunnable);
        super.onDestroy();
    }
}
