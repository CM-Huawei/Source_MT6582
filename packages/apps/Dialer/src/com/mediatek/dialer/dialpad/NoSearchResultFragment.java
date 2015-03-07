package com.mediatek.dialer.dialpad;

import android.app.Fragment;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;
import com.android.dialer.R;

/**
 * M: support tablet landscape
 * This fragment is used to display the empty result in tablet landscape mode.
 * And it is also a place holder in the layout, which will be repalced by 
 * RegularSearchFragment or SmartDialSearchFragment when dial pad is in search
 * mode
 */
public class NoSearchResultFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
            Bundle savedInstanceState) {
        final Resources resources = getResources();
        TextView v = new TextView(getActivity());
        v.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 
                LayoutParams.MATCH_PARENT));
        v.setGravity(Gravity.CENTER);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.dialpad_key_special_characters_size));
        v.setText(getString(R.string.searchFrame_title));
        return v;
    }
}
