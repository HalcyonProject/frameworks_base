/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.controls.management

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.util.LifecycleActivity
import java.util.concurrent.Executor
import javax.inject.Inject

/**
 * Activity to select an application to favorite the [Control] provided by them.
 */
class ControlsProviderSelectorActivity @Inject constructor(
    @Main private val executor: Executor,
    private val listingController: ControlsListingController
) : LifecycleActivity() {

    companion object {
        private const val TAG = "ControlsProviderSelectorActivity"
    }

    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        recyclerView = RecyclerView(applicationContext)
        recyclerView.adapter = AppAdapter(executor, lifecycle, listingController,
                LayoutInflater.from(this), ::launchFavoritingActivity)
        recyclerView.layoutManager = LinearLayoutManager(applicationContext)

        setContentView(recyclerView)
    }

    /**
     * Launch the [ControlsFavoritingActivity] for the specified component.
     * @param component a component name for a [ControlsProviderService]
     */
    fun launchFavoritingActivity(component: ComponentName?) {
        component?.let {
            val intent = Intent(applicationContext, ControlsFavoritingActivity::class.java).apply {
                putExtra(ControlsFavoritingActivity.EXTRA_APP, listingController.getAppLabel(it))
                putExtra(ControlsFavoritingActivity.EXTRA_COMPONENT, it)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
    }
}