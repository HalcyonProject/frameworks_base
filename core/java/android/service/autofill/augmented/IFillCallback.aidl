/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.service.autofill.augmented;

import android.os.ICancellationSignal;

/**
 * Interface to receive the result of an autofill request.
 *
 * @hide
 */
interface IFillCallback {
    // TODO(b/111330312): add cancellation (after we have CTS tests, so we can test it)
//    void onCancellable(in ICancellationSignal cancellation);
    // TODO(b/111330312): might need to pass the response (once IME implements Smart Suggestions)
    void onSuccess();
}
