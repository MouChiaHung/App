package com.ours.yours.app.ui.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.orhanobut.logger.Logger;
import com.ours.yours.R;

import static android.view.Gravity.CENTER;

public class BottomBarTab extends FrameLayout {
    private Context mContext;
    private ImageView mTabIcon;
    private TextView mTabTitle;
    private int mTabPosition = -1;

    public BottomBarTab(Context context, @DrawableRes int icon, CharSequence title) {
        this(context, null, icon, title);
    }

    public BottomBarTab(Context context, AttributeSet attrs, @DrawableRes int icon, CharSequence title) {
        this(context, attrs, 0, icon, title);
    }

    public BottomBarTab(Context context, AttributeSet attrs, int defStyleAttr, @DrawableRes int icon, CharSequence title) {
        super(context, attrs, defStyleAttr);
        Logger.d("... going to initView()");
        initView(context, icon, title);
    }

    private void initView(Context context, int icon, CharSequence title) {
        mContext = context;
        TypedArray typeArray = context.obtainStyledAttributes(new int[]{R.attr.selectableItemBackgroundBorderless});
        Drawable drawable = typeArray.getDrawable(0);
        setBackground(drawable);
        typeArray.recycle();

        LinearLayout lLContainer = new LinearLayout(context);
        lLContainer.setOrientation(LinearLayout.VERTICAL);
        lLContainer.setGravity(CENTER);
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        lLContainer.setLayoutParams(params);

        mTabIcon = new ImageView(context);
        int pxSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 27, getResources().getDisplayMetrics());
        mTabIcon.setImageResource(icon);
        mTabIcon.setLayoutParams(new LayoutParams(pxSize, pxSize));
        mTabIcon.setColorFilter(ContextCompat.getColor(context, R.color.tab_unselected));
        lLContainer.addView(mTabIcon);

        mTabTitle = new TextView(context);
        mTabTitle.setText(title);
        mTabTitle.setTextSize(10);
        mTabTitle.setTextColor(ContextCompat.getColor(context, R.color.tab_unselected));
        LinearLayout.LayoutParams paramsTitle = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        paramsTitle.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
        mTabTitle.setLayoutParams(paramsTitle);
        lLContainer.addView(mTabTitle);

        addView(lLContainer);
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        Logger.d("... position:" + mTabPosition + " selected:" + selected);
        if (selected) {
            mTabIcon.setColorFilter(ContextCompat.getColor(mContext, R.color.colorPrimary));
            mTabTitle.setTextColor(ContextCompat.getColor(mContext, R.color.colorPrimary));
        } else {
            mTabIcon.setColorFilter(ContextCompat.getColor(mContext, R.color.tab_unselected));
            mTabTitle.setTextColor(ContextCompat.getColor(mContext, R.color.tab_unselected));
        }
    }

    public void setTabPosition(int position) {
        mTabPosition = position;
    }

    public int getTabPosition() {
        return mTabPosition;
    }
}
