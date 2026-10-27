package cn.g.anime;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;
public final class Api {
 public static byte[] get(String url,int limit)throws Exception{
  if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("请求已取消");
  if(!Playback.isHttps(url))throw new IOException("来源需要 HTTPS 安全连接");
  for(int redirects=0;redirects<5;redirects++){
   HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","GAnime/0.3 (Android; personal anime client)");c.setRequestProperty("Accept-Encoding","identity");
   try{int status=c.getResponseCode();if(status>=300&&status<=399){String target=c.getHeaderField("Location");if(target==null)throw new IOException("来源重定向无效");url=new URL(new URL(url),target).toString();if(!Playback.isHttps(url))throw new IOException("来源跳转到不安全连接");continue;}
    if(status!=200)throw new IOException("来源返回 HTTP "+status);if(c.getContentLengthLong()>limit)throw new IOException("来源响应过大");
    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("请求已取消");if(out.size()+n>limit)throw new IOException("来源响应过大");out.write(buf,0,n);}return out.toByteArray();}
   }finally{c.disconnect();}
  }throw new IOException("来源跳转次数过多");
 }
 public static JSONObject json(String url)throws Exception{JSONObject o=new JSONObject(new String(get(url,6*1024*1024),"UTF-8"));if(o.has("code")&&o.optInt("code")!=1)throw new IOException("来源暂时无法提供内容");return o;}
 public static final class Page{public final List<JSONObject> items=new ArrayList<>();public int count;}
 public static Page list(Source s,int category,String query,int page)throws Exception{
  if(query.isEmpty()&&!s.supportsCategory(category))return new Page();
  String url=s.api+"?ac=detail&pg="+page;if(query.isEmpty())url+="&t="+s.types[category];else url+="&wd="+URLEncoder.encode(query,"UTF-8");
  JSONObject data=json(url);Page result=new Page();result.count=data.optInt("pagecount",1);JSONArray list=data.optJSONArray("list");
  if(list!=null)for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);if(item!=null&&s.accepts(item.optInt("type_id"))){item.put("_source",s.id);result.items.add(item);}}
  return result;
 }
 public static JSONObject detail(Source s,String id)throws Exception{
  JSONObject data=json(s.api+"?ac=detail&ids="+URLEncoder.encode(id,"UTF-8"));JSONArray list=data.optJSONArray("list");if(list==null||list.length()==0)throw new IOException("来源中已找不到这部动漫");JSONObject item=list.getJSONObject(0);
  if(!id.equals(item.optString("vod_id"))||!s.accepts(item.optInt("type_id")))throw new IOException("来源返回了不匹配的内容");item.put("_source",s.id);return item;
 }
}
