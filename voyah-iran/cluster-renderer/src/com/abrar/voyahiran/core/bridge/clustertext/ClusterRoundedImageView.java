package com.abrar.voyahiran.core.bridge.clustertext;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.ViewGroup;
import android.widget.ImageView;

/** Bitmap-canvas-safe rounding, independent of the factory widget and hardware outline. */
public final class ClusterRoundedImageView extends ImageView {
    private final Path outside = new Path();
    private final Paint erase = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] radii;

    public ClusterRoundedImageView(Context context, int theme) {
        this(context,theme,-1);
    }

    private ClusterRoundedImageView(Context context, int theme, float radiusPixels) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        float radius = theme == 1 ? 74 : theme == 4 ? 24 : 10;
        // Factory RoundedImageView's actual radius-array order, including classic's asymmetry.
        radii = theme == 2 ? new float[]{18,18,4,4,18,18,4,4}
                : new float[]{radius,radius,radius,radius,radius,radius,radius,radius};
        for(int i=0;i<radii.length;i++) radii[i] *= density;
        if(radiusPixels>=0) java.util.Arrays.fill(radii,radiusPixels);
        erase.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        outside.reset();
        outside.setFillType(Path.FillType.EVEN_ODD);
        outside.addRect(0,0,w,h,Path.Direction.CW);
        outside.addRoundRect(new RectF(0,0,w,h),radii,Path.Direction.CW);
    }

    @Override public void draw(Canvas canvas) {
        int layer = canvas.saveLayer(0,0,getWidth(),getHeight(),null);
        super.draw(canvas);
        canvas.drawPath(outside,erase);
        canvas.restoreToCount(layer);
    }

    public static void replace(ImageView old,int theme) {
        replace(old,theme,-1);
    }

    public static void replaceBadge(ImageView old) {
        replace(old,4,4f);
    }

    private static void replace(ImageView old,int theme,float radiusPixels) {
        if(old instanceof ClusterRoundedImageView)return;
        ViewGroup parent=(ViewGroup)old.getParent();
        int index=parent.indexOfChild(old);
        ClusterRoundedImageView view=new ClusterRoundedImageView(old.getContext(),theme,radiusPixels);
        view.setId(old.getId()); view.setLayoutParams(old.getLayoutParams());
        view.setScaleType(old.getScaleType()); view.setImageDrawable(old.getDrawable());
        view.setPadding(old.getPaddingLeft(),old.getPaddingTop(),old.getPaddingRight(),old.getPaddingBottom());
        view.setVisibility(old.getVisibility()); view.setAlpha(old.getAlpha());
        view.setContentDescription(old.getContentDescription());
        parent.removeViewAt(index); parent.addView(view,index);
    }
}
