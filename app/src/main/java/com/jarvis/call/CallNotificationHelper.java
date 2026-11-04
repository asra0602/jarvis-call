package com.jarvis.call;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Notifikasi panggilan masuk dengan full-screen intent.
 * <p>
 * Ini jalur resmi Android untuk "seperti ditelepon": channel prioritas
 * tinggi + kategori CALL + full-screen intent ke CallActivity. Apakah
 * layar panggilan muncul sendiri atau hanya sebagai notifikasi, tetap
 * diputuskan sistem Android dan izin full-screen intent di HP pengguna.
 */
public final class CallNotificationHelper {

    public static final String CHANNEL_ID = "jarvis_incoming_call";
    private static final int NOTIFICATION_ID = 9001;

    private CallNotificationHelper() {
    }

    public static void showIncomingCallNotification(Context context) {
        createChannel(context);

        Intent intent = new Intent(context, CallActivity.class);
        intent.putExtra(CallActivity.EXTRA_SOURCE, "notifikasi full-screen");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent fullScreenIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Ketuk notifikasi juga membuka layar yang sama.
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("Jarvis memanggil")
                .setContentText("Ketuk untuk menjawab — atau tunggu layar panggilan muncul.")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setFullScreenIntent(fullScreenIntent, true)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                .setVibrate(new long[]{0, 500, 500, 500});

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
    }

    public static void cancelNotification(Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Panggilan Jarvis",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Notifikasi panggilan masuk dari Jarvis");
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), null);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 500, 500, 500});
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
