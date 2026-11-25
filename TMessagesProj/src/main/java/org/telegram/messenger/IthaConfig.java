package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * IthaGram: ajustes propios (pantalla "Itha Ajustes"). Se guardan en SharedPreferences "itha_config".
 */
public class IthaConfig {

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("itha_config", Context.MODE_PRIVATE);
    }

    // ---- numero de telefono en Ajustes ----

    /** Ocultar el numero de telefono de la cabecera de Ajustes. */
    public static boolean isPhoneHidden() {
        return prefs().getBoolean("phone_hidden", false);
    }

    public static void setPhoneHidden(boolean value) {
        prefs().edit().putBoolean("phone_hidden", value).apply();
    }

    /** Devuelve el numero, o nada si la opcion de ocultarlo esta activa. */
    public static String maskPhone(String phone) {
        if (phone == null || !isPhoneHidden()) {
            return phone;
        }
        return "";
    }

    // ---- encabezado del chat ----

    /** Ocultar el avatar del encabezado dentro de los chats (se aplica al abrir un chat). */
    public static boolean isChatAvatarHidden() {
        return prefs().getBoolean("chat_avatar_hidden", false);
    }

    public static void setChatAvatarHidden(boolean value) {
        prefs().edit().putBoolean("chat_avatar_hidden", value).apply();
    }

    /** Ocultar el fondo (la pildora de vidrio) detras del titulo del chat. */
    public static boolean isChatPillHidden() {
        return prefs().getBoolean("chat_pill_hidden", false);
    }

    public static void setChatPillHidden(boolean value) {
        prefs().edit().putBoolean("chat_pill_hidden", value).apply();
    }

    /** Dice si esta barra es el encabezado de un chat (tiene el contenedor del avatar y titulo). */
    public static boolean hasChatHeader(android.view.ViewGroup bar) {
        for (int i = 0, n = bar.getChildCount(); i < n; i++) {
            if (bar.getChildAt(i) instanceof org.telegram.ui.Components.ChatAvatarContainer) {
                return true;
            }
        }
        return false;
    }

    /**
     * Borde derecho de la pildora del chat. Si la pildora compacta esta activa, la ajusta al texto
     * (el contenedor va pegado a la izquierda); si no, deja el borde por defecto.
     */
    public static int compactPillRight(android.view.ViewGroup bar, int left, int rightDefault) {
        if (!isChatPillCompact()) {
            return rightDefault;
        }
        for (int i = 0, n = bar.getChildCount(); i < n; i++) {
            android.view.View child = bar.getChildAt(i);
            if (child instanceof org.telegram.ui.Components.ChatAvatarContainer) {
                org.telegram.ui.Components.ChatAvatarContainer c = (org.telegram.ui.Components.ChatAvatarContainer) child;
                final boolean avatar = c.hasVisibleAvatar();
                // getVisualWidth ya viene recortado 8dp con la opcion activa: lo devolvemos para sacar el ancho del texto
                final int textW = c.getVisualWidth() + org.telegram.messenger.AndroidUtilities.dp(8)
                        - (avatar ? org.telegram.messenger.AndroidUtilities.dp(70) : org.telegram.messenger.AndroidUtilities.dp(34));
                final int textEnd = left + c.getLeftPadding()
                        + (avatar ? org.telegram.messenger.AndroidUtilities.dp(49.66f) : org.telegram.messenger.AndroidUtilities.dp(13)) + Math.max(textW, 0);
                final int right = textEnd + org.telegram.messenger.AndroidUtilities.dp(16);
                return Math.max(left + org.telegram.messenger.AndroidUtilities.dp(60), Math.min(rightDefault, right));
            }
        }
        return rightDefault;
    }

    /** Centrar el titulo del chat (y la pildora) entre el boton de volver y el menu. */
    public static boolean isChatTitleCentered() {
        return prefs().getBoolean("chat_title_centered", false);
    }

    public static void setChatTitleCentered(boolean value) {
        prefs().edit().putBoolean("chat_title_centered", value).apply();
    }

    /** Avatar a la derecha del texto en el encabezado del chat. */
    public static boolean isChatAvatarRight() {
        return prefs().getBoolean("chat_avatar_right", false);
    }

    public static void setChatAvatarRight(boolean value) {
        prefs().edit().putBoolean("chat_avatar_right", value).apply();
    }

    /** Resultado de layoutChatPill: bordes de la pildora del chat. */
    public static int pillLeft, pillRight;

    /**
     * Calcula donde va la pildora del chat segun las opciones (compacta y/o centrada) y mueve el
     * contenido (avatar y titulo) para que quede centrado dentro de ella.
     */
    public static void layoutChatPill(android.view.ViewGroup bar, int leftDefault, int rightDefault, float modeFactor) {
        pillLeft = leftDefault;
        pillRight = rightDefault;
        final boolean compact = isChatPillCompact();
        final boolean center = isChatTitleCentered();
        final boolean avatarRight = isChatAvatarRight();
        if (!compact && !center && !avatarRight) {
            return;
        }
        for (int i = 0, n = bar.getChildCount(); i < n; i++) {
            android.view.View child = bar.getChildAt(i);
            if (!(child instanceof org.telegram.ui.Components.ChatAvatarContainer)) {
                continue;
            }
            org.telegram.ui.Components.ChatAvatarContainer c = (org.telegram.ui.Components.ChatAvatarContainer) child;
            final boolean avatar = c.hasVisibleAvatar();
            // ancho de texto que entra de verdad en la pildora: se le avisa al contenedor para que nunca deje el avatar fuera
            final int capLeft = c.getLeftPadding() + (avatar && !avatarRight ? org.telegram.messenger.AndroidUtilities.dp(49.66f) : org.telegram.messenger.AndroidUtilities.dp(13));
            final int capRight = avatar && avatarRight ? org.telegram.messenger.AndroidUtilities.dp(58) : org.telegram.messenger.AndroidUtilities.dp(16);
            final int cap = Math.max(org.telegram.messenger.AndroidUtilities.dp(40), (rightDefault - leftDefault) - capLeft - capRight);
            if (c.ithaPillCap != cap) {
                c.ithaPillCap = cap;
                c.post(() -> c.requestLayout());
            }
            if (c.ithaAvailWidth > cap) {
                c.ithaAvailWidth = cap;
            }
            // getVisualWidth viene recortado 8dp si la compacta esta activa: se lo devolvemos para sacar el ancho del texto
            int textW = Math.max(0, c.getVisualWidth() + (compact ? org.telegram.messenger.AndroidUtilities.dp(8) : 0)
                    - (avatar ? org.telegram.messenger.AndroidUtilities.dp(70) : org.telegram.messenger.AndroidUtilities.dp(34)));
            if ((center || avatarRight) && c.ithaAvailWidth > 0) {
                // la banda de texto y el centrado se calculan aqui, con el texto de este momento, igual que la pildora
                textW = c.ithaApplyBand();
            }
            int contentRight = leftDefault + c.getLeftPadding()
                    + (avatar && !avatarRight ? org.telegram.messenger.AndroidUtilities.dp(49.66f) : org.telegram.messenger.AndroidUtilities.dp(13))
                    + textW + (avatar && avatarRight ? org.telegram.messenger.AndroidUtilities.dp(58) : org.telegram.messenger.AndroidUtilities.dp(16));
            contentRight = Math.max(leftDefault + org.telegram.messenger.AndroidUtilities.dp(60), Math.min(rightDefault, contentRight));
            final int contentW = contentRight - leftDefault;
            final int full = rightDefault - leftDefault;
            final int pillW = compact ? contentW : full;
            final int left = leftDefault + (center ? (full - pillW) / 2 : 0);
            // en seleccion o busqueda (modeFactor) la barra estira la pildora: se mezcla con el ancho completo, como Telegram
            final float k = Math.max(0f, Math.min(1f, modeFactor));
            pillLeft = Math.round(left + (leftDefault - left) * k);
            pillRight = Math.round((left + pillW) + (rightDefault - (left + pillW)) * k);
            if (center) {
                c.setTranslationX((left + (pillW - contentW) / 2f - leftDefault) * (1f - k));
            }
            return;
        }
    }

    /** Pildora compacta: el fondo del titulo del chat se ajusta mas al texto. */
    public static boolean isChatPillCompact() {
        return prefs().getBoolean("chat_pill_compact", false);
    }

    public static void setChatPillCompact(boolean value) {
        prefs().edit().putBoolean("chat_pill_compact", value).apply();
    }

    /** La pantalla de Ajustes que esta abierta (para refrescar su cabecera al instante). */
    public static java.lang.ref.WeakReference<org.telegram.ui.SettingsActivity> settingsRef;

    /** Si el numero esta oculto, saca el separador que quedaria sobrando al principio ("@usuario" solo). */
    public static CharSequence fixHeader(StringBuilder sb) {
        if (isPhoneHidden() && sb.indexOf(" \u2022 ") == 0) {
            return sb.substring(3);
        }
        return sb;
    }
}
