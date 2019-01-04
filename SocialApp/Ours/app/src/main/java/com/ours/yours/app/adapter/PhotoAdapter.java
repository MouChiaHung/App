package com.ours.yours.app.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.entity.Card;
import com.ours.yours.app.entity.CardList;
import com.ours.yours.app.entity.Photo;
import com.ours.yours.app.manager.CardManager;
import com.ours.yours.app.ui.fragment.first.child.FirstAddCardFragment;
import com.ours.yours.app.ui.mvp.OurPresenter;
import com.ours.yours.app.ui.mvp.OurView;
import com.ours.yours.app.utils.BitmapHelper;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * adapter provides a binding from data set synchronized with firebase to views displayed within a RecyclerView
 */
public class PhotoAdapter extends BaseModelAdapter<Photo> implements OurPresenter<OurView>{
    public final static int PROGRESS_ON  = 0;
    public final static int PROGRESS_OFF = 1;

    private String mClickedAuthorId; //passed from a picking up on home fragment
    private String mClickedCardId;   //passed from a picking up on home fragment

    private OurView mOurView;
    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private Thread mMVPThread;
    private final Object lock = new Lock();
    private int mResult = BaseModelAdapter.RESULT_FAILURE;

    private Photo mPhotoCache;
    private boolean isRemoveBtnVisible = false;

    private AddPhotoImageOnClickListener mAddPhotoImageOnClickListener;
    private Map<Long, Worker> mWorkers;

    private ImageView.ScaleType mScaleType;

    private int mMaxHeight = -1;
    private int mMinHeight = -1;

    public PhotoAdapter(Context context) {
        super(context);
        mContext = context;
        mLayoutInflater = LayoutInflater.from(mContext);
    }

    public void setRemoveBtnVisible(boolean removeBtnVisible) {
        isRemoveBtnVisible = removeBtnVisible;
    }

    public void setAddPhotoImageOnClickListener(AddPhotoImageOnClickListener listener) {
        mAddPhotoImageOnClickListener = listener;
    }

    public void setScaleType(ImageView.ScaleType scaleType) {
        this.mScaleType = scaleType;
    }

    public void setMaxHeight(int maxHeight) {
        this.mMaxHeight = maxHeight;
    }

    public void setMinHeight(int minHeight) {
        this.mMinHeight = minHeight;
    }

    /**
     * involves inflating a layout from XML and then returning it to the holder
     * when RecyclerView needs a new ViewHolder to represent item
     *
     * a new ViewHolder will be constructed with an inflated View which represents the item
     * by either creating a new View manually or inflating it from an XML layout file.
     */
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (mLayoutInflater == null) return null;
        Logger.d(">>>");
        View item_view = mLayoutInflater.inflate(R.layout.item_photo, parent, false);
        ViewGroup.LayoutParams params = item_view.getLayoutParams();
        //params.height = (int) (parent.getHeight() * 1);
        Logger.d("... item params.width:" + params.width + ", params.height:" + params.height + "\n"
                + " and parent.getWidth():" + parent.getWidth() + ", parent.getHeight():" + parent.getHeight());
        item_view.setLayoutParams(params);
        return new ViewHolder(item_view);
    }

    /**
     * involves populating data into item through holder at the position (which is positioned by layout manager)
     * when RecyclerView displays of updates the UI with the data through the holder referring to the given position
     *
     * position is the index of one of items within the adapter's data set
     */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Logger.d(">>> about the position:" + position);
        ((ViewHolder) holder).bindData(getItemsOfDataSet().get(position));
    }

    /**
     * returns the total count of items in the data set of adapter
     */
    @Override
    public int getItemCount() {
        return super.getItemCount();
    }

    @Override
    public void attach(OurView view) {
        Logger.d(">>>");
        if (view == null) {
            Logger.e("!!! view is null");
            return;
        }
        mOurView = view;
    }

    @Override
    public void detach(OurView view) {
        Logger.d(">>>");
        if (mOurView != view) {
            Logger.e("!!! mOurView doesn't refer to same one as view");
            return;
        }
        if (mOurView == null) {
            return;
        }
        mOurView = null;
    }

    @Override
    public void fetch() {
        Logger.d(">>>");
        if (mMVPThread != null) {
            if (mMVPThread.isAlive()) {
                Logger.d("... previous fetching task is still running but going to interrupt it");
                mMVPThread.interrupt();
            } else {
                mMVPThread = null;
            }
        }
        mMVPThread = new Thread(new FetchTask());
        mMVPThread.start();
    }

    @Override
    public void remove(final int position) {
        if (mOurView == null) {
            Logger.e("!!! mOurView is null");
            return;
        }
        if (getItemCount() <= 0) {
            Logger.e("!!! data set of adapter is empty");
            mOurView.onRemove(RESULT_FAILURE, position, null);
            return;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mPhotoCache = removeItemFromDataSet(position);
                if (mPhotoCache != null) {
                    Logger.d("... succeeded to remove one photo:" + mPhotoCache.getName());
                    mOurView.onRemove(RESULT_SUCCESS, position, mPhotoCache);
                } else {
                    Logger.e("!!! failed to remove one photo:" + mPhotoCache.getName());
                    mOurView.onRemove(RESULT_FAILURE, position, null);
                }
            }
        });
    }

    @Override
    public void recover(final int position) {
        final int count_before = getItemCount();
        if (mOurView == null) {
            Logger.e("!!! mOurView is null");
            return;
        }
        if (mPhotoCache == null) {
            Logger.e("!!! no cache to be recovered");
            mOurView.onRecover(RESULT_FAILURE, position, null);
            return;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                addItemToDataSet(position, mPhotoCache);
                //addItemToDataSet(BaseModelAdapter.ADD_POSITION_AT_FIRST, mPhotoCache); //leading index chaos
                if (getItemCount() > count_before) {
                    mOurView.onRecover(RESULT_SUCCESS, position, mPhotoCache);
                } else  {
                    mOurView.onRecover(RESULT_FAILURE, position, null);
                }
            }
        });
    }

    @Override
    public void cancel(boolean cancel) {
        if (mWorkers == null) {
            Logger.e("!!! mWorkers is null");
            return;
        }
        if (!cancel) {
            Logger.e("!!! do nothing when cancel goes with false");
            return;
        }
        for (Map.Entry<Long, Worker> entry : mWorkers.entrySet()) {
            Worker worker = entry.getValue();
            if (worker.getThread() != null) {
                if (worker.getThread().isAlive()) {
                    Logger.d("... going to cancel on the worker and others will break:" + worker.getStamp());
                    worker.cancel();
                    break; //all of bitmap helper decoding URL loops running on threads will break if either of worker goes for cancel
                } else {
                    Logger.d("... worker has completed its task:" + worker.getStamp());
                }
            } else {
                Logger.e("!!! work get thread returned null:" + worker.getStamp());
            }
        }
    }

    @Override
    public void load() {
        Logger.d(">>>");
        if (mMVPThread != null) {
            if (mMVPThread.isAlive()) {
                Logger.d("... previous loading task is still running but going to interrupt it");
                mMVPThread.interrupt();
            } else {
                mMVPThread = null;
            }
        }
        mMVPThread = new Thread(new LoadTask());
        mMVPThread.start();
    }

    public void setClickedAuthorId(String card_id) {
        this.mClickedAuthorId = card_id;
    }

    public void setClickedCardId(String card_id) {
        this.mClickedCardId = card_id;
    }

    /**
     * reads a card from firebase real time database according to card id of the specific card
     */
    private void readCardFromFirebase(final String author_id, final String card_id, final Card dest) throws ClassNotFoundException {
        Logger.d(">>>");
        if (card_id != null) {
            mOurView.onNotice(PROGRESS_ON, "努力下載中...");
            Logger.d("... going to CardManager.getInstance(mContext).subscribeCardInFirebase()");
            CardManager.getInstance(mContext).subscribeCardInFirebase(author_id, card_id, new CardSubscriber() {
                @Override
                public void onUpdate(Card card) {
                    Logger.d(">>>");
                    mOurView.onNotice(PROGRESS_OFF, null);
                    dest.setCard(card);
                    if (card_id.equals(dest.getCardId())) {
                        Logger.d("... got a matched card");
                        mResult = RESULT_SUCCESS;
                    } else {
                        Logger.e("!!! got a unmatched card");
                        mResult = RESULT_FAILURE;
                    }
                    if (mMVPThread != null && mMVPThread.isAlive()) {
                        Logger.d("... going to interrupt mvp thread");
                        mMVPThread.interrupt();
                    }
                    /*
                    synchronized (lock) { //becomes the owner of the object's monitor
                        if (mMVPThread != null) {
                            lock.notifyAll(); //relinquishes the ownership of the object's monitor
                        }
                    }
                    */
                }

                @Override
                public void onClone(CardList cards) {

                }

                @Override
                public void onError(String error) {
                    Logger.e("!!! error:" + error);
                    mOurView.onNotice(PROGRESS_OFF, null);
                    mResult = RESULT_FAILURE;
                    if (mMVPThread != null && mMVPThread.isAlive()) {
                        Logger.d("... going to interrupt mvp thread");
                        mMVPThread.interrupt();
                    }
                }
            });
        } else {
            Logger.e("!!! card id is null");
        }
    }

    /**
     * ViewHolder holds references of an item view and its subviews and metadata about its place within the RecyclerView
     * that means it describes and provides access to all the views within each item
     * by providing a direct reference to each of the views within a data item at the corresponding index
     * in order to cache the views within the item layout for fast access
     */
    private class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView mPhoto;
        private ImageButton mRemove;

        private ViewHolder(View itemView) {
            super(itemView);
            mPhoto = itemView.findViewById(R.id.photoItem);
            if (mScaleType != null) {
                Logger.d("... going to set scale type of image view:" + mScaleType.toString());
                mPhoto.setScaleType(mScaleType);
            }
            if (mMaxHeight > 0) {
                Logger.d("... going to set max height of image view:" + mMaxHeight);
                mPhoto.setMaxHeight(mMaxHeight);
            }
            if (mMaxHeight > 0) {
                Logger.d("... going to set min height of image view:" + mMinHeight);
                mPhoto.setMinimumHeight(mMinHeight);
            }
            mRemove = itemView.findViewById(R.id.photoRemove);
        }

        private void bindData(Photo photo) {
            ViewCompat.setTransitionName(mPhoto, String.valueOf(getAdapterPosition()) + "_image");
            ViewCompat.setTransitionName(mRemove, String.valueOf(getAdapterPosition()) + "_image");
            final String url = photo.getUrl();
            if (url.equals(Photo.DEFAULT_URL) && photo.getName().equals(Photo.DEFAULT_NAME)) {
                Logger.d("... this is the mock and no need to display mock on image view");
                /*
                Glide.with(getContext())
                        .load(R.drawable.pic_loading)
                        .centerCrop()
                        .override(getSize(getContext()).x, getSize(getContext()).y)
                        //.override(300, 300)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .crossFade()
                        .error(R.drawable.pic_loading)
                        .into(mPhoto);
                mRemove.setVisibility(View.VISIBLE);
                */
                return;
            } else if (url.equals(String.valueOf(R.drawable.add_photo)) && photo.getName().equals(Photo.DEFAULT_NAME)) {
                Logger.d("... this is the mock showcasing to add photo");
                if (getItemCount() > 1) {
                    Logger.e("!!! mock should be single and at the top");
                }
                mPhoto.setVisibility(View.VISIBLE);
                Glide.with(getContext())
                        .load(R.drawable.add_photo)
                        .fitCenter()
                        .listener(new RequestListener<Integer, GlideDrawable>() {
                            @Override
                            public boolean onException(Exception e, Integer model, Target<GlideDrawable> target, boolean isFirstResource) {
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(GlideDrawable resource, Integer model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                                return false;
                            }
                        })
                        .override(getSize(getContext()).x, getSize(getContext()).y)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .crossFade()
                        .error(R.drawable.add_photo)
                        .into(mPhoto);
                mRemove.setVisibility(View.GONE);
                if (mAddPhotoImageOnClickListener != null) mPhoto.setOnClickListener(mAddPhotoImageOnClickListener);
                return;
            }
            mPhoto.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Logger.d("... clicked item at " + getAdapterPosition() + " is touched but do nothing");
                }
            });
            String scheme = Uri.parse(url).getScheme();
            if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("ftp")) {
                if (mWorkers == null) mWorkers = new HashMap();
                final long time_stamp = System.currentTimeMillis();
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Logger.d("... going to decode a bitmap into view from an URL");
                        BitmapHelper.getInstance().decodeFromURL(url, new BitmapHelper.OnHttpURLConnection() {
                            @Override
                            public void onConnect() {

                            }

                            @Override
                            public void onGet(final Bitmap bitmap) {
                                if (bitmap == null) {
                                    Worker worker_removed = mWorkers.remove(time_stamp);
                                    if (worker_removed != null) Logger.e("!!! bitmap is null and removed a worker:" + worker_removed.getStamp());
                                    else Logger.e("!!! bitmap is null and failed to removed any worker");
                                    return;
                                }
                                Logger.d("... got a bitmap(w:" + bitmap.getWidth() + " and h:" + bitmap.getHeight() + ")");
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (isRemoveBtnVisible) {
                                            mRemove.setVisibility(View.VISIBLE);
                                        } else {
                                            mRemove.setVisibility(View.INVISIBLE);
                                        }
                                        mPhoto.setVisibility(View.VISIBLE);
                                        StringBuilder stringBuilder = new StringBuilder();
                                        stringBuilder.append("... bitmap.getWidth() :" + bitmap.getWidth() + "\n");
                                        stringBuilder.append("... mPhoto.getWidth() :" + mPhoto.getWidth() + "\n");
                                        stringBuilder.append("... bitmap.getHeight  :" + bitmap.getHeight() + "\n");
                                        stringBuilder.append("... mPhoto.getHeight():" + mPhoto.getHeight() + "\n");
                                        Logger.d(stringBuilder.toString());
                                        mPhoto.setImageBitmap(bitmap);
                                    }
                                });
                                Worker worker_removed = mWorkers.remove(time_stamp);
                                if (worker_removed != null) Logger.d("... eventually removed a worker:" + worker_removed.getStamp());
                                else Logger.d("... eventually failed to removed any worker");
                            }

                            @Override
                            public void onException(Exception e) {
                                Worker worker_removed = mWorkers.remove(time_stamp);
                                if (worker_removed != null) Logger.e("!!! exception: " + e.getMessage() + " and removed a worker:" + worker_removed.getStamp());
                                else Logger.e("!!! exception: " + e.getMessage() + " and failed to removed any worker");
                            }
                        });
                    }
                });
                Worker worker = new Worker(thread, time_stamp);
                mWorkers.put(time_stamp, worker);
                worker.start();
            } else {
                Logger.d("... going to decode a bitmap into view from an URI");
                if (isRemoveBtnVisible) {
                    mRemove.setVisibility(View.VISIBLE);
                } else {
                    mRemove.setVisibility(View.INVISIBLE);
                }
                mPhoto.setVisibility(View.VISIBLE);
                Glide.with(getContext())
                        .load(url)
                        .override(getSize(getContext()).x, getSize(getContext()).y)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(false)
                        .crossFade()
                        .error(R.drawable.ic_stub)
                        .into(mPhoto);
            }

            mRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    remove(getAdapterPosition());
                }
            });

            /* *
             * The UI event queue will process events in order. After setContentView() is invoked
             * , the event queue will contain a message asking for a relayout
             * , so anything you post to the queue will happen after the layout pass - Romain Guy
             * /
            mPhoto.post(new Runnable() {
                @Override
                public void run() {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("... mPhoto.getWidth()         :" + mPhoto.getWidth() + "\n");
                    stringBuilder.append("... mPhoto.getMeasuredWidth() :" + mPhoto.getMeasuredWidth() + "\n");
                    stringBuilder.append("... mPhoto.getHeight()        :" + mPhoto.getHeight() + "\n");
                    stringBuilder.append("... mPhoto.getMeasuredHeight():" + mPhoto.getMeasuredHeight() + "\n");
                    Logger.d(stringBuilder.toString()
                    );
                }
            });
             */
        }

        private Point getSize(Context context) {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            assert wm != null;
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            return size;
        }
    }

    /**
     * reads urls and file names of specific card from Firebase real time database
     * for feeding the data set of adapter
     *
     * an invocation of the Object#wait() on the current thread (thread must own this object's monitor)
     * causes it to wait until either another thread invokes the Object#notify() method
     * or the Object#notifyAll() method for this object (as of the object's monitor), or a specified amount of time has elapsed.
     * furthermore, if the thread is interrupted by any thread before or while it is waiting, then an InterruptedException is thrown.
     * This exception is not thrown until the lock status of this object has been restored (thread gains control of this object).
     *
     * moreover, after calling to Object#wait() on the current thread,it will to place itself in the wait set for this object
     * and then to relinquish any and all synchronization claims on this object.
     * it means current thread becomes disabled for thread scheduling purposes and lies dormant until one of four things happens:
     * 1.some other thread invokes the notify method on this object (on the object's monitor).
     * 2.some other thread invokes the notifyAll method on this object.
     * 3.some other thread interrupts current thread.
     * 4.the specified amount of real time has elapsed
     *
     * an invocation of the Object#notify() on the current thread (thread must own this object's monitor)
     * wakes up a single thread that is waiting on this object's monitor.
     * furthermore, the awakened thread will not be able to proceed until the current thread relinquishes the lock on this object
     * which is just relinquished from Object#notify()
     *
     * an invocation of the Thread#interrupt() on this task's thread from the current thread
     * will clear its interrupt status and let it receive an InterruptedException
     *
     * "lock" serves as an object's monitor in support of the asynchronous fetching task
     */
    private class FetchTask implements Runnable {
        @Override
        public void run() {
            Card card = new Card();
            Photo photo;
            final List<Photo> photos;
            try {
                synchronized (lock) { //becomes the owner of the object's monitor
                    readCardFromFirebase(mClickedAuthorId, mClickedCardId, card);
                    Logger.d("... going to wait");
                    lock.wait(); //relinquishes the ownership of the object's monitor
                }
            } catch (InterruptedException e) {
                Logger.d("... got InterruptedException and proceed");
                photos = new LinkedList<>();
                for (int i= 0; i<card.getArticleCount(); i++) {
                    photo = new Photo(card.getArticlePhoto().get(i), card.getArticlePhotoFileName().get(i));
                    ((LinkedList<Photo>) photos).addLast(photo);
                }
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        setItemsAsDataSet(photos);
                    }
                });
            } catch (ClassNotFoundException e) {
                Logger.d("... got ClassNotFoundException:" + e.toString());
            }
            Logger.d("... task done and now count of items:" + getItemCount());
        }
    }

    /**
     * reads urls and file names of specific card from Firebase real time database
     * for feeding the data set of adapter then displays on the UI
     */
    private class LoadTask implements Runnable {
        @Override
        public void run() {
            final Card card = new Card();
            Photo photo;
            final List<Photo> photos;
            try {
                synchronized (lock) { //becomes the owner of the object's monitor
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                readCardFromFirebase(mClickedAuthorId, mClickedCardId, card);
                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                    Logger.d("... going to wait");
                    lock.wait(); //relinquishes the ownership of the object's monitor
                }
            } catch (InterruptedException e) {
                Logger.d("... got InterruptedException and proceed");
                photos = new LinkedList<>();
                for (int i= 0; i<card.getArticleCount(); i++) {
                    photo = new Photo(card.getArticlePhoto().get(i), card.getArticlePhotoFileName().get(i));
                    ((LinkedList<Photo>) photos).addLast(photo);
                }
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        setItemsAsDataSet(photos);
                    }
                });
                if (mOurView != null) {
                    mOurView.onLoad(mResult, card);
                }
            }
            Logger.d("... task done and now count of items:" + getItemCount());
        }
    }

    /**
     * callback for card manager in response to user when to read the database of firebase to the data set of card adapter
     */
    class CardSubscriber implements CardManager.Subscriber {
        @Override
        public void onUpdate(Card card) {}

        @Override
        public void onClone(CardList cards) {}

        @Override
        public void onError(String error) {}

        @Override
        public void onAuthors(List<String> author_ids) {

        }

        @Override
        public void onGetParentIdsAndCounts(Map<String, Long> parentIdsAndSiblingCounts) {

        }
    }

    private static final class Lock {}

    public interface AddPhotoImageOnClickListener extends ImageView.OnClickListener {

    }

    private class Worker {
        Thread thread;
        long stamp;
        BitmapHelper bitmapHelper;

        private Worker(Thread thread, long time_stamp) {
            this.thread = thread;
            this.stamp = time_stamp;
            this.bitmapHelper = BitmapHelper.getInstance();
        }

        private long getStamp() {
            return stamp;
        }

        private Thread getThread() {
            return thread;
        }

        private void start() {
            if (thread != null) {
                Logger.d("... going to worker start:" + stamp);
                thread.start();
            }
        }

        private void cancel() {
            if (bitmapHelper != null) bitmapHelper.cancel(true);
        }
    }
}
