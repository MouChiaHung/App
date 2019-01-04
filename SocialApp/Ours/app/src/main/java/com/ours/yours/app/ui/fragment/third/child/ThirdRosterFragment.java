package com.ours.yours.app.ui.fragment.third.child;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.adapter.RosterAdapter;
import com.ours.yours.app.entity.Friend;

import java.util.ArrayList;
import java.util.List;

import me.yokeyword.fragmentation.SupportFragment;

public class ThirdRosterFragment extends SupportFragment implements RosterAdapter.OnItemClickListener{
    private final static String ARG_FRIENDS = "ARG_FRIENDS";
    private View mView;
    private RecyclerView mRecy;
    private RosterAdapter mAdapter;
    private List<Friend> mFriends;
    private OnRosterClickListener mListener;

    public static ThirdRosterFragment newInstance(ArrayList<Friend> friends) {
        Logger.d(">>>");
        Bundle args = new Bundle();
        args.putParcelableArrayList(ARG_FRIENDS, friends);

        ThirdRosterFragment fragment = new ThirdRosterFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Logger.d(">>>");
        mView = inflater.inflate(R.layout.app_third_child_roster, container, false);
        initView();
        return mView;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Logger.d(">>>");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Logger.d(">>>");
    }

    private void initView() {
        mRecy = mView.findViewById(R.id.recy_roster);
        mAdapter = new RosterAdapter(_mActivity);
        mRecy.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecy.setHasFixedSize(false);
        mAdapter.setItemClickListener(this);
        /**
         * get friends from arguments of fragment or connection again
         */
        mFriends = getArguments().getParcelableArrayList(ARG_FRIENDS);
        if (mFriends == null) {
            Logger.e("!!! friends from arguments is null");
            mFriends = new ArrayList<>();
            getFriends(mFriends);
        }
        mAdapter.setData(mFriends);
        mRecy.setAdapter(mAdapter);
    }

    private void getFriends(List<Friend> friends) {

    }

    public void setOnRosterClickListener(OnRosterClickListener mListener) {
        this.mListener = mListener;
    }

    @Override
    public void onItemClick(int position) {
        Logger.d(">>> click position:" + position);
        if(mListener != null) mListener.onRosterClick(position);
    }

    public void smoothScrollToPosition(int position) {
        mRecy.smoothScrollToPosition(position);
    }

    public interface OnRosterClickListener {
        void onRosterClick(int position);
    }
}
