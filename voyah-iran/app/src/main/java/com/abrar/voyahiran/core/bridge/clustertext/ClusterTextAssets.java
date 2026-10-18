package com.abrar.voyahiran.core.bridge.clustertext;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
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
    public static class BoldTextView extends TextView {
        private final Typeface bundled;
        public BoldTextView(Context context, Context owner) {
            super(context); bundled = font(owner); refreshFont();
        }
        private void refreshFont() { if (bundled != null) setTypeface(factoryFontLanguage() ? bundled : null); }
        @Override protected void onMeasure(int w, int h) { refreshFont(); super.onMeasure(w,h); }
        @Override protected void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); refreshFont(); }
        @Override protected void onTextChanged(CharSequence t,int start,int before,int count) {
            super.onTextChanged(t,start,before,count); refreshFont();
        }
    }
    public static final class MarqueeBoldTextView extends BoldTextView {
        public MarqueeBoldTextView(Context context,Context owner) {
            super(context,owner); setEllipsize(TextUtils.TruncateAt.MARQUEE); setMarqueeRepeatLimit(-1); setSingleLine(true);
        }
        @Override public boolean isFocused() { return true; }
        @Override protected void onFocusChanged(boolean focused,int direction,Rect old) {
            if(focused) super.onFocusChanged(true,direction,old);
        }
        @Override public void onWindowFocusChanged(boolean focused) { if(focused)super.onWindowFocusChanged(true); }
    }
    public static final class MarqueeTextView extends TextView {
        public MarqueeTextView(Context context) {
            super(context); setEllipsize(TextUtils.TruncateAt.MARQUEE); setMarqueeRepeatLimit(-1); setSingleLine(true);
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
            else replacement=new TextView(old.getContext());
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
