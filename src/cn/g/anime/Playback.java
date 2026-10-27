package cn.g.anime;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
/** Parse media URLs without executing provider scripts or loading HTML players. */
public final class Playback {
 public static final class Episode{public final String title,url;Episode(String title,String url){this.title=title;this.url=url;}}
 public static final class Route{public final String name;public final List<Episode> episodes=new ArrayList<>();Route(String name){this.name=name;}}
 public static boolean isHttps(String url){try{URI u=new URI(url);return "https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null;}catch(Exception e){return false;}}
 public static boolean isMedia(String url){if(!isHttps(url))return false;try{String p=new URI(url).getPath().toLowerCase(Locale.ROOT);return p.endsWith(".m3u8")||p.endsWith(".mp4")||p.endsWith(".m4v");}catch(Exception e){return false;}}
 public static List<Route> parse(String names,String urls){
  List<Route> result=new ArrayList<>();String[] labels=names.split("\\$\\$\\$",-1),groups=urls.split("\\$\\$\\$",-1);
  for(int i=0;i<groups.length;i++){Route route=new Route(i<labels.length?labels[i]:"线路 "+(i+1));
   for(String part:groups[i].split("#")){int split=part.indexOf('$');if(split<0)continue;String title=part.substring(0,split).trim(),url=part.substring(split+1).trim();if(isMedia(url))route.episodes.add(new Episode(title.isEmpty()?"正片":title,url));}
   if(!route.episodes.isEmpty())result.add(route);
  }return result;
 }
 public static int findEpisode(List<Episode> episodes,String wanted){
  if(wanted==null||wanted.trim().isEmpty())return -1;
  for(int i=0;i<episodes.size();i++)if(episodes.get(i).title.equals(wanted))return i;
  String number=episodeNumber(wanted);if(number==null)return -1;
  int result=-1;for(int i=0;i<episodes.size();i++)if(number.equals(episodeNumber(episodes.get(i).title))){if(result>=0)return -1;result=i;}
  return result;
 }
 private static String episodeNumber(String title){
  String s=java.text.Normalizer.normalize(title.trim(),java.text.Normalizer.Form.NFKC);
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)^(?:第\\s*(\\d+)\\s*[集话話]|(?:episode|ep|e)\\s*(\\d+)|(\\d+)\\s*[集话話]?)$").matcher(s);
  if(!m.matches())return null;
  for(int i=1;i<=m.groupCount();i++)if(m.group(i)!=null){try{return String.valueOf(Integer.parseInt(m.group(i)));}catch(NumberFormatException ignored){return null;}}
  return null;
 }
 public static int resumePosition(int saved,int duration){return saved<0||duration>0&&saved>=Math.max(0,duration-3000)?0:saved;}
 public static String progressKey(String source,String id,String route,String episode){return source+":"+id+":"+route.length()+":"+route+":"+episode;}
}
