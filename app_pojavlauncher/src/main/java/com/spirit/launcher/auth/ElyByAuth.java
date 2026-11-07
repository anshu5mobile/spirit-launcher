// Copyright (c) Spirit Launcher
package com.spirit.launcher.auth;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Ely.by login (Yggdrasil API). Saare methods network use karte hain,
 * isliye main thread pe MAT chalao (background thread / executor me chalao).
 */
public final class ElyByAuth {
    public static final String AUTH_URL = "https://authserver.ely.by/auth";
    public static final String INJECTOR_API_URL = "https://authserver.ely.by/api/authlib-injector";
    private static final String TAG = "ElyByAuth";

    private ElyByAuth() {}

    public static class ElyByException extends Exception {
        public final boolean requires2FA;
        public final boolean relogin;
        public final int httpStatus;

        public ElyByException(String message, int httpStatus, boolean requires2FA, boolean relogin, Throwable cause) {
            super(message, cause);
            this.httpStatus = httpStatus;
            this.requires2FA = requires2FA;
            this.relogin = relogin;
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static JsonObject post(String endpoint, JsonObject body) throws ElyByException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(AUTH_URL + endpoint).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            c.setDoOutput(true);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : readAll(in);

            JsonObject json = null;
            if (!text.trim().isEmpty()) {
                try {
                    json = Tools.GLOBAL_GSON.fromJson(text, JsonObject.class);
                } catch (Exception ignored) { /* non-json response */ }
            }

            if (code >= 400) {
                String msg = null;
                if (json != null) {
                    msg = str(json, "errorMessage");
                    if (msg == null) msg = str(json, "error");
                }
                if (msg == null) msg = "Ely.by error (HTTP " + code + ")";
                boolean twoFa = msg.toLowerCase(Locale.ROOT).contains("two factor");
                throw new ElyByException(msg, code, twoFa, false, null);
            }
            return json != null ? json : new JsonObject();
        } catch (IOException e) {
            throw new ElyByException("Network error: " + e.getMessage(), -1, false, false, e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    @Nullable
    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? null : e.getAsString();
    }

    static String dashUuid(String id) {
        if (id == null) return null;
        String s = id.replace("-", "");
        if (s.length() != 32) return id;
        return s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16)
                + "-" + s.substring(16, 20) + "-" + s.substring(20);
    }

    private static MinecraftAccount toAccount(JsonObject res, String clientToken) throws ElyByException {
        JsonObject profile = (res.has("selectedProfile") && res.get("selectedProfile").isJsonObject())
                ? res.getAsJsonObject("selectedProfile") : null;
        String token = str(res, "accessToken");
        if (profile == null || token == null || str(profile, "name") == null) {
            throw new ElyByException("This Ely.by account has no Minecraft profile", -1, false, false, null);
        }
        MinecraftAccount acc = new MinecraftAccount();
        acc.accountType = MinecraftAccount.TYPE_ELYBY;
        acc.isMicrosoft = false;
        acc.username = str(profile, "name");
        acc.profileId = dashUuid(str(profile, "id"));
        acc.accessToken = token;
        acc.clientToken = clientToken;
        return acc;
    }

    public static MinecraftAccount login(String usernameOrEmail, String password, @Nullable String totp)
            throws ElyByException {
        if (usernameOrEmail == null || usernameOrEmail.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new ElyByException("Enter your Ely.by username/email and password", -1, false, false, null);
        }
        String clientToken = UUID.randomUUID().toString().replace("-", "");
        JsonObject body = new JsonObject();
        body.addProperty("username", usernameOrEmail.trim());
        body.addProperty("password", (totp != null && !totp.trim().isEmpty()) ? password + ":" + totp.trim() : password);
        body.addProperty("clientToken", clientToken);
        body.addProperty("requestUser", true);
        return toAccount(post("/authenticate", body), clientToken);
    }

    public static String addAccount(String usernameOrEmail, String password, @Nullable String totp)
            throws ElyByException {
        MinecraftAccount acc = login(usernameOrEmail, password, totp);

        MinecraftAccount existing = MinecraftAccount.load(acc.username);
        if (existing != null && !existing.isElyby()) {
            throw new ElyByException("An offline/Microsoft account named \"" + acc.username
                    + "\" already exists. Remove it first.", -1, false, false, null);
        }

        acc.updateSkinFace();
        try {
            return acc.save();
        } catch (IOException e) {
            throw new ElyByException("Could not save account: " + e.getMessage(), -1, false, false, e);
        }
    }

    public static boolean isValid(MinecraftAccount acc) {
        JsonObject body = new JsonObject();
        body.addProperty("accessToken", acc.accessToken);
        try {
            post("/validate", body);
            return true;
        } catch (ElyByException e) {
            return !(e.httpStatus >= 400 && e.httpStatus < 500);
        }
    }

    public static void ensureValid(MinecraftAccount acc) throws ElyByException {
        if (isValid(acc)) return;

        JsonObject body = new JsonObject();
        body.addProperty("accessToken", acc.accessToken);
        body.addProperty("clientToken", acc.clientToken);
        body.addProperty("requestUser", true);
        try {
            JsonObject res = post("/refresh", body);
            String token = str(res, "accessToken");
            if (token == null) throw new ElyByException("Refresh failed", -1, false, true, null);
            acc.accessToken = token;
            acc.save();
        } catch (ElyByException e) {
            if (e.httpStatus >= 400 && e.httpStatus < 500) {
                throw new ElyByException("Ely.by session expired, please log in again", e.httpStatus, false, true, e);
            }
            throw e;
        } catch (IOException e) {
            throw new ElyByException("Could not save account: " + e.getMessage(), -1, false, false, e);
        }
    }

    public static boolean downloadFace(String username, File out) {
        HttpURLConnection c = null;
        try {
            String url = "https://skinsystem.ely.by/skins/" + URLEncoder.encode(username, "UTF-8") + ".png";
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            if (c.getResponseCode() != 200) return false;

            Bitmap skin;
            try (InputStream in = c.getInputStream()) {
                skin = BitmapFactory.decodeStream(in);
            }
            if (skin == null || skin.getWidth() < 64) return false;

            int s = skin.getWidth() / 64;
            Rect dst = new Rect(0, 0, 8 * s, 8 * s);
            Bitmap head = Bitmap.createBitmap(8 * s, 8 * s, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(head);
            Paint p = new Paint();
            p.setFilterBitmap(false);
            canvas.drawBitmap(skin, new Rect(8 * s, 8 * s, 16 * s, 16 * s), dst, p);
            canvas.drawBitmap(skin, new Rect(40 * s, 8 * s, 48 * s, 16 * s), dst, p);

            Bitmap scaled = Bitmap.createScaledBitmap(head, 100, 100, false);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                scaled.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Could not download Ely.by skin face", e);
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
