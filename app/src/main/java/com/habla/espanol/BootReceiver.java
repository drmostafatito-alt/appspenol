package com.habla.espanol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && context.getSharedPreferences("habla_progress", Context.MODE_PRIVATE).getBoolean("reminders_enabled", true)) ReminderReceiver.scheduleNext(context);
    }
}
