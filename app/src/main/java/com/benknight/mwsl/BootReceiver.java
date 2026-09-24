package com.benknight.mwsl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if (Prefs.trackingEnabled(context)) {
            Intent s = new Intent(context, (Class<?>) TrackingService.class).setAction("com.benknight.mwsl.START");
            context.startForegroundService(s);
        }
    }
}
