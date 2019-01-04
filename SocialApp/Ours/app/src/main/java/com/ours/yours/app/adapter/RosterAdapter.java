package com.ours.yours.app.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.entity.Friend;

import java.util.ArrayList;
import java.util.List;

public class RosterAdapter extends RecyclerView.Adapter<RosterAdapter.ViewHolder> implements View.OnClickListener{
    private Context mContext;
    private LayoutInflater mInflater;
    private RosterAdapter.ViewHolder mVH;
    private List<Friend> mItems = new ArrayList<Friend>();
    private OnItemClickListener mListener;

    public RosterAdapter(Context context) {
        Logger.d(">>>");
        mContext = context;
        mInflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Logger.d(">>>");
        View view = mInflater.inflate(R.layout.item_third_roster, parent, false);
        mVH = new ViewHolder(view);
        mVH.itemView.setOnClickListener(this);
        return mVH;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Logger.d(">>> position:" + position);
        ViewCompat.setTransitionName(holder.mFriendPhotoView, String.valueOf(position) + "_image");
        ViewCompat.setTransitionName(holder.mFriendTitleView, String.valueOf(position) + "_tv");

        String urlPhoto = mItems.get(position).getUrlPhoto();
        Glide.with(mContext).load(urlPhoto).into(holder.mFriendPhotoView);
        holder.mFriendTitleView.setText(mItems.get(position).getName());
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public void setData(List<Friend> data) {
        if (mItems != null) mItems.clear();
        mItems.addAll(data);
    }

    public void setItemClickListener(OnItemClickListener mListener) {
        this.mListener = mListener;
    }

    /**
     * Called when one of items is clicked, third child home fragment implements the callback
     */
    @Override
    public void onClick(View v) {
        Logger.d(">>> click position:" + mVH.getAdapterPosition());
        if(mListener != null) mListener.onItemClick(mVH.getAdapterPosition());
    }

    public interface OnItemClickListener {
        public void onItemClick(int position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder{
        private ImageView mFriendPhotoView;
        private TextView mFriendTitleView;

        public ViewHolder(View itemView) {
            super(itemView);
            mFriendPhotoView = itemView.findViewById(R.id.imgFriendPhoto);
            mFriendTitleView = itemView.findViewById(R.id.tvFriendName);
        }
    }
}
