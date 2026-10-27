package cn.g.anime;
import android.app.*;
import android.content.*;
import android.os.*;
import android.text.Html;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
 private final ExecutorService network=Executors.newFixedThreadPool(2);
 private Store store;private Source source;private int category=0,tab=0,page=1,pages=1,generation=0;private String query="";private boolean detail=false,loading=false;
 private LinearLayout root,body,content,controls;private ScrollView scroll;private EditText search;private TextView status;private Button more;private final List<JSONObject> items=new ArrayList<>();
 @Override public void onCreate(Bundle state){super.onCreate(state);store=new Store(this);try{source=Source.find(store.setting("source","feifan"));}catch(Exception e){source=Source.ALL[0];}if(state!=null){tab=state.getInt("tab",0);category=state.getInt("category",0);query=state.getString("query","");}home(true);}
 @Override protected void onSaveInstanceState(Bundle b){b.putInt("tab",tab);b.putInt("category",category);b.putString("query",query);super.onSaveInstanceState(b);}
 @Override protected void onDestroy(){generation++;network.shutdownNow();super.onDestroy();}
 @Override protected void onResume(){super.onResume();if(store!=null&&!detail&&(tab==1||tab==2))shelf();}
 private void shell(){root=Ui.column(this);root.setBackgroundColor(Ui.BG);setContentView(root);Ui.inset(this,root);}
 private void home(boolean reload){
  generation++;loading=false;detail=false;shell();
  LinearLayout header=Ui.column(this);Ui.pad(header,18);TextView brand=Ui.text(this,"G 动漫  ✦",27,Ui.ACCENT);brand.setTypeface(null,1);header.addView(brand);header.addView(Ui.text(this,"今天，也看一集。",13,Ui.MUTED));root.addView(header);
  controls=Ui.column(this);Ui.pad(controls,12);root.addView(controls);
  scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));content=Ui.column(this);Ui.pad(content,14);scroll.addView(content);
  LinearLayout nav=Ui.row(this);Ui.pad(nav,8);String[] labels={"发现","追番","足迹","来源"};for(int i=0;i<labels.length;i++){final int n=i;Button b=Ui.button(this,labels[i],i==tab);Ui.weighted(nav,b);b.setOnClickListener(v->{tab=n;home(false);});}root.addView(nav);
  if(tab==0){discoverControls();if(reload||items.isEmpty())load(true);else render();}else{controls.setVisibility(View.GONE);if(tab==3)sources();else shelf();}
 }
 private void discoverControls(){
  LinearLayout line=Ui.row(this);search=new EditText(this);search.setSingleLine(true);search.setTextColor(Ui.TEXT);search.setHintTextColor(Ui.MUTED);search.setHint("搜索动漫名字");search.setText(query);search.setTextSize(15);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setBackground(Ui.bg(this,Ui.CARD,14));Ui.pad(search,12);line.addView(search,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));Button go=Ui.button(this,"搜索",true);line.addView(go,new LinearLayout.LayoutParams(Ui.dp(this,70),Ui.dp(this,48)));controls.addView(line);
  go.setOnClickListener(v->doSearch());search.setOnEditorActionListener((t,id,event)->{if(id==EditorInfo.IME_ACTION_SEARCH){doSearch();return true;}return false;});Ui.space(controls,9);
  LinearLayout filters=Ui.row(this);Button provider=Ui.button(this,source.name+" ▾",false);Ui.weighted(filters,provider);provider.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("选择资源来源").setItems(Arrays.stream(Source.ALL).map(s->s.name).toArray(String[]::new),(d,n)->{source=Source.ALL[n];store.setSetting("source",source.id);home(true);}).show());Button kind=Ui.button(this,query.isEmpty()?Source.CATEGORIES[category]+" ▾":"搜索全部动漫 ▾",false);Ui.weighted(filters,kind);kind.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("浏览分类").setItems(Source.CATEGORIES,(d,n)->{category=n;query="";home(true);}).show());controls.addView(filters);
 }
 private void doSearch(){query=search.getText().toString().trim();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();load(true);}
 private void load(boolean reset){
  if(loading)return;if(reset){page=1;items.clear();}loading=true;final int token=++generation,requested=page;final Source s=source;final int cat=category;final String q=query;render();
  network.execute(()->{try{Api.Page result=Api.list(s,cat,q,requested);runOnUiThread(()->{if(token!=generation||isDestroyed())return;loading=false;pages=result.count;Set<String> seen=new HashSet<>();for(JSONObject o:items)seen.add(Store.key(o));for(JSONObject o:result.items)if(seen.add(Store.key(o)))items.add(o);render();});}catch(Exception e){runOnUiThread(()->{if(token!=generation||isDestroyed())return;loading=false;render();status.setText("加载失败："+friendly(e)+"\n可以重试或切换资源来源");more.setText("重试本页");more.setEnabled(true);more.setOnClickListener(v->load(false));});}});
 }
 private String friendly(Exception e){if(e instanceof java.net.SocketTimeoutException)return "来源连接超时";if(e instanceof java.net.UnknownHostException)return "暂时无法连接来源";if(e instanceof javax.net.ssl.SSLException)return "来源安全连接失败";return e.getMessage()==null?"来源暂时不可用":e.getMessage();}
 private void render(){
  content.removeAllViews();status=Ui.text(this,loading?"正在从 "+source.name+" 获取动漫…":(query.isEmpty()?"最近更新":"“"+query+"” 的动漫结果")+" · "+items.size()+" 部",14,Ui.MUTED);content.addView(status);Ui.space(content,12);
  for(JSONObject item:items)card(item);if(!loading&&items.isEmpty()){content.addView(Ui.text(this,"还没有找到动漫\n可以换个名字、加载下一页，或切换来源。",17,Ui.TEXT));Ui.space(content,20);}
  more=Ui.button(this,loading?"加载中…":page<pages?"加载下一页（"+page+" / "+pages+"）":"已显示全部结果",false);more.setEnabled(!loading&&page<pages);more.setOnClickListener(v->{page++;load(false);});content.addView(more,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));Ui.space(content,10);content.addView(Ui.text(this,"资源来自 "+source.name+" · "+android.net.Uri.parse(source.api).getHost(),11,Ui.MUTED));
 }
 private void card(JSONObject item){
  Source s;try{s=Source.find(item.optString("_source"));}catch(Exception e){return;}LinearLayout card=Ui.row(this);Ui.pad(card,12);card.setBackground(Ui.bg(this,Ui.CARD,18));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,Ui.dp(this,12));content.addView(card,cp);
  ImageView poster=Ui.poster(this,item.optString("vod_pic"));card.addView(poster,new LinearLayout.LayoutParams(Ui.dp(this,86),Ui.dp(this,120)));LinearLayout text=Ui.column(this);text.setPadding(Ui.dp(this,14),0,0,0);card.addView(text,new LinearLayout.LayoutParams(0,-2,1));TextView title=Ui.text(this,item.optString("vod_name"),17,Ui.TEXT);title.setTypeface(null,1);title.setMaxLines(2);text.addView(title);Ui.space(text,7);text.addView(Ui.text(this,item.optString("type_name")+" · "+item.optString("vod_year"),12,Ui.MUTED));Ui.space(text,7);text.addView(Ui.text(this,item.optString("vod_remarks"),12,Ui.MINT));Ui.space(text,7);text.addView(Ui.text(this,"来源："+s.name,11,Ui.MUTED));if(item.has("_episode"))text.addView(Ui.text(this,"上次："+item.optString("_episode"),12,Ui.ACCENT));card.setOnClickListener(v->openDetail(item));
  if(tab==1||tab==2)card.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("移除这条记录？").setMessage(item.optString("vod_name")).setPositiveButton("移除",(d,w)->{store.remove(tab==1?"favorites":"history",item);shelf();}).setNegativeButton("取消",null).show();return true;});
 }
 private void shelf(){
  content.removeAllViews();String name=tab==1?"favorites":"history";content.addView(Ui.text(this,tab==1?"我的追番":"观看足迹",22,Ui.TEXT));Ui.space(content,12);JSONArray a=store.entries(name);if(a.length()==0)content.addView(Ui.text(this,tab==1?"收藏喜欢的动漫，下次直接来这里。":"看过的动漫和播放进度会保存在本机。",15,Ui.MUTED));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)card(o);}if(tab==2&&a.length()>0){Button clear=Ui.button(this,"清空观看记录与进度",false);content.addView(clear);clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("清空全部观看记录？").setMessage("同时删除本机保存的播放进度。").setPositiveButton("清空",(d,w)->{store.clearHistory();shelf();}).setNegativeButton("取消",null).show());}
 }
 private void sources(){
  content.removeAllViews();content.addView(Ui.text(this,"每一部，都有出处",23,Ui.TEXT));Ui.space(content,12);content.addView(Ui.text(this,"应用免费，不加入广告、付费入口和追踪 SDK。收藏、足迹和播放进度只保存在本机。",15,Ui.MUTED));Ui.space(content,14);
  for(Source s:Source.ALL){LinearLayout box=Ui.column(this);Ui.pad(box,15);box.setBackground(Ui.bg(this,Ui.CARD,16));box.addView(Ui.text(this,s.name,19,Ui.ACCENT));Ui.space(box,6);box.addView(Ui.text(this,"资源提供方："+s.website+"\n数据接口："+s.api,12,Ui.MUTED));Ui.space(box,8);Button open=Ui.button(this,"查看来源网站 ↗",false);box.addView(open);open.setOnClickListener(v->Ui.open(this,s.website));content.addView(box);Ui.space(content,12);}
  content.addView(Ui.text(this,"第三方来源的内容授权与视频内广告未经逐条核实，标注来源不代表获得授权。视频可能失效、限地区或自带广告；打开原站后适用原站规则。",13,Ui.MUTED));Ui.space(content,16);content.addView(Ui.text(this,"G 动漫 0.1 · Android 原生播放器\n应用不托管、上传或代理视频，不加载第三方网页播放器。网络请求会向提供方传递正常连接所需的信息。",12,Ui.MUTED));
 }
 private void openDetail(JSONObject brief){
  detail=true;final int token=++generation;shell();LinearLayout area=Ui.column(this);Ui.pad(area,18);root.addView(area);Button back=Ui.button(this,"‹ 返回",false);area.addView(back);back.setOnClickListener(v->home(false));area.addView(Ui.text(this,"正在获取最新选集…",18,Ui.TEXT));
  network.execute(()->{try{JSONObject item=Api.detail(Source.find(brief.optString("_source")),brief.optString("vod_id"));runOnUiThread(()->{if(token==generation&&!isDestroyed())showDetail(item);});}catch(Exception e){runOnUiThread(()->{if(token!=generation||isDestroyed())return;area.addView(Ui.text(this,"加载失败："+friendly(e),15,Ui.MUTED));Button retry=Ui.button(this,"重试",true);area.addView(retry);retry.setOnClickListener(v->openDetail(brief));});}});
 }
 private void showDetail(JSONObject item){
  detail=true;shell();ScrollView sv=new ScrollView(this);root.addView(sv,new LinearLayout.LayoutParams(-1,-1));LinearLayout c=Ui.column(this);Ui.pad(c,18);sv.addView(c);Button back=Ui.button(this,"‹ 返回",false);c.addView(back);back.setOnClickListener(v->home(false));Ui.space(c,16);
  LinearLayout top=Ui.row(this);top.addView(Ui.poster(this,item.optString("vod_pic")),new LinearLayout.LayoutParams(Ui.dp(this,106),Ui.dp(this,150)));LinearLayout meta=Ui.column(this);meta.setPadding(Ui.dp(this,16),0,0,0);top.addView(meta,new LinearLayout.LayoutParams(0,-2,1));TextView title=Ui.text(this,item.optString("vod_name"),23,Ui.TEXT);title.setTypeface(null,1);meta.addView(title);Ui.space(meta,8);meta.addView(Ui.text(this,item.optString("type_name")+" · "+item.optString("vod_year")+"\n"+item.optString("vod_remarks"),13,Ui.MINT));c.addView(top);Ui.space(c,14);
  final Source s=Source.find(item.optString("_source"));c.addView(Ui.text(this,"资源来源："+s.name+"\n"+s.website,13,Ui.MUTED));LinearLayout actions=Ui.row(this);Button favorite=Ui.button(this,store.contains("favorites",item)?"已追番 ♥":"加入追番 ♡",true);Ui.weighted(actions,favorite);favorite.setOnClickListener(v->{if(store.contains("favorites",item))store.remove("favorites",item);else store.put("favorites",item);favorite.setText(store.contains("favorites",item)?"已追番 ♥":"加入追番 ♡");});Button origin=Ui.button(this,"查看原站 ↗",false);Ui.weighted(actions,origin);origin.setOnClickListener(v->Ui.open(this,s.website));c.addView(actions);
  Ui.space(c,14);c.addView(Ui.text(this,"选集",20,Ui.TEXT));List<Playback.Route> routes=Playback.parse(item.optString("vod_play_from"),item.optString("vod_play_url"));
  if(routes.isEmpty())c.addView(Ui.text(this,"这个来源没有可直接播放的安全视频地址，请尝试其他来源。",15,Ui.MUTED));
  LinearLayout episodes=Ui.column(this);String lastRoute="",lastEpisode="";JSONArray h=store.entries("history");for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o!=null&&Store.key(o).equals(Store.key(item))){lastRoute=o.optString("_route");lastEpisode=o.optString("_episode");break;}}
  if(!routes.isEmpty()){
   int selected=0;for(int i=0;i<routes.size();i++)if(routes.get(i).name.equals(lastRoute))selected=i;final int[] routeIndex={selected};final String previousEpisode=lastEpisode;
   Button route=Ui.button(this,"线路："+routes.get(selected).name+" ▾",false);c.addView(route);route.setOnClickListener(v->{String[] labels=new String[routes.size()];for(int i=0;i<labels.length;i++)labels[i]=routes.get(i).name;new AlertDialog.Builder(this).setTitle("选择播放线路").setItems(labels,(d,n)->{routeIndex[0]=n;route.setText("线路："+routes.get(n).name+" ▾");episodeButtons(episodes,item,routes.get(n));}).show();});
   Button play=Ui.button(this,lastEpisode.isEmpty()?"开始观看 ▶":"继续观看 · "+lastEpisode,true);c.addView(play);play.setOnClickListener(v->{Playback.Route r=routes.get(routeIndex[0]);int ep=0;for(int i=0;i<r.episodes.size();i++)if(r.episodes.get(i).title.equals(previousEpisode))ep=i;launch(item,r,r.episodes.get(ep));});Ui.space(c,8);c.addView(episodes);episodeButtons(episodes,item,routes.get(selected));
  }
  Ui.space(c,18);c.addView(Ui.text(this,"简介",20,Ui.TEXT));String desc=Html.fromHtml(item.optString("vod_content"),Html.FROM_HTML_MODE_LEGACY).toString().trim();c.addView(Ui.text(this,desc.isEmpty()?"来源暂未提供简介":desc,14,Ui.MUTED));Ui.space(c,18);Button switchSource=Ui.button(this,"去其他来源搜索这部动漫",false);c.addView(switchSource);switchSource.setOnClickListener(v->{query=item.optString("vod_name");source=Source.ALL[(Arrays.asList(Source.ALL).indexOf(s)+1)%Source.ALL.length];store.setSetting("source",source.id);tab=0;home(true);});
 }
 private void episodeButtons(LinearLayout box,JSONObject item,Playback.Route route){box.removeAllViews();LinearLayout row=null;for(int i=0;i<route.episodes.size();i++){if(i%3==0){row=Ui.row(this);box.addView(row);}Playback.Episode ep=route.episodes.get(i);Button b=Ui.button(this,ep.title,false);b.setTextSize(12);Ui.weighted(row,b);b.setOnClickListener(v->launch(item,route,ep));}if(row!=null)for(int i=route.episodes.size()%3;i>0&&i<3;i++)Ui.weighted(row,new View(this));}
 private void launch(JSONObject item,Playback.Route route,Playback.Episode ep){Intent i=new Intent(this,PlayerActivity.class);i.putExtra("source",item.optString("_source"));i.putExtra("id",item.optString("vod_id"));i.putExtra("route",route.name);i.putExtra("episode",ep.title);startActivity(i);}
 @Override public void onBackPressed(){if(detail){home(false);return;}if(tab!=0){tab=0;home(false);return;}super.onBackPressed();}
}
