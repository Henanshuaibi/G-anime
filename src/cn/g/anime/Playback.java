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
 public static String progressKey(String source,String id,String route,String episode){return source+":"+id+":"+route.length()+":"+route+":"+episode;}
}
