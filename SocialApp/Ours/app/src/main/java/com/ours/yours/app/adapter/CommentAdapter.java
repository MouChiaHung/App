package com.ours.yours.app.adapter;

import android.content.Context;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.base.BaseChildFragment;
import com.ours.yours.app.entity.Comment;

import java.util.Date;
import java.util.List;

/**
 * adapter provides a binding from data set synchronized with firebase to views displayed within a RecyclerView
 */
public class CommentAdapter extends BaseModelAdapter<Comment> {
    public final static int EMPTY_DATA_SET = -20;
    private Context mContext;
    private BaseChildFragment mFragment;
    private LayoutInflater mInflater;
    private ViewHolder mVH;
    private CommentClickListener mCommentClickListener;
    private CommentLoader mCommentLoader;

    public CommentAdapter(Context context, BaseChildFragment fragment) {
        super(context);
        mContext = context;
        mFragment = fragment;
        mInflater = LayoutInflater.from(context);
    }

    /**
     * called when RecyclerView needs a new ViewHolder to represent items
     *
     * a new ViewHolder is constructed with a new View which can represent the items of the given type.
     * by either creating a new View manually or inflating it from an XML layout file.
     *
     * crashes of calling to RecyclerView#notifyDataSetChanged() before this method finish
     */
    @NonNull
    @Override
    public CommentAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Logger.d(">>>");
        View view = mInflater.inflate(R.layout.item_first_comment, parent, false);
        mVH = new ViewHolder(view);
        if (viewType == EMPTY_DATA_SET) {
            Logger.d("... empty data set");
        }
        return mVH;
    }

    /**
     * called when RecyclerView is going to update the holder and display the data at the specific position
     *
     * position is the index of one of items within the adapter's data set
     * 
     * this method will synchronize data set with firebase database if the wanted position is beyond the length of data set
     */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Logger.d(">>> about the position:" + position);
        CommentAdapter.ViewHolder commentViewHolder = (CommentAdapter.ViewHolder) holder;
        if (position >= getItemCount()) {
            Logger.d("... position(" + position + ")" + " >= size of data set(" + getItemCount()+  ")" + " and going to load");
            if (mFragment.isInternetConnectedOrConnecting()) {
                Logger.d("... going to onNeedLoad()");
                mCommentLoader.onNeedLoad(getItemsOfDataSet());
            } else {
                Logger.e("沒網路耶...");
            }
        }
        commentViewHolder.bindData(getItemOfDataSet(position));
    }

    @Override
    public int getItemCount() {
       //Logger.d("... count of data set:" + getCountOfModels() + ", going to return:" + ((getCountOfModels() > 0) ? getCountOfModels() : getCountOfModels()));
        return (super.getItemCount() > 0) ? super.getItemCount() : 0;
    }

    @Override
    public int getItemViewType(int position) {
        if (getItemCount() <= 0) return EMPTY_DATA_SET;
        return super.getItemViewType(position);
    }

    public void setCommentClickListener(CommentClickListener listener) {
        mCommentClickListener = listener;
    }

    public void setCommentLoadListener(CommentLoader loader) {
        mCommentLoader = loader;
    }

    /**
     * callback for triggering responses to UI that a comment item within RecyclerView is clicked
     */
    public interface CommentClickListener {
        void onCommentClick(Comment comment, View item_view);
    }

    /**
     * callback for triggering responses to UI that adapter needs to synchronize data set with firebase
     */
    public interface CommentLoader {
        void onNeedLoad(List<Comment> comments);
    }

    /**
     * a ViewHolder describes an item view and metadata about its place within the RecyclerView
     */
    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView mProfileNameView;
        private TextView mCommentView;
        private ImageView mProfilePhotoView;
        private TextView mMapDistanceView;
        private TextView mDateView;

        public ViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(new OnItemViewClick());
            mProfilePhotoView = itemView.findViewById(R.id.commentProfilePhoto);
            mProfileNameView = itemView.findViewById(R.id.commentProfileName);
            mCommentView = itemView.findViewById(R.id.commentContent);
            mMapDistanceView = itemView.findViewById(R.id.commentMap);
            mDateView = itemView.findViewById(R.id.commentDate);
        }

        public void bindData(Comment comment) {
            if (comment == null) return;
            //comment.printCommentData();
            ViewCompat.setTransitionName(mProfilePhotoView, String.valueOf(getAdapterPosition()) + "_image");
            ViewCompat.setTransitionName(mProfileNameView, String.valueOf(getAdapterPosition()) + "_tv");
            ViewCompat.setTransitionName(mCommentView, String.valueOf(getAdapterPosition()) + "_tv");
            ViewCompat.setTransitionName(mMapDistanceView, String.valueOf(getAdapterPosition()) + "_tv");
            ViewCompat.setTransitionName(mDateView, String.valueOf(getAdapterPosition()) + "_tv");

            String profile_photo = comment.getProfilePhoto();
            Glide.with(mContext)
                    .load(profile_photo)
                    .centerCrop()
                    .override(getSize(mContext).x, getSize(mContext).y)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .crossFade()
                    .error(R.drawable.ic_stub)
                    .into(mProfilePhotoView);
            mProfileNameView.setText(comment.getProfileName());
            mCommentView.setText(comment.getComment());
            mMapDistanceView.setText(String.valueOf(comment.getDistance()) + "km");
            Date now = new Date();
            //Logger.e("... now:" + now.getTime());
            //Logger.e("... comment time:" + comment.getDate());
            long interval_millisec  = now.getTime() - comment.getDate();
            long interval_sec       = interval_millisec/1000;
            long interval_min       = interval_sec/(60);
            long interval_hr        = interval_min/(60);
            long interval_day       = interval_hr/(24);
            //Logger.e("... interval_millisec:" + interval_millisec);
            //Logger.e("... interval_sec:" + interval_sec);
            //Logger.e("... interval_hr:" + interval_hr);
            //Logger.e("... interval_day   time:" + interval_day);
            StringBuffer buffer = new StringBuffer();
            do {
                if (interval_day >= 1) {
                    buffer.append(interval_day);
                    buffer.append("天");
                    buffer.append("前");
                    break;
                }
                if (interval_hr >= 1) {
                    buffer.append(interval_hr);
                    buffer.append("小時");
                    buffer.append("前");
                    break;
                }
                if (interval_min >= 1) {
                    buffer.append(interval_min);
                    buffer.append("分鐘");
                    buffer.append("前");
                    break;
                }
                buffer.append("剛剛");
                break;
            } while (false);
            mDateView.setText(buffer.toString());
        }

        private Point getSize(Context context) {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            return size;
        }

        class OnItemViewClick implements View.OnClickListener {

            @Override
            public void onClick(View v) {
                if(getAdapterPosition() != RecyclerView.NO_POSITION) {
                    Logger.d(">>> click position:" + getAdapterPosition());
                } else {
                    Logger.e("!!! no position");
                }
                if(mCommentClickListener != null) {
                    Comment comment_clicked = getItemOfDataSet(getAdapterPosition());
                    mCommentClickListener.onCommentClick(comment_clicked, v);
                }
            }
        }
    }
}
