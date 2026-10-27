import cn.g.anime.*;
import org.json.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
public final class LiveCheck {
 private static byte[] sample(String url)throws Exception{
  if(!Playback.isHttps(url))throw new Exception("unsafe media segment");
  java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","GAnime/0.1 (Android; personal anime client)");c.setRequestProperty("Range","bytes=0-65535");
  try{int status=c.getResponseCode();if(status!=200&&status!=206)throw new Exception("segment HTTP "+status);if(!Playback.isHttps(c.getURL().toString()))throw new Exception("unsafe redirected segment");try(java.io.InputStream in=c.getInputStream();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while(out.size()<65536&&(n=in.read(b,0,Math.min(b.length,65536-out.size())))!=-1)out.write(b,0,n);return out.toByteArray();}}finally{c.disconnect();}
 }
 public static void main(String[] args)throws Exception{
  JSONArray report=new JSONArray();int failures=0;
  for(Source source:Source.ALL){
   JSONObject result=new JSONObject().put("source",source.name).put("api",source.api);
   try{
    Api.Page p=Api.list(source,0,"",1);if(p.items.isEmpty())throw new Exception("empty anime category");JSONObject brief=p.items.get(0);JSONObject item=Api.detail(source,brief.optString("vod_id"));
    List<Playback.Route> routes=Playback.parse(item.optString("vod_play_from"),item.optString("vod_play_url"));if(routes.isEmpty())throw new Exception("no direct routes");
    Playback.Episode episode=routes.get(0).episodes.get(0);String url=episode.url;String playlist=new String(Api.get(url,2*1024*1024),"UTF-8").replace("\ufeff","");if(!playlist.startsWith("#EXTM3U"))throw new Exception("invalid HLS manifest");
    if(playlist.contains("#EXT-X-STREAM-INF")){String target=Arrays.stream(playlist.split("\\r?\\n")).map(String::trim).filter(x->!x.isEmpty()&&!x.startsWith("#")).findFirst().orElseThrow(()->new Exception("empty master"));url=new URI(url).resolve(target).toString();playlist=new String(Api.get(url,3*1024*1024),"UTF-8");}
    if(!playlist.contains("#EXTINF"))throw new Exception("no media segments");String segment=Arrays.stream(playlist.split("\\r?\\n")).map(String::trim).filter(x->!x.isEmpty()&&!x.startsWith("#")).findFirst().orElseThrow(()->new Exception("no segment"));String segmentUrl=new URI(url).resolve(segment).toString();byte[] media=sample(segmentUrl);if(media.length<188)throw new Exception("empty media segment");
    Api.Page search=Api.list(source,0,item.optString("vod_name"),1);if(search.items.isEmpty())throw new Exception("search did not find anime");
    Api.Page chinese=Api.list(source,1,"",1);if(source.supportsCategory(1)&&chinese.items.isEmpty())throw new Exception("empty Chinese anime category");if(!source.supportsCategory(1)&&!chinese.items.isEmpty())throw new Exception("unsupported Chinese category leaked");
    result.put("chinese_anime_list",chinese.items.size()).put("anime_list",p.items.size()).put("detail",item.optString("vod_name")).put("routes",routes.size()).put("episodes",routes.get(0).episodes.size()).put("search",search.items.size()).put("hls_manifest",true).put("segment_bytes",media.length).put("segment_host",new URI(segmentUrl).getHost()).put("status","PASS");
   }catch(Exception e){failures++;result.put("status","FAIL").put("error",e.toString());}
   report.put(result);System.out.println(result.toString());
  }
  Files.createDirectories(Paths.get("build"));Files.write(Paths.get("build/live-check.json"),new JSONObject().put("checked_at",java.time.Instant.now().toString()).put("device_playback_verified",false).put("sources",report).toString(2).getBytes("UTF-8"));if(failures>0)System.exit(1);
 }
}
