package com.abrar.voyahiran.feature.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import com.abrar.voyahiran.R;
import java.nio.*;
import java.util.concurrent.*;

/** One map producer, two GPU outputs. No readback, screenshot polling or second map display. */
final class MapFrameRelay {
    private final HandlerThread thread = new HandlerThread("MapFrameRelay");
    private final Handler handler;
    private EGLDisplay egl;
    private EGLContext context;
    private EGLConfig config;
    private EGLSurface scratch, card, cluster;
    private SurfaceTexture input;
    private Surface source;
    private int program, texture, mask, width, height, cardW, cardH, clusterW, clusterH;
    private boolean frame;
    private volatile boolean closed;
    private Runnable ready;
    private final java.util.Map<String,Integer> uniforms=new java.util.HashMap<>();
    private int position;
    private int cardGeneration;
    private ClusterMapCalibration calibration;
    private long calibrationAt;
    private final float[] matrix = new float[16];
    private final FloatBuffer vertices = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final Context app;
    private final java.util.function.Consumer<Throwable> failure;
    MapFrameRelay(Context app, int width, int height, java.util.function.Consumer<Throwable> failure) {
        this.app=app.getApplicationContext();this.width=width;this.height=height;this.failure=failure;
        vertices.put(new float[]{-1,-1,1,-1,-1,1,1,1}).position(0);
        thread.start();handler=new Handler(thread.getLooper());
        FutureTask<Void> init=new FutureTask<>(()->{initialize();return null;});handler.post(init);
        try {init.get(4,TimeUnit.SECONDS);}catch(Exception e){close();throw new IllegalStateException("Map GPU initialization failed",e);}
    }
    Surface getSource(){return source;}
    void resize(int w,int h){post(()->{width=w;height=h;input.setDefaultBufferSize(w,h);});}
    void card(Surface s,int w,int h){post(()->{
        int generation=++cardGeneration;
        destroy(card);card=null;cardW=w;cardH=h;
        if(s!=null&&s.isValid())connectCard(s,generation,0);
    });}
    // VirtualDisplay.setSurface returns before SurfaceFlinger releases the old producer.
    // Retry only this transient handoff; keep the GL thread free to consume map frames.
    private void connectCard(Surface s,int generation,int attempt){
        if(closed || generation!=cardGeneration || !s.isValid())return;
        EGLSurface candidate=EGL14.eglCreateWindowSurface(egl,config,s,new int[]{EGL14.EGL_NONE},0);
        if(candidate==EGL14.EGL_NO_SURFACE){
            int error=EGL14.eglGetError();
            if(error==EGL14.EGL_BAD_ALLOC && attempt<40){
                handler.postDelayed(()->{if(!closed)try{connectCard(s,generation,attempt+1);}catch(Throwable e){failure.accept(e);}},50);
                return;
            }
            throw new IllegalStateException("Card EGL connection failed error=0x"+Integer.toHexString(error)+" attempts="+(attempt+1));
        }
        card=candidate;draw();
    }
    void cluster(Surface s,int w,int h,Runnable ready){post(()->{destroy(cluster);cluster=null;this.ready=ready;clusterW=w;clusterH=h;if(s!=null&&s.isValid())cluster=window(s);draw();});}
    private void post(Runnable r){if(!closed)handler.post(()->{if(!closed)try{r.run();}catch(Throwable e){failure.accept(e);}});}
    private void initialize(){
        egl=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] out=new int[2];
        require(EGL14.eglInitialize(egl,out,0,out,1),"eglInitialize");
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        require(EGL14.eglChooseConfig(egl,new int[]{EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT|EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE},0,configs,0,1,count,0)&&count[0]>0,"eglChooseConfig");
        config=configs[0];context=EGL14.eglCreateContext(egl,config,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        scratch=EGL14.eglCreatePbufferSurface(egl,config,new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);current(scratch);
        EGL14.eglSwapInterval(egl,0);
        int vs=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 pos; varying vec2 uv; void main(){gl_Position=vec4(pos,0.,1.);uv=vec2((pos.x+1.)*.5,(1.-pos.y)*.5);}");
        int fs=shader(GLES20.GL_FRAGMENT_SHADER,
            "#extension GL_OES_EGL_image_external : require\nprecision highp float; varying vec2 uv; uniform samplerExternalOES image; uniform sampler2D maskImage; uniform mat4 texMatrix; uniform vec2 outputSize; uniform vec4 mapRect; uniform vec4 maskRect; uniform float masked; void main(){vec2 p=uv*outputSize;vec2 q=(p-mapRect.xy)/mapRect.zw;if(any(lessThan(q,vec2(0.)))||any(greaterThan(q,vec2(1.)))){gl_FragColor=vec4(0.);return;} vec4 c=texture2D(image,(texMatrix*vec4(q.x,1.-q.y,0.,1.)).xy);float a=1.;if(masked>.5){vec2 m=(p-maskRect.xy)/maskRect.zw;if(any(lessThan(m,vec2(0.)))||any(greaterThan(m,vec2(1.))))a=0.;else a=texture2D(maskImage,m).r;}gl_FragColor=vec4(c.rgb*a,c.a*a);}");
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);
        int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);require(ok[0]!=0,GLES20.glGetProgramInfoLog(program));GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);
        position=GLES20.glGetAttribLocation(program,"pos");
        for(String name:new String[]{"image","maskImage","texMatrix","masked","outputSize","mapRect","maskRect"})uniforms.put(name,GLES20.glGetUniformLocation(program,name));
        int[] names=new int[2];GLES20.glGenTextures(2,names,0);texture=names[0];mask=names[1];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);parameters(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        input=new SurfaceTexture(texture);input.setDefaultBufferSize(width,height);source=new Surface(input);
        input.setOnFrameAvailableListener(s->post(()->{current(scratch);input.updateTexImage();input.getTransformMatrix(matrix);frame=true;draw();}),handler);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,mask);parameters(GLES20.GL_TEXTURE_2D);
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inScaled=false;
        Bitmap bitmap=BitmapFactory.decodeResource(app.getResources(),R.drawable.cluster_map_mask_11,opts);
        require(bitmap!=null,"Missing cluster mask");GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);bitmap.recycle();
    }
    private void draw(){
        if(!frame)return;
        if(card!=null)try{render(card,cardW,cardH,false);}catch(IllegalStateException e){destroy(card);card=null;android.util.Log.w("ClusterMap","Card surface lost",e);}
        if(cluster!=null)try{render(cluster,clusterW,clusterH,true);Runnable callback=ready;ready=null;if(callback!=null)callback.run();}catch(IllegalStateException e){destroy(cluster);cluster=null;ready=null;android.util.Log.w("ClusterMap","Cluster surface lost",e);}
        current(scratch);
    }
    private void render(EGLSurface target,int w,int h,boolean masked){
        if(w<=0||h<=0)return;current(target);GLES20.glViewport(0,0,w,h);GLES20.glClearColor(0,0,0,0);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);GLES20.glUseProgram(program);
        int a=position;vertices.position(0);GLES20.glEnableVertexAttribArray(a);GLES20.glVertexAttribPointer(a,2,GLES20.GL_FLOAT,false,0,vertices);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);GLES20.glUniform1i(uniform("image"),0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,mask);GLES20.glUniform1i(uniform("maskImage"),1);
        GLES20.glUniformMatrix4fv(uniform("texMatrix"),1,false,matrix,0);GLES20.glUniform1f(uniform("masked"),masked?1:0);
        // OEM output uses a 1920x720 coordinate space; card output fills its own surface.
        GLES20.glUniform2f(uniform("outputSize"),masked?1920:w,masked?720:h);
        if(masked){
            long now=android.os.SystemClock.elapsedRealtime();
            if(calibration==null||now-calibrationAt>1000){calibration=ClusterMapCalibration.Companion.current();calibrationAt=now;}
            ClusterMapCalibration t=calibration;
            GLES20.glUniform1f(uniform("masked"),t.getMaskEnabled()?1:0);
            float mw=width*t.getMapScale(),mh=height*t.getMapScale();
            GLES20.glUniform4f(uniform("mapRect"),t.mapLeft(width),t.mapTop(height),mw,mh);
            float sw=t.getPanelWidth()*t.getMaskScale(),sh=t.getPanelHeight()*t.getMaskScale();
            GLES20.glUniform4f(uniform("maskRect"),t.getMaskLeft(),t.getMaskTop(),sw,sh);
        }else{GLES20.glUniform4f(uniform("mapRect"),0,0,w,h);GLES20.glUniform4f(uniform("maskRect"),0,0,w,h);}
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);require(EGL14.eglSwapBuffers(egl,target),"eglSwapBuffers");
    }
    private int uniform(String n){return uniforms.get(n);}
    private static int shader(int type,String code){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,code);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);require(ok[0]!=0,GLES20.glGetShaderInfoLog(s));return s;}
    private static void parameters(int target){GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);}
    private EGLSurface window(Surface s){EGLSurface result=EGL14.eglCreateWindowSurface(egl,config,s,new int[]{EGL14.EGL_NONE},0);require(result!=EGL14.EGL_NO_SURFACE,"eglCreateWindowSurface");return result;}
    private void current(EGLSurface s){require(EGL14.eglMakeCurrent(egl,s,s,context),"eglMakeCurrent");}
    private void destroy(EGLSurface s){if(s!=null&&s!=EGL14.EGL_NO_SURFACE){current(scratch);EGL14.eglDestroySurface(egl,s);}}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    void close(){if(closed)return;closed=true;CountDownLatch done=new CountDownLatch(1);handler.post(()->{try{
        if(input!=null)input.setOnFrameAvailableListener(null);
        destroy(card);destroy(cluster);if(source!=null)source.release();if(input!=null)input.release();
        if(egl!=null){EGL14.eglMakeCurrent(egl,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(scratch!=null)EGL14.eglDestroySurface(egl,scratch);if(context!=null)EGL14.eglDestroyContext(egl,context);EGL14.eglTerminate(egl);}
    }finally{done.countDown();thread.quitSafely();}});try{done.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
