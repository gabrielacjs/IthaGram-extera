package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadQueueManager;
import org.telegram.messenger.DownloadQueueManager.Item;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Locale;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.DownloadQueueManager.tr;

/** Pantalla "Gestor de descargas": lista la cola y permite iniciarla o detenerla. */
public class DownloadQueueActivity extends BaseFragment implements DownloadQueueManager.Listener {

    private static final int MENU_START_QUEUE = 1;
    private static final int MENU_STOP_QUEUE = 2;
    private static final int MENU_START_ALL = 3;
    private static final int MENU_STOP_ALL = 4;
    private static final int MENU_REMOVE_DONE = 5;
    private static final int MENU_REMOVE_ALL = 6;
    private static final int MENU_SETTINGS = 7;

    private final DownloadQueueManager manager = DownloadQueueManager.getInstance();
    private LinearLayout listLayout;
    private TextView summaryView;
    private TextView emptyView;
    private TextView actionButton;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(tr("Gestor de descargas", "Download manager"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                switch (id) {
                    case -1:
                        finishFragment();
                        break;
                    case MENU_START_QUEUE:
                    case MENU_START_ALL:
                        manager.start();
                        break;
                    case MENU_STOP_QUEUE:
                    case MENU_STOP_ALL:
                        manager.stop();
                        break;
                    case MENU_REMOVE_DONE:
                        confirmRemoveDone();
                        break;
                    case MENU_REMOVE_ALL:
                        confirmRemoveAll();
                        break;
                    case MENU_SETTINGS:
                        showSettings();
                        break;
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem other = menu.addItem(0, R.drawable.ic_ab_other);
        other.addSubItem(MENU_START_QUEUE, R.drawable.msg_download, tr("Iniciar cola", "Start queue"));
        other.addSubItem(MENU_STOP_QUEUE, R.drawable.msg_cancel, tr("Detener cola", "Stop queue"));
        other.addSubItem(MENU_START_ALL, R.drawable.msg_download, tr("Iniciar todo", "Start all"));
        other.addSubItem(MENU_STOP_ALL, R.drawable.msg_cancel, tr("Detener todo", "Stop all"));
        other.addSubItem(MENU_REMOVE_DONE, R.drawable.msg_delete, tr("Eliminar descargado", "Remove downloaded"));
        other.addSubItem(MENU_REMOVE_ALL, R.drawable.msg_delete, tr("Eliminar todos", "Remove all"));
        other.addSubItem(MENU_SETTINGS, R.drawable.msg_retry, tr("Configuración de la cola", "Queue settings"));

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        summaryView = new TextView(context);
        summaryView.setTextSize(14);
        summaryView.setGravity(Gravity.CENTER);
        summaryView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        summaryView.setPadding(dp(16), dp(10), dp(16), dp(10));
        root.addView(summaryView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 0, 0, 0));

        ScrollView scroll = new ScrollView(context);
        listLayout = new LinearLayout(context);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listLayout, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 40, 0, 64));

        emptyView = new TextView(context);
        emptyView.setTextSize(15);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setText(tr("La cola está vacía.\nMantén presionado un archivo y elige \"Agregar a la cola de descarga\".",
                "The queue is empty.\nLong-press a file and choose \"Add to download queue\"."));
        root.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));

        actionButton = new TextView(context);
        actionButton.setTextSize(15);
        actionButton.setTypeface(AndroidUtilities.bold());
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setTextColor(0xFFFFFFFF);
        actionButton.setBackgroundColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        actionButton.setOnClickListener(v -> {
            if (manager.isRunning()) {
                manager.stop();
            } else {
                manager.start();
            }
        });
        root.addView(actionButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.BOTTOM));

        refresh();
        return fragmentView;
    }

    @Override
    public boolean onFragmentCreate() {
        manager.addListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        manager.removeListener(this);
        super.onFragmentDestroy();
    }

    @Override
    public void onQueueChanged() {
        refresh();
    }

    private void refresh() {
        if (listLayout == null) {
            return;
        }
        ArrayList<Item> items = manager.getItems();
        String name = manager.getQueueName();
        if (name.isEmpty()) {
            name = tr("Cola principal", "Main queue");
        }
        summaryView.setText(String.format(Locale.US, "%s - %d %s - %d %s", name, items.size(),
                tr("descargas", "downloads"), manager.countDone(), tr("descargadas", "downloaded")));
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        actionButton.setText(manager.isRunning() ? tr("Detener cola", "Stop queue") : tr("Iniciar cola", "Start queue"));

        listLayout.removeAllViews();
        Context context = listLayout.getContext();
        for (Item item : new ArrayList<>(items)) {
            listLayout.addView(buildRow(context, item));
        }
    }

    private View buildRow(Context context, Item item) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        row.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView title = new TextView(context);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setText(item.getDisplayName());
        row.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView sub = new TextView(context);
        sub.setTextSize(13);
        sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        sub.setText(describe(item));
        row.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        if (item.state == DownloadQueueManager.STATE_LOADING) {
            ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(1000);
            bar.setProgress(item.size > 0 ? (int) (1000L * item.loaded / item.size) : 0);
            row.addView(bar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8, 0, 6, 0, 0));
        }

        row.setOnClickListener(v -> showItemMenu(item));
        return row;
    }

    /** Abre la copia guardada en Descargas cuando la copia interna ya fue borrada. */
    private void openSaved(Item item, org.telegram.messenger.MessageObject mo) {
        try {
            String mime = mo != null && mo.getDocument() != null ? mo.getDocument().mime_type : null;
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(android.net.Uri.parse(item.savedUri), TextUtils.isEmpty(mime) ? "*/*" : mime);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            getParentActivity().startActivity(intent);
        } catch (Throwable e) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                    tr("No se pudo abrir el archivo", "Could not open the file")).show();
        }
    }

    private void showItemMenu(Item item) {
        if (getParentActivity() == null) {
            return;
        }
        final boolean done = item.state == DownloadQueueManager.STATE_DONE;
        CharSequence[] opts = done
                ? new CharSequence[]{
                        tr("Abrir", "Open"),
                        tr("Guardar en Descargas", "Save to Downloads"),
                        tr("Eliminar de la lista", "Remove from the list")}
                : new CharSequence[]{tr("Eliminar de la cola", "Remove from the queue")};
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(item.getDisplayName());
        b.setItems(opts, (d, which) -> {
            if (!done) {
                manager.remove(item);
                return;
            }
            switch (which) {
                case 0: {
                    org.telegram.messenger.MessageObject mo = item.getMessage();
                    java.io.File internal = mo != null
                            ? org.telegram.messenger.FileLoader.getInstance(item.account).getPathToMessage(mo.messageOwner)
                            : null;
                    if ((internal == null || !internal.exists()) && item.savedUri != null) {
                        openSaved(item, mo);
                    } else if (mo != null) {
                        AndroidUtilities.openForView(mo, getParentActivity(), null, false);
                    }
                    break;
                }
                case 1: {
                    boolean ok = manager.saveToDownloads(item, getParentActivity());
                    String text = ok
                            ? tr("Guardando en Descargas…", "Saving to Downloads…")
                            : (item.savedUri != null
                                    ? tr("Ya está guardado en Descargas", "Already saved in Downloads")
                                    : tr("No se pudo guardar", "Could not save"));
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, text).show();
                    break;
                }
                case 2:
                    manager.remove(item);
                    break;
            }
        });
        b.setNegativeButton(tr("Cancelar", "Cancel"), null);
        showDialog(b.create());
    }

    private String describe(Item item) {
        String size = AndroidUtilities.formatFileSize(item.size);
        switch (item.state) {
            case DownloadQueueManager.STATE_LOADING:
                return AndroidUtilities.formatFileSize(item.loaded) + " / " + size;
            case DownloadQueueManager.STATE_DONE:
                return size + " - " + tr("Descargado", "Downloaded");
            case DownloadQueueManager.STATE_FAILED:
                return size + " - " + tr("Error", "Failed");
            default:
                return size + " - " + tr("En espera", "Waiting");
        }
    }

    private void confirmRemoveDone() {
        final int freeable = manager.countFreeable();
        if (freeable == 0) {
            manager.removeDownloaded(false);
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(tr("Eliminar descargado", "Remove downloaded"));
        b.setMessage(tr(
                freeable + " archivo(s) ya están guardados en Descargas. ¿Borrar también la copia dentro de la app para liberar espacio? (tu copia en Descargas no se toca)",
                freeable + " file(s) are already saved in Downloads. Also delete the copy inside the app to free space? (your Downloads copy is kept)"));
        b.setPositiveButton(tr("Quitar y liberar espacio", "Remove and free space"), (d, w) -> manager.removeDownloaded(true));
        b.setNeutralButton(tr("Solo quitar de la lista", "Only remove from list"), (d, w) -> manager.removeDownloaded(false));
        b.setNegativeButton(tr("Cancelar", "Cancel"), null);
        showDialog(b.create());
    }

    private void confirmRemoveAll() {
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(tr("Eliminar todos", "Remove all"));
        b.setMessage(tr("Se vaciará la cola. Los archivos ya descargados no se borran.", "The queue will be emptied. Downloaded files are kept."));
        b.setPositiveButton(tr("Eliminar", "Remove"), (d, w) -> manager.removeAll());
        b.setNegativeButton(tr("Cancelar", "Cancel"), null);
        showDialog(b.create());
    }

    private void showSettings() {
        CharSequence[] opts = new CharSequence[]{
                tr("Nombre de la cola", "Queue name") + ": " + (manager.getQueueName().isEmpty() ? tr("Cola principal", "Main queue") : manager.getQueueName()),
                tr("Descargas simultáneas", "Simultaneous downloads") + ": " + manager.getMaxConcurrent(),
                tr("Iniciar al conectar Wi-Fi", "Start when Wi-Fi connects") + ": " + (manager.isStartOnWifi() ? tr("Sí", "Yes") : "No"),
                tr("Detener si se corta el Wi-Fi", "Stop when Wi-Fi drops") + ": " + (manager.isStopOnWifiLost() ? tr("Sí", "Yes") : "No"),
                tr("Guardar en Descargas al terminar", "Save to Downloads when done") + ": " + (manager.isAutoSaveToDownloads() ? tr("Sí", "Yes") : "No"),
                tr("Borrar la copia de la app al guardar", "Delete app copy after saving") + ": " + (manager.isDeleteAfterSave() ? tr("Sí", "Yes") : "No"),
                tr("Restablecer la configuración", "Reset settings")
        };
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(tr("Configuración de la cola", "Queue settings"));
        b.setItems(opts, (d, which) -> {
            switch (which) {
                case 0:
                    askName();
                    return;
                case 1:
                    askConcurrent();
                    return;
                case 2:
                    manager.setStartOnWifi(!manager.isStartOnWifi());
                    break;
                case 3:
                    manager.setStopOnWifiLost(!manager.isStopOnWifiLost());
                    break;
                case 4:
                    manager.setAutoSaveToDownloads(!manager.isAutoSaveToDownloads());
                    break;
                case 5:
                    manager.setDeleteAfterSave(!manager.isDeleteAfterSave());
                    break;
                case 6:
                    manager.resetSettings();
                    break;
            }
            refresh();
            showSettings();
        });
        b.setNegativeButton(tr("Cerrar", "Close"), null);
        showDialog(b.create());
    }

    private void askConcurrent() {
        final int current = manager.getMaxConcurrent();
        CharSequence[] nums = new CharSequence[3];
        for (int i = 0; i < 3; i++) {
            nums[i] = (i + 1) + (i + 1 == current ? "   \u2713" : "");
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(tr("Descargas simultáneas", "Simultaneous downloads"));
        b.setItems(nums, (d, which) -> {
            manager.setMaxConcurrent(which + 1);
            refresh();
            showSettings();
        });
        b.setNegativeButton(tr("Cancelar", "Cancel"), (d, w) -> showSettings());
        showDialog(b.create());
    }

    private void askName() {
        final android.widget.EditText edit = new android.widget.EditText(getParentActivity());
        edit.setText(manager.getQueueName());
        edit.setSingleLine(true);
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(tr("Nombre de la cola", "Queue name"));
        b.setView(edit);
        b.setPositiveButton(tr("Guardar", "Save"), (d, w) -> {
            manager.setQueueName(edit.getText().toString());
            refresh();
            showSettings();
        });
        b.setNegativeButton(tr("Cancelar", "Cancel"), (d, w) -> showSettings());
        showDialog(b.create());
    }
}
