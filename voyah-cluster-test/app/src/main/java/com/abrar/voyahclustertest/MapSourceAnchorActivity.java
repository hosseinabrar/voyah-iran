package com.abrar.voyahclustertest;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

/** A second task beneath the source map avoids the OEM empty-stack focus bug on transfer. */
public final class MapSourceAnchorActivity extends Activity {
 @Override public void onCreate(Bundle state){super.onCreate(state);
  if(!getWindowManager().getDefaultDisplay().getName().startsWith("VoyahClusterTest.MapSource.")){finish();return;}
  View v=new View(this);v.setBackgroundColor(android.graphics.Color.BLACK);setContentView(v);
 }
}
