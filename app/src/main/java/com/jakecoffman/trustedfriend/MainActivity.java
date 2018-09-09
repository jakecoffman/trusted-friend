package com.jakecoffman.trustedfriend;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private EditText mEmailEditText;
    private AlertDialog mAlertDialog;
    private final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0;
    private MyApplication mApp;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView.Adapter mRecyclerAdapter;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mApp = (MyApplication) getApplication();
        mEmailEditText = new EditText(this);
        mEmailEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        mAlertDialog = new AlertDialog.Builder(this)
                .setTitle("Add friend")
                .setMessage("Enter friend's email to add them")
                .setView(mEmailEditText)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        new AddFriendTask().execute(mEmailEditText.getText().toString());
                        Snackbar.make(findViewById(android.R.id.content), "Friend request sent", Snackbar.LENGTH_LONG)
                                .setAction("Action", null)
                                .show();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Nothing
                    }
                }).create();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mEmailEditText.selectAll();
                mEmailEditText.setHint("email@example.com");
                mAlertDialog.show();
            }
        });

        Intent intent = new Intent(this, RegistrationIntentService.class);
        startService(intent);

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.my_recycler);
        recyclerView.setHasFixedSize(true);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, R.drawable.divider));
//        recyclerView.addItemDecoration(new VerticalSpaceItemDecoration(8));
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        mRecyclerAdapter = new MainRecyclerAdapter(mApp);
        recyclerView.setAdapter(mRecyclerAdapter);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.refresh);
        // Setup refresh listener which triggers new data loading
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new ListFriendsTask().execute();
            }
        });

        mSwipeRefreshLayout.setRefreshing(true);
        new ListFriendsTask().execute();

        // Configure the refreshing colors
        mSwipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || needsBatteryOptimizationDisable()) {
            // Should we show an explanation?
//            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                explainYouself();
//            } else {
                // No explanation needed, we can request the permission.
//                makePermissionRequests();
//            }
        }
    }

    public void explainYouself() {
        new AlertDialog.Builder(this)
                .setTitle("Location Permission")
                .setMessage("On the next popups, grant this app permission to Location " + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? "and Disable Battery Optimization " : "") + "or it won't work. See help for more info.")
//                        .setView(mEmailEditText)
                .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        makePermissionRequests();
                    }
                })
                .create()
                .show();
    }

    public boolean needsBatteryOptimizationDisable() {
        return !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ((PowerManager) getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()));
    }

    @SuppressLint("BatteryLife")
    public void makePermissionRequests() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
        if (needsBatteryOptimizationDisable()) {
            Intent intent = new Intent();
            // App's core function is scheduling automated actions, such as for [...] location actions.
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
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

            case R.id.action_help:
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jakecoffman/trusted-friend/wiki"));
                startActivity(browserIntent);
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

    private class ListFriendsTask extends AsyncTask<Void, Integer, List<Friend>> {
        ManagedChannel mChannel;

        @Override
        protected List<Friend> doInBackground(Void ...stuff) {
            long now = new Date().getTime();
            try {
                Pair<ManagedChannel, TrustedFriendGrpc.TrustedFriendBlockingStub> pair = mApp.summonChannel();
                mChannel = pair.first;
                FriendsResponse response = pair.second.listFriends(FriendsRequest.newBuilder().build());
                Log.d(TAG, "Round trip time took " + Long.toString(new Date().getTime() - now));
                return response.getFriendsList();
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
        protected void onPostExecute(List<Friend> list) {
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
            mApp.setFriendships(list);
            mRecyclerAdapter.notifyDataSetChanged();
        }
    }

    private class AddFriendTask extends AsyncTask<String, Integer, Boolean> {
        final String TAG = "AddFriendTask";
        ManagedChannel mChannel;

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                Pair<ManagedChannel, TrustedFriendGrpc.TrustedFriendBlockingStub> pair = mApp.summonChannel();
                mChannel = pair.first;
                AddResponse r = pair.second.addFriend(AddRequest.newBuilder().setEmail(params[0]).build());
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
