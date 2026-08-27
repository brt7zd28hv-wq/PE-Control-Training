package com.example.pecontroltraining;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    TrainingView view;
    Handler handler = new Handler(Looper.getMainLooper());
    MediaPlayer tone;
    Runnable ticker;
    boolean running = false;
    long sessionSeconds = 0;
    int bpm = 80;
    int level = 1;
    int maxLevel = 10;
    boolean highIntensity = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        view = new TrainingView(this);
        setContentView(view);
        loadPrefs();
        ticker = () -> {
            if (running) {
                sessionSeconds++;
                view.invalidate();
                handler.postDelayed(ticker, 1000);
            }
        };
    }

    void loadPrefs() {
        android.content.SharedPreferences p = getSharedPreferences("prefs", 0);
        bpm = p.getInt("bpm", 80);
        level = p.getInt("level", 1);
        highIntensity = p.getBoolean("high", false);
    }

    void savePrefs() {
        getSharedPreferences("prefs",0).edit()
            .putInt("bpm",bpm).putInt("level",level)
            .putBoolean("high",highIntensity).apply();
    }

    void startStop() {
        running = !running;
        if (running) {
            handler.post(ticker);
            scheduleBeat();
        } else {
            handler.removeCallbacksAndMessages(null);
        }
        view.invalidate();
    }

    void scheduleBeat() {
        if (!running) return;
        try {
            if (tone != null) tone.release();
            tone = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
            if (tone != null) {
                tone.setVolume(0.15f,0.15f);
                tone.setOnCompletionListener(mp -> mp.release());
                tone.start();
            }
        } catch(Exception ignored) {}
        long interval = Math.max(150, 60000L / bpm);
        handler.postDelayed(this::scheduleBeat, interval);
    }

    void adjustBpm(int delta) {
        bpm = Math.max(30, Math.min(220, bpm + delta));
        savePrefs(); view.invalidate();
    }

    void adjustLevel(int delta) {
        level = Math.max(1, Math.min(maxLevel, level + delta));
        bpm = Math.min(220, 60 + level * 10 + (highIntensity ? 20 : 0));
        savePrefs(); view.invalidate();
    }

    String timeText() {
        return String.format(Locale.UK, "%02d:%02d", sessionSeconds/60, sessionSeconds%60);
    }

    class TrainingView extends View {
        Paint p = new Paint(3);
        int selected = 0;
        String[] menu = {"START / PAUSE", "LEVEL -", "LEVEL +", "BPM -", "BPM +", "HIGH INTENSITY", "VIDEO SOURCES", "CATEGORIES"};
        float pulse = 0;

        TrainingView(Context c) { super(c); setFocusable(true); setFocusableInTouchMode(true); requestFocus(); }

        void txt(Canvas c, String s, float x, float y, float size, boolean bold) {
            p.setColor(Color.WHITE); p.setTextSize(size); p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            c.drawText(s,x,y,p);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w=getWidth(), h=getHeight();
            c.drawColor(Color.rgb(8,9,13));

            p.setColor(Color.rgb(18,20,29)); c.drawRoundRect(30,25,w-30,h-25,28,28,p);
            txt(c,"PE CONTROL",60,75,30,true);
            txt(c,"Stamina & control training",60,105,18,false);

            // Metronome
            float cx=w*0.33f, cy=h*0.48f;
            p.setColor(Color.rgb(124,92,255)); c.drawCircle(cx,cy,135, p);
            p.setColor(Color.rgb(8,9,13)); c.drawCircle(cx,cy,112,p);
            txt(c,String.valueOf(bpm),(float)(cx-55),cy+20,58,true);
            txt(c,"BPM",(float)(cx-25),cy+55,18,false);
            txt(c,running ? "RUNNING" : "READY",(float)(cx-45),cy+92,15,true);

            txt(c,"LEVEL "+level,60,h-62,22,true);
            txt(c,"SESSION "+timeText(),210,h-62,22,false);

            // Controls
            float x=w*0.57f, y=130;
            for(int i=0;i<menu.length;i++) {
                boolean focus=(i==selected);
                p.setColor(focus ? Color.rgb(124,92,255) : Color.rgb(34,36,47));
                c.drawRoundRect(x,y+i*56,x+330,y+44+i*56,12,12,p);
                txt(c,menu[i],x+18,y+30+i*56,17,true);
            }
            txt(c,"◀ ▶  Navigate    OK  Select",x, h-45,16,false);
        }

        void select() {
            switch(selected) {
                case 0: startStop(); break;
                case 1: adjustLevel(-1); break;
                case 2: adjustLevel(1); break;
                case 3: adjustBpm(-5); break;
                case 4: adjustBpm(5); break;
                case 5: highIntensity=!highIntensity; savePrefs(); view.invalidate(); break;
                case 6: showSources(); break;
                case 7: showCategories(); break;
            }
        }

        void showSources() {
            final EditText input=new EditText(MainActivity.this);
            input.setHint("https://permitted-source.example");
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Video Sources")
                .setMessage("Add a permitted/public-domain or appropriately licensed source. The app should play content from the source rather than bypass protected streams.")
                .setView(input)
                .setPositiveButton("Save", (d,w)-> getSharedPreferences("prefs",0).edit().putString("source",input.getText().toString()).apply())
                .setNegativeButton("Cancel",null).show();
        }

        void showCategories() {
            final EditText input=new EditText(MainActivity.this);
            input.setHint("e.g. breathing, pelvic floor, stamina");
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Search Categories")
                .setMessage("Enter a category or search phrase for relevant permitted educational material.")
                .setView(input)
                .setPositiveButton("Save", (d,w)-> getSharedPreferences("prefs",0).edit().putString("category",input.getText().toString()).apply())
                .setNegativeButton("Cancel",null).show();
        }

        @Override public boolean onKeyDown(int key, KeyEvent e) {
            if (key==KeyEvent.KEYCODE_DPAD_DOWN) { selected=(selected+1)%menu.length; invalidate(); return true; }
            if (key==KeyEvent.KEYCODE_DPAD_UP) { selected=(selected-1+menu.length)%menu.length; invalidate(); return true; }
            if (key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER) { select(); return true; }
            if (key==KeyEvent.KEYCODE_DPAD_LEFT) { if(selected==2) adjustLevel(-1); else if(selected==4) adjustBpm(-5); return true; }
            if (key==KeyEvent.KEYCODE_DPAD_RIGHT) { if(selected==1) adjustLevel(1); else if(selected==3) adjustBpm(5); return true; }
            if (key==KeyEvent.KEYCODE_BACK && running) { startStop(); return true; }
            return super.onKeyDown(key,e);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            if(e.getAction()!=MotionEvent.ACTION_UP) return true;
            float x=e.getX(), y=e.getY();
            if(x>getWidth()*0.57f && y>120 && y<120+menu.length*56) {
                selected=Math.max(0,Math.min(menu.length-1,(int)((y-120)/56)));
                select();
            }
            return true;
        }
    }

    @Override protected void onDestroy() {
        running=false; handler.removeCallbacksAndMessages(null);
        if(tone!=null) try { tone.release(); } catch(Exception ignored) {}
        super.onDestroy();
    }
}
