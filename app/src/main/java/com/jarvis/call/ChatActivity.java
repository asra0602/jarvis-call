package com.jarvis.call;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Chat lokal v1. Pesan hanya tampil di app ini dan tidak dikirim
 * ke mana pun. Balasan "Jarvis" di v1 adalah teks simulasi, bukan
 * jawaban Muse yang sebenarnya — itu butuh backend di tahap berikutnya.
 */
public class ChatActivity extends AppCompatActivity {

    private LinearLayout messagesLayout;
    private ScrollView scrollView;
    private EditText inputText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        messagesLayout = findViewById(R.id.messages_layout);
        scrollView = findViewById(R.id.messages_scroll);
        inputText = findViewById(R.id.input_text);
        Button sendButton = findViewById(R.id.btn_send);

        addMessage("Jarvis (simulasi v1)",
                "Halo, ini layar chat di app Jarvis Call. "
                        + "Di v1 ini chat masih lokal — belum terhubung ke Muse. "
                        + "Coba kirim pesan, nanti aku balas dengan teks simulasi.");

        sendButton.setOnClickListener(v -> {
            String text = inputText.getText().toString().trim();
            if (text.isEmpty()) {
                return;
            }
            inputText.setText("");
            addMessage("Kamu", text);
            addMessage("Jarvis (simulasi v1)",
                    "Pesanmu sudah kuterima di app (lokal v1): \"" + text + "\". "
                            + "Untuk jawaban Muse beneran, app ini nanti perlu backend / FCM — itu tahap berikutnya.");
        });
    }

    private void addMessage(String sender, String body) {
        TextView view = new TextView(this);
        String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        view.setText(sender + " · " + time + "\n" + body);
        view.setTextSize(16);
        view.setPadding(16, 16, 16, 16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        view.setLayoutParams(params);
        messagesLayout.addView(view);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
