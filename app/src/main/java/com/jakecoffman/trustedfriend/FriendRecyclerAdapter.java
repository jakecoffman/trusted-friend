package com.jakecoffman.trustedfriend;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.jakecoffman.trustedfriend.models.LocationRequest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FriendRecyclerAdapter extends RecyclerView.Adapter<FriendRecyclerAdapter.ViewHolder> {
    private MyApplication mApp;
    private String mFriendId;
    private String mFriendEmail;

    private SimpleDateFormat sdf = new SimpleDateFormat("LLL dd yyyy hh:mm:ss a", Locale.US);

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public View mTextView;
        public Location currentItem;

        public ViewHolder(View v) {
            super(v);
            v.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Context context = mTextView.getContext();
                    Intent intent = new Intent(context, MapsActivity.class);
                    intent.putExtra("latitude", currentItem.getLocation().getLatitude()); // TODO: how to pass whole object?
                    intent.putExtra("longitude", currentItem.getLocation().getLongitude()); // TODO: how to pass whole object?
                    context.startActivity(intent);
                }
            });
            mTextView = v;
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public FriendRecyclerAdapter(MyApplication app, String friendId, String friendEmail) {
        mApp = app;
        mFriendId = friendId;
        mFriendEmail = friendEmail;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public FriendRecyclerAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                               int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_request, parent, false);
        // set the view's size, margins, paddings and layout parameters
        // ...
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        Location lr = mApp.getLocations(mFriendId).get(position);
        String sb;
        if (lr.getGeocode() != null && !lr.getGeocode().equals("")) {
            sb = lr.getGeocode();
        } else {
            sb = String.valueOf(lr.getLocation().getLatitude()) +
                    ", " +
                    lr.getLocation().getLongitude();
        }
        ((TextView) holder.mTextView.findViewById(R.id.request_info)).setText(sb);
        ((TextView) holder.mTextView.findViewById(R.id.request_date_text)).setText(sdf.format(new Date(lr.getRequestDate() * 1000)));
        if (lr.getFrom().equals(mFriendId)) {
            ((ImageView) holder.mTextView.findViewById(R.id.coming_or_going)).setImageResource(R.drawable.ic_call_received_black_24dp);
        } else {
            ((ImageView) holder.mTextView.findViewById(R.id.coming_or_going)).setImageResource(R.drawable.ic_call_made_black_24dp);
        }

        holder.currentItem = mApp.getLocations(mFriendId).get(position);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mApp.getLocations(mFriendId).size();
    }
}
