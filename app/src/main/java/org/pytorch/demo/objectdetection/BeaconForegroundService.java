package org.pytorch.demo.objectdetection;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class BeaconForegroundService extends Service {

    private static final int NOTIFICATION_ID = 123;
    private static final String NOTIFICATION_CHANNEL_ID = "BeaconConnectionChannel";
    private static final String NOTIFICATION_CHANNEL_NAME = "Beacon Connection";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Beacon Connection")
                .setContentText("Beacon Connection Service is running")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        // 이곳에 연결 상태 확인 및 관련 작업을 수행하는 코드를 추가합니다.

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
