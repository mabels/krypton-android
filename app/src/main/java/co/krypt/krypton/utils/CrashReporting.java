package co.krypt.krypton.utils;

import com.github.anrwatchdog.ANRError;
import com.github.anrwatchdog.ANRWatchDog;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import co.krypt.krypton.BuildConfig;

/**
 * Created by Kevin King on 3/21/18.
 * Copyright 2018. KryptCo, Inc.
 */

public class CrashReporting {
    private static ANRWatchDog anrWatchDog = null;
    public static synchronized void startANRReporting() {
        if (anrWatchDog == null) {
            anrWatchDog = new ANRWatchDog()
                    .setIgnoreDebugger(true)
                    .setReportMainThreadOnly();
            if (!BuildConfig.DEBUG) {
                anrWatchDog.setANRListener(new ANRWatchDog.ANRListener() {
                    @Override
                    public void onAppNotResponding(ANRError error) {
                        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
                        crashlytics.recordException(error);
                    }
                });
            }
            anrWatchDog.start();
        }

    }
}
