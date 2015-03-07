package com.android.camera.manager;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.Camera;
import com.android.camera.Log;
import com.android.camera.R;

public class ShowCSSpeedManager extends ViewManager {

    private TextView mCSInfoView;
    private SpannableString mSpannableString;
    
    // because the CS number is 01~99
    // so the just need two number [two digit]
    private static final int BEGINING_LOCATION = 0;
    private static final int END_LOCATION = 2;
    private static final float FRONT_SIZE_OF_CS_NUMBER = 1.7f;
    public ShowCSSpeedManager(Camera context) {
        super(context);
    }

    @Override
    protected View getView() {
        View view = inflate(R.layout.onscreen_cs_speed);
        mCSInfoView = (TextView)view.findViewById(R.id.cs_info_view);
        return view;
    }

    @Override
    protected void onRefresh() {
        if (mCSInfoView != null) {
          mSpannableString.setSpan(new RelativeSizeSpan(FRONT_SIZE_OF_CS_NUMBER), BEGINING_LOCATION, END_LOCATION, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          mSpannableString.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), BEGINING_LOCATION, END_LOCATION, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//          mSpannableString.setSpan(new ForegroundColorSpan(Color.MAGENTA), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          mCSInfoView.setText(mSpannableString);
          int visibility = mCSInfoView != null ? View.VISIBLE : View.INVISIBLE;
          mCSInfoView.setVisibility(visibility);
      }
    }

    public void showText(String text) {
        mSpannableString = new SpannableString(text);
        show();
    }

}
