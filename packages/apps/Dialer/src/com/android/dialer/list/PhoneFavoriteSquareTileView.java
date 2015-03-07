/*

 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.dialer.list;

import android.content.Context;
import android.provider.ContactsContract.QuickContact;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import com.android.contacts.common.R;
import com.android.contacts.common.list.ContactEntry;

import java.util.regex.Pattern;

/**
 * Displays the contact's picture overlayed with their name
 * in a perfect square. It also has an additional touch target for a secondary action.
 */
public class PhoneFavoriteSquareTileView extends PhoneFavoriteTileView {
    private static final String TAG = PhoneFavoriteSquareTileView.class.getSimpleName();
    private ImageButton mSecondaryButton;

    // TODO: Use a more expansive name token separator if needed. For now it should be fine to
    // not split by dashes, underscore etc.
    private static final Pattern NAME_TOKEN_SEPARATOR_PATTERN = Pattern.compile("\\s+");

    public PhoneFavoriteSquareTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSecondaryButton = (ImageButton) findViewById(R.id.contact_tile_secondary_button);
    }

    @Override
    protected int getApproximateImageSize() {
        // The picture is the full size of the tile (minus some padding, but we can be generous)
        return mListener.getApproximateTileWidth();
    }

    private void launchQuickContact() {
        QuickContact.showQuickContact(getContext(), PhoneFavoriteSquareTileView.this,
                getLookupUri(), QuickContact.MODE_LARGE, null);
    }

    @Override
    protected String getNameForView(String name) {
        if (TextUtils.isEmpty(name)) return name;
        final String[] tokens = NAME_TOKEN_SEPARATOR_PATTERN.split(name, 2);
        if (tokens.length < 1) return name;
        return tokens[0];
    }

    @Override
    public void loadFromContact(ContactEntry entry) {
        super.loadFromContact(entry);
        if (entry != null) {
            final boolean contactIsFavorite = entry.isFavorite;
            mSecondaryButton.setVisibility(contactIsFavorite ? GONE : VISIBLE);

            if (contactIsFavorite) {
                mStarView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        launchQuickContact();
                    }
                });
            } else {
                mSecondaryButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        launchQuickContact();
                    }
                });
            }
        }
    }
}
