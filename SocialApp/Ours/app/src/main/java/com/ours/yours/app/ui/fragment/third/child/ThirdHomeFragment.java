package com.ours.yours.app.ui.fragment.third.child;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.MainActivity;
import com.ours.yours.app.entity.Friend;
import com.ours.yours.app.event.TabSelectedEvent;

import org.greenrobot.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.List;

import me.yokeyword.eventbusactivityscope.EventBusActivityScope;
import me.yokeyword.fragmentation.SupportFragment;

public class ThirdHomeFragment extends SupportFragment implements ThirdRosterFragment.OnRosterClickListener{
    private View mView;
    private ArrayList<Friend> mFriends;
    private ThirdRosterFragment thirdRosterFragment;

    public static ThirdHomeFragment newInstance() {
        Bundle args = new Bundle();

        ThirdHomeFragment fragment = new ThirdHomeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Logger.d(">>>");
        mView = inflater.inflate(R.layout.app_third_child_home, container, false);
        initView();
        return mView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Logger.d(">>>");
    }

    private void initView() {
        EventBusActivityScope.getDefault(_mActivity).register(this);
        /**
         * get data base of friends from connection
         */
        mFriends = new ArrayList<>();
        getFriends(mFriends);
        if (findChildFragment(ThirdRosterFragment.class) == null) {
            Logger.d("... going to create and load roaster fragment");
            thirdRosterFragment = ThirdRosterFragment.newInstance(mFriends);
            thirdRosterFragment.setOnRosterClickListener(this);
            loadRootFragment(R.id.fl_roster_container, thirdRosterFragment, true, false);
        }
    }

    private void getFriends(List<Friend> friends) {
        friends.add(new Friend("https://png.icons8.com/windows/50/000000/human-head.png", "user01"));
        friends.add(new Friend("https://png.icons8.com/windows/50/000000/human-head.png", "user02"));
        friends.add(new Friend("https://png.icons8.com/windows/50/000000/human-head.png", "user03"));
        friends.add(new Friend("https://png.icons8.com/windows/50/000000/human-head.png", "user04"));
        friends.add(new Friend("https://png.icons8.com/windows/50/000000/human-head.png", "user05"));
    }

    /**
     * Guess there's no chance to be called
     * In Fragmentation Library, SupportActivityDelegate.onBackPressed() don't ask child fragment manager
     * to find the top of its active fragment and call that fragment's override onBackPressedSupport() to deal with back pressed event
     */
    @Override
    public boolean onBackPressedSupport() {
        Logger.d(">>> @_@");
        return super.onBackPressedSupport();
    }

    /**
     * called when user repeats to select this THIRD Tab button
     */
    @Subscribe
    public void onTabSelectedEvent(TabSelectedEvent event) {
        Logger.d(">>>");
        if (event.getPosition() != MainActivity.THIRD) {
            Logger.e("!!! event.getPosition() != MainActivity.THIRD");
            return;
        }
        if (thirdRosterFragment != null) thirdRosterFragment.smoothScrollToPosition(0);
    }

    @Override
    public void onRosterClick(int position) {
        //loadRootFragment() for ThirdContentFragment updated by friend at position
    }
}
