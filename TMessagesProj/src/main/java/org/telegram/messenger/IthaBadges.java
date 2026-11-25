package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Insignias de apoyo. La lista vive privada en un Worker; aqui solo se pregunta por los
 * usuarios que se estan viendo y se verifica la firma de la respuesta.
 */
public class IthaBadges {

    private static final String TAG = "IthaDiag";
    private static final String ENDPOINT = "https://ithagram-insignias.itha131932.workers.dev/v1/badges";
    private static final String PUBLIC_KEY = "XAV2ZqIcpQQ2oO6NxL2V3lwme9e8qwlTSPOnhrf5Tps=";
    private static final String PREFS = "itha_badges";
    private static final long TTL_SECONDS = 10 * 60;
    private static final long RETRY_MS = 60 * 1000;
    private static final long MAX_SKEW_SECONDS = 2 * 24 * 60 * 60;
    private static final int MAX_IDS = 50;

    private static final class Entry {
        final List<String> kinds;
        final long fetchedAt;
        Entry(List<String> kinds, long fetchedAt) {
            this.kinds = kinds;
            this.fetchedAt = fetchedAt;
        }
    }

    private static final Object LOCK = new Object();
    private static final Map<Long, Entry> cache = new ConcurrentHashMap<>();
    private static final Set<Long> pending = new HashSet<>();
    private static final Map<Long, List<Runnable>> waiters = new HashMap<>();
    private static boolean loaded;
    private static boolean flushScheduled;
    private static long retryAfter;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void ensureLoaded() {
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            loaded = true;
            try {
                for (Map.Entry<String, ?> e : prefs().getAll().entrySet()) {
                    if (!e.getKey().startsWith("b_") || !(e.getValue() instanceof String)) {
                        continue;
                    }
                    String[] parts = ((String) e.getValue()).split("\\|", -1);
                    if (parts.length != 2) {
                        continue;
                    }
                    List<String> kinds = new ArrayList<>();
                    for (String k : parts[0].split(",")) {
                        if (IthaBadgeDrawable.isKnown(k)) {
                            kinds.add(k);
                        }
                    }
                    cache.put(Long.parseLong(e.getKey().substring(2)), new Entry(kinds, Long.parseLong(parts[1])));
                }
            } catch (Throwable t) {
                Log.w(TAG, "insignias: cache ilegible", t);
            }
        }
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }

    /** Devuelve el dibujo de la insignia principal del usuario, o null. Si no se conoce aun, la pide. */
    public static Drawable getDrawable(long userId, int sizeDp, Runnable onUpdate) {
        if (userId <= 0) {
            return null;
        }
        ensureLoaded();
        Entry entry = cache.get(userId);
        if (entry == null || now() - entry.fetchedAt > TTL_SECONDS) {
            request(userId, onUpdate);
        }
        return entry != null && !entry.kinds.isEmpty() ? new IthaBadgeDrawable(entry.kinds.get(0), sizeDp) : null;
    }

    /** Insignias conocidas del usuario (vacia si no tiene). Si no se saben aun, las pide y avisa con onUpdate. */
    public static List<String> kindsFor(long userId, Runnable onUpdate) {
        if (userId <= 0) {
            return new ArrayList<>();
        }
        ensureLoaded();
        Entry entry = cache.get(userId);
        if (entry == null || now() - entry.fetchedAt > TTL_SECONDS) {
            request(userId, onUpdate);
        }
        return entry != null ? entry.kinds : new ArrayList<String>();
    }

    /** Solo mira lo que ya se sabe, sin pedir nada. */
    public static Drawable peekDrawable(long userId, int sizeDp) {
        if (userId <= 0) {
            return null;
        }
        ensureLoaded();
        Entry entry = cache.get(userId);
        return entry != null && !entry.kinds.isEmpty() ? new IthaBadgeDrawable(entry.kinds.get(0), sizeDp) : null;
    }

    private static void request(long userId, Runnable onUpdate) {
        synchronized (LOCK) {
            if (onUpdate != null) {
                List<Runnable> list = waiters.get(userId);
                if (list == null) {
                    list = new ArrayList<>();
                    waiters.put(userId, list);
                }
                if (list.size() < 5) {
                    list.add(onUpdate);
                }
            }
            pending.add(userId);
            if (!flushScheduled) {
                flushScheduled = true;
                long delay = Math.max(400, retryAfter - System.currentTimeMillis());
                Utilities.globalQueue.postRunnable(IthaBadges::flush, delay);
            }
        }
    }

    private static void flush() {
        List<Long> ids = new ArrayList<>();
        synchronized (LOCK) {
            flushScheduled = false;
            Iterator<Long> it = pending.iterator();
            while (it.hasNext() && ids.size() < MAX_IDS) {
                ids.add(it.next());
                it.remove();
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        boolean ok = false;
        try {
            ok = fetch(ids);
        } catch (Throwable t) {
            Log.w(TAG, "insignias: fallo de red", t);
        }
        synchronized (LOCK) {
            if (!ok) {
                retryAfter = System.currentTimeMillis() + RETRY_MS;
                waiters.keySet().removeAll(ids);
            }
            if (!pending.isEmpty() && !flushScheduled) {
                flushScheduled = true;
                Utilities.globalQueue.postRunnable(IthaBadges::flush, ok ? 400 : RETRY_MS);
            }
        }
    }

    private static boolean fetch(List<Long> ids) throws Exception {
        JSONArray arr = new JSONArray();
        for (Long id : ids) {
            arr.put(String.valueOf(id));
        }
        byte[] body = new JSONObject().put("ids", arr).toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = c.getOutputStream()) {
            os.write(body);
        }
        if (c.getResponseCode() != 200) {
            Log.w(TAG, "insignias: respuesta " + c.getResponseCode());
            return false;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream is = c.getInputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0 && bos.size() < 256 * 1024) {
                bos.write(buf, 0, n);
            }
        }
        JSONObject resp = new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
        String payload = resp.getString("payload");
        byte[] sig = Base64.decode(resp.getString("sig"), Base64.DEFAULT);
        byte[] key = Base64.decode(PUBLIC_KEY, Base64.DEFAULT);
        if (!IthaEd25519.verify(key, payload.getBytes(StandardCharsets.UTF_8), sig)) {
            Log.w(TAG, "insignias: firma invalida, se ignora la respuesta");
            return false;
        }
        JSONObject data = new JSONObject(payload);
        if (Math.abs(now() - data.getLong("ts")) > MAX_SKEW_SECONDS) {
            Log.w(TAG, "insignias: respuesta fuera de fecha");
            return false;
        }
        JSONObject badges = data.getJSONObject("badges");
        long t = now();
        SharedPreferences.Editor editor = prefs().edit();
        List<Runnable> toRun = new ArrayList<>();
        for (Long id : ids) {
            List<String> kinds = new ArrayList<>();
            JSONArray list = badges.optJSONArray(String.valueOf(id));
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    String k = list.optString(i);
                    if (IthaBadgeDrawable.isKnown(k)) {
                        kinds.add(k);
                    }
                }
            }
            Entry old = cache.get(id);
            cache.put(id, new Entry(kinds, t));
            editor.putString("b_" + id, android.text.TextUtils.join(",", kinds) + "|" + t);
            boolean changed = old == null ? !kinds.isEmpty() : !old.kinds.equals(kinds);
            synchronized (LOCK) {
                List<Runnable> w = waiters.remove(id);
                if (w != null && changed) {
                    toRun.addAll(w);
                }
            }
        }
        editor.apply();
        if (!toRun.isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> {
                for (Runnable r : toRun) {
                    try {
                        r.run();
                    } catch (Throwable ignore) {
                    }
                }
            });
        }
        return true;
    }
}
