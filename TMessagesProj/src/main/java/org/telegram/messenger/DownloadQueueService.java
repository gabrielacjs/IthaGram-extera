package org.telegram.messenger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Servicio en primer plano de la cola de descargas: muestra una notificacion fija
 * (con boton Detener) mientras la cola descarga, para que el sistema no cierre la app.
 */
public class DownloadQueueService extends Service {

    public static final String EXTRA_TEXT = "text";
    public static final String ACTION_STOP = "org.telegram.messenger.DOWNLOAD_QUEUE_STOP";

    private static final int NOTIFICATION_ID = 77210;
    private static final String CHANNEL_ID = "download_queue";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        showNotification(DownloadQueueManager.tr("Descargando...", "Downloading..."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            AndroidUtilities.runOnUIThread(() -> DownloadQueueManager.getInstance().stop());
            stopSelf();
            return START_NOT_STICKY;
        }
        String text = intent != null ? intent.getStringExtra(EXTRA_TEXT) : null;
        if (text != null) {
            showNotification(text);
        }
        return START_NOT_STICKY;
    }

    private void showNotification(String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        DownloadQueueManager.tr("Cola de descargas", "Download queue"), NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(channel);
            }
            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            Intent stopIntent = new Intent(this, DownloadQueueService.class).setAction(ACTION_STOP);
            PendingIntent stopPending = PendingIntent.getService(this, 0, stopIntent, piFlags);
            Intent openIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            PendingIntent openPending = openIntent != null ? PendingIntent.getActivity(this, 1, openIntent, piFlags) : null;

            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CHANNEL_ID)
                    : new Notification.Builder(this);
            builder.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(DownloadQueueManager.tr("Cola de descargas", "Download queue"))
                    .setContentText(text)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .addAction(0, DownloadQueueManager.tr("Detener", "Stop"), stopPending);
            if (openPending != null) {
                builder.setContentIntent(openPending);
            }
            Notification notification = builder.build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
