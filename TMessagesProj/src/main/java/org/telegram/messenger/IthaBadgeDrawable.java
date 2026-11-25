package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Sello blando translucido con un glifo, al estilo de las insignias de apoyo. */
public class IthaBadgeDrawable extends Drawable {

    public static final String DONANTE = "donante";
    public static final String COLABORADOR = "colaborador";
    public static final String FUNDADOR = "fundador";
    public static final String BETA = "beta";
    public static final String DISENADOR = "disenador";
    public static final String PLUGINS = "plugins";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path glyph = new Path();
    private final Path glyphFill = new Path();
    private final Path spark = new Path();
    private final int color;
    private final String kind;
    private final int sizePx;
    private int alpha = 255;

    public static int colorFor(String kind) {
        switch (kind) {
            case DONANTE: return 0xFFFF7AA8;
            case COLABORADOR: return 0xFF7F9BFF;
            case FUNDADOR: return 0xFFFFD23F;
            case BETA: return 0xFF2EC4B6;
            case DISENADOR: return 0xFFE040FB;
            case PLUGINS: return 0xFFC9A0FF;
        }
        return 0;
    }

    public static boolean isKnown(String kind) {
        return colorFor(kind) != 0;
    }

    public IthaBadgeDrawable(String kind, int sizeDp) {
        this.kind = kind;
        this.color = colorFor(kind);
        this.sizePx = AndroidUtilities.dp(sizeDp);
        buildGlyph();
    }

    private void buildGlyph() {
        switch (kind) {
            case DONANTE:
                glyph.moveTo(50, 72);
                glyph.cubicTo(22, 54, 17, 38, 28, 30);
                glyph.cubicTo(36, 24, 46, 29, 50, 37);
                glyph.cubicTo(54, 29, 64, 24, 72, 30);
                glyph.cubicTo(83, 38, 78, 54, 50, 72);
                glyph.close();
                break;
            case COLABORADOR:
                glyph.moveTo(38, 36);
                glyph.lineTo(24, 50);
                glyph.lineTo(38, 64);
                glyph.moveTo(62, 36);
                glyph.lineTo(76, 50);
                glyph.lineTo(62, 64);
                glyph.moveTo(54, 32);
                glyph.lineTo(46, 68);
                break;
            case FUNDADOR:
                glyph.moveTo(26, 64);
                glyph.lineTo(22, 38);
                glyph.lineTo(38, 50);
                glyph.lineTo(50, 30);
                glyph.lineTo(62, 50);
                glyph.lineTo(78, 38);
                glyph.lineTo(74, 64);
                glyph.close();
                break;
            case BETA:
                // frasco: contorno + liquido
                glyph.moveTo(41, 26);
                glyph.lineTo(59, 26);
                glyph.moveTo(44, 26);
                glyph.lineTo(44, 44);
                glyph.lineTo(29, 68);
                glyph.cubicTo(26, 74, 30, 78, 35, 78);
                glyph.lineTo(65, 78);
                glyph.cubicTo(70, 78, 74, 74, 71, 68);
                glyph.lineTo(56, 44);
                glyph.lineTo(56, 26);
                glyphFill.moveTo(35, 64);
                glyphFill.lineTo(65, 64);
                glyphFill.lineTo(69, 71);
                glyphFill.cubicTo(71, 74, 69, 75, 65, 75);
                glyphFill.lineTo(35, 75);
                glyphFill.cubicTo(31, 75, 29, 74, 31, 71);
                glyphFill.close();
                break;
            case DISENADOR:
                // paleta con cuatro agujeros
                glyph.setFillType(Path.FillType.EVEN_ODD);
                glyph.moveTo(50, 22);
                glyph.cubicTo(28, 22, 18, 38, 20, 52);
                glyph.cubicTo(22, 66, 34, 76, 46, 76);
                glyph.cubicTo(54, 76, 54, 68, 58, 64);
                glyph.cubicTo(62, 60, 72, 62, 76, 56);
                glyph.cubicTo(84, 42, 72, 22, 50, 22);
                glyph.close();
                glyph.addCircle(36, 44, 5.5f, Path.Direction.CW);
                glyph.addCircle(50, 34, 5.5f, Path.Direction.CW);
                glyph.addCircle(64, 42, 5.5f, Path.Direction.CW);
                glyph.addCircle(38, 60, 5.5f, Path.Direction.CW);
                break;
            case PLUGINS:
                // pieza de rompecabezas
                glyph.addRoundRect(30, 36, 70, 72, 7, 7, Path.Direction.CW);
                glyph.addCircle(50, 31, 9, Path.Direction.CW);
                glyph.addCircle(76, 54, 9, Path.Direction.CW);
                break;
        }
    }

    private void addSpark(float x, float y, float s) {
        spark.moveTo(x, y - s);
        spark.quadTo(x, y, x + s, y);
        spark.quadTo(x, y, x, y + s);
        spark.quadTo(x, y, x - s, y);
        spark.quadTo(x, y, x, y - s);
        spark.close();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) {
            return;
        }
        final float scale = Math.min(b.width(), b.height()) / 100f;
        final int save = canvas.save();
        canvas.translate(b.left + (b.width() - 100 * scale) / 2f, b.top + (b.height() - 100 * scale) / 2f);
        canvas.scale(scale, scale);

        // sello: todo se pinta opaco dentro de una capa para que la transparencia no se acumule
        final int layer = canvas.saveLayerAlpha(0, 0, 100, 100, (int) (0x4D * (alpha / 255f)));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color | 0xFF000000);
        for (int i = 0; i < 8; i++) {
            double a = 2 * Math.PI * i / 8;
            canvas.drawCircle((float) (50 + 36 * Math.cos(a)), (float) (50 + 36 * Math.sin(a)), 15, paint);
        }
        canvas.drawCircle(50, 50, 38, paint);
        canvas.restoreToCount(layer);

        paint.setColor(color);
        paint.setAlpha(alpha);
        if (kind.equals(COLABORADOR) || kind.equals(BETA)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(kind.equals(BETA) ? 7 : 8);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            canvas.drawPath(glyph, paint);
            paint.setStyle(Paint.Style.FILL);
            if (kind.equals(BETA)) {
                canvas.drawPath(glyphFill, paint);
            }
        } else {
            canvas.drawPath(glyph, paint);
        }

        spark.rewind();
        addSpark(88, 16, 7);
        addSpark(12, 86, 5);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setAlpha(alpha);
        canvas.drawPath(spark, paint);
        canvas.restoreToCount(save);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return sizePx;
    }

    @Override
    public int getIntrinsicHeight() {
        return sizePx;
    }
}
