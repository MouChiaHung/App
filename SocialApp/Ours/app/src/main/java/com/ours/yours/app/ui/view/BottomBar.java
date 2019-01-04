package com.ours.yours.app.ui.view;

import android.content.Context;
import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.List;

public class BottomBar extends LinearLayout {
    private LinearLayout mTabsContainer;
    private LayoutParams mLayoutParamsForChild;
    private List<BottomBarTab> mTabs = new ArrayList<BottomBarTab>(); //holds references without creating instance
    private int mCurrentPosition = 0; //needs to be stored in parcelable

    public BottomBar(Context context) {
        this(context, null);
    }

    public BottomBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BottomBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attrs) {
        Logger.d(">>>");
        setOrientation(VERTICAL);
        mTabsContainer = new LinearLayout(context);
        mTabsContainer.setBackgroundColor(Color.WHITE);
        mTabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        addView(mTabsContainer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mLayoutParamsForChild = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        mLayoutParamsForChild.weight = 1;
    }

    public void addTab(final BottomBarTab tab) {
        Logger.d(">>>");
        tab.setLayoutParams(mLayoutParamsForChild);
        mTabsContainer.addView(tab);
        mTabs.add(tab);
    }

    public int getCurrentPosition() {
        return mCurrentPosition;
    }

    public void setCurrentPosition(int pos) {
        mCurrentPosition = pos;
    }

    public void setTabSelectHint(int index, boolean isSelected) {
        //((BottomBarTab) mTabsContainer.getChildAt(index)).setSelected(isSelected);
        if (mTabs.get(index) != null) {
            mTabs.get(index).setSelected(isSelected);
        } else {
            Logger.e("!!! mTabs.get(index) is null ");
        }
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        Logger.d(">>>");
        Parcelable savedState = super.onSaveInstanceState();
        return new BottomBarSavedState(savedState, mCurrentPosition);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        Logger.d(">>>");
        BottomBarSavedState savedState = (BottomBarSavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        if (mCurrentPosition != savedState.mSavedCurrentPosition) {
            mTabs.get(mCurrentPosition).setSelected(false);
            mTabs.get(savedState.mSavedCurrentPosition).setSelected(true);
        }
        mCurrentPosition = savedState.mSavedCurrentPosition;
    }

    static class BottomBarSavedState extends BaseSavedState {
        private int mSavedCurrentPosition;

        public BottomBarSavedState(Parcelable source, int position) {
            super(source);
            this.mSavedCurrentPosition = position;
        }

        public BottomBarSavedState(Parcel source) {
            super(source);
            this.mSavedCurrentPosition = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(mSavedCurrentPosition);
        }

        public static final Parcelable.Creator<BottomBarSavedState> CREATOR = new Parcelable.Creator<BottomBarSavedState>() {
            @Override
            public BottomBarSavedState createFromParcel(Parcel source) {
                return new BottomBarSavedState(source);
            }

            @Override
            public BottomBarSavedState[] newArray(int size) {
                return new BottomBarSavedState[size];
            }
        };
    }
}
