package com.habla.espanol;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Posts a daily reminder only when the user's daily goal is still unmet, and keeps re-arming itself. */
public class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL = "habla_reminders";
    private static final int REMINDER_REQUEST = 201;

    @Override public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences("habla_progress", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("reminders_enabled", true)) return;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        int goal = Math.max(1, prefs.getInt("daily_goal", 1));
        int done = prefs.getString("daily_date", "").equals(today) ? prefs.getInt("daily_lessons", 0) : 0;
        if (done < goal) post(context, goal - done);
    }

    static void scheduleNext(Context context) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 20);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        PendingIntent pi = pending(context, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarm.cancel(pi);
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    static void cancel(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) {
            PendingIntent pi = pending(context, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) alarm.cancel(pi);
        }
    }

    private static PendingIntent pending(Context context, int flags) {
        return PendingIntent.getBroadcast(context, REMINDER_REQUEST, new Intent(context, ReminderReceiver.class), flags);
    }

    private static void post(Context context, int remaining) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 33 && !manager.areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(CHANNEL, "تذكير الهدف اليومي", NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("¡Habla! وقت المراجعة 🎯")
                .setContentText("باقي " + remaining + " درس لتحقيق هدف اليوم. درس واحد يكمل سلسلتك.")
                .setAutoCancel(true);
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        builder.setContentIntent(PendingIntent.getActivity(context, 202, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.notify(202, builder.build());
    }
}
