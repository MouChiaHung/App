package com.ours.yours.app.adapter;

import android.content.Context;
import android.graphics.Point;
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
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.dao.FileHelper;
import com.ours.yours.app.entity.PhotoFile;
import com.ours.yours.app.ui.mvp.OurPresenter;
import com.ours.yours.app.ui.mvp.OurView;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * adapter provides a binding from data set synchronized with file system to views displayed within a RecyclerView
 */
public class FileAdapter extends BaseModelAdapter<PhotoFile> implements OurPresenter<OurView> {
    private final static int FETCH_RESULT_BASE    = 20;
    private final static int FETCH_RESULT_SUCCESS = FETCH_RESULT_BASE + 1;
    private final static int FETCH_RESULT_FAILURE = FETCH_RESULT_BASE + 2;
    private final static String DEFAULT_PATH = "/data/data/com.finger.tool/fpdata";
    private final static String DEFAULT_FILE_END = "";

    public final static int PROGRESS_ON  = 0;
    public final static int PROGRESS_OFF = 1;

    private String mPath = DEFAULT_PATH;
    private String mEnd = DEFAULT_FILE_END;
    private OurView mOurView;
    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private Thread mMVPThread;
    private final Object lock = new Lock();

    /**
     * threads access
     */
    int remove_result = RESULT_FAILURE;

    public FileAdapter(Context context) {
        super(context);
        mContext = context;
        mLayoutInflater = LayoutInflater.from(mContext);
    }

    public void setEnd(String end) {
        this.mEnd = end;
    }

    public void setPath(String path) {
        this.mPath = path;
    }

    public String getEnd() {
        return mEnd;
    }

    public String getPath() {
        return mPath;
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
        View item_view = mLayoutInflater.inflate(R.layout.item_file, parent, false);
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
        if (view == null) {
            Logger.d("!!! view is null");
            return;
        }
        mOurView = view;
    }

    @Override
    public void detach(OurView view) {
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
    public void remove(int position) {
        Logger.d(">>>");
        if (mMVPThread != null) {
            if (mMVPThread.isAlive()) {
                Logger.d("... previous fetching task is still running but going to interrupt it");
                mMVPThread.interrupt();
            } else {
                mMVPThread = null;
            }
        }
        mMVPThread = new Thread(new RemoveTask(position));
        mMVPThread.start();
    }

    @Override
    public void recover(int position) {

    }

    @Override
    public void cancel(boolean cancel) {

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

    /**
     * reads a file list from file system according to path of the specific directory
     */
    private void readFileList(final String path, final String end, final List<File> files) throws ClassNotFoundException {
        Logger.d(">>>");
        if (path != null) {
            mOurView.onNotice(PROGRESS_ON, "努力下載中...");
            //if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (false) {
                try {
                    FileHelper.getInstance().listNIO(path, end, files);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                FileHelper.getInstance().list(path, end, files);
            }
            mOurView.onNotice(PROGRESS_OFF, null);
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
        } else {
            Logger.e("!!! path id is null");
        }
    }

    /**
     * removes a file list from file system according to path of the specific directory
     */
    private void removeFileFromFileSystem(final String path, final String end) {
        Logger.d(">>>");
        if (path != null) {
            remove_result = FileHelper.getInstance().remove(path, end) ? RESULT_SUCCESS : RESULT_FAILURE;
            if (mMVPThread != null && mMVPThread.isAlive()) {
                Logger.d("... going to interrupt mvp thread and remove_result:" + remove_result);
                mMVPThread.interrupt();
            }
        } else {
            Logger.e("!!! path id is null");
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
        private TextView mNameView;
        private TextView mDateView;
        private ImageButton mRemove;

        private ViewHolder(View itemView) {
            super(itemView);
            mPhoto = itemView.findViewById(R.id.photoItem);
            mNameView = itemView.findViewById(R.id.tvName);
            mDateView = itemView.findViewById(R.id.tvDate);
            mRemove = itemView.findViewById(R.id.photoRemove);
        }

        private void bindData(PhotoFile file) {
            ViewCompat.setTransitionName(mPhoto, String.valueOf(getAdapterPosition()) + "_image");
            ViewCompat.setTransitionName(mNameView, String.valueOf(getAdapterPosition()) + "_tv");
            ViewCompat.setTransitionName(mDateView, String.valueOf(getAdapterPosition()) + "_tv");
            ViewCompat.setTransitionName(mRemove, String.valueOf(getAdapterPosition()) + "_image");
            String url = file.getUrl();
            mPhoto.setVisibility(View.VISIBLE);
            Glide.with(getContext())
                    .load(url)
                    .fitCenter()
                    .override(getSize(getContext()).x, getSize(getContext()).y)
                    //.override(300, 300)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .crossFade()
                    .error(R.drawable.ic_stub)
                    .into(mPhoto);

            Date now = new Date();
            long interval_millisec  = now.getTime() - file.getDate();
            long interval_sec       = interval_millisec/1000;
            long interval_min       = interval_sec/(60);
            long interval_hr        = interval_min/(60);
            long interval_day       = interval_hr/(24);
            Logger.e("... interval_millisec:" + interval_millisec);
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
            mNameView.setText(file.getTemplate());
            mRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((OnClickRemoveItem) mOurView).onClickRemoveItem(getAdapterPosition());
                }
            });
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
     * reads file names of specific path from file system
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
            final List<File> files = new LinkedList<>();
            PhotoFile photoFile;
            final List<PhotoFile> photoFiles = new LinkedList<>();
            try {
                synchronized (lock) { //becomes the owner of the object's monitor
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                readFileList(mPath, mEnd, files);
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
                for (int i= 0; i<files.size(); i++) {
                    photoFile = new PhotoFile();
                    photoFile.setTemplate(files.get(i).getAbsolutePath());
                    photoFile.setDate(files.get(i).lastModified());
                    Logger.d("... going to add a file to data set of adapter");
                    photoFile.printPhotoData();
                    ((LinkedList<PhotoFile>) photoFiles).addLast(photoFile);
                }
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        setItemsAsDataSet(photoFiles);
                    }
                });
            }
            Logger.d("... task done and now count of items:" + getItemCount());
        }
    }

    /**
     * reads file names of specific path from file system
     * for feeding the data set of adapter then displays on the UI
     */
    private class LoadTask implements Runnable {
        @Override
        public void run() {
            final List<File> files = new LinkedList<>();
            PhotoFile photoFile;
            final List<PhotoFile> photoFiles = new LinkedList<>();
            try {
                synchronized (lock) { //becomes the owner of the object's monitor
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                readFileList(mPath, mEnd, files);
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
                for (File file : files) {
                    photoFile = new PhotoFile();
                    photoFile.setTemplate(file.getAbsolutePath());
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        try {
                            photoFile.setDate(Files.readAttributes(file.toPath(), BasicFileAttributes.class).creationTime().toMillis());
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    } else {
                        photoFile.setDate(file.lastModified());
                    }
                    Logger.d("... going to add a file to data set of adapter");
                    photoFile.printPhotoData();
                    ((LinkedList<PhotoFile>) photoFiles).addLast(photoFile);
                }
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        setItemsAsDataSet(photoFiles);
                    }
                });
                if (mOurView != null) {
                    mOurView.onLoad(RESULT_SUCCESS, files);
                }
            }
            Logger.d("... task done and now count of items:" + getItemCount());
        }
    }

    /**
     * removes file(s) of specific path from file system
     * for ejecting unwanted data set of adapter then displays on the UI
     */
    private class RemoveTask implements Runnable {
        int position;

        public RemoveTask(int position_remove) {
            this.position = position_remove;
        }

        @Override
        public void run() {
            try {
                synchronized (lock) { //becomes the owner of the object's monitor
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            removeFileFromFileSystem(getItemOfDataSet(position).getTemplate(), "");
                        }
                    }).start();
                    Logger.d("... going to wait");
                    lock.wait(); //relinquishes the ownership of the object's monitor
                }
            } catch (InterruptedException e) {
                Logger.d("... got InterruptedException and proceed");
                switch (remove_result) {
                    case BaseModelAdapter.RESULT_SUCCESS:
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                removeItemFromDataSet(position);
                            }
                        });
                        break;
                    case BaseModelAdapter.RESULT_FAILURE:
                        break;
                    default:
                        break;
                }
                if (mOurView != null) {
                    mOurView.onRemove(remove_result, -1, null);
                }
            }
            Logger.d("... task done and now count of items:" + getItemCount());
        }
    }

    private static final class Lock {};

    /**
     * callback to invoked when the remove button of one of item is pressed
     */
    public interface OnClickRemoveItem {
        void onClickRemoveItem(int position);
    }
}
