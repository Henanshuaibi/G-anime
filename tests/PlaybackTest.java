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
  for(Source s:Source.ALL){check(Playback.isHttps(s.api),"secure source API");for(int t:s.types)if(t>0)check(s.accepts(t),"allowlisted category");check(!s.accepts(34)&&!s.accepts(6)&&!s.accepts(39),"exclude non-anime and adult categories");}
  List<Playback.Episode> eps=Playback.parse("hls","第01集$https://example.com/1.m3u8#第02集$https://example.com/2.m3u8#SP01$https://example.com/sp.m3u8").get(0).episodes;
  check(Playback.findEpisode(eps,"EP 2")==1,"match the same episode across source labels");
  check(Playback.findEpisode(eps,"1")==0,"match a plain episode number");
  check(Playback.findEpisode(eps,"SP01")==2,"preserve exact special episode names");
  check(Playback.findEpisode(eps,"OVA1")==-1,"never confuse specials with the first episode");
  check(Playback.findEpisode(eps,"第1-2集")==-1,"never match a combined episode to a single episode");
  check(Playback.findEpisode(eps,"EP 900")==-1,"missing episodes require manual selection");
  List<Playback.Episode> ambiguous=Playback.parse("hls","第01集$https://example.com/a.m3u8#EP1$https://example.com/b.m3u8").get(0).episodes;
  check(Playback.findEpisode(ambiguous,"1")==-1,"do not guess ambiguous numbering");
  check(Playback.resumePosition(-1,10000)==0,"reject negative resume positions");
  check(Playback.resumePosition(7000,10000)==0,"restart an episode already at its ending");
  check(Playback.resumePosition(6000,10000)==6000,"retain a normal resume position");
  check(Playback.resumePosition(6000,-1)==6000,"preserve resume while duration is unknown");
  System.out.println("Playback/source checks passed: "+checks);
 }
}
