package com.memetaillab.beta1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context c, Intent i) {
        boolean protect = false;
        try {
            protect = !new Db(c).openLivePositions().isEmpty();
        } catch (Exception e) {
        }
        boolean track = Prefs.tracking(c);
        if (track || protect) {
            String action = track ? "com.memetaillab.beta1.START" : "com.memetaillab.beta1.PROTECT";
            Intent s = new Intent(c, (Class<?>) TrackerService.class).setAction(action);
            c.startForegroundService(s);
        }
    }
}
