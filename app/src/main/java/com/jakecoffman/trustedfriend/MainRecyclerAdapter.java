package com.jakecoffman.trustedfriend;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

public class MainRecyclerAdapter extends RecyclerView.Adapter<MainRecyclerAdapter.ViewHolder> {
    private MyApplication mApp;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public static final String TAG = "MyRecyclerAdapter";

        // each data item is just a string in this case
        public View mView;
        public Friend currentItem;
        public String holderIdToken;

        public ViewHolder(View v) {
            super(v);
            v.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Context context = mView.getContext();
                    Intent intent = new Intent(context, FriendActivity.class);
                    intent.putExtra("friend", currentItem.getId()); // TODO: how to pass whole object?
                    intent.putExtra("friend-email", currentItem.getEmail());
                    Log.d(TAG, "Setting " + holderIdToken);
                    context.startActivity(intent);
                }
            });
            mView = v;
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public MainRecyclerAdapter(MyApplication app) {
        mApp = app;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public MainRecyclerAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                             int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_main, parent, false);
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
        TextView nameTextView = (TextView) holder.mView.findViewById(R.id.info_text);
        TextView subtextTextView = (TextView) holder.mView.findViewById(R.id.sub_text);
        ImageView imageView = (ImageView) holder.mView.findViewById(R.id.info_img);

        Friend user = mApp.getFriendships().get(position);
        holder.currentItem = user;
        nameTextView.setText(user.getName());
        subtextTextView.setText(user.getEmail());
        Picasso.with(holder.mView.getContext())
                .load(user.getPicture())
                .into(imageView);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mApp.getFriendships().size();
    }
}