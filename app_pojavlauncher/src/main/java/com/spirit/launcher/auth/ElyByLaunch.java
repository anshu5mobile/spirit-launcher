// Copyright (c) Spirit Launcher
package com.spirit.launcher.auth;

import android.os.Looper;

import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.File;

/**
 * Game start karne se pehle Ely.by account ke liye:
 * 1) token validate/refresh, 2) authlib-injector.jar (cache me na ho to download),
 * 3) JVM argument return karta hai jo javaArgList me add karna hai.
 */
public final class ElyByLaunch {
    private ElyByLaunch() {}

    public static String prepare(MinecraftAccount account, File authlibDir) throws Throwable {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return doPrepare(account, authlibDir);
        }

        final String[] result = new String[1];
        final Throwable[] error = new Throwable[1];
        Thread worker = new Thread(() -> {
            try {
                result[0] = doPrepare(account, authlibDir);
            } catch (Throwable t) {
                error[0] = t;
            }
        }, "ElyByLaunchPrepare");
        worker.start();
        worker.join();

        if (error[0] != null) throw error[0];
        return result[0];
    }

    private static String doPrepare(MinecraftAccount account, File authlibDir) throws Exception {
        ElyByAuth.ensureValid(account);
        File jar = AuthlibInjector.ensureJar(authlibDir);
        return AuthlibInjector.jvmArg(jar);
    }
}
