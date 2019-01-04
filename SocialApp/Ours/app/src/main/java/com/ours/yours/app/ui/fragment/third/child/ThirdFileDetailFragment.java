package com.ours.yours.app.ui.fragment.third.child;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.adapter.BaseModelAdapter;
import com.ours.yours.app.adapter.FileAdapter;
import com.ours.yours.app.base.BaseChildFragment;
import com.ours.yours.app.entity.PhotoFile;
import com.ours.yours.app.ui.mvp.OurModel;
import com.ours.yours.app.ui.mvp.OurPresenter;
import com.ours.yours.app.ui.mvp.OurView;

public class ThirdFileDetailFragment extends BaseChildFragment implements OurView, FileAdapter.OnClickRemoveItem {
    private final static String ARG = "ARG_CARD_PHOTO_FRAGMENT";

    public  final static int HANDLER_MSG_WHAT_BASE        = 10;
    public  final static int HANDLER_MSG_WHAT_LOAD_SUCCESS = HANDLER_MSG_WHAT_BASE + 2;
    public  final static int HANDLER_MSG_WHAT_LOAD_FAILURE = HANDLER_MSG_WHAT_BASE + 3;

    private Handler mHandler = new UIHandler();
    private View mView;
    private RecyclerView mRecy;
    private FileAdapter mAdapter;
    private LinearLayoutManager mLayoutManager;
    private Button mSnackBtn;
    private static boolean is_debug = false;

    public static ThirdHomeFragment newInstance() {
        Logger.d(">>>");
        Bundle args = new Bundle();

        ThirdHomeFragment fragment = new ThirdHomeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Logger.d(">>>");
        mView = inflater.inflate(R.layout.app_third_child_file_detail, container, false);
        mSnackBtn = mView.findViewById(R.id.snackBtnOfThirdFileDetailFragment);
        initAdapter();
        initRecyclerView();
        return mView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Logger.d(">>>");
    }

    @Override
    public void onResume() {
        super.onResume();
        Logger.d(">>>");
        if (getArguments().getParcelable(ARG) == null) Logger.e("!!! no card saved in argument of this fragment");
        bindFileData();
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
    public void onSupportVisible() {
        super.onSupportVisible();
        Logger.d(">>>");
        mAdapter.load();
    }

    @Override
    public void onSupportInvisible() {
        super.onSupportInvisible();
        Logger.d(">>>");
    }

    @Override
    /**
     * here not consumes this back press event at TransactionDelegate#dispatchBackPressedEvent()
     */
    public boolean onBackPressedSupport() {
        return super.onBackPressedSupport();
    }

    private void initAdapter() {
        mAdapter = new FileAdapter(_mActivity);
        mAdapter.attach(this);
        readPhotosFromMocks(mAdapter);
    }

    private void initRecyclerView() {
        mRecy = mView.findViewById(R.id.photosRecyclerViewOfThirdFileDetailFragment);
        mLayoutManager = new LinearLayoutManager(_mActivity, LinearLayoutManager.VERTICAL, false);
        mLayoutManager.setAutoMeasureEnabled(true);
        //sets layout manager to position the items
        mRecy.setLayoutManager(mLayoutManager);
        mRecy.setHasFixedSize(false);
        mRecy.setAdapter(mAdapter);
        syncFileData();
    }

    /**
     * just to synchronize the present data set with the display on UI
     * by calling RecyclerView.Adapter#notifyDataSetChanged() to trigge RecyclerView.Adapter#onBindViewHolder()
     * which leads to the recycler view carries out updating the display on its own
     *
     * don't call RecyclerView.Adapter#notifyDataSetChanged() in RecyclerView.Adapter#onBindViewHolder()
     * for avoiding endless loops
     */
    private void bindFileData() {
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
    private void syncFileData() {
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
    private void readPhotosFromMocks(FileAdapter adapter) {
        Logger.d(">>>");
        PhotoFile mock;
        for (int i = 0; i < 1; i++) {
            mock = new PhotoFile();
            adapter.addItemToDataSet(BaseModelAdapter.ADD_POSITION_AT_LAST, mock);
        }
    }

    @Override
    public void onNotice(int result, String message) {

    }

    @Override
    public void onFetch(int result, Object pojo) {

    }

    @Override
    public void onLoad(int result, Object pojo) {
        switch (result) {
            case BaseModelAdapter.RESULT_SUCCESS:
                Logger.d("... got RESULT_LOAD_SUCCESS");
                hideProgress();
                Message msg1 = new Message();
                msg1.what = HANDLER_MSG_WHAT_LOAD_SUCCESS;
                mHandler.sendMessage(msg1);
                break;
            case BaseModelAdapter.RESULT_FAILURE:
                Logger.e("!!! got RESULT_LOAD_FAILURE");
                hideProgress();
                Message msg2 = new Message();
                msg2.what = HANDLER_MSG_WHAT_LOAD_FAILURE;
                mHandler.sendMessage(msg2);
                break;
            default:
                break;
        }
    }

    @Override
    public void onRemove(int result, int position, Object model) {
        switch (result) {
            case BaseModelAdapter.RESULT_SUCCESS:
                Logger.d("... got RESULT_LOAD_SUCCESS");
                Snackbar.make(mSnackBtn, "succeeded to remove", Snackbar.LENGTH_LONG).show();
                break;
            case BaseModelAdapter.RESULT_FAILURE:
                Logger.e("!!! got RESULT_LOAD_FAILURE");
                Snackbar.make(mSnackBtn, "failed to remove", Snackbar.LENGTH_LONG).show();
                break;
            default:
                break;
        }
    }

    @Override
    public void onRecover(int result, int position, Object model) {

    }

    @Override
    public void feed(OurModel model, OurPresenter presenter) {

    }

    @Override
    public void onClickRemoveItem(final int position) {
        Snackbar snackbar = Snackbar.make(mSnackBtn, "sure to remove?", Snackbar.LENGTH_LONG);
        snackbar.setAction("yes", new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                mAdapter.remove(position);
            }
        }).show();
    }

    @SuppressLint("HandlerLeak")
    private class UIHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_WHAT_LOAD_SUCCESS:
                    bindFileData();
                    break;
                case HANDLER_MSG_WHAT_LOAD_FAILURE:
                    break;
                default:

                    break;
            }
        }
    }

    private static class InnerLogger {
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
