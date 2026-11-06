package com.jarvis.call;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.os.Build;
import android.provider.Settings;

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
        showIncomingCallNotification(context, "notifikasi full-screen",
                "Jarvis memanggil",
                "Ketuk untuk menjawab — atau tunggu layar panggilan muncul.");
    }

    public static void showIncomingCallNotification(Context context, String source,
                                                    String title, String text) {
        // Overlay dulu: kalau izin "Tampil di atas aplikasi lain" sudah
        // diberikan, buka CallActivity langsung di atas aplikasi apa pun
        // yang sedang dibuka. Ini yang membuat panggilan menimpa layar,
        // bukan cuma jadi notifikasi. Kalau izin belum ada / gagal dibuka,
        // jatuh ke notifikasi full-screen seperti biasa.
        if (tryDirectLaunch(context, source)) {
            return;
        }
        createChannel(context);

        Intent intent = new Intent(context, CallActivity.class);
        intent.putExtra(CallActivity.EXTRA_SOURCE, source);
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
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setFullScreenIntent(fullScreenIntent, true)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                .setVibrate(new long[]{0, 500, 500, 500});

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
    }

    /** Izin overlay ("Tampil di atas aplikasi lain") sudah diberikan? */
    public static boolean hasOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return Settings.canDrawOverlays(context);
    }

    /**
     * Coba buka CallActivity langsung (overlay). Mengembalikan true kalau
     * activity berhasil diluncurkan. Aplikasi dengan izin SYSTEM_ALERT_WINDOW
     * dikecualikan dari larangan membuka activity dari background, jadi
     * cara ini jalan dari FCM service / worker sekalipun.
     */
    private static boolean tryDirectLaunch(Context context, String source) {
        if (!hasOverlayPermission(context)) {
            return false;
        }
        try {
            Intent intent = new Intent(context, CallActivity.class);
            intent.putExtra(CallActivity.EXTRA_SOURCE, source);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
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
