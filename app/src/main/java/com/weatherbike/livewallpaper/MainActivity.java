package com.weatherbike.livewallpaper;

import android.Manifest;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    static final String PREFS = "weather_bike_prefs";
    static final String LAT = "lat", LON = "lon", HAS = "has";
    private static final int LOCATION_REQ = 102;
    private TextView status;
    private SharedPreferences prefs;
    private int dp(int n){ return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences(PREFS, MODE_PRIVATE);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24),dp(20),dp(24),dp(20)); box.setBackgroundColor(0xff17191d);
        TextView title=new TextView(this);
        title.setText("WEATHER BIKE\nLIVE WALLPAPER"); title.setTextColor(Color.WHITE);
        title.setTextSize(29); title.setGravity(Gravity.CENTER); box.addView(title);
        TextView subtitle=new TextView(this);
        subtitle.setText("\nYour motorcycle responds to phone time and local weather.\n");
        subtitle.setTextColor(0xffdddddd); subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextSize(16); box.addView(subtitle);
        status=new TextView(this); status.setTextSize(15); status.setTextColor(0xffb4b4b4);
        status.setGravity(Gravity.CENTER); box.addView(status); updateStatus();
        addButton(box,"Use current location for live weather",()->useLocation());
        addButton(box,"Preview / Apply Live Wallpaper",()->preview());
        setContentView(box);
    }
    private void addButton(LinearLayout box,String caption,Runnable click){
        Button b=new Button(this); b.setText(caption); b.setAllCaps(false);
        b.setOnClickListener(v->click.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(8),0,dp(8)); box.addView(b,p);
    }
    private void updateStatus(){
        status.setText(prefs.getBoolean(HAS,false)?"Weather location saved. Updates every 30 minutes.":"\nLocation not set. Time-based lighting works immediately.\n");
    }
    private void useLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQ); return;
        }
        LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(lm==null){status.setText("Location service unavailable.");return;}
        status.setText("Finding your location...");
        try{
            Location best=null;
            for(String provider:lm.getProviders(true)){
                Location loc=lm.getLastKnownLocation(provider);
                if(loc!=null && (best==null || loc.getTime()>best.getTime()))best=loc;
            }
            if(best!=null && System.currentTimeMillis()-best.getTime()<6*60*60*1000L){save(best);return;}
            String provider=lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)?LocationManager.NETWORK_PROVIDER:LocationManager.GPS_PROVIDER;
            lm.requestSingleUpdate(provider,new LocationListener(){
                @Override public void onLocationChanged(Location loc){save(loc);}
                @Override public void onProviderEnabled(String p){}
                @Override public void onProviderDisabled(String p){}
                @Override public void onStatusChanged(String p,int s,Bundle b){}
            },Looper.getMainLooper());
            status.postDelayed(()->{if(!prefs.getBoolean(HAS,false)) status.setText("Still waiting for location. Turn on Location and try again.");},15000);
        }catch(Exception e){status.setText("Enable your phone's Location setting and try again.");}
    }
    private void save(Location loc){
        prefs.edit().putString(LAT,Double.toString(loc.getLatitude())).putString(LON,Double.toString(loc.getLongitude())).putBoolean(HAS,true).apply();
        updateStatus();Toast.makeText(this,"Weather location saved",Toast.LENGTH_SHORT).show();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==LOCATION_REQ && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED)useLocation();
    }
    private void preview(){
        try{
            Intent i=new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new ComponentName(this,WeatherWallpaperService.class));
            startActivity(i);
        }catch(Exception e){startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));}
    }
}
