package cn.g.anime;
import android.app.*;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class PlayerActivity extends Activity {
 private final ExecutorService network=Executors.newSingleThreadExecutor();private final Handler handler=new Handler();
 private Store store;private Source source;private JSONObject item;private List<Playback.Route> routes;private int routeIndex=0,episodeIndex=0;private VideoView video;private TextView title,status;private LinearLayout root,info;private FrameLayout frame;private Button previous,next;private String key="";private boolean ready=false,active=false,wantsPlay=true,failed=false;private int pendingPosition=-1;private MediaPlayer media;private AudioManager audio;private AudioFocusRequest focus;
 private final Runnable ticker=new Runnable(){public void run(){if(active){save();handler.postDelayed(this,4000);}}};
 private final Runnable timeout=()->{if(!ready&&!isFinishing())error("视频连接超时，可以重试或返回详情切换来源。");};
 @Override public void onCreate(Bundle state){super.onCreate(state);store=new Store(this);audio=(AudioManager)getSystemService(AUDIO_SERVICE);try{source=Source.find(getIntent().getStringExtra("source"));}catch(Exception e){finish();return;}if(state!=null){pendingPosition=state.getInt("position",0);wantsPlay=state.getBoolean("wantsPlay",true);if(state.containsKey("route"))getIntent().putExtra("route",state.getString("route"));if(state.containsKey("episode"))getIntent().putExtra("episode",state.getString("episode"));}build();fetch();}
 private void build(){
  root=Ui.column(this);root.setBackgroundColor(Ui.BG);setContentView(root);Ui.inset(this,root);LinearLayout bar=Ui.row(this);Ui.pad(bar,8);Button back=Ui.button(this,"‹",false);bar.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,50),Ui.dp(this,46)));back.setOnClickListener(v->finish());title=Ui.text(this,"正在获取选集…",16,Ui.TEXT);title.setPadding(Ui.dp(this,12),0,0,0);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(bar);
  frame=new FrameLayout(this);frame.setBackgroundColor(0xff000000);root.addView(frame,new LinearLayout.LayoutParams(-1,Ui.dp(this,230)));video=new VideoView(this);FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);frame.addView(video,vp);MediaController control=new MediaController(this);control.setAnchorView(frame);video.setMediaController(control);
  ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));info=Ui.column(this);Ui.pad(info,16);scroll.addView(info);status=Ui.text(this,"正在连接 "+source.name+"…",14,Ui.MUTED);info.addView(status);Ui.space(info,12);
  LinearLayout actions=Ui.row(this);previous=Ui.button(this,"上一集",false);Ui.weighted(actions,previous);Button full=Ui.button(this,"横屏 / 竖屏",false);Ui.weighted(actions,full);next=Ui.button(this,"下一集",true);Ui.weighted(actions,next);info.addView(actions);previous.setOnClickListener(v->change(-1));next.setOnClickListener(v->change(1));full.setOnClickListener(v->setRequestedOrientation(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
  Button choose=Ui.button(this,"选择集数 / 线路",false);info.addView(choose);choose.setOnClickListener(v->choose());Button retry=Ui.button(this,"重新连接视频",false);info.addView(retry);retry.setOnClickListener(v->{if(item==null)fetch();else{save();pendingPosition=store.position(key);wantsPlay=true;play();}});Button switchSource=Ui.button(this,"换个播放源",false);info.addView(switchSource);switchSource.setOnClickListener(v->{Intent i=new Intent(this,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);i.putExtra("search",item==null?getIntent().getStringExtra("title"):item.optString("vod_name"));startActivity(i);finish();});Ui.space(info,16);TextView attribution=Ui.text(this,"资源来源："+source.name+"\n"+source.review+"\n\n抽查不能保证每部、每集都没有广告。",12,Ui.MUTED);info.addView(attribution);Button origin=Ui.button(this,"查看来源网站 ↗",false);info.addView(origin);origin.setOnClickListener(v->Ui.open(this,source.website));layout();
 }
 private void fetch(){
  status.setText("正在从 "+source.name+" 获取最新播放地址…");previous.setEnabled(false);next.setEnabled(false);
  network.execute(()->{try{JSONObject data=Api.detail(source,getIntent().getStringExtra("id"));List<Playback.Route> parsed=Playback.parse(data.optString("vod_play_from"),data.optString("vod_play_url"));if(parsed.isEmpty())throw new Exception("没有可直接播放的视频地址");runOnUiThread(()->{if(isDestroyed())return;item=data;routes=parsed;String route=getIntent().getStringExtra("route"),episode=getIntent().getStringExtra("episode");routeIndex=0;for(int i=0;i<routes.size();i++)if(routes.get(i).name.equals(route))routeIndex=i;episodeIndex=0;List<Playback.Episode> eps=routes.get(routeIndex).episodes;for(int i=0;i<eps.size();i++)if(eps.get(i).title.equals(episode))episodeIndex=i;if(active)play();});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed())error("获取播放地址失败："+(e.getMessage()==null?"来源暂不可用":e.getMessage()));});}});
 }
 private void play(){
  if(!active||item==null)return;handler.removeCallbacks(timeout);ready=false;failed=false;media=null;video.stopPlayback();Playback.Route route=routes.get(routeIndex);Playback.Episode ep=route.episodes.get(episodeIndex);key=Playback.progressKey(source.id,item.optString("vod_id"),route.name,ep.title);int saved=pendingPosition>=0?pendingPosition:store.position(key);pendingPosition=-1;
  title.setText(item.optString("vod_name")+" · "+ep.title);status.setText("正在连接视频…\n来源："+source.name);previous.setEnabled(episodeIndex>0);next.setEnabled(episodeIndex+1<route.episodes.size());
  try{item.put("_episode",ep.title);item.put("_route",route.name);}catch(Exception ignored){}store.put("history",item);getIntent().putExtra("route",route.name);getIntent().putExtra("episode",ep.title);
  video.setOnPreparedListener(mp->{handler.removeCallbacks(timeout);media=mp;ready=true;int pos=saved;int duration=video.getDuration();if(duration>0&&pos>duration-3000)pos=0;if(pos>0)video.seekTo(pos);if(active&&wantsPlay&&requestFocus())video.start();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);status.setText("来源："+source.name+" · "+route.name+"\n点击画面显示播放控制，退出后自动保存进度。");});
  video.setOnInfoListener((mp,what,extra)->{if(what==MediaPlayer.MEDIA_INFO_BUFFERING_START)status.setText("视频缓冲中… · "+source.name);else if(what==MediaPlayer.MEDIA_INFO_BUFFERING_END)status.setText("来源："+source.name+" · "+route.name);return false;});
  video.setOnErrorListener((mp,what,extra)->{error("播放失败（"+what+" / "+extra+"）\n可能是来源限制或当前设备不支持该编码。可重试，或返回详情换源。");return true;});
  video.setOnCompletionListener(mp->{store.position(key,0);if(episodeIndex+1<route.episodes.size()){episodeIndex++;wantsPlay=true;play();}else{ready=false;wantsPlay=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);status.setText("本线路已播放完毕 · 来源："+source.name);}});
  video.setVideoURI(Uri.parse(ep.url));handler.postDelayed(timeout,30000);
 }
 private boolean requestFocus(){
  if(focus==null)focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()).setOnAudioFocusChangeListener(change->{if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){if(ready){wantsPlay=video.isPlaying();video.pause();}}else if(change==AudioManager.AUDIOFOCUS_GAIN&&active&&ready&&wantsPlay)video.start();}).build();return audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
 }
 private void change(int delta){if(routes==null||item==null)return;int target=episodeIndex+delta;if(target<0||target>=routes.get(routeIndex).episodes.size())return;save();episodeIndex=target;pendingPosition=-1;wantsPlay=true;play();}
 private void choose(){
  if(routes==null)return;
  if(routes.size()==1){chooseEpisodes(0);return;}
  String[] labels=new String[routes.size()];for(int i=0;i<labels.length;i++)labels[i]="线路 "+(i+1)+" · "+routes.get(i).episodes.size()+" 集";
  new AlertDialog.Builder(this).setTitle("播放线路").setItems(labels,(d,n)->chooseEpisodes(n)).show();
 }
 private void chooseEpisodes(int n){
  Playback.Route r=routes.get(n);String[] eps=new String[r.episodes.size()];for(int i=0;i<eps.length;i++)eps[i]=r.episodes.get(i).title;
  new AlertDialog.Builder(this).setTitle("选集").setItems(eps,(dialog,index)->{save();routeIndex=n;episodeIndex=index;pendingPosition=-1;wantsPlay=true;play();}).show();
 }
 private void error(String message){handler.removeCallbacks(timeout);failed=true;ready=false;video.stopPlayback();media=null;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);status.setText(message+"\n资源来源："+source.name);}
 private void save(){if(ready&&!failed&&!key.isEmpty())store.position(key,video.getCurrentPosition());}
 private void layout(){boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)frame.getLayoutParams();p.height=landscape?0:Ui.dp(this,230);p.weight=landscape?1:0;frame.setLayoutParams(p);((View)info.getParent()).setVisibility(landscape?View.GONE:View.VISIBLE);getWindow().getDecorView().setSystemUiVisibility(landscape?View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION:View.SYSTEM_UI_FLAG_VISIBLE);}
 @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);layout();}
 @Override protected void onResume(){super.onResume();active=true;handler.post(ticker);if(item!=null&&!ready)play();}
 @Override protected void onPause(){active=false;save();if(ready){wantsPlay=video.isPlaying();pendingPosition=video.getCurrentPosition();}video.stopPlayback();ready=false;media=null;handler.removeCallbacks(ticker);handler.removeCallbacks(timeout);if(focus!=null)audio.abandonAudioFocusRequest(focus);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onPause();}
 @Override protected void onSaveInstanceState(Bundle b){b.putInt("position",ready?video.getCurrentPosition():Math.max(0,pendingPosition));b.putBoolean("wantsPlay",wantsPlay);b.putString("route",getIntent().getStringExtra("route"));b.putString("episode",getIntent().getStringExtra("episode"));super.onSaveInstanceState(b);}
 @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);video.stopPlayback();network.shutdownNow();super.onDestroy();}
 @Override public void onBackPressed(){if(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE){setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);return;}super.onBackPressed();}
}
