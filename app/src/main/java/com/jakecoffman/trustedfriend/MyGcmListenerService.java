package com.jakecoffman.trustedfriend;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;

public class MyGcmListenerService extends GcmListenerService {

    private static final String TAG = "MyGcmListenerService";

    public MyGcmListenerService() {
    }

    @Override
    public void onMessageReceived(String from, Bundle data) {
        String frm = data.getString("frm");

        String type = data.getString("type");
        if (type == null) {
            Log.e(TAG, "type on message not provided");
            return;
        }

        switch (type) {
            case "location-request":
                notify(frm + " requested your current location");

                LocationManager mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
                MyApplication myApp = (MyApplication) getApplication();
                MyLocationListener locationListener = new MyLocationListener(data.getString("id"), mLocationManager, myApp);

                try {
                    mLocationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, Looper.getMainLooper());
                } catch (SecurityException e) {
                    Log.e(TAG, "Failed to get network update", e);
                }
                break;

            case "alert-request":
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                int volume = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                am.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0);
                incessantNotify(frm);
                break;

            default:
                // TODO: TELL SERVER I DIDN"T UNDERSTANDED!
                Log.e(TAG, "message type unknown: " + type);
        }
    }

    private void notify(String message) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
                PendingIntent.FLAG_ONE_SHOT);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_add_location_black_24dp)
                .setContentTitle("Trusted Friend")
                .setContentText(message)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build());
    }

    private void incessantNotify(String message) {
        // TODO: Take you to the friend that requested this
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
                PendingIntent.FLAG_ONE_SHOT);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (defaultSoundUri == null) {
            defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_warning_black_24dp)
                .setContentTitle("Trusted Friend")
                .setContentText(message + " alerted you")
                .setAutoCancel(false)
                .setSound(defaultSoundUri, AudioManager.STREAM_ALARM)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Notification notification = notificationBuilder.build();
        notification.flags |= Notification.FLAG_INSISTENT;

        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build());
    }
}
