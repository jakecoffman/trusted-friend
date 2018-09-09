package com.jakecoffman.trustedfriend;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;

// handles results from getting a location
public class MyLocationListener implements LocationListener {
    private static final String TAG = "MyLocationListener";
    private static final int TWO_MINUTES = 1000 * 60 * 2;
    private String mRequestId;
    private LocationManager mLocationManager;
    private MyApplication mApp;

    MyLocationListener(String requestId, LocationManager locman, MyApplication app) {
        mRequestId = requestId;
        mLocationManager = locman;
        mApp = app;
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "Sending current location back");
        String locationProvider = LocationManager.GPS_PROVIDER;
        Location lastKnownLocation = null;
        try {
            lastKnownLocation = mLocationManager.getLastKnownLocation(locationProvider);
        } catch (SecurityException e) {
            Log.d(TAG, "uggg!", e);
        }

        if (lastKnownLocation != null && isBetterLocation(lastKnownLocation, location)) {
            location = lastKnownLocation;
        }

        new LocationResponseTask().execute(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {
        // TODO: Respond with an error
        Log.e(TAG, "provider is disabled: " + provider);
    }

    /**
     * Determines whether one Location reading is better than the current Location fix
     *
     * @param location            The new Location that you want to evaluate
     * @param currentBestLocation The current Location fix, to which you want to compare the new one
     */
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether two providers are the same
     */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    private class LocationResponseTask extends AsyncTask<Location, Integer, Boolean> {
        final String TAG = "LocationResponseTask";
        ManagedChannel mChannel;

        @Override
        protected Boolean doInBackground(Location... locations) {
            try {
                Pair<ManagedChannel, TrustedFriendGrpc.TrustedFriendBlockingStub> pair = mApp.summonChannel();
                mChannel = pair.first;
                LatLng ll = LatLng.newBuilder().setLatitude(locations[0].getLatitude()).setLongitude(locations[0].getLongitude()).build();
                MyLocationResponse r = pair.second.myLocation(MyLocationRequest.newBuilder().setRequestId(mRequestId).setLocation(ll).build());
                return true;
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                pw.flush();
                Log.e(TAG, sw.toString());
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean ok) {
            super.onPostExecute(ok);
            try {
                mChannel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
