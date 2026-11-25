package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.IthaConfig;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.DownloadQueueManager.tr;

/**
 * IthaGram: "Ajustes de chat". Opciones del encabezado que se ve dentro de los chats, con una vista previa.
 */
public class IthaChatSettingsActivity extends BaseFragment {

    private LinearLayout listLayout;
    private Context ctx;
    private PreviewView preview;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(tr("Ajustes de chat", "Chat settings"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        ScrollView scroll = new ScrollView(context);
        listLayout = new LinearLayout(context);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listLayout, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        ctx = context;
        rebuild();

        return fragmentView;
    }

    private void rebuild() {
        listLayout.removeAllViews();
        final Context context = ctx;

        preview = new PreviewView(context);
        listLayout.addView(preview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 116, 0, 0, 0, 0));

        TextView info = new TextView(context);
        info.setText(tr("La vista previa de arriba se actualiza al instante. Los cambios se ven al abrir un chat.", "The preview above updates instantly. Changes show when you open a chat."));
        info.setTextSize(13);
        info.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        info.setPadding(dp(20), dp(10), dp(20), dp(10));
        listLayout.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        section(context, tr("Encabezado de chat", "Chat header"));
        check(context, tr("Ocultar avatar", "Hide avatar"), tr("Quita el avatar del encabezado. Tocá el título para ver el perfil.", "Removes the avatar from the header. Tap the title to see the profile."),
                IthaConfig.isChatAvatarHidden(), (value) -> {
                    IthaConfig.setChatAvatarHidden(value);
                    preview.invalidate();
                });
        check(context, tr("Avatar a la derecha", "Avatar on the right"), tr("Pone el avatar a la derecha del nombre.", "Puts the avatar to the right of the name."),
                IthaConfig.isChatAvatarRight(), (value) -> {
                    IthaConfig.setChatAvatarRight(value);
                    preview.invalidate();
                });
        check(context, tr("Píldora compacta", "Compact pill"), tr("Ajusta el fondo del título más al texto, sin espacio de sobra.", "Fits the title background closer to the text."),
                IthaConfig.isChatPillCompact(), (value) -> {
                    IthaConfig.setChatPillCompact(value);
                    preview.invalidate();
                });
        check(context, tr("Centrar título", "Center title"), tr("Centra el avatar y el nombre entre el botón de volver y el menú.", "Centers the avatar and name between the back and menu buttons."),
                IthaConfig.isChatTitleCentered(), (value) -> {
                    IthaConfig.setChatTitleCentered(value);
                    preview.invalidate();
                });
        check(context, tr("Ocultar fondo de píldora", "Hide pill background"), tr("Quita el fondo de vidrio detrás del título del chat.", "Removes the glass background behind the chat title."),
                IthaConfig.isChatPillHidden(), (value) -> {
                    IthaConfig.setChatPillHidden(value);
                    preview.invalidate();
                });
    }

    private interface BoolCallback {
        void run(boolean value);
    }

    private void check(Context context, String title, String subtitle, boolean checked, BoolCallback callback) {
        final TextCheckCell cell = new TextCheckCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setTextAndValueAndCheck(title, subtitle, checked, true, false);
        cell.setOnClickListener(v -> {
            boolean value = !cell.isChecked();
            cell.setChecked(value);
            callback.run(value);
        });
        listLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void section(Context context, String title) {
        TextView header = new TextView(context);
        header.setText(title);
        header.setTextSize(14);
        header.setTypeface(AndroidUtilities.bold());
        header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        header.setPadding(dp(20), dp(18), dp(20), dp(8));
        listLayout.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    /** Dibujo de ejemplo del encabezado de un chat: boton de volver, pildora con avatar y titulo, menu. */
    private class PreviewView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        PreviewView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int w = getMeasuredWidth();
            final int h = getMeasuredHeight();
            final float cy = h / 2f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Theme.getColor(Theme.key_chat_wallpaper));
            canvas.drawRect(0, 0, w, h, paint);

            final int bubble = Theme.getColor(Theme.key_windowBackgroundWhite);
            final int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
            final int hint = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);

            // boton de volver
            paint.setColor(bubble);
            canvas.drawCircle(dp(34), cy, dp(20), paint);
            paint.setColor(text);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(dp(37), cy - dp(7), dp(30), cy, paint);
            canvas.drawLine(dp(30), cy, dp(37), cy + dp(7), paint);
            paint.setStyle(Paint.Style.FILL);

            // boton de menu (tres puntos)
            paint.setColor(bubble);
            canvas.drawCircle(w - dp(34), cy, dp(20), paint);
            paint.setColor(text);
            for (int i = -1; i <= 1; i++) {
                canvas.drawCircle(w - dp(34), cy + i * dp(6), dp(1.6f), paint);
            }

            // nombre del usuario actual
            TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
            String name = user != null ? ContactsController.formatName(user.first_name, user.last_name) : null;
            if (name == null || name.trim().isEmpty()) {
                name = "IthaGram";
            }
            final String sub = tr("en línea", "online");
            final boolean avatar = !IthaConfig.isChatAvatarHidden();

            // la pildora se ajusta al texto, igual que en el chat
            paint.setTypeface(AndroidUtilities.bold());
            paint.setTextSize(dp(16));
            final float nameW = paint.measureText(name);
            paint.setTypeface(null);
            paint.setTextSize(dp(13));
            final float subW = paint.measureText(sub);
            // en el chat el contenido va pegado a la izquierda; compacta = la pildora se ajusta al texto
            final float pl0 = dp(60);
            final float full = w - dp(60) * 2;
            final float textW = Math.max(nameW, subW);
            final boolean right = avatar && IthaConfig.isChatAvatarRight();
            final float contentW = right ? dp(16) + textW + dp(8) + dp(38) + dp(10) : (avatar ? dp(52) : dp(16)) + textW + dp(10);
            final float pillW = IthaConfig.isChatPillCompact() ? Math.min(full, contentW) : full;
            final boolean centered = IthaConfig.isChatTitleCentered();
            final float pl = pl0 + (centered ? (full - pillW) / 2f : 0f);
            final float pr = pl + pillW;
            // inicio del contenido (avatar y texto); centrado dentro de la pildora si corresponde
            final float cl = pl + (centered ? (pillW - Math.min(contentW, pillW)) / 2f : 0f);

            if (!IthaConfig.isChatPillHidden()) {
                rect.set(pl, cy - dp(24), pr, cy + dp(24));
                paint.setColor(bubble);
                canvas.drawRoundRect(rect, dp(24), dp(24), paint);
            }

            float textLeft = cl + dp(16);
            if (avatar) {
                final float acx = right ? cl + dp(16) + textW + dp(8) + dp(19) : cl + dp(24);
                paint.setColor(Theme.getColor(Theme.key_avatar_backgroundBlue));
                canvas.drawCircle(acx, cy, dp(19), paint);
                paint.setColor(0xffffffff);
                paint.setTypeface(AndroidUtilities.bold());
                paint.setTextSize(dp(16));
                final String initial = name.substring(0, 1).toUpperCase();
                canvas.drawText(initial, acx - paint.measureText(initial) / 2f, cy + dp(5.5f), paint);
                if (!right) {
                    textLeft = cl + dp(52);
                }
            }

            paint.setColor(text);
            paint.setTypeface(AndroidUtilities.bold());
            paint.setTextSize(dp(16));
            String shown = name;
            final float maxW = pr - textLeft - dp(8);
            while (shown.length() > 1 && paint.measureText(shown) > maxW) {
                shown = shown.substring(0, shown.length() - 1);
            }
            canvas.drawText(shown, textLeft, cy - dp(2), paint);

            paint.setColor(hint);
            paint.setTypeface(null);
            paint.setTextSize(dp(13));
            canvas.drawText(sub, textLeft, cy + dp(15), paint);
        }
    }
}
