package cn.g.anime;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

public final class Ui {
 public static final int BG=0xff10121c,CARD=0xff1c2030,TEXT=0xfff3f1fc,MUTED=0xffa4aec4,ACCENT=0xffbca6ff,MINT=0xff8fe3cd;
 private static final ExecutorService IMAGES=Executors.newFixedThreadPool(3);
 private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(8*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
 public static int dp(Context c,float n){return (int)(c.getResources().getDisplayMetrics().density*n+0.5f);}
 public static GradientDrawable bg(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
 public static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
 public static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 public static TextView text(Context c,String text,int sp,int color){TextView t=new TextView(c);t.setText(text);t.setTextSize(sp);t.setTextColor(color);t.setLineSpacing(dp(c,3),1);return t;}
 public static void pad(View v,int n){int p=dp(v.getContext(),n);v.setPadding(p,p,p,p);}
 public static void space(LinearLayout l,int h){View v=new View(l.getContext());l.addView(v,new LinearLayout.LayoutParams(1,dp(l.getContext(),h)));}
 public static Button button(Context c,String title,boolean primary){Button b=new Button(c);b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?BG:TEXT);b.setBackground(bg(c,primary?ACCENT:CARD,14));b.setMinHeight(dp(c,46));b.setMinimumHeight(dp(c,46));return b;}
 public static void weighted(LinearLayout l,View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(l.getContext(),48),1);p.setMargins(dp(l.getContext(),3),dp(l.getContext(),3),dp(l.getContext(),3),dp(l.getContext(),3));l.addView(v,p);}
 public static void inset(Activity a,View root){root.setOnApplyWindowInsetsListener((v,in)->{v.setPadding(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());return in.consumeSystemWindowInsets();});root.requestApplyInsets();}
 public static ImageView poster(Activity a,String url){
  ImageView v=new ImageView(a);v.setScaleType(ImageView.ScaleType.CENTER_CROP);v.setBackground(bg(a,0xff303549,10));v.setClipToOutline(true);v.setTag(url);
  if(!Playback.isHttps(url))return v;Bitmap hit=CACHE.get(url);if(hit!=null){v.setImageBitmap(hit);return v;}
  IMAGES.execute(()->{try{byte[] data=Api.get(url,3*1024*1024);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0)return;BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;while(bounds.outWidth/opts.inSampleSize>480||bounds.outHeight/opts.inSampleSize>720)opts.inSampleSize*=2;Bitmap b=BitmapFactory.decodeByteArray(data,0,data.length,opts);if(b==null)return;CACHE.put(url,b);a.runOnUiThread(()->{if(!a.isDestroyed()&&url.equals(v.getTag()))v.setImageBitmap(b);});}catch(Exception ignored){}});return v;
 }
 public static void open(Activity a,String url){if(!Playback.isHttps(url))return;try{a.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(ActivityNotFoundException e){Toast.makeText(a,"没有可用的浏览器",Toast.LENGTH_SHORT).show();}}
}
