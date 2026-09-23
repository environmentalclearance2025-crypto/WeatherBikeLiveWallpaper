package com.weatherbike.livewallpaper;

import android.content.SharedPreferences;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherWallpaperService extends WallpaperService {
  @Override public Engine onCreateEngine(){return new BikeEngine();}
  private final class BikeEngine extends Engine {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final SharedPreferences prefs=getSharedPreferences(MainActivity.PREFS,MODE_PRIVATE);
    private Bitmap bitmap;
    private volatile int kind=0;
    private long checked=0;
    private boolean visible=false;
    private int w=1,h=1;
    private float phase=0, offset=0.5f;
    private final Runnable tick=this::draw;
    @Override public void onCreate(SurfaceHolder holder){
      super.onCreate(holder);
      bitmap=BitmapFactory.decodeResource(getResources(),R.drawable.bike_wallpaper);
      setOffsetNotificationsEnabled(true);
    }
    @Override public void onVisibilityChanged(boolean v){
      visible=v; handler.removeCallbacks(tick);
      if(v){checked=0;refresh();draw();}
    }
    @Override public void onSurfaceChanged(SurfaceHolder holder,int f,int width,int height){
      super.onSurfaceChanged(holder,f,width,height);w=width;h=height;draw();
    }
    @Override public void onSurfaceDestroyed(SurfaceHolder holder){
      visible=false;handler.removeCallbacks(tick);super.onSurfaceDestroyed(holder);
    }
    @Override public void onOffsetsChanged(float x,float y,float xs,float ys,int xp,int yp){
      offset=x;if(visible)draw();
    }
    @Override public void onDestroy(){
      visible=false;handler.removeCallbacks(tick);io.shutdownNow();
      if(bitmap!=null)bitmap.recycle();super.onDestroy();
    }
    private void refresh(){
      long now=System.currentTimeMillis();
      if(now-checked<1800000L || !prefs.getBoolean(MainActivity.HAS,false))return;
      String lat=prefs.getString(MainActivity.LAT,null),lon=prefs.getString(MainActivity.LON,null);
      if(lat==null||lon==null)return;checked=now;
      io.execute(()->{
        HttpURLConnection c=null;
        try{
          URL u=new URL("https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+"&current=weather_code,precipitation&timezone=auto");
          c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);
          try(InputStream in=c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[2048];int n;
            while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);
            JSONObject current=new JSONObject(bytes.toString("UTF-8")).getJSONObject("current");
            int code=current.optInt("weather_code",-1);
            // 0 clear, 1 clouds, 2 fog, 3 rain, 4 snow, 5 storm.
            if(code==0||code==1)kind=0;
            else if(code==2||code==3)kind=1;
            else if(code==45||code==48)kind=2;
            else if(code>=95)kind=5;
            else if((code>=71&&code<=77)||code==85||code==86)kind=4;
            else if((code>=51&&code<=67)||(code>=80&&code<=82))kind=3;
            else kind=0;
            handler.post(this::draw);
          }
        }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}
      });
    }
    private void draw(){
      if(!visible||w<=1||h<=1||bitmap==null)return;
      refresh();Canvas c=null;
      try{
        c=getSurfaceHolder().lockCanvas();if(c==null)return;
        float scale=Math.max((float)w/bitmap.getWidth(),(float)h/bitmap.getHeight());
        float bw=scale*bitmap.getWidth(),bh=scale*bitmap.getHeight();
        float dx=(w-bw)/2f-(offset-0.5f)*Math.min(0.08f*w,Math.max(0,bw-w));
        p.setColor(Color.WHITE);p.setAlpha(255);p.setShader(null);p.setStyle(Paint.Style.FILL);
        c.drawBitmap(bitmap,null,new RectF(dx,(h-bh)/2f,dx+bw,(h-bh)/2f+bh),p);
        light(c);weather(c);
      }catch(Exception ignored){}finally{if(c!=null)getSurfaceHolder().unlockCanvasAndPost(c);}
      phase++;handler.removeCallbacks(tick);
      handler.postDelayed(tick,(kind==2||kind>=3)?45L:1000L);
    }
    private void light(Canvas c){
      float hr=LocalDateTime.now().getHour()+LocalDateTime.now().getMinute()/60f;
      if(hr<5.5||hr>=20.5)c.drawColor(Color.argb(130,6,15,36));
      else if(hr<7.5)c.drawColor(Color.argb(65,226,133,69));
      else if(hr>=16.5&&hr<19)c.drawColor(Color.argb(55,255,125,45));
      else if(hr>=19)c.drawColor(Color.argb(105,27,29,65));
      else c.drawColor(Color.argb(8,255,245,225));
      if((hr>=5.5&&hr<7.5)||(hr>=16.5&&hr<19)){
        p.setShader(new RadialGradient(w*.80f,h*.15f,h*.5f,
          new int[]{Color.argb(80,255,170,75),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);p.setShader(null);
      }
    }
    private void weather(Canvas c){
      if(kind==0)return;
      if(kind==1){c.drawColor(Color.argb(35,75,90,102));return;}
      if(kind==2){
        c.drawColor(Color.argb(38,190,197,200));
        p.setColor(Color.argb(23,235,238,240));
        for(int i=0;i<5;i++){float x=((phase*(0.5f+i*.1f))%(w*2))-w;
          c.drawOval(x,h*(.12f+i*.16f),x+w*1.3f,h*(.24f+i*.16f),p);}
        return;
      }
      if(kind==4){p.setColor(Color.argb(170,242,247,251));Random r=new Random(778);
        for(int i=0;i<95;i++){float x=r.nextFloat()*w,y=(r.nextFloat()*h+phase*(2+i%5))%h;
          c.drawCircle(x+(float)Math.sin(phase*.05+i)*7,y,1+i%3,p);}return;}
      boolean storm=kind==5;
      c.drawColor(Color.argb(storm?63:31,22,38,57));
      Random r=new Random(44551);
      p.setColor(Color.argb(storm?138:104,190,215,238));
      p.setStrokeWidth(Math.max(1.4f,w/450f));
      int count=storm?170:105;
      for(int i=0;i<count;i++){
        float x=r.nextFloat()*w,y=(r.nextFloat()*h+phase*(storm?27:19)+i*7)%(h+45)-45;
        c.drawLine(x,y,x-12,y+32,p);
      }
      p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);
      for(int i=0;i<8;i++){
        float x=(i*127f+43)%w,y=h*(.60f+(i*.07f)% .32f);
        float rad=3+(phase*1.4f+i*13)%27;
        p.setColor(Color.argb(Math.max(0,90-(int)rad*3),210,230,240));
        c.drawOval(x-rad*1.8f,y-rad*.55f,x+rad*1.8f,y+rad*.55f,p);
      }
      p.setStyle(Paint.Style.FILL);
      if(storm && ((int)phase)%420==3)c.drawColor(Color.argb(85,236,242,255));
    }
  }
}
