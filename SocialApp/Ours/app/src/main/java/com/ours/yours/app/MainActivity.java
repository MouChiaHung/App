package com.ours.yours.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentationMagician;
import android.view.View;

import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.base.BaseMainFragment;
import com.ours.yours.app.event.TabSelectedEvent;
import com.ours.yours.app.manager.CardManager;
import com.ours.yours.app.manager.CommentManager;
import com.ours.yours.app.ui.fragment.first.FirstFragment;
import com.ours.yours.app.ui.fragment.first.child.FirstHomeFragment;
import com.ours.yours.app.ui.fragment.second.SecondFragment;
import com.ours.yours.app.ui.fragment.third.ThirdFragment;
import com.ours.yours.app.ui.fragment.third.child.ThirdHomeFragment;
import com.ours.yours.app.ui.view.BottomBar;
import com.ours.yours.app.ui.view.BottomBarTab;
import com.theartofdev.edmodo.cropper.CropImage;

import java.util.LinkedList;
import java.util.List;

import me.yokeyword.eventbusactivityscope.EventBusActivityScope;
import me.yokeyword.fragmentation.SupportActivity;
import me.yokeyword.fragmentation.SupportFragment;
import me.yokeyword.fragmentation.SupportHelper;

public class MainActivity extends SupportActivity implements BaseMainFragment.OnBackBaseListener{
    public static final int FIRST = 0;
    public static final int SECOND = 1;
    public static final int THIRD = 2;
    private Bundle mBundle;
    private View mView;
    private BottomBar mBottomBar;
    private BottomBarTab mBottomBarFirst, mBottomBarSecond, mBottomBarThird;
    private View.OnClickListener mSelectListener;
    private SupportFragment[] mFragments = new SupportFragment[3];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Logger.d(">>>");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_main);

        mBottomBar = findViewById(R.id.bottomBar);
        mBottomBarFirst = new BottomBarTab(this, R.drawable.ic_home_white_24dp, "村落");
        mBottomBarFirst.setTabPosition(FIRST);
        mBottomBarSecond = new BottomBarTab(this, R.drawable.ic_discover_white_24dp, "飛鴿");
        mBottomBarSecond.setTabPosition(SECOND);
        mBottomBarThird = new BottomBarTab(this, R.drawable.ic_message_white_24dp, "廣播");
        mBottomBarThird.setTabPosition(THIRD);

        if (findFragment(FirstFragment.class) == null) {
            Logger.d("... going to loadMultipleRootFragment()");
            /**
             * instantiates fragment manager belonging to activity
             * , didn't adds this ops for first, second, third fragments add and replace to back stack
             */
            mFragments[FIRST] = new FirstFragment();
            mFragments[SECOND] = new SecondFragment();
            mFragments[THIRD] = new ThirdFragment();
            loadMultipleRootFragment(R.id.fl_container, FIRST, mFragments[FIRST], mFragments[SECOND], mFragments[THIRD]);
            mBottomBarFirst.setSelected(true);
        } else {
            mFragments[FIRST] = findFragment(FirstFragment.class);
            mFragments[SECOND] = findFragment(SecondFragment.class);
            mFragments[THIRD] = findFragment(ThirdFragment.class);
        }

        mSelectListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v instanceof BottomBarTab) {
                    int goto_position = ((BottomBarTab) v).getTabPosition();
                    int prev_position = mBottomBar.getCurrentPosition();
                    Logger.d("... goto_position:" + goto_position);
                    Logger.d("... prev_position:" + prev_position);
                    mBottomBar.setCurrentPosition(goto_position);
                    if (goto_position == prev_position) { //select same one
                        int childFragmentCount = mFragments[goto_position].getChildFragmentManager().getBackStackEntryCount();
                        if (childFragmentCount > 1) {
                            /**
                             * pops all of ops which are belonging to child fragment manager and stored in back stack
                             * upon the initial op holding a home fragment tagged "XXXHomeFragment", eventually leads to show "XXXHomeFragment
                             *
                             * like calling mFragments[goto_position].getChildFragmentManager().popBackStack(XXXHomeFragment.class.toString(), false)
                             */
                             if(goto_position == FIRST) mFragments[goto_position].popToChild(FirstHomeFragment.class, false);
                             if(goto_position == THIRD) mFragments[goto_position].popToChild(ThirdHomeFragment.class, false);
                        } else if (childFragmentCount == 1){
                            EventBusActivityScope.getDefault(MainActivity.this).post(new TabSelectedEvent(goto_position));
                        } else {
                            //un-implemented
                        }
                    } else { //select another
                        /**
                         * didn't adds this ops to back stack
                         */
                        showHideFragment(mFragments[goto_position], mFragments[prev_position]);
                        mBottomBar.setTabSelectHint(prev_position, false);
                        mBottomBar.setTabSelectHint(goto_position, true);
                    }
                } else {
                    Logger.e("!!! v is not an instance of BottomBarTab");
                }
            }
        };

        mBottomBarFirst.setOnClickListener(mSelectListener);
        mBottomBarSecond.setOnClickListener(mSelectListener);
        mBottomBarThird.setOnClickListener(mSelectListener);
        mBottomBar.addTab(mBottomBarFirst);
        mBottomBar.addTab(mBottomBarSecond);
        mBottomBar.addTab(mBottomBarThird);


    }

    @Override
    public void onPause() {
        Logger.d(">>>");
        super.onPause();
        //Logger.e("!!! amo test MainACPause: going to SupportHelper.getActiveFragment(getFragmentManager()) that includes child fragment manager");
        //SupportHelper.getActiveFragment(getSupportFragmentManager());
    }

    @Override
    public void onResume() {
        //Logger.d(">>>");
        super.onResume();
        //Logger.e("!!! amo test MainACResume: going to SupportHelper.getActiveFragment(getFragmentManager()) that includes child fragment manager");
        //SupportHelper.getActiveFragment(getSupportFragmentManager());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        List<Fragment> parents = FragmentationMagician.getActiveFragments(getSupportFragmentManager());
        List<Fragment> children;

        /*
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(">>> ");
        stringBuilder.append("requestCode:" + requestCode);
        stringBuilder.append(", resultCode:" + resultCode);
        if (data != null) stringBuilder.append(", action:" + data.getAction());
        Logger.d("... " + stringBuilder.toString());
        stringBuilder = new StringBuilder();



        if (parents == null) return;
        for (Fragment p : parents) {
            if (p == null) continue;
            stringBuilder.append("... one of active parent fragment:" + p.getClass().getSimpleName() + "\n");
            children = FragmentationMagician.getActiveFragments(p.getChildFragmentManager());
            for (Fragment c : children) {
                if (c == null) continue;
                stringBuilder.append("...one of active child fragment:" + c.getClass().getSimpleName() + "\n");
            }

        }
        Logger.d("... " + stringBuilder.toString());
        */
        for (Fragment p : parents) {
            if (p == null) continue;
            children = FragmentationMagician.getActiveFragments(p.getChildFragmentManager());
            for (Fragment c : children) {
                if (c == null) continue;
                if (c.getClass().getSimpleName().equals("FirstAddCardFragment") && requestCode == CropImage.PICK_IMAGE_CHOOSER_REQUEST_CODE) {
                    Logger.d("... going to pass this result to FirstAddCardFragment");
                    c.onActivityResult(requestCode, resultCode, data);
                }
                if (c.getClass().getSimpleName().equals("FirstEditCardFragment") && requestCode == CropImage.PICK_IMAGE_CHOOSER_REQUEST_CODE) {
                    Logger.d("... going to pass this result to FirstEditCardFragment");
                    c.onActivityResult(requestCode, resultCode, data);
                }
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        Logger.d(">>>");
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        Logger.d(">>>");
        Logger.clearLogAdapters();
        try {
            CardManager.getInstance(this).unsubscribe();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        try {
            CommentManager.getInstance(this).unsubscribe();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    @Override
    public void onBackToFirstMainFragment() {
        Logger.d(">>>");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mBottomBarFirst.performClick();
            }
        });
    }
}
