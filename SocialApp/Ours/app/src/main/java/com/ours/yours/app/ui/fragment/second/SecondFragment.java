package com.ours.yours.app.ui.fragment.second;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.base.BaseMainFragment;

public class SecondFragment extends BaseMainFragment {
    private View mView;

    public static SecondFragment newInstance() {

        Bundle args = new Bundle();

        SecondFragment fragment = new SecondFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Logger.d(">>>");
        //super.onCreateView(inflater, container, savedInstanceState);
        mView = inflater.inflate(R.layout.app_second_main, container, false);
        return mView;
    }
}
