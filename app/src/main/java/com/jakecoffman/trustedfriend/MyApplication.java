package com.jakecoffman.trustedfriend;

import android.app.Application;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.jakecoffman.trustedfriend.models.LocationRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

public class MyApplication extends Application {
    final String TAG = "MyApplication";

    private boolean mSignedIn;
    private String mIdToken;
    private GoogleSignInOptions mGso;
    private GoogleApiClient mGoogleApiClient;
    List<Friend> mFriendships;
    Map<String, List<Location>> mLocationRequests;

    @Override
    public void onCreate() {
        super.onCreate();
        mSignedIn = false;
        if (mFriendships == null) {
            mFriendships = new ArrayList<>();
        }
        if (mLocationRequests == null) {
            mLocationRequests = new HashMap<>();
        }

        mGso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.server_client_id))
                .requestEmail()
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, mGso)
                .build();
    }

    public boolean isSignedIn() {
        return mSignedIn;
    }

    public GoogleSignInOptions getGoogleSignInOptions() {
        return mGso;
    }

    public void setIdToken(String id) {
        mIdToken = id;
    }

    public String getIdToken() {
        silentSignIn();
        return mIdToken;
    }

    public void setFriendships(List<Friend> friendships) {
        mFriendships = friendships;
    }

    public List<Friend> getFriendships() {
        return mFriendships;
    }

    // Don't call on UI thread
    private void silentSignIn() {
        ConnectionResult result = mGoogleApiClient.blockingConnect();
        if (result.isSuccess()) {
            GoogleSignInResult googleSignInResult =
                    Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient).await();
            if (googleSignInResult.isSuccess()) {
                mSignedIn = true;
                mIdToken = googleSignInResult.getSignInAccount().getIdToken();
            }
        }
    }

    public void signOut() {
        mSignedIn = false;
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        Log.d(TAG, "signOut:onResult:" + status);
                    }
                });
    }

    public void revokeAccess() {
        mSignedIn = false;
        Auth.GoogleSignInApi.revokeAccess(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        Log.d(TAG, "revokeAccess:onResult:" + status);
                    }
                });
    }

    public List<Location> getLocations(String friendId) {
        List<Location> lr = mLocationRequests.get(friendId);
        if (lr == null) {
            return new ArrayList<>();
        }
        return lr;
    }

    public void setLocations(String friendId, List<Location> locationRequests) {
        mLocationRequests.put(friendId, locationRequests);
    }

    public Pair<ManagedChannel, TrustedFriendGrpc.TrustedFriendBlockingStub> summonChannel() {
        ManagedChannel mChannel = ManagedChannelBuilder.forTarget(getString(R.string.grpc_url))
                //.usePlaintext(true) // TODO disable when released
                .build();
        TrustedFriendGrpc.TrustedFriendBlockingStub stub = TrustedFriendGrpc
                .newBlockingStub(mChannel);
        Metadata.Key key = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
        Metadata md = new Metadata();
        md.put(key, "Bearer " + getIdToken());
        stub = MetadataUtils.attachHeaders(stub, md);
        return new Pair<>(mChannel, stub);
    }
}
