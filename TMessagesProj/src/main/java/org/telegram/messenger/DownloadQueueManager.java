package org.telegram.messenger;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.SystemClock;
import android.util.Base64;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Cola de descargas manual: el usuario agrega archivos desde el menu del mensaje
 * y los baja cuando inicia la cola (con limite de descargas simultaneas y opciones de Wi-Fi).
 */
public class DownloadQueueManager implements NotificationCenter.NotificationCenterDelegate {

    public static final int STATE_PENDING = 0;
    public static final int STATE_LOADING = 1;
    public static final int STATE_DONE = 2;
    public static final int STATE_FAILED = 3;

    public static class Item {
        public int account;
        public String name;
        public long size;
        public long loaded;
        public int state;
        public String fileName;
        public String serialized;
        /** Uri del archivo guardado en Descargas (si ya se guardo). */
        public String savedUri;
        /** Reintentos automaticos tras un error (no se guarda). */
        public int retries;
        public long notBefore;
        private MessageObject message;
        private String displayName;

        /** Nombre real del archivo (el de los atributos del documento), no el nombre interno de la cache. */
        public String getDisplayName() {
            if (displayName == null) {
                try {
                    MessageObject mo = getMessage();
                    if (mo != null && mo.getDocument() != null) {
                        String n = FileLoader.getDocumentFileName(mo.getDocument());
                        if (n != null && !n.isEmpty()) {
                            displayName = n;
                        }
                    }
                } catch (Throwable ignore) {
                }
                if (displayName == null) {
                    displayName = name;
                }
            }
            return displayName;
        }

        public MessageObject getMessage() {
            if (message == null && serialized != null) {
                try {
                    byte[] data = Base64.decode(serialized, Base64.DEFAULT);
                    SerializedData sd = new SerializedData(data);
                    TLRPC.Message msg = TLRPC.Message.TLdeserialize(sd, sd.readInt32(false), false);
                    sd.cleanup();
                    if (msg != null) {
                        message = new MessageObject(account, msg, false, false);
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            return message;
        }
    }

    public interface Listener {
        void onQueueChanged();
    }

    private static volatile DownloadQueueManager instance;

    public static DownloadQueueManager getInstance() {
        DownloadQueueManager local = instance;
        if (local == null) {
            synchronized (DownloadQueueManager.class) {
                local = instance;
                if (local == null) {
                    instance = local = new DownloadQueueManager();
                }
            }
        }
        return local;
    }

    private final ArrayList<Item> items = new ArrayList<>();
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private final HashSet<Integer> observedAccounts = new HashSet<>();
    private final SharedPreferences prefs;
    private boolean running;
    private static final int MAX_RETRIES = 5;
    private long lastProgressNotify;
    private long lastServiceUpdate;
    private ConnectivityManager.NetworkCallback networkCallback;

    private DownloadQueueManager() {
        prefs = ApplicationLoader.applicationContext.getSharedPreferences("download_queue", Context.MODE_PRIVATE);
        load();
        updateNetworkCallback();
    }

    // ---------- ajustes ----------

    public int getMaxConcurrent() {
        return Math.max(1, Math.min(3, prefs.getInt("max_concurrent", 1)));
    }

    public void setMaxConcurrent(int value) {
        prefs.edit().putInt("max_concurrent", Math.max(1, Math.min(3, value))).apply();
        if (running) {
            pump();
        }
    }

    public boolean isStartOnWifi() {
        return prefs.getBoolean("start_on_wifi", false);
    }

    public void setStartOnWifi(boolean value) {
        prefs.edit().putBoolean("start_on_wifi", value).apply();
        updateNetworkCallback();
    }

    /** Copiar automaticamente a la carpeta Descargas cuando termina cada archivo (por defecto: si). */
    public boolean isAutoSaveToDownloads() {
        return prefs.getBoolean("auto_save_downloads", true);
    }

    public void setAutoSaveToDownloads(boolean value) {
        prefs.edit().putBoolean("auto_save_downloads", value).apply();
    }

    /** Borrar la copia interna de la app una vez guardada en Descargas (por defecto: no). */
    public boolean isDeleteAfterSave() {
        return prefs.getBoolean("delete_after_save", false);
    }

    public void setDeleteAfterSave(boolean value) {
        prefs.edit().putBoolean("delete_after_save", value).apply();
    }

    /** Copia el archivo ya descargado a Descargas (misma carpeta que "Guardar en Descargas" del chat). */
    public boolean saveToDownloads(Item item, Context context) {
        return saveInternal(item, context, false);
    }

    private boolean saveInternal(final Item item, Context context, final boolean deleteAfter) {
        return saveInternal(item, context, deleteAfter, false);
    }

    /** Aviso temporal de diagnostico (solo para el guardado automatico). */
    private void debug(boolean auto, final String text) {
        if (!auto) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                Toast.makeText(ApplicationLoader.applicationContext, "Cola: " + text, Toast.LENGTH_LONG).show();
            } catch (Throwable ignore) {
            }
        });
    }

    private boolean saveInternal(final Item item, Context context, final boolean deleteAfter, final boolean auto) {
        try {
            MessageObject mo = item.getMessage();
            if (mo == null) {
                debug(auto, "mensaje no disponible");
                return false;
            }
            File f = FileLoader.getInstance(item.account).getPathToMessage(mo.messageOwner);
            if (f == null || !f.exists()) {
                debug(auto, "archivo no encontrado en la app: " + (f == null ? "null" : f.getPath()));
                return false;
            }
            Context ctx = context;
            if (ctx == null) {
                try {
                    org.telegram.ui.ActionBar.BaseFragment last = org.telegram.ui.LaunchActivity.getSafeLastFragment();
                    if (last != null) {
                        ctx = last.getParentActivity();
                    }
                } catch (Throwable ignore) {
                }
            }
            if (ctx == null) {
                ctx = ApplicationLoader.applicationContext;
            }
            if (ctx == null) {
                debug(auto, "sin contexto");
                return false;
            }
            final String path = f.getPath();
            String saveName = item.getDisplayName();
            String mime = mo.getDocument() != null ? mo.getDocument().mime_type : null;
            debug(auto, "guardando " + saveName);
            // type 2 = Descargas (0 seria Imagenes, 1 Peliculas, 3 Musica); igual que "Guardar en Descargas" del chat
            MediaController.saveFile(path, ctx, 2, saveName, mime, uri -> {
                if (uri == null) {
                    debug(auto, "Telegram no pudo guardar");
                    return;
                }
                debug(auto, "guardado en Descargas");
                item.savedUri = uri.toString();
                if (deleteAfter) {
                    try {
                        File src = new File(path);
                        if (src.exists()) {
                            src.delete();
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
                save();
                notifyChanged();
            });
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            debug(auto, "error: " + e);
            return false;
        }
    }

    public boolean isStopOnWifiLost() {
        return prefs.getBoolean("stop_on_wifi_lost", false);
    }

    public void setStopOnWifiLost(boolean value) {
        prefs.edit().putBoolean("stop_on_wifi_lost", value).apply();
        updateNetworkCallback();
    }

    public String getQueueName() {
        return prefs.getString("queue_name", "");
    }

    public void setQueueName(String name) {
        prefs.edit().putString("queue_name", name == null ? "" : name.trim()).apply();
    }

    public void resetSettings() {
        prefs.edit().remove("max_concurrent").remove("start_on_wifi").remove("stop_on_wifi_lost").remove("queue_name").remove("auto_save_downloads").remove("delete_after_save").apply();
        updateNetworkCallback();
    }

    // ---------- listeners ----------

    public void addListener(Listener l) {
        if (!listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            for (Listener l : new ArrayList<>(listeners)) {
                l.onQueueChanged();
            }
        });
    }

    // ---------- cola ----------

    public ArrayList<Item> getItems() {
        return items;
    }

    public boolean isRunning() {
        return running;
    }

    public int countDone() {
        int c = 0;
        for (Item i : items) {
            if (i.state == STATE_DONE) {
                c++;
            }
        }
        return c;
    }

    /** @return true si se agrego (false si ya estaba o no es un archivo). */
    public boolean add(int account, MessageObject message) {
        if (message == null || message.getDocument() == null) {
            return false;
        }
        TLRPC.Document doc = message.getDocument();
        String fileName = FileLoader.getAttachFileName(doc);
        for (Item i : items) {
            if (i.account == account && fileName.equals(i.fileName)) {
                return false;
            }
        }
        Item item = new Item();
        item.account = account;
        item.fileName = fileName;
        String name = FileLoader.getDocumentFileName(doc);
        item.name = (name == null || name.isEmpty()) ? fileName : name;
        item.size = doc.size;
        item.message = message;
        try {
            SerializedData sd = new SerializedData();
            message.messageOwner.serializeToStream(sd);
            item.serialized = Base64.encodeToString(sd.toByteArray(), Base64.NO_WRAP);
            sd.cleanup();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        File f = FileLoader.getInstance(account).getPathToMessage(message.messageOwner);
        item.state = (f != null && f.exists()) ? STATE_DONE : STATE_PENDING;
        items.add(item);
        if (running) {
            registerObservers();
        }
        save();
        notifyChanged();
        return true;
    }

    public void remove(Item item) {
        cancelLoading(item);
        items.remove(item);
        save();
        notifyChanged();
        pump();
    }

    /** Cuantos descargados ya estan guardados en Descargas y tienen copia en la app. */
    public int countFreeable() {
        int c = 0;
        for (Item i : items) {
            if (i.state == STATE_DONE && i.savedUri != null) {
                File f = internalFile(i);
                if (f != null && f.exists()) {
                    c++;
                }
            }
        }
        return c;
    }

    private File internalFile(Item i) {
        MessageObject mo = i.getMessage();
        if (mo == null || mo.getDocument() == null) {
            return null;
        }
        return FileLoader.getInstance(i.account).getPathToMessage(mo.messageOwner);
    }

    public void removeDownloaded() {
        removeDownloaded(false);
    }

    /** @param deleteAppCopy borra la copia interna de la app, solo de los que ya estan guardados en Descargas. */
    public void removeDownloaded(boolean deleteAppCopy) {
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            if (it.state == STATE_DONE) {
                if (deleteAppCopy && it.savedUri != null) {
                    try {
                        File f = internalFile(it);
                        if (f != null && f.exists()) {
                            f.delete();
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
                items.remove(i);
            }
        }
        save();
        notifyChanged();
    }

    public void removeAll() {
        stop();
        items.clear();
        save();
        notifyChanged();
    }

    public void start() {
        running = true;
        registerObservers();
        for (Item i : items) {
            if (i.state == STATE_FAILED) {
                i.state = STATE_PENDING;
            }
            i.retries = 0;
            i.notBefore = 0;
        }
        pump();
        notifyChanged();
    }

    public void stop() {
        running = false;
        for (Item i : items) {
            cancelLoading(i);
        }
        unregisterObservers();
        notifyChanged();
        updateService();
    }

    private void cancelLoading(Item item) {
        if (item.state == STATE_LOADING) {
            MessageObject mo = item.getMessage();
            if (mo != null && mo.getDocument() != null) {
                FileLoader.getInstance(item.account).cancelLoadFile(mo.getDocument());
            }
            item.state = STATE_PENDING;
        }
    }

    private void pump() {
        if (!running) {
            return;
        }
        int loading = 0;
        for (Item i : items) {
            if (i.state == STATE_LOADING) {
                loading++;
            }
        }
        // si se bajo el limite mientras descargaba, pausar las que sobran (las ultimas en empezar)
        for (int k = items.size() - 1; k >= 0 && loading > getMaxConcurrent(); k--) {
            Item i = items.get(k);
            if (i.state == STATE_LOADING) {
                cancelLoading(i);
                loading--;
            }
        }
        for (Item i : items) {
            if (loading >= getMaxConcurrent()) {
                break;
            }
            if (i.state != STATE_PENDING) {
                continue;
            }
            if (i.notBefore > SystemClock.elapsedRealtime()) {
                continue;
            }
            MessageObject mo = i.getMessage();
            if (mo == null || mo.getDocument() == null) {
                i.state = STATE_FAILED;
                continue;
            }
            File f = FileLoader.getInstance(i.account).getPathToMessage(mo.messageOwner);
            if (f != null && f.exists()) {
                i.state = STATE_DONE;
                continue;
            }
            i.state = STATE_LOADING;
            loading++;
            FileLoader.getInstance(i.account).loadFile(mo.getDocument(), mo, FileLoader.PRIORITY_LOW, 0);
        }
        boolean anyActive = false;
        for (Item i : items) {
            if (i.state == STATE_LOADING || i.state == STATE_PENDING) {
                anyActive = true;
                break;
            }
        }
        if (!anyActive) {
            running = false;
            unregisterObservers();
        }
        save();
        notifyChanged();
        updateService();
    }

    // ---------- eventos de descarga ----------

    // Solo escucha eventos de descarga MIENTRAS la cola esta en marcha.
    // Con la cola detenida no se engancha a nada y no puede afectar al chat.
    private void registerObservers() {
        for (Item i : items) {
            if (observedAccounts.add(i.account)) {
                NotificationCenter nc = NotificationCenter.getInstance(i.account);
                nc.addObserver(this, NotificationCenter.fileLoaded);
                nc.addObserver(this, NotificationCenter.fileLoadFailed);
                nc.addObserver(this, NotificationCenter.fileLoadProgressChanged);
            }
        }
    }

    private void unregisterObservers() {
        for (Integer account : new ArrayList<>(observedAccounts)) {
            NotificationCenter nc = NotificationCenter.getInstance(account);
            nc.removeObserver(this, NotificationCenter.fileLoaded);
            nc.removeObserver(this, NotificationCenter.fileLoadFailed);
            nc.removeObserver(this, NotificationCenter.fileLoadProgressChanged);
        }
        observedAccounts.clear();
    }

    private Item find(int account, String fileName) {
        for (Item i : items) {
            if (i.account == account && fileName != null && fileName.equals(i.fileName)) {
                return i;
            }
        }
        return null;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileLoaded) {
            Item item = find(account, (String) args[0]);
            if (item != null) {
                item.state = STATE_DONE;
                item.retries = 0;
                item.loaded = item.size;
                if (isAutoSaveToDownloads()) {
                    final Item savedItem = item;
                    AndroidUtilities.runOnUIThread(() -> saveInternal(savedItem, null, isDeleteAfterSave(), true), 700);
                }
                save();
                notifyChanged();
                pump();
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            Item item = find(account, (String) args[0]);
            if (item != null && item.state == STATE_LOADING) {
                Integer canceled = args.length > 1 && args[1] instanceof Integer ? (Integer) args[1] : 0;
                if (canceled == 1) {
                    item.state = STATE_PENDING;
                } else if (running && item.retries < MAX_RETRIES) {
                    // error: reintentar solo, con una espera creciente
                    item.retries++;
                    long delay = 3000L * item.retries;
                    item.state = STATE_PENDING;
                    item.notBefore = SystemClock.elapsedRealtime() + delay;
                    AndroidUtilities.runOnUIThread(this::pump, delay + 100);
                } else {
                    item.state = STATE_FAILED;
                }
                save();
                notifyChanged();
                pump();
            }
        } else if (id == NotificationCenter.fileLoadProgressChanged) {
            Item item = find(account, (String) args[0]);
            if (item != null && item.state == STATE_LOADING) {
                item.loaded = (Long) args[1];
                long total = (Long) args[2];
                if (total > 0) {
                    item.size = total;
                }
                long now = SystemClock.elapsedRealtime();
                if (now - lastProgressNotify > 500) {
                    lastProgressNotify = now;
                    notifyChanged();
                }
                if (now - lastServiceUpdate > 2000) {
                    lastServiceUpdate = now;
                    updateService();
                }
            }
        }
    }

    // ---------- segundo plano ----------

    private String buildStatus() {
        int n = 0;
        int waiting = 0;
        long loaded = 0;
        long total = 0;
        for (Item i : items) {
            if (i.state == STATE_LOADING) {
                n++;
                loaded += i.loaded;
                total += i.size;
            } else if (i.state == STATE_PENDING) {
                waiting++;
            }
        }
        String s = tr("Descargando ", "Downloading ") + n + tr(" archivo(s)", " file(s)");
        if (total > 0) {
            s += " - " + (int) (100L * loaded / total) + "%";
        }
        if (waiting > 0) {
            s += " - " + waiting + tr(" en espera", " waiting");
        }
        return s;
    }

    /** Mantiene una notificacion fija (servicio en primer plano) solo mientras la cola descarga. */
    private void updateService() {
        try {
            Context c = ApplicationLoader.applicationContext;
            Intent intent = new Intent(c, DownloadQueueService.class);
            if (running) {
                intent.putExtra(DownloadQueueService.EXTRA_TEXT, buildStatus());
                if (Build.VERSION.SDK_INT >= 26) {
                    c.startForegroundService(intent);
                } else {
                    c.startService(intent);
                }
            } else {
                c.stopService(intent);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ---------- Wi-Fi ----------

    // Solo se registra si alguna opcion de Wi-Fi esta activada.
    private void updateNetworkCallback() {
        if (Build.VERSION.SDK_INT < 24) {
            return;
        }
        boolean needed = isStartOnWifi() || isStopOnWifiLost();
        try {
            ConnectivityManager cm = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return;
            }
            if (!needed && networkCallback != null) {
                cm.unregisterNetworkCallback(networkCallback);
                networkCallback = null;
            } else if (needed && networkCallback == null) {
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    private boolean wasWifi;

                    @Override
                    public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                        boolean wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                        if (wifi && !wasWifi && isStartOnWifi() && !items.isEmpty()) {
                            AndroidUtilities.runOnUIThread(DownloadQueueManager.this::start);
                        } else if (!wifi && wasWifi && isStopOnWifiLost() && running) {
                            AndroidUtilities.runOnUIThread(DownloadQueueManager.this::stop);
                        }
                        wasWifi = wifi;
                    }

                    @Override
                    public void onLost(Network network) {
                        if (wasWifi && isStopOnWifiLost() && running) {
                            AndroidUtilities.runOnUIThread(DownloadQueueManager.this::stop);
                        }
                        wasWifi = false;
                    }
                };
                cm.registerDefaultNetworkCallback(networkCallback);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ---------- persistencia ----------

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Item i : items) {
                JSONObject o = new JSONObject();
                o.put("account", i.account);
                o.put("name", i.name);
                o.put("size", i.size);
                o.put("state", i.state == STATE_LOADING ? STATE_PENDING : i.state);
                o.put("fileName", i.fileName);
                o.put("msg", i.serialized);
                o.put("uri", i.savedUri);
                arr.put(o);
            }
            prefs.edit().putString("items", arr.toString()).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void load() {
        try {
            String s = prefs.getString("items", null);
            if (s == null) {
                return;
            }
            JSONArray arr = new JSONArray(s);
            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.getJSONObject(k);
                Item i = new Item();
                i.account = o.optInt("account");
                i.name = o.optString("name");
                i.size = o.optLong("size");
                i.state = o.optInt("state");
                i.fileName = o.optString("fileName");
                i.serialized = o.optString("msg", null);
                i.savedUri = o.isNull("uri") ? null : o.optString("uri", null);
                items.add(i);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ---------- textos (es/en) ----------

    public static String tr(String es, String en) {
        try {
            String lang = LocaleController.getInstance().getCurrentLocaleInfo().pluralLangCode;
            if (lang != null && lang.startsWith("es")) {
                return es;
            }
        } catch (Throwable ignore) {
        }
        return en;
    }
}
