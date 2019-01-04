package com.ours.yours.app.ui.fragment.first.child;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.GravityCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.auth.FirebaseAuth;
import com.ours.yours.R;
import com.ours.yours.app.adapter.BaseModelAdapter;
import com.ours.yours.app.adapter.PhotoAdapter;
import com.ours.yours.app.base.BaseChildFragment;
import com.ours.yours.app.entity.Card;
import com.ours.yours.app.entity.CardList;
import com.ours.yours.app.entity.Photo;
import com.ours.yours.app.firebase.FirebaseDatabaseHelper;
import com.ours.yours.app.manager.CardManager;
import com.ours.yours.app.ui.mvp.OurModel;
import com.ours.yours.app.ui.mvp.OurPresenter;
import com.ours.yours.app.ui.mvp.OurView;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import static android.view.View.GONE;

public class FirstCardPhotoFragment extends BaseChildFragment implements OurView {
    private final static String ARG = "ARG_CARD_PHOTO_FRAGMENT";

    public final static int PROGRESS_ON  = 0;
    public final static int PROGRESS_OFF = 1;

    private final static int HANDLER_MSG_WHAT_BASE                  = 20;
    public  final static int HANDLER_MSG_WHAT_INIT_CARD_SUCCESS     = HANDLER_MSG_WHAT_BASE + 2;
    public  final static int HANDLER_MSG_WHAT_INIT_CARD_FAILURE     = HANDLER_MSG_WHAT_BASE + 4;

    private final static int POP_RESULT_BASE              = 30;
    public  final static int POP_RESULT_INIT_CARD_SUCCESS = POP_RESULT_BASE + 1;
    public  final static int POP_RESULT_INIT_CARD_FAILURE = POP_RESULT_BASE + 2;

    private View mView;
    private RecyclerView mRecy;
    private PhotoAdapter mAdapter;
    private LinearLayoutManager mLayoutManager;

    private Handler mHandler;
    private String mClickedAuthorId; //passed from a picking up on home fragment
    private String mClickedCardId;   //passed from a picking up on home fragment

    private OnPop mOnPop;
    private int mResultCode; //used to pass a result to OnPop in response to user

    private static boolean is_debug = false;

    /**
     * inner class access
     */
    private static int index_of_photos = 0;

    public static FirstCardPhotoFragment newInstance(String author_id, String card_id) {
        Bundle args = new Bundle();
        FirstCardPhotoFragment fragment = new FirstCardPhotoFragment();
        fragment.setClickedAuthorId(author_id);
        fragment.setClickedCardId(card_id);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Logger.d(">>>");
        mView = inflater.inflate(R.layout.app_first_child_card_detail_photo, container, false);
        initHandler();
        initAdapter();
        initRecyclerView();
        return mView;
    }

    @Override
    public void onResume() {
        super.onResume();
        Logger.d(">>>");
        if (getArguments().getParcelable(ARG) == null) Logger.e("!!! no card saved in argument of this fragment");
        bindCardData();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Logger.d(">>>");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mAdapter.detach(this);
        Logger.d(">>>");
    }

    @Override
    public void pop() {
        Logger.d(">>> mResultCode:" + mResultCode);
        super.pop();
        if (mOnPop != null) mOnPop.onPop(mResultCode);
    }

    @Override
    /**
     * here not consumes this back press event at TransactionDelegate#dispatchBackPressedEvent()
     */
    public boolean onBackPressedSupport() {
        return false;
    }

    public void setClickedAuthorId(String card_id) {
        this.mClickedAuthorId = card_id;
    }

    public void setClickedCardId(String card_id) {
        this.mClickedCardId = card_id;
    }

    public void setOnPop(OnPop onPop) {
        this.mOnPop = onPop;
    }

    @SuppressLint("HandlerLeak")
    private void initHandler() {
        mHandler = new UIHandler();
    }

    private void initAdapter() {
        mAdapter = new PhotoAdapter(_mActivity);
        mAdapter.attach(this);
        readPhotosFromMocks(mAdapter);
    }

    private void initRecyclerView() {
        mRecy = mView.findViewById(R.id.photosRCV);
        mLayoutManager = new LinearLayoutManager(_mActivity, LinearLayoutManager.HORIZONTAL, false);
        mLayoutManager.setAutoMeasureEnabled(true);
        //sets layout manager to position the items
        mRecy.setLayoutManager(mLayoutManager);
        mRecy.setHasFixedSize(false);
        mRecy.setAdapter(mAdapter);
    }

    /**
     * just to synchronize the present data set with the display on UI
     * by calling RecyclerView.Adapter#notifyDataSetChanged() to trigge RecyclerView.Adapter#onBindViewHolder()
     * which leads to the recycler view carries out updating the display on its own
     *
     * don't call RecyclerView.Adapter#notifyDataSetChanged() in RecyclerView.Adapter#onBindViewHolder()
     * for avoiding endless loops
     */
    private void bindCardData() {
        if (mAdapter == null) {
            Logger.e("!!! adapter is null");
            return;
        }
        mAdapter.notifyDataSetChanged();
    }

    /**
     * just to synchronize the data set of adapter with firebase database
     * by calling OurPresenter#load() which invokes OurPresenter.MVPCallback#onLoad() while loading task is done
     *
     * OurView should be attached before calling this method in order for OurPresenter to access reference of OurView (MVP)
     */
    private void syncCardData() {
        if (mAdapter == null) {
            Logger.e("!!! adapter is null");
            return;
        }
        mAdapter.load();
    }

    /**
     * for testing and fixing the issue
     * that RecyclerView.Adapter#onBindViewHolder() won't be invoked if RecyclerView.Adapter#getItemCount() return 0
     */
    private void readPhotosFromMocks(PhotoAdapter adapter) {
        Logger.d(">>>");
        Photo mock;
        String url = Photo.DEFAULT_URL;
        String name = Photo.DEFAULT_NAME;

        for (int i = 0; i < 1; i++) {
            mock = new Photo(url, name);
            adapter.addItemToDataSet(BaseModelAdapter.ADD_POSITION_AT_LAST, mock);
        }
    }

    @Override
    public void feed(OurModel model, OurPresenter presente) {

    }

    @Override
    public void onNotice(int result, String message) {

    }

    @Override
    public void onFetch(int result, Object pojo) {

    }

    @Override
    public void onLoad(int result, Object pojo) {

    }


    @Override
    public void onRemove(int result, int position, Object model) {

    }

    @Override
    public void onRecover(int result, int position, Object model) {

    }

    /**
     * callback for popping this fragment from back back
     */
    public interface OnPop {
        void onPop(int resultCode);
    }


    @SuppressLint("HandlerLeak")
    private class UIHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_WHAT_INIT_CARD_SUCCESS:
                    mResultCode = POP_RESULT_INIT_CARD_SUCCESS;
                    bindCardData();
                    break;
                case HANDLER_MSG_WHAT_INIT_CARD_FAILURE:
                    mResultCode = POP_RESULT_INIT_CARD_FAILURE;
                    pop();
                    break;
                default:

                    break;
            }
        }
    }

    private static class Logger {
        static void d(@NonNull String message, @Nullable Object... args) {
            if (is_debug) com.orhanobut.logger.Logger.d(message, args);
        }

        static void d(@Nullable Object object) {
            if (is_debug) com.orhanobut.logger.Logger.d(object);
        }

        static void e(@NonNull String message, @Nullable Object... args) {
            if (is_debug) com.orhanobut.logger.Logger.e(message, args);
        }
    }
}
