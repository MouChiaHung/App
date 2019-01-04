package com.ours.yours.app.ui.fragment.third;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.base.BaseMainFragment;
import com.ours.yours.app.ui.fragment.third.child.ThirdHomeFragment;

public class ThirdFragment extends BaseMainFragment {
    private View mView;

    public ThirdFragment() {
        Logger.d(">>>");
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Logger.d(">>>");
        //super.onCreateView(inflater, container, savedInstanceState);
        mView = inflater.inflate(R.layout.app_third_main, container, false);
        return mView;
    }

    /**
     * called when VisibleDelegate#onActivityCreated() if VisibleDelegate#mIsFirstVisible is true
     */
    @Override
    public void onLazyInitView(@Nullable Bundle savedInstanceState) {
        Logger.d(">>>");
        super.onLazyInitView(savedInstanceState);
        /**
         * instantiates child fragment manager belonging to third main fragment
         */
        Logger.d("... going to loadRootFragment() for child fragment home");
        loadRootFragment(R.id.fl_third_container, ThirdHomeFragment.newInstance());
    }
}
