package com.abrar.voyahiran.core.bridge.clustertext;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.Locale;

/** Local source ports of the factory Bold/Marquee widgets. Android-only: loaded in the
 * cluster process without starting our Application or depending on its protection runtime. */
public final class ClusterTextAssets {
    public static void installSourceBadge(android.widget.ImageView icon) {
        ClusterRoundedImageView.replaceBadge(icon);
    }
    public static void installCover(android.widget.ImageView cover,int theme) {
        ClusterRoundedImageView.replace(cover,theme);
    }
    private static Typeface semibold;
    private static synchronized Typeface font(Context owner) {
        if (semibold == null) semibold = Typeface.createFromAsset(owner.getAssets(), "cluster/text/semibold.ttf");
        return semibold;
    }
    private static boolean factoryFontLanguage() {
        String language = Locale.getDefault().getLanguage().toLowerCase(Locale.ENGLISH);
        return language.endsWith("en") || language.endsWith("zh");
    }
    /** Detached bitmap views have no window to correct the huge single-line layout's scroll.
     * Always measure in the real width. Long labels scroll using the existing bitmap frame clock. */
    public static class BoundedTextView extends TextView {
        private final boolean moving;
        private StaticLayout fullLine;
        private String lastText;
        private float lastSize;
        private Typeface lastTypeface;
        private int lastWidth;
        private Bitmap lineBitmap;
        private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private float lastSpacing, lastScale;
        private int lastColor, lastDirection;
        private boolean lastIncludePad;
        private long began;
        public BoundedTextView(Context c) { this(c,false); }
        BoundedTextView(Context c,boolean moving) { super(c); this.moving=moving; }
        @Override public void setSingleLine(boolean single) {
            super.setSingleLine(single);
            if(single){setMaxLines(1);super.setHorizontallyScrolling(false);}
        }
        @Override protected void onMeasure(int w,int h) {
            super.setHorizontallyScrolling(false);
            if(moving){setMaxLines(1);super.setEllipsize(TextUtils.TruncateAt.END);}
            super.onMeasure(w,h);
        }
        @Override protected void onDraw(Canvas canvas) {
            if(!moving){super.onDraw(canvas);return;}
            String text=getText().toString().replace('\n',' ');
            int width=getWidth()-getCompoundPaddingLeft()-getCompoundPaddingRight();
            if(width<=0)return;
            if(fullLine==null || !text.equals(lastText) || lastSize!=getTextSize() || lastTypeface!=getTypeface() || lastWidth!=width
                || lastSpacing!=getLetterSpacing() || lastScale!=getTextScaleX() || lastColor!=getCurrentTextColor()
                || lastDirection!=getTextDirection() || lastIncludePad!=getIncludeFontPadding()){
                lastText=text;lastSize=getTextSize();lastTypeface=getTypeface();lastWidth=width;began=SystemClock.uptimeMillis();
                lastSpacing=getLetterSpacing();lastScale=getTextScaleX();lastColor=getCurrentTextColor();
                lastDirection=getTextDirection();lastIncludePad=getIncludeFontPadding();
                lineBitmap=null;
                getPaint().setColor(lastColor);getPaint().drawableState=getDrawableState();
                int desired=Math.max(1,(int)Math.ceil(Layout.getDesiredWidth(text,getPaint()))+1);
                fullLine=StaticLayout.Builder.obtain(text,0,text.length(),getPaint(),desired)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(getIncludeFontPadding()).setMaxLines(1).build();
                if(desired>width){
                    // Rasterize glyphs at a fixed origin once. Fractional scrolling must move
                    // this raster, not re-hint each glyph independently on every software frame.
                    lineBitmap=Bitmap.createBitmap(desired,fullLine.getHeight(),Bitmap.Config.ARGB_8888);
                    fullLine.draw(new Canvas(lineBitmap));
                }
            }
            if(fullLine.getWidth()<=width){super.onDraw(canvas);return;}
            getPaint().setColor(getCurrentTextColor());getPaint().drawableState=getDrawableState();
            float distance=fullLine.getWidth()-width;
            float speed=30f*getResources().getDisplayMetrics().density;
            float travel=distance/speed;
            float time=(SystemClock.uptimeMillis()-began)/1000f%(travel+2.4f);
            float offset=Math.min(distance,Math.max(0,time-1.2f)*speed);
            if(fullLine.getParagraphDirection(0)<0)offset=distance-offset;
            int top=getCompoundPaddingTop(),bottom=getHeight()-getCompoundPaddingBottom();
            int gravity=getGravity()&android.view.Gravity.VERTICAL_GRAVITY_MASK;
            float y=gravity==android.view.Gravity.CENTER_VERTICAL?top+(bottom-top-fullLine.getHeight())/2f:
                gravity==android.view.Gravity.BOTTOM?bottom-fullLine.getHeight():top;
            int save=canvas.save();canvas.clipRect(getCompoundPaddingLeft(),top,getWidth()-getCompoundPaddingRight(),bottom);
            canvas.drawBitmap(lineBitmap,getCompoundPaddingLeft()-offset,y,bitmapPaint);canvas.restoreToCount(save);
        }
    }
    public static class BoldTextView extends BoundedTextView {
        private final Typeface bundled;
        public BoldTextView(Context context, Context owner) {
            this(context,owner,false);
        }
        BoldTextView(Context context,Context owner,boolean moving) { super(context,moving);bundled=font(owner);refreshFont(); }
        private void refreshFont() { if (bundled != null) setTypeface(factoryFontLanguage() ? bundled : null); }
        @Override protected void onMeasure(int w, int h) { refreshFont(); super.onMeasure(w,h); }
        @Override protected void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); refreshFont(); }
        @Override protected void onTextChanged(CharSequence t,int start,int before,int count) {
            super.onTextChanged(t,start,before,count); refreshFont();
        }
    }
    public static final class MarqueeBoldTextView extends BoldTextView {
        public MarqueeBoldTextView(Context context,Context owner) {
            super(context,owner,true); setEllipsize(TextUtils.TruncateAt.MARQUEE); setMarqueeRepeatLimit(-1); setSingleLine(true);
        }
        @Override public boolean isFocused() { return true; }
        @Override protected void onFocusChanged(boolean focused,int direction,Rect old) {
            if(focused) super.onFocusChanged(true,direction,old);
        }
        @Override public void onWindowFocusChanged(boolean focused) { if(focused)super.onWindowFocusChanged(true); }
    }
    public static final class MarqueeTextView extends BoundedTextView {
        public MarqueeTextView(Context context) {
            super(context,true); setEllipsize(TextUtils.TruncateAt.MARQUEE); setMarqueeRepeatLimit(-1); setSingleLine(true);
        }
        @Override public boolean isFocused() { return true; }
    }

    /** Replace only text leaves; keep the factory layout, constraints, images and geometry. */
    public static int install(View root,Context owner) {
        if(!(root instanceof ViewGroup))return 0;
        int count=0;
        ViewGroup parent=(ViewGroup)root;
        for(int i=0;i<parent.getChildCount();i++) {
            View child=parent.getChildAt(i);
            if(!(child instanceof TextView)) { count+=install(child,owner); continue; }
            TextView old=(TextView)child;
            String name=old.getClass().getSimpleName();
            TextView replacement;
            if(name.equals("MarqueeBoldTextView"))replacement=new MarqueeBoldTextView(old.getContext(),owner);
            else if(name.equals("MarqueeTextView"))replacement=new MarqueeTextView(old.getContext());
            else if(name.equals("BoldTextView"))replacement=new BoldTextView(old.getContext(),owner);
            else replacement=new BoundedTextView(old.getContext());
            replacement.setId(old.getId()); replacement.setLayoutParams(old.getLayoutParams());
            replacement.setTextSize(TypedValue.COMPLEX_UNIT_PX,old.getTextSize());
            replacement.setTextColor(old.getTextColors()); replacement.setGravity(old.getGravity());
            if(!(replacement instanceof BoldTextView))replacement.setTypeface(old.getTypeface());
            replacement.setIncludeFontPadding(old.getIncludeFontPadding());
            replacement.setPadding(old.getPaddingLeft(),old.getPaddingTop(),old.getPaddingRight(),old.getPaddingBottom());
            replacement.setMinWidth(old.getMinWidth()); replacement.setMaxWidth(old.getMaxWidth());
            if(old.getMinHeight()>=0)replacement.setMinHeight(old.getMinHeight());
            if(old.getMaxHeight()>=0)replacement.setMaxHeight(old.getMaxHeight());
            if(old.getMinLines()>=0)replacement.setMinLines(old.getMinLines());
            if(old.getMaxLines()>=0)replacement.setMaxLines(old.getMaxLines());
            replacement.setEllipsize(old.getEllipsize()); replacement.setLetterSpacing(old.getLetterSpacing());
            replacement.setTextScaleX(old.getTextScaleX()); replacement.setTextDirection(old.getTextDirection());
            replacement.setLayoutDirection(old.getLayoutDirection());
            replacement.setVisibility(old.getVisibility()); replacement.setAlpha(old.getAlpha());
            replacement.setText(old.getText());
            parent.removeViewAt(i); parent.addView(replacement,i); count++;
        }
        return count;
    }
}
