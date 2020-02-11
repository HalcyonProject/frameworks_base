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

package com.android.tests.rollback;

import static com.android.cts.rollback.lib.RollbackInfoSubject.assertThat;
import static com.android.cts.rollback.lib.RollbackUtils.getUniqueRollbackInfoForPackage;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.rollback.RollbackManager;
import android.os.ParcelFileDescriptor;
import android.provider.DeviceConfig;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.TestApp;
import com.android.cts.rollback.lib.RollbackUtils;

import libcore.io.IoUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class NetworkStagedRollbackTest {
    private static final String NETWORK_STACK_CONNECTOR_CLASS =
            "android.net.INetworkStackConnector";
    private static final String PROPERTY_WATCHDOG_REQUEST_TIMEOUT_MILLIS =
            "watchdog_request_timeout_millis";

    private static final TestApp NETWORK_STACK = new TestApp("NetworkStack",
            getNetworkStackPackageName(), -1, false, findNetworkStackApk());

    private static File findNetworkStackApk() {
        final File apk = new File("/system/priv-app/NetworkStack/NetworkStack.apk");
        if (apk.isFile()) {
            return apk;
        }
        return new File("/system/priv-app/NetworkStackNext/NetworkStackNext.apk");
    }

    /**
     * Adopts common shell permissions needed for rollback tests.
     */
    @Before
    public void adoptShellPermissions() {
        InstallUtils.adoptShellPermissionIdentity(
                Manifest.permission.INSTALL_PACKAGES,
                Manifest.permission.DELETE_PACKAGES,
                Manifest.permission.TEST_MANAGE_ROLLBACKS,
                Manifest.permission.FORCE_STOP_PACKAGES,
                Manifest.permission.WRITE_DEVICE_CONFIG);
    }

    /**
     * Drops shell permissions needed for rollback tests.
     */
    @After
    public void dropShellPermissions() {
        InstallUtils.dropShellPermissionIdentity();
    }

    @Test
    public void testNetworkFailedRollback_Phase1() throws Exception {
        // Remove available rollbacks and uninstall NetworkStack on /data/
        RollbackManager rm = RollbackUtils.getRollbackManager();
        String networkStack = getNetworkStackPackageName();

        rm.expireRollbackForPackage(networkStack);
        uninstallNetworkStackPackage();

        assertThat(getUniqueRollbackInfoForPackage(rm.getAvailableRollbacks(),
                networkStack)).isNull();

        // Reduce health check deadline
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ROLLBACK,
                PROPERTY_WATCHDOG_REQUEST_TIMEOUT_MILLIS,
                Integer.toString(120000), false);
        // Simulate re-installation of new NetworkStack with rollbacks enabled
        installNetworkStackPackage();
    }

    @Test
    public void testNetworkFailedRollback_Phase2() throws Exception {
        RollbackManager rm = RollbackUtils.getRollbackManager();
        assertThat(getUniqueRollbackInfoForPackage(rm.getAvailableRollbacks(),
                getNetworkStackPackageName())).isNotNull();

        // Sleep for < health check deadline
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        // Verify rollback was not executed before health check deadline
        assertThat(getUniqueRollbackInfoForPackage(rm.getRecentlyCommittedRollbacks(),
                getNetworkStackPackageName())).isNull();
    }

    @Test
    public void testNetworkFailedRollback_Phase3() throws Exception {
        // Sleep for > health check deadline (120s to trigger rollback + 120s to reboot)
        // The device is expected to reboot during sleeping. This device method will fail and
        // the host will catch the assertion. If reboot doesn't happen, the host will fail the
        // assertion.
        Thread.sleep(TimeUnit.SECONDS.toMillis(240));
    }

    @Test
    public void testNetworkFailedRollback_Phase4() throws Exception {
        RollbackManager rm = RollbackUtils.getRollbackManager();
        assertThat(getUniqueRollbackInfoForPackage(rm.getRecentlyCommittedRollbacks(),
                getNetworkStackPackageName())).isNotNull();
    }

    private static String getNetworkStackPackageName() {
        Intent intent = new Intent(NETWORK_STACK_CONNECTOR_CLASS);
        ComponentName comp = intent.resolveSystemService(
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager(), 0);
        return comp.getPackageName();
    }

    private static void installNetworkStackPackage() throws Exception {
        Install.single(NETWORK_STACK).setStaged().setEnableRollback()
                .addInstallFlags(PackageManager.INSTALL_REPLACE_EXISTING).commit();
    }

    private static void uninstallNetworkStackPackage() {
        // Uninstall the package as a privileged user so we won't fail due to permission.
        runShellCommand("pm uninstall " + getNetworkStackPackageName());
    }

    private static void runShellCommand(String cmd) {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(cmd);
        IoUtils.closeQuietly(pfd);
    }
}