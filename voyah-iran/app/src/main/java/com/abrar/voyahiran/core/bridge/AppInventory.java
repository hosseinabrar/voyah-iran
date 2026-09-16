package com.abrar.voyahiran.core.bridge;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;

/** Read-only package inventory for the USB installer, invoked with app_process. */
public final class AppInventory {
    public static void main(String[] args) {
        try {
            if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
            Class<?> threadClass = Class.forName("android.app.ActivityThread");
            Object thread = threadClass.getMethod("systemMain").invoke(null);
            Context context = (Context) threadClass.getMethod("getSystemContext").invoke(thread);
            PackageManager pm = context.getPackageManager();
            for (ApplicationInfo app : pm.getInstalledApplications(0)) {
                if (app.packageName.equals("com.abrar.voyahiran") ||
                    (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue;
                JSONObject item = new JSONObject();
                item.put("Package", app.packageName);
                item.put("Label", app.loadLabel(pm).toString());
                try {
                    Drawable icon = app.loadIcon(pm);
                    Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
                    icon.setBounds(0, 0, 64, 64);
                    icon.draw(new Canvas(bitmap));
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
                    bitmap.recycle();
                    item.put("Icon", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
                } catch (Exception e) { item.put("Icon", ""); }
                System.out.println("VI_APP:" + item.toString());
            }
            System.out.println("VI_APPS_DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
