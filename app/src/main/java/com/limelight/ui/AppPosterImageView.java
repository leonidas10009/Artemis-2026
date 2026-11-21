package com.limelight.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * Image view for application cards.
 *
 * The foreground uses the normal fitCenter scale type so the complete poster
 * remains visible. A darkened, center-cropped copy of the same image is drawn
 * behind it to fill the letterbox area, matching the Web UI card treatment.
 */
public class AppPosterImageView extends AppCompatImageView {
    private static final int BACKDROP_ALPHA = 180;

    private static final ColorMatrixColorFilter BACKDROP_FILTER = createBackdropFilter();

    public AppPosterImageView(Context context) {
        super(context);
    }

    public AppPosterImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AppPosterImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private static ColorMatrixColorFilter createBackdropFilter() {
        ColorMatrix saturation = new ColorMatrix();
        saturation.setSaturation(1.15f);

        ColorMatrix brightness = new ColorMatrix(new float[] {
                0.42f, 0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.42f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.42f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        });

        saturation.postConcat(brightness);
        return new ColorMatrixColorFilter(saturation);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable drawable = getDrawable();
        int width = getWidth();
        int height = getHeight();

        if (drawable != null && width > 0 && height > 0) {
            int drawableWidth = drawable.getIntrinsicWidth();
            int drawableHeight = drawable.getIntrinsicHeight();

            if (drawableWidth > 0 && drawableHeight > 0) {
                float scale = Math.max(width / (float) drawableWidth, height / (float) drawableHeight);
                float dx = (width - drawableWidth * scale) * 0.5f;
                float dy = (height - drawableHeight * scale) * 0.5f;

                int saveCount = canvas.save();
                canvas.translate(dx, dy);
                canvas.scale(scale, scale);

                drawable.setAlpha(BACKDROP_ALPHA);
                drawable.setColorFilter(BACKDROP_FILTER);
                drawable.draw(canvas);
                drawable.setColorFilter(null);
                drawable.setAlpha(255);

                canvas.restoreToCount(saveCount);
            }
        }

        super.onDraw(canvas);
    }
}
