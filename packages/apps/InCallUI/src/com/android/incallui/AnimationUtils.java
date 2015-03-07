/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.incallui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.ImageView;

/**
 * Utilities for Animation.
 */
public class AnimationUtils {
    private static final String LOG_TAG = AnimationUtils.class.getSimpleName();
    /**
     * Turn on when you're interested in fading animation. Intentionally untied from other debug
     * settings.
     */
    private static final boolean FADE_DBG = false;

    /**
     * Duration for animations in msec, which can be used with
     * {@link ViewPropertyAnimator#setDuration(long)} for example.
     */
    public static final int ANIMATION_DURATION = 250;

    private AnimationUtils() {
    }

    /**
     * Simple Utility class that runs fading animations on specified views.
     */
    public static class Fade {

        // View tag that's set during the fade-out animation; see hide() and
        // isFadingOut().
        private static final int FADE_STATE_KEY = R.id.fadeState;
        private static final String FADING_OUT = "fading_out";

        /**
         * Sets the visibility of the specified view to View.VISIBLE and then
         * fades it in. If the view is already visible (and not in the middle
         * of a fade-out animation), this method will return without doing
         * anything.
         *
         * @param view The view to be faded in
         */
        public static void show(final View view) {
            if (FADE_DBG) log("Fade: SHOW view " + view + "...");
            if (FADE_DBG) log("Fade: - visibility = " + view.getVisibility());
            if ((view.getVisibility() != View.VISIBLE) || isFadingOut(view)) {
                view.animate().cancel();
                // ...and clear the FADE_STATE_KEY tag in case we just
                // canceled an in-progress fade-out animation.
                view.setTag(FADE_STATE_KEY, null);

                view.setAlpha(0);
                view.setVisibility(View.VISIBLE);
                view.animate().setDuration(ANIMATION_DURATION);
                view.animate().alpha(1);
                if (FADE_DBG) log("Fade: ==> SHOW " + view
                                  + " DONE.  Set visibility = " + View.VISIBLE);
            } else {
                if (FADE_DBG) log("Fade: ==> Ignoring, already visible AND not fading out.");
            }
        }

        /**
         * Fades out the specified view and then sets its visibility to the
         * specified value (either View.INVISIBLE or View.GONE). If the view
         * is not currently visibile, the method will return without doing
         * anything.
         *
         * Note that *during* the fade-out the view itself will still have
         * visibility View.VISIBLE, although the isFadingOut() method will
         * return true (in case the UI code needs to detect this state.)
         *
         * @param view The view to be hidden
         * @param visibility The value to which the view's visibility will be
         *                   set after it fades out.
         *                   Must be either View.INVISIBLE or View.GONE.
         */
        public static void hide(final View view, final int visibility) {
            if (FADE_DBG) log("Fade: HIDE view " + view + "...");
            if (view.getVisibility() == View.VISIBLE &&
                (visibility == View.INVISIBLE || visibility == View.GONE)) {

                // Use a view tag to mark this view as being in the middle
                // of a fade-out animation.
                view.setTag(FADE_STATE_KEY, FADING_OUT);

                view.animate().cancel();
                view.animate().setDuration(ANIMATION_DURATION);
                view.animate().alpha(0f).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setAlpha(1);
                        view.setVisibility(visibility);
                        view.animate().setListener(null);
                        // ...and we're done with the fade-out, so clear the view tag.
                        view.setTag(FADE_STATE_KEY, null);
                        if (FADE_DBG) log("Fade: HIDE " + view
                                + " DONE.  Set visibility = " + visibility);
                    }
                });
            }
        }

        /**
         * @return true if the specified view is currently in the middle
         * of a fade-out animation.  (During the fade-out, the view's
         * visibility is still VISIBLE, although in many cases the UI
         * should behave as if it's already invisible or gone.  This
         * method allows the UI code to detect that state.)
         *
         * @see #hide(View, int)
         */
        public static boolean isFadingOut(final View view) {
            if (FADE_DBG) {
                log("Fade: isFadingOut view " + view + "...");
                log("Fade:   - getTag() returns: " + view.getTag(FADE_STATE_KEY));
                log("Fade:   - returning: " + (view.getTag(FADE_STATE_KEY) == FADING_OUT));
            }
            return (view.getTag(FADE_STATE_KEY) == FADING_OUT);
        }

    }

    /**
     * Drawable achieving cross-fade, just like TransitionDrawable. We can have
     * call-backs via animator object (see also {@link CrossFadeDrawable#getAnimator()}).
     */
    private static class CrossFadeDrawable extends LayerDrawable {
        private final ObjectAnimator mAnimator;

        public CrossFadeDrawable(Drawable[] layers) {
            super(layers);
            mAnimator = ObjectAnimator.ofInt(this, "crossFadeAlpha", 0xff, 0);
        }

        private int mCrossFadeAlpha;

        /**
         * This will be used from ObjectAnimator.
         * Note: this method is protected by proguard.flags so that it won't be removed
         * automatically.
         */
        @SuppressWarnings("unused")
        public void setCrossFadeAlpha(int alpha) {
            mCrossFadeAlpha = alpha;
            invalidateSelf();
        }

        public ObjectAnimator getAnimator() {
            return mAnimator;
        }

        @Override
        public void draw(Canvas canvas) {
            Drawable first = getDrawable(0);
            Drawable second = getDrawable(1);

            if (mCrossFadeAlpha > 0) {
                first.setAlpha(mCrossFadeAlpha);
                first.draw(canvas);
                first.setAlpha(255);
            }

            if (mCrossFadeAlpha < 0xff) {
                second.setAlpha(0xff - mCrossFadeAlpha);
                second.draw(canvas);
                second.setAlpha(0xff);
            }
        }
    }

    private static CrossFadeDrawable newCrossFadeDrawable(Drawable first, Drawable second) {
        Drawable[] layers = new Drawable[2];
        layers[0] = first;
        layers[1] = second;
        return new CrossFadeDrawable(layers);
    }

    /**
     * Starts cross-fade animation using TransitionDrawable. Nothing will happen if "from" and "to"
     * are the same.
     */
    public static void startCrossFade(
            final ImageView imageView, final Drawable from, final Drawable to) {
        // We skip the cross-fade when those two Drawables are equal, or they are BitmapDrawables
        // pointing to the same Bitmap.
        final boolean drawableIsEqual = (from != null && to != null && from.equals(to));
        final boolean hasFromImage = ((from instanceof BitmapDrawable) &&
                ((BitmapDrawable) from).getBitmap() != null);
        final boolean hasToImage = ((to instanceof BitmapDrawable) &&
                ((BitmapDrawable) to).getBitmap() != null);
        final boolean areSameImage = drawableIsEqual || (hasFromImage && hasToImage &&
                ((BitmapDrawable) from).getBitmap().equals(((BitmapDrawable) to).getBitmap()));

        if (!areSameImage) {
            if (FADE_DBG) {
                log("Start cross-fade animation for " + imageView
                        + "(" + Integer.toHexString(from.hashCode()) + " -> "
                        + Integer.toHexString(to.hashCode()) + ")");
            }

            CrossFadeDrawable crossFadeDrawable = newCrossFadeDrawable(from, to);
            ObjectAnimator animator = crossFadeDrawable.getAnimator();
            imageView.setImageDrawable(crossFadeDrawable);
            animator.setDuration(ANIMATION_DURATION);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (FADE_DBG) {
                        log("cross-fade animation start ("
                                + Integer.toHexString(from.hashCode()) + " -> "
                                + Integer.toHexString(to.hashCode()) + ")");
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (FADE_DBG) {
                        log("cross-fade animation ended ("
                                + Integer.toHexString(from.hashCode()) + " -> "
                                + Integer.toHexString(to.hashCode()) + ")");
                    }
                    animation.removeAllListeners();
                    // Workaround for issue 6300562; this will force the drawable to the
                    // resultant one regardless of animation glitch.
                    imageView.setImageDrawable(to);
                }
            });
            animator.start();

            /* We could use TransitionDrawable here, but it may cause some weird animation in
             * some corner cases. See issue 6300562
             * TODO: decide which to be used in the long run. TransitionDrawable is old but system
             * one. Ours uses new animation framework and thus have callback (great for testing),
             * while no framework support for the exact class.

            Drawable[] layers = new Drawable[2];
            layers[0] = from;
            layers[1] = to;
            TransitionDrawable transitionDrawable = new TransitionDrawable(layers);
            imageView.setImageDrawable(transitionDrawable);
            transitionDrawable.startTransition(ANIMATION_DURATION); */
            imageView.setTag(to);
        } else if (!hasFromImage && hasToImage) {
            imageView.setImageDrawable(to);
            imageView.setTag(to);
        } else {
            if (FADE_DBG) {
                log("*Not* start cross-fade. " + imageView);
            }
        }
    }

    // Debugging / testing code

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}