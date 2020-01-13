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

package com.android.overlaytest.remounted;

import static org.junit.Assert.assertTrue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import org.junit.Assert;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;

class SystemPreparer extends ExternalResource {
    private static final long REBOOT_SLEEP_MS = 30000;
    private static final long OVERLAY_ENABLE_TIMEOUT_MS = 20000;

    // The paths of the files pushed onto the device through this rule.
    private ArrayList<String> mPushedFiles = new ArrayList<>();

    // The package names of packages installed through this rule.
    private ArrayList<String> mInstalledPackages = new ArrayList<>();

    private final TemporaryFolder mHostTempFolder;
    private final DeviceProvider mDeviceProvider;

    SystemPreparer(TemporaryFolder hostTempFolder, DeviceProvider deviceProvider) {
        mHostTempFolder = hostTempFolder;
        mDeviceProvider = deviceProvider;
    }

    /** Copies a file within the host test jar to a path on device. */
    SystemPreparer pushResourceFile(String resourcePath,
            String outputPath) throws DeviceNotAvailableException, IOException {
        final ITestDevice device = mDeviceProvider.getDevice();
        assertTrue(device.pushFile(copyResourceToTemp(resourcePath), outputPath));
        mPushedFiles.add(outputPath);
        return this;
    }

    /** Installs an APK within the host test jar onto the device. */
    SystemPreparer installResourceApk(String resourcePath, String packageName)
            throws DeviceNotAvailableException, IOException {
        final ITestDevice device = mDeviceProvider.getDevice();
        final File tmpFile = copyResourceToTemp(resourcePath);
        final String result = device.installPackage(tmpFile, true);
        Assert.assertNull(result);
        mInstalledPackages.add(packageName);
        return this;
    }

    /** Sets the enable state of an overlay pacakage. */
    SystemPreparer setOverlayEnabled(String packageName, boolean enabled)
            throws ExecutionException, TimeoutException {
        final ITestDevice device = mDeviceProvider.getDevice();

        // Wait for the overlay to change its enabled state.
        final FutureTask<Boolean> enabledListener = new FutureTask<>(() -> {
            while (true) {
                device.executeShellCommand(String.format("cmd overlay %s %s",
                        enabled ? "enable" : "disable", packageName));

                final String pattern = (enabled ? "[x]" : "[ ]") + " " + packageName;
                if (device.executeShellCommand("cmd overlay list").contains(pattern)) {
                    return true;
                }
            }
        });

        final Executor executor = (cmd) -> new Thread(cmd).start();
        executor.execute(enabledListener);
        try {
            enabledListener.get(OVERLAY_ENABLE_TIMEOUT_MS, MILLISECONDS);
        } catch (InterruptedException ignored) {
        }

        return this;
    }

    /** Restarts the device and waits until after boot is completed. */
    SystemPreparer reboot() throws DeviceNotAvailableException {
        final ITestDevice device = mDeviceProvider.getDevice();
        device.executeShellCommand("stop");
        device.executeShellCommand("start");
        try {
            // Sleep until the device is ready for test execution.
            Thread.sleep(REBOOT_SLEEP_MS);
        } catch (InterruptedException ignored) {
        }

        return this;
    }

    /** Copies a file within the host test jar to a temporary file on the host machine. */
    private File copyResourceToTemp(String resourcePath) throws IOException {
        final File tempFile = mHostTempFolder.newFile(resourcePath);
        final ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream assetIs = classLoader.getResource(resourcePath).openStream();
             FileOutputStream assetOs = new FileOutputStream(tempFile)) {
            if (assetIs == null) {
                throw new IllegalStateException("Failed to find resource " + resourcePath);
            }

            int b;
            while ((b = assetIs.read()) >= 0) {
                assetOs.write(b);
            }
        }

        return tempFile;
    }

    /** Removes installed packages and files that were pushed to the device. */
    @Override
    protected void after() {
        final ITestDevice device = mDeviceProvider.getDevice();
        try {
            for (final String file : mPushedFiles) {
                device.deleteFile(file);
            }
            for (final String packageName : mInstalledPackages) {
                device.uninstallPackage(packageName);
            }
        } catch (DeviceNotAvailableException e) {
            Assert.fail(e.toString());
        }
    }

    interface DeviceProvider {
        ITestDevice getDevice();
    }
}