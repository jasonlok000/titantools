package com.everyonesniche.titantools.helper;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

/**
 * A TitanTools helper app: the phone's Shortcut keys picks apps, so each one-press action is a tiny app of its own. Opening it
 * asks TitanTools to do its action (from this activity's meta-data), then closes at once, with no screen.
 */
public final class Go extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        overridePendingTransition(0, 0);
        try {
            ActivityInfo info = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
            Intent intent = new Intent("com.everyonesniche.titantools.action.TRIGGER")
                    .setClassName("com.everyonesniche.titantools", "com.everyonesniche.titantools.quick.TriggerActivity")
                    .putExtra("action", info.metaData.getString("action"))
                    .putExtra("slot", info.metaData.getInt("slot", 0))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Install TitanTools first.", Toast.LENGTH_SHORT).show();
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
