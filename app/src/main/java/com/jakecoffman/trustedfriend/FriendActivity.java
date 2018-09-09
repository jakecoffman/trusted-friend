package com.jakecoffman.trustedfriend;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.android.gms.maps.model.LatLng;
import com.jakecoffman.trustedfriend.models.LocationRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

public class FriendActivity extends AppCompatActivity {
    private static final String TAG = "FriendActivity";
    public String mFriendId;
    public String mFriendEmail;
    private MyApplication mApp;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView.Adapter mRecyclerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mApp = (MyApplication) getApplication();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setImageResource(R.drawable.ic_add_location_black_24dp);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new RequestLocationTask(view).execute();
            }
        });

        Bundle extras = getIntent().getExtras();
        mFriendId = extras.getString("friend");
        mFriendEmail = extras.getString("friend-email");

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.my_recycler);
        recyclerView.setHasFixedSize(true);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, R.drawable.divider));
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        mRecyclerAdapter = new FriendRecyclerAdapter(mApp, mFriendId, mFriendEmail);
        recyclerView.setAdapter(mRecyclerAdapter);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.refresh);
        mSwipeRefreshLayout.setRefreshing(true);
        new ListLocationsTask().execute();

        // Setup refresh listener which triggers new data loading
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new ListLocationsTask().execute();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.friend, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_logout:
                mApp.signOut();
                Intent intent = new Intent(getApplicationContext(), SignInActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
                return true;

            case R.id.action_unfriend:
                new UnFriendTask().execute();
                finish();
                return true;

            case R.id.action_alert:
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case DialogInterface.BUTTON_POSITIVE:
                                new AlertRequestTask().execute();
                                break;

                            case DialogInterface.BUTTON_NEGATIVE:
                                break;
                        }
                    }
                };

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Really alert them?")
                        .setPositiveButton("Yes", dialogClickListener)
                        .setNegativeButton("No", dialogClickListener)
                        .show();
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

    private class ListLocationsTask extends AsyncTask<String, Integer, List<Location>> {
        final String TAG = "ListLocationsTask";
        ManagedChannel mChannel;

        @Override
        protected List<Location> doInBackground(String... params) {
            long now = new Date().getTime();
            try {
                Pair<ManagedChannel, TrustedFriendGrpc.TrustedFriendBlockingStub> pair = mApp.summonChannel();
                mChannel = pair.first;
                ListLocationResponse response = pair.second.listLocations(ListLocationRequest.newBuilder()
                        .setFriendId(mFriendId).build());
                Log.d(TAG, "Round trip time took " + Long.toString(new Date().getTime() - now));
                return response.getLocationsList();
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                pw.flush();
                Log.e(TAG, sw.toString());
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<Location> list) {
            super.onPostExecute(list);

            try {
                mChannel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (mSwipeRefreshLayout != null) {
                mSwipeRefreshLayout.setRefreshing(false);
            }
            if (list == null) {
                return;
            }

            mApp.setLocations(mFriendId, list);
            mRecyclerAdapter.notifyDataSetChanged();
        }
    }

    private class AlertRequestTask extends AsyncTask<String, Integer, Boolean> {
        final String TAG = "AlertRequest";
        ManagedChannel mChannel;

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                Pair<ManagedChannel, TrustedFriendGrpc.TrustedFriendBlockingStub> pair = mApp.summonChannel();
                mChannel = pair.first;
                AlertResponse r = pair.second.alert(AlertRequest.newBuilder().setFriendId(mFriendId).build());
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
        protected void onPostExecute(Boolean worked) {
            super.onPostExecute(worked);

            try {
                mChannel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (mSwipeRefreshLayout != null) {
                mSwipeRefreshLayout.setRefreshing(false);
            }
        }

        // TODO: tell them if the alert worked
    }

    private class RequestLocationTask extends AsyncTask<Void, Integer, Boolean> {
        final String TAG = "RequestLocationTask";
        ManagedChannel mChannel;
        View mView;

        RequestLocationTask(View view) {
            mView = view;
        }

        @Override
        protected Boolean doInBackground(Void... unused) {
            try {
                Pair<ManagedChannel, TrustedFriendGrpc.TrustedFriendBlockingStub> pair = mApp.summonChannel();
                mChannel = pair.first;
                AskLocationResponse r = pair.second.askLocation(AskLocationRequest.newBuilder().setFriendId(mFriendId).build());
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
            if (ok) {
                Snackbar.make(mView, "Location requested", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            } else {
                Snackbar.make(mView, "Error requesting", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        }
    }

    private class UnFriendTask extends AsyncTask<Void, Integer, Boolean> {
        final String TAG = "UnFriendTask";
        ManagedChannel mChannel;

        @Override
        protected Boolean doInBackground(Void... unused) {
            try {
                Pair<ManagedChannel, TrustedFriendGrpc.TrustedFriendBlockingStub> pair = mApp.summonChannel();
                mChannel = pair.first;
                RemoveFriendResponse r = pair.second.removeFriend(RemoveFriendRequest.newBuilder().setFriendId(mFriendId).build());
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
