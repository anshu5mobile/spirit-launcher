// Copyright (c) Spirit Launcher
package com.spirit.launcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.Tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Ely.by account se game chalane ke liye authlib-injector.jar chahiye.
 * ensureJar() ek baar download karta hai (cache), jvmArg() launch args me add karna hai.
 * Background thread me chalao.
 */
public final class AuthlibInjector {
    private static final String RELEASE_API =
            "https://api.github.com/repos/yushijinhun/authlib-injector/releases/latest";

    private AuthlibInjector() {}

    public static File ensureJar(File dir) throws IOException {
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);

        File[] existing = dir.listFiles((d, name) -> name.startsWith("authlib-injector") && name.endsWith(".jar"));
        if (existing != null && existing.length > 0) return existing[0];

        String json = new String(fetch(RELEASE_API), StandardCharsets.UTF_8);
        JsonObject release = Tools.GLOBAL_GSON.fromJson(json, JsonObject.class);
        JsonArray assets = release.getAsJsonArray("assets");
        if (assets != null) {
            for (JsonElement el : assets) {
                JsonObject a = el.getAsJsonObject();
                String name = a.get("name").getAsString();
                if (name.endsWith(".jar")) {
                    byte[] data = fetch(a.get("browser_download_url").getAsString());
                    File tmp = new File(dir, name + ".part");
                    File out = new File(dir, name);
                    try (FileOutputStream fos = new FileOutputStream(tmp)) {
                        fos.write(data);
                    }
                    if (!tmp.renameTo(out)) throw new IOException("Could not save " + name);
                    return out;
                }
            }
        }
        throw new IOException("authlib-injector jar not found in latest release");
    }

    /** JVM argument jo launch args me jodna hai. */
    public static String jvmArg(File jar) {
        return "-javaagent:" + jar.getAbsolutePath() + "=" + ElyByAuth.INJECTOR_API_URL;
    }

    private static byte[] fetch(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "SpiritLauncher");
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode() + " for " + url);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
