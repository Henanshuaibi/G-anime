import cn.g.anime.Playback;
import cn.g.anime.Source;
import java.util.List;
public final class PlaybackTest {
 private static int checks;
 private static void check(boolean x,String message){checks++;if(!x)throw new AssertionError(message);}
 public static void main(String[] args){
  List<Playback.Route> r=Playback.parse("web$$$hls","第1集$https://example.com/share/1$$$第1集$https://cdn.example.com/a.m3u8?sign=a=b#第2集$https://cdn.example.com/b.mp4");
  check(r.size()==1,"HTML players must not become routes");check(r.get(0).name.equals("hls"),"preserve original route label");check(r.get(0).episodes.size()==2,"parse two episodes");check(r.get(0).episodes.get(0).url.endsWith("?sign=a=b"),"preserve query signatures");
  check(Playback.parse("x","bad#1$javascript:alert(1)#2$file:///x.mp4#3$http://example.com/x.m3u8#4$https://example.com/player.html").isEmpty(),"reject scripts, local files, cleartext and HTML");
  check(!Playback.isMedia("https://user:pass@example.com/x.mp4"),"reject embedded credentials");check(!Playback.isMedia("https://example.com/route?url=x.m3u8"),"require media extension in path");check(Playback.isMedia("https://example.com/X.M3U8?a=1"),"case insensitive extension");
  check(Playback.parse("x","1$https://example.com/x.m3u8#broken#2$https://example.com/y.m3u8").get(0).episodes.size()==2,"skip malformed episodes without losing good ones");check(Playback.parse("","broken").isEmpty(),"empty and malformed data");
  check(!Playback.progressKey("a","1","bc","d").equals(Playback.progressKey("a","1","b","c:d")),"progress keys cannot collide across routes");
  for(Source s:Source.ALL){check(Playback.isHttps(s.api),"secure source API");for(int t:s.types)check(s.accepts(t),"allowlisted category");check(!s.accepts(34)&&!s.accepts(6)&&!s.accepts(39),"exclude non-anime and adult categories");}
  System.out.println("Playback/source checks passed: "+checks);
 }
}
