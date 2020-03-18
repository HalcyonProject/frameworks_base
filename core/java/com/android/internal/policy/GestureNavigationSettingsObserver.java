/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.policy;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

/**
 * @hide
 */
public class GestureNavigationSettingsObserver extends ContentObserver {
    private Context mContext;
    private Runnable mOnChangeRunnable;

    public GestureNavigationSettingsObserver(Handler handler, Context context,
            Runnable onChangeRunnable) {
        super(handler);
        mContext = context;
        mOnChangeRunnable = onChangeRunnable;
    }

    public void register() {
        ContentResolver r = mContext.getContentResolver();
        r.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.BACK_GESTURE_INSET_SCALE_LEFT),
                false, this, UserHandle.USER_ALL);
        r.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.BACK_GESTURE_INSET_SCALE_RIGHT),
                false, this, UserHandle.USER_ALL);
    }

    public void unregister() {
        mContext.getContentResolver().unregisterContentObserver(this);
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        if (mOnChangeRunnable != null) {
            mOnChangeRunnable.run();
        }
    }

    public int getLeftSensitivity(Resources userRes) {
        return getSensitivity(userRes, Settings.Secure.BACK_GESTURE_INSET_SCALE_LEFT);
    }

    public int getRightSensitivity(Resources userRes) {
        return getSensitivity(userRes, Settings.Secure.BACK_GESTURE_INSET_SCALE_RIGHT);
    }

    private int getSensitivity(Resources userRes, String side) {
        final int inset = userRes.getDimensionPixelSize(
                com.android.internal.R.dimen.config_backGestureInset);
        final float scale = Settings.Secure.getFloatForUser(
                mContext.getContentResolver(), side, 1.0f, UserHandle.USER_CURRENT);
        return (int) (inset * scale);
    }
}