package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;

/**
 * IthaGram: reinicia la app. Esta pantalla vive en OTRO proceso (ver AndroidManifest): mata el proceso principal y
 * vuelve a abrir la app, asi funciona aunque el telefono bloquee que una app se abra a si misma en segundo plano.
 */
public class IthaRestartActivity extends Activity {

    private static final String EXTRA_NEXT = "itha_next";
    private static final String EXTRA_PID = "itha_pid";

    /** Reinicia la app completa. Llamar con la pantalla actual (la app tiene que estar visible). */
    public static void restart(Activity from) {
        try {
            if (from == null) {
                return;
            }
            Context c = from.getApplicationContext();
            Intent next = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
            if (next == null) {
                return;
            }
            next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            Intent intent = new Intent(c, IthaRestartActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            intent.putExtra(EXTRA_NEXT, next);
            intent.putExtra(EXTRA_PID, Process.myPid());
            from.startActivity(intent);
        } catch (Throwable ignore) {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent in = getIntent();
            Intent next = in.getParcelableExtra(EXTRA_NEXT);
            int pid = in.getIntExtra(EXTRA_PID, -1);
            if (pid > 0 && pid != Process.myPid()) {
                Process.killProcess(pid);
            }
            if (next != null) {
                startActivity(next);
            }
        } catch (Throwable ignore) {
        }
        finish();
        Runtime.getRuntime().exit(0);
    }
}
