package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.ui.Stars.StarsController.findAttribute;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ProfileGiftsView extends View implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final long dialogId;
    private final View avatarContainer;
    private final ProfileActivity.AvatarImageView avatarImage;
    private final Theme.ResourcesProvider resourcesProvider;

    private boolean active = true;
    public void setActive(boolean f) {
        this.active = f;
    }

    public ProfileGiftsView(Context context, int currentAccount, long dialogId, @NonNull View avatarContainer, ProfileActivity.AvatarImageView avatarImage, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.currentAccount = currentAccount;
        this.dialogId = dialogId;

        this.avatarContainer = avatarContainer;
        this.avatarImage = avatarImage;

        this.resourcesProvider = resourcesProvider;
    }

    public float expandProgress, collapseProgress;
    public boolean isOpening;

    public void setExpandProgress(float progress) {
        if (this.expandProgress != progress) {
            this.expandProgress = progress;
            invalidate();
        }
    }

    public void setCollapseProgress(float progress, boolean isOpening) {
        this.isOpening = isOpening;
        float newProgress = Utilities.clamp01((progress - 0.3f) / 0.7f);
        if (this.collapseProgress != newProgress) {
            this.collapseProgress = newProgress;
            invalidate();
        }
    }

    private float actionBarProgress;

    public void setActionBarActionMode(float progress) {
//        if (Theme.isCurrentThemeDark()) {
//            return;
//        }
        actionBarProgress = progress;
        invalidate();
    }


    private float left, right, cy;
    private float expandY, maxExpandY;
    private final AnimatedFloat rightAnimated = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    public void setBounds(float left, float right, float cy, boolean animated, int maxExpandY) {
        boolean changed = Math.abs(left - this.left) > 0.1f || Math.abs(right - this.right) > 0.1f || Math.abs(cy - this.cy) > 0.1f;
        this.left = left;
        this.right = right;
        if (!animated) {
            this.rightAnimated.set(this.right, true);
        }
        this.cy = cy;
        this.maxExpandY = maxExpandY + cy;
        if (changed) {
            invalidate();
        }
    }

    public void setExpandCoords(float y) {
        this.expandY = y;
        invalidate();
    }

    private float progressToInsets = 1f;

    public void setProgressToStoriesInsets(float progressToInsets) {
        if (this.progressToInsets == progressToInsets) {
            return;
        }
        this.progressToInsets = progressToInsets;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.starUserGiftsLoaded);

        for (Gift gift : gifts) {
            gift.emojiDrawable.addView(this);
        }

        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);

        for (Gift gift : gifts) {
            gift.emojiDrawable.removeView(this);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starUserGiftsLoaded) {
            if ((long) args[0] == dialogId) {
                update();
            }
        }
    }

    public final class Gift {

        public final long id;
        public final TLRPC.Document document;
        public final long documentId;
        public final int color;
        public final String slug;
        private StarsReactionsSheet.Particles particles;

        public int position = -1;

        public Gift(TL_stars.TL_starGiftUnique gift) {
            id = gift.id;
            document = gift.getDocument();
            documentId = document == null ? 0 : document.id;
            final TL_stars.starGiftAttributeBackdrop backdrop = findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
            color = backdrop.center_color | 0xFF000000;
            slug = gift.slug;
            initParticles();
        }

        public Gift(TLRPC.TL_emojiStatusCollectible status) {
            id = status.collectible_id;
            document = null;
            documentId = status.document_id;
            color = status.center_color | 0xFF000000;
            slug = status.slug;
            initParticles();
        }

        private void initParticles() {
            particles = new StarsReactionsSheet.Particles(StarsReactionsSheet.Particles.TYPE_RADIAL, 6);
            final float gsz = dp(36);
            particles.bounds.set(-gsz / 2.0f, -gsz / 2.0f, gsz / 2.0f, gsz / 2.0f);
        }

        public boolean equals(Gift b) {
            return b != null && b.id == id;
        }

        public RadialGradient gradient;
        public final Matrix gradientMatrix = new Matrix();
        public Paint gradientPaint;
        public AnimatedEmojiDrawable emojiDrawable;
        public AnimatedFloat animatedFloat;

        public final RectF bounds = new RectF();
        public final ButtonBounce bounce = new ButtonBounce(ProfileGiftsView.this);

        public void copy(Gift b) {
            gradient = b.gradient;
            emojiDrawable = b.emojiDrawable;
            gradientPaint = b.gradientPaint;
            animatedFloat = b.animatedFloat;
            particles = b.particles;
            position = b.position;
        }

        public void draw(
                Canvas canvas,
                float cx, float cy,
                float ascale, float rotate,
                float alpha,
                float gradientAlpha
        ) {
            if (alpha <= 0.0f) return;
            final float gsz = dp(45);
            bounds.set(cx - gsz / 2, cy - gsz / 2, cx + gsz / 2, cy + gsz / 2);
            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(rotate);
            final float scale = ascale * bounce.getScale(0.1f);
            canvas.scale(scale, scale);
            particles.process();
            particles.draw(canvas, color, alpha);
            if (gradientPaint != null) {
                gradientPaint.setAlpha((int) (0xFF * alpha * gradientAlpha));
                canvas.drawRect(-gsz / 2.0f, -gsz / 2.0f, gsz / 2.0f, gsz / 2.0f, gradientPaint);
            }
            if (emojiDrawable != null) {
                final int sz = dp(24);
                emojiDrawable.setBounds(-sz / 2, -sz / 2, sz / 2, sz / 2);
                emojiDrawable.setAlpha((int) (0xFF * alpha));
                emojiDrawable.draw(canvas);
            }
            canvas.restore();
        }
    }

    private StarsController.GiftsList list;

    public final ArrayList<Gift> oldGifts = new ArrayList<>();
    public final ArrayList<Gift> gifts = new ArrayList<>();
    public final HashSet<Long> giftIds = new HashSet<>();
    public int maxCount;

    public void update() {
        if (!MessagesController.getInstance(currentAccount).enableGiftsInProfile) {
            return;
        }

        maxCount = MessagesController.getInstance(currentAccount).stargiftsPinnedToTopLimit;
        oldGifts.clear();
        oldGifts.addAll(gifts);
        gifts.clear();
        giftIds.clear();

        final TLRPC.EmojiStatus emojiStatus;
        if (dialogId >= 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            emojiStatus = user == null ? null : user.emoji_status;
        } else {
            final TLRPC.User chat = MessagesController.getInstance(currentAccount).getUser(-dialogId);
            emojiStatus = chat == null ? null : chat.emoji_status;
        }
        if (emojiStatus instanceof TLRPC.TL_emojiStatusCollectible) {
            giftIds.add(((TLRPC.TL_emojiStatusCollectible) emojiStatus).collectible_id);
        }
        list = StarsController.getInstance(currentAccount).getProfileGiftsList(dialogId);
        if (list != null) {
            for (int i = 0; i < list.gifts.size(); i++) {
                final TL_stars.SavedStarGift savedGift = list.gifts.get(i);
                if (!savedGift.unsaved && savedGift.pinned_to_top && savedGift.gift instanceof TL_stars.TL_starGiftUnique) {
                    final Gift gift = new Gift((TL_stars.TL_starGiftUnique) savedGift.gift);
                    if (!giftIds.contains(gift.id)) {
                        gifts.add(gift);
                        giftIds.add(gift.id);
                    }
                }
            }
        }

        boolean changed = false;
        if (gifts.size() != oldGifts.size()) {
            changed = true;
        } else for (int i = 0; i < gifts.size(); i++) {
            if (!gifts.get(i).equals(oldGifts.get(i))) {
                changed = true;
                break;
            }
        }

        for (int i = 0; i < gifts.size(); i++) {
            final Gift g = gifts.get(i);
            Gift oldGift = null;
            for (int j = 0; j < oldGifts.size(); ++j) {
                if (oldGifts.get(j).id == g.id) {
                    oldGift = oldGifts.get(j);
                    break;
                }
            }

            if (oldGift != null) {
                g.copy(oldGift);
            } else {
                g.gradient = new RadialGradient(0, 0, dp(22.5f), new int[]{g.color, Theme.multAlpha(g.color, 0.0f)}, new float[]{0, 1}, Shader.TileMode.CLAMP);
                g.gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                g.gradientPaint.setShader(g.gradient);
                if (g.document != null) {
                    g.emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, g.document);
                } else {
                    g.emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, g.documentId);
                }
                g.animatedFloat = new AnimatedFloat(this, 0, 320, null);
                g.animatedFloat.force(0.0f);
                if (isAttachedToWindow()) {
                    g.emojiDrawable.addView(this);
                }
            }
        }

        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < maxCount; i++) {
            positions.add(i);
        }

        for (int i = 0; i < oldGifts.size(); i++) {
            final Gift g = oldGifts.get(i);
            Gift newGift = null;
            for (int j = 0; j < gifts.size(); ++j) {
                if (gifts.get(j).id == g.id) {
                    newGift = gifts.get(j);
                    break;
                }
            }
            if (newGift == null) {
                g.emojiDrawable.removeView(this);
                g.emojiDrawable = null;
                g.gradient = null;
            } else {
                positions.remove((Object) g.position);
            }
        }

        if (!positions.isEmpty()) {
            BagRandomizer<Integer> bagRandomizer = new BagRandomizer<>(positions);
            for (int i = 0; i < gifts.size(); ++i) {
                final Gift g = gifts.get(i);
                if (g.position == -1) {
                    g.position = bagRandomizer.next();
                }
            }
        }

        if (changed)
            invalidate();
    }

    private final TimeInterpolator giftCollapseXInterpolator = new DecelerateInterpolator();
    private final TimeInterpolator giftCollapseYInterpolator = new LinearInterpolator();

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final java.util.List<String> ithaKinds = dialogId > 0 ? org.telegram.messenger.IthaBadges.kindsFor(dialogId, () -> invalidate()) : null;
        final boolean hasIthaBadges = ithaKinds != null && !ithaKinds.isEmpty();
        if ((gifts.isEmpty() && !hasIthaBadges) || expandProgress >= 1.0f || collapseProgress <= 0f) return;

        final float realX = avatarContainer.getX();
        final float realY = avatarContainer.getY();
        final float realW = (avatarContainer.getWidth()) * avatarContainer.getScaleX();
        final float realH = (avatarContainer.getHeight()) * avatarContainer.getScaleY();

        final float sz = dpf2(96);
        final float ax = Math.min(realX, (getWidth() - sz) / 2f);
        final float ay = Math.max(realY, (maxExpandY - sz) / 2f);
        final float aw = Math.max(realW, sz);
        final float ah = Math.max(realH, sz);


        canvas.save();
        canvas.clipRect(0, 0, getWidth(), expandY);

        final float acx = ax + aw / 2.0f;
        final float acy = ay + ah / 2.0f;

        final float realCX = realX + realW / 2.0f;
        final float realCY = realY + realH / 2.0f;

        float expandScale = expandY / maxExpandY;
        float closedAlpha = Utilities.clamp01((expandY - (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight())) / dp(50));

        for (int i = 0; i < gifts.size(); ++i) {
            final Gift gift = gifts.get(i);
            final float enter = gift.animatedFloat.set(1.0f);
            float scale = lerp(0.5f, 1.0f, enter);
            final float alpha = enter * (1.0f - expandProgress) * (1.0f - actionBarProgress) * (closedAlpha);

            float gx, gy;
            float delayValue;

            switch (gift.position) {
                case 0: // left
                    gx = acx / 2f - (dp(20) * expandScale);
                    gy = acy - dp(13);
                    delayValue = 1.6f;
                    break;
                case 1: // top left
                    gx = acx * 2f / 3f - (dp(6) * expandScale);
                    gy = ay - dp(4);
                    delayValue = 0;
                    break;
                case 2: // bottom left
                    gx = acx * 2f / 3f - (dp(12) * expandScale);
                    gy = ay + ah - dp(16);
                    delayValue = 0.9f;
                    break;
                case 3: // right
                    gx = acx * 1.5f + (dp(20) * expandScale);
                    gy = acy - dp(13);
                    delayValue = 1.6f;
                    break;
                case 4: // top right
                    gx = acx * 4f / 3f + (dp(12) * expandScale);
                    gy = ay - dp(4);
                    delayValue = 0.9f;
                    break;
                default: // bottom right
                    gx = acx * 4f / 3f + (dp(12) * expandScale);
                    gy = ay + ah - dp(16);
                    delayValue = 0;
                    break;
            }

            final float collapseProgressWithEnter;
            if (isOpening || enter >= 1f) {
                collapseProgressWithEnter = collapseProgress;
            } else {
                collapseProgressWithEnter = Math.min(enter, collapseProgress);
            }

            final float delayFraction = 0.2f;
            final float maxDelayFraction = 1.6f * delayFraction;
            final float intervalFraction = 1f - maxDelayFraction;

            float delay = delayValue * delayFraction;
            float collapse = collapseProgressWithEnter >= 1f - delay ? 1f
                    : Utilities.clamp01((collapseProgressWithEnter - maxDelayFraction + delay) / intervalFraction);
            if (collapse < 1f) {
                gx = AndroidUtilities.lerp(realCX, gx, giftCollapseXInterpolator.getInterpolation(collapse));
                gy = AndroidUtilities.lerp(realCY, gy, giftCollapseYInterpolator.getInterpolation(collapse));
                scale = AndroidUtilities.lerp(scale / 2f, scale, collapse);
            }

            gift.draw(canvas, gx, gy, scale, 0, alpha, 1f);
        }

        if (hasIthaBadges) {
            // las insignias giran alrededor del avatar: por arriba (espacio libre) y por abajo, nunca detras de la foto
            final int count = Math.min(4, ithaKinds.size());
            final float tSec = (android.os.SystemClock.uptimeMillis() % 1000000L) / 1000f;
            ithaRx = aw / 2f + dp(46);
            ithaRyUp = ah / 2f + dp(16);
            ithaRyDown = ah / 2f + dp(10);
            ithaAcx = acx;
            ithaAcy = acy;

            // ritmo: rebote seguido (120 pulsos por minuto) cuando suena musica, suave cuando no
            final long nowMs = android.os.SystemClock.uptimeMillis();
            final float dt = ithaLastMs == 0 ? 0f : Math.min(0.05f, (nowMs - ithaLastMs) / 1000f);
            ithaLastMs = nowMs;
            boolean music = false;
            try {
                final org.telegram.messenger.MediaController mc = org.telegram.messenger.MediaController.getInstance();
                music = mc.getPlayingMessageObject() != null && !mc.isMessagePaused();
            } catch (Throwable ignore) {
            }
            ithaBeatAmt += ((music ? 1f : 0.3f) - ithaBeatAmt) * Math.min(1f, dt * 4f);
            ithaBeatPhase += dt / (music ? 0.5f : 1.6f);
            ithaBeatPhase -= (float) Math.floor(ithaBeatPhase);

            for (int i = 0; i < count; ++i) {
                final String kind = ithaKinds.get(i);
                final float enter = ithaEnter(kind).set(1.0f);
                final float theta = (float) (tSec * (2 * Math.PI / 10.0) + i * 2 * Math.PI / count + 0.6);
                final float depth = (float) Math.sin(theta); // 1 = abajo/adelante, -1 = arriba/atras

                // rebote: sale disparada hacia afuera, pasa de largo y vuelve (resorte)
                final float bb = (ithaBeatPhase + i * 0.03f) % 1f;
                final float kick = (float) (Math.exp(-7f * bb) * Math.cos(18f * bb)) * ithaBeatAmt;

                final float push = 1f + 0.07f * kick;
                float gx = ithaAcx + (float) Math.cos(theta) * ithaRx * push;
                float gy = ithaOrbitY(theta) + (ithaOrbitY(theta) - ithaAcy) * 0.07f * kick - dp(5) * kick;
                float scale = lerp(0.5f, 1.0f, enter) * (1f + 0.16f * depth) * (1f + 0.20f * kick);
                final float alpha = enter * (1.0f - expandProgress) * (1.0f - actionBarProgress) * closedAlpha * (0.8f + 0.1f * (depth + 1f));

                final float collapseWithEnter = (isOpening || enter >= 1f) ? collapseProgress : Math.min(enter, collapseProgress);
                final float delayFraction = 0.2f;
                final float maxDelayFraction = 1.6f * delayFraction;
                final float intervalFraction = 1f - maxDelayFraction;
                final float delay = 0.5f * i * delayFraction;
                final float collapse = collapseWithEnter >= 1f - delay ? 1f
                        : Utilities.clamp01((collapseWithEnter - maxDelayFraction + delay) / intervalFraction);
                if (collapse < 1f) {
                    gx = AndroidUtilities.lerp(realCX, gx, giftCollapseXInterpolator.getInterpolation(collapse));
                    gy = AndroidUtilities.lerp(realCY, gy, giftCollapseYInterpolator.getInterpolation(collapse));
                    scale = AndroidUtilities.lerp(scale / 2f, scale, collapse);
                }

                // efecto gelatina: se estira y se aplasta mientras gira
                final float wobble = 0.06f * (float) Math.sin(tSec * 3.1f + i * 1.7f);
                if (collapse >= 1f && enter >= 1f) {
                    // estela de cometa detras de la burbuja
                    for (int k = 3; k >= 1; --k) {
                        final float th = theta - k * 0.17f;
                        final float dd = (float) Math.sin(th);
                        drawIthaTrail(canvas, kind, ithaAcx + (float) Math.cos(th) * ithaRx, ithaOrbitY(th), 1f + 0.16f * dd, alpha * 0.07f * (4 - k));
                    }
                }
                drawIthaBadge(canvas, kind, gx, gy, scale * (1f + wobble), scale * (1f - wobble), alpha, tSec, i * 1.7f, kick);
            }
            if (count > 0) {
                postInvalidateOnAnimation();
            }
        }

        canvas.restore();
    }

    private final java.util.HashMap<String, AnimatedFloat> ithaEnter = new java.util.HashMap<>();
    private final java.util.HashMap<String, android.graphics.drawable.Drawable> ithaDrawables = new java.util.HashMap<>();
    private final Paint ithaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private AnimatedFloat ithaEnter(String kind) {
        AnimatedFloat f = ithaEnter.get(kind);
        if (f == null) {
            f = new AnimatedFloat(this, 0, 320, null);
            f.force(0.0f);
            ithaEnter.put(kind, f);
        }
        return f;
    }

    private float ithaRx, ithaRyUp, ithaRyDown, ithaAcx, ithaAcy;
    private long ithaLastMs;
    private float ithaBeatAmt = 0.3f, ithaBeatPhase;

    /** Y de la orbita: la mitad de arriba es mas alta (usa el espacio libre sobre la foto). */
    private float ithaOrbitY(float theta) {
        final float s = (float) Math.sin(theta);
        return ithaAcy + (s < 0 ? s * ithaRyUp : s * ithaRyDown);
    }

    private static class IthaFx {
        Shader glow, body, ring, shine;
    }

    private final java.util.HashMap<String, IthaFx> ithaFxMap = new java.util.HashMap<>();
    private final android.graphics.Path ithaStar = new android.graphics.Path();
    private final android.graphics.Path ithaClip = new android.graphics.Path();
    private final RectF ithaRect = new RectF();

    private IthaFx ithaFx(String kind, int color) {
        IthaFx fx = ithaFxMap.get(kind);
        if (fx != null) return fx;
        fx = new IthaFx();
        final float r = dpf2(23);
        fx.glow = new RadialGradient(0, 0, dp(40), new int[]{Theme.multAlpha(color, 0.60f), Theme.multAlpha(color, 0.0f)}, new float[]{0, 1}, Shader.TileMode.CLAMP);
        // cuerpo tipo esfera de cristal: luz arriba a la izquierda, borde mas oscuro y saturado
        fx.body = new RadialGradient(-0.30f * r, -0.35f * r, 1.35f * r, new int[]{
                Theme.multAlpha(androidx.core.graphics.ColorUtils.blendARGB(color, 0xFFFFFFFF, 0.55f), 0.60f),
                Theme.multAlpha(color, 0.34f),
                Theme.multAlpha(androidx.core.graphics.ColorUtils.blendARGB(color, 0xFF000000, 0.20f), 0.42f)
        }, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        // aro de neon con un brillo que gira
        final int a = Theme.multAlpha(color, 0.55f);
        fx.ring = new android.graphics.SweepGradient(0, 0, new int[]{a, 0xFFFFFFFF, a, a}, new float[]{0f, 0.08f, 0.35f, 1f});
        // banda de destello
        fx.shine = new android.graphics.LinearGradient(-dpf2(8), 0, dpf2(8), 0, new int[]{0x00FFFFFF, 0x99FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        ithaFxMap.put(kind, fx);
        return fx;
    }

    private void buildIthaStar() {
        for (int k = 0; k < 8; k++) {
            final double ang = k * Math.PI / 4;
            final float rad = (k % 2 == 0) ? 1f : 0.28f;
            final float x = (float) Math.cos(ang) * rad;
            final float y = (float) Math.sin(ang) * rad;
            if (k == 0) ithaStar.moveTo(x, y); else ithaStar.lineTo(x, y);
        }
        ithaStar.close();
    }

    private void drawIthaTrail(Canvas canvas, String kind, float cx, float cy, float scale, float alpha) {
        if (alpha <= 0.0f) return;
        final int color = org.telegram.messenger.IthaBadgeDrawable.colorFor(kind);
        if (color == 0) return;
        final IthaFx fx = ithaFx(kind, color);
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale * 0.75f, scale * 0.75f);
        ithaPaint.setStyle(Paint.Style.FILL);
        ithaPaint.setShader(fx.glow);
        ithaPaint.setAlpha((int) (0xFF * Math.min(1f, alpha)));
        canvas.drawCircle(0, 0, dp(40), ithaPaint);
        ithaPaint.setShader(null);
        canvas.restore();
    }

    private void drawIthaBadge(Canvas canvas, String kind, float cx, float cy, float scaleX, float scaleY, float alpha, float tSec, float phase, float beat) {
        if (alpha <= 0.0f) return;
        final int color = org.telegram.messenger.IthaBadgeDrawable.colorFor(kind);
        if (color == 0) return;
        if (ithaStar.isEmpty()) buildIthaStar();
        final IthaFx fx = ithaFx(kind, color);
        android.graphics.drawable.Drawable d = ithaDrawables.get(kind);
        if (d == null) {
            d = new org.telegram.messenger.IthaBadgeDrawable(kind, 30);
            ithaDrawables.put(kind, d);
        }
        final float r = dpf2(23);
        final float pulse = 0.5f + 0.5f * (float) Math.sin(tSec * 2.6f + phase);
        final float bp = Math.max(0f, beat);

        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scaleX, scaleY);

        // resplandor que "respira"
        canvas.save();
        final float gs = 1f + 0.14f * pulse + 0.35f * bp;
        canvas.scale(gs, gs);
        ithaPaint.setStyle(Paint.Style.FILL);
        ithaPaint.setShader(fx.glow);
        ithaPaint.setAlpha(Math.min(0xFF, (int) (0xFF * alpha * (0.65f + 0.35f * pulse + 0.6f * bp))));
        canvas.drawCircle(0, 0, dp(40), ithaPaint);
        canvas.restore();

        // cuerpo de cristal
        ithaPaint.setShader(fx.body);
        ithaPaint.setAlpha((int) (0xFF * alpha));
        canvas.drawCircle(0, 0, r, ithaPaint);

        // reflejo brillante arriba a la izquierda
        ithaPaint.setShader(null);
        ithaPaint.setColor(0xFFFFFFFF);
        ithaPaint.setAlpha((int) (0x55 * alpha));
        canvas.save();
        canvas.translate(-0.40f * r, -0.52f * r);
        canvas.rotate(-40f);
        ithaRect.set(-0.34f * r, -0.16f * r, 0.34f * r, 0.16f * r);
        canvas.drawOval(ithaRect, ithaPaint);
        canvas.restore();

        // aro de neon con brillo giratorio
        ithaPaint.setStyle(Paint.Style.STROKE);
        ithaPaint.setStrokeWidth(dpf2(1.6f));
        ithaPaint.setShader(fx.ring);
        ithaPaint.setAlpha((int) (0xFF * alpha));
        canvas.save();
        canvas.rotate(tSec * 140f + phase * 40f);
        canvas.drawCircle(0, 0, r, ithaPaint);
        canvas.restore();
        ithaPaint.setShader(null);
        ithaPaint.setStyle(Paint.Style.FILL);

        // icono, con un pequeno latido
        final int sz = dp(38);
        canvas.save();
        final float is = 1f + 0.05f * pulse + 0.12f * bp;
        canvas.scale(is, is);
        d.setBounds(-sz / 2, -sz / 2, sz / 2, sz / 2);
        d.setAlpha((int) (0xFF * alpha));
        d.draw(canvas);
        canvas.restore();

        // destello que cruza la burbuja cada tanto
        final float sp = (((tSec + phase * 0.9f) % 3.4f)) / 0.75f;
        if (sp < 1f) {
            ithaClip.reset();
            ithaClip.addCircle(0, 0, r - dpf2(1), android.graphics.Path.Direction.CW);
            canvas.save();
            canvas.clipPath(ithaClip);
            canvas.translate(lerp(-r * 1.3f, r * 1.3f, sp), 0);
            canvas.rotate(22f);
            ithaPaint.setShader(fx.shine);
            ithaPaint.setAlpha((int) (0xFF * alpha * Math.sin(Math.PI * sp)));
            canvas.drawRect(-dpf2(8), -r * 1.6f, dpf2(8), r * 1.6f, ithaPaint);
            ithaPaint.setShader(null);
            canvas.restore();
        }

        // chispas que titilan alrededor
        ithaPaint.setShader(null);
        ithaPaint.setColor(0xFFFFFFFF);
        for (int k = 0; k < 3; ++k) {
            float tw = Math.max(0f, (float) Math.sin(tSec * 3.3f + k * 2.3f + phase));
            tw = tw * tw;
            if (tw < 0.03f) continue;
            final float ang = tSec * 0.9f + k * 2.094f + phase;
            final float sx = (float) Math.cos(ang) * r * 1.18f;
            final float sy = (float) Math.sin(ang) * r * 1.05f;
            final float s = dpf2(4.5f) * (0.5f + 0.5f * tw);
            canvas.save();
            canvas.translate(sx, sy);
            canvas.scale(s, s);
            ithaPaint.setAlpha((int) (0xFF * alpha * tw));
            canvas.drawPath(ithaStar, ithaPaint);
            canvas.restore();
        }
        canvas.restore();
    }

    public Gift getGiftUnder(float x, float y) {
        for (int i = 0; i < gifts.size(); ++i) {
            if (gifts.get(i).bounds.contains(x, y))
                return gifts.get(i);
        }
        return null;
    }

    private Gift pressedGift;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!active) return false;
        final Gift hit = getGiftUnder(event.getX(), event.getY());
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            pressedGift = hit;
            if (pressedGift != null) {
                pressedGift.bounce.setPressed(true);
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (pressedGift != hit && pressedGift != null) {
                pressedGift.bounce.setPressed(false);
                pressedGift = null;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (pressedGift != null) {
                onGiftClick(pressedGift);
                pressedGift.bounce.setPressed(false);
                pressedGift = null;
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (pressedGift != null) {
                pressedGift.bounce.setPressed(false);
                pressedGift = null;
            }
        }
        return pressedGift != null;
    }

    public void onGiftClick(Gift gift) {
        Browser.openUrl(getContext(), "https://t.me/nft/" + gift.slug);
    }
}
