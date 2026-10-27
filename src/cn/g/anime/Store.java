package cn.g.anime;
import android.content.*;
import org.json.*;
public final class Store{
 private final SharedPreferences p;public Store(Context c){p=c.getSharedPreferences("library",Context.MODE_PRIVATE);}
 public static String key(JSONObject i){return i.optString("_source")+":"+i.optString("vod_id");}
 public String setting(String n,String fallback){return p.getString("setting:"+n,fallback);}public void setSetting(String n,String v){p.edit().putString("setting:"+n,v).apply();}
 public JSONArray entries(String n){try{return new JSONArray(p.getString(n,"[]"));}catch(Exception e){return new JSONArray();}}
 public boolean contains(String n,JSONObject item){JSONArray a=entries(n);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&key(item).equals(key(o)))return true;}return false;}
 public void remove(String n,JSONObject item){JSONArray a=entries(n),next=new JSONArray();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&!key(o).equals(key(item)))next.put(o);}p.edit().putString(n,next.toString()).apply();}
 public void put(String n,JSONObject item){JSONObject brief=new JSONObject();try{for(String k:new String[]{"_source","vod_id","vod_name","vod_pic","vod_remarks","type_name","type_id","vod_year","_episode","_route"})if(item.has(k))brief.put(k,item.get(k));brief.put("_time",System.currentTimeMillis());}catch(Exception ignored){}
  JSONArray next=new JSONArray(),old=entries(n);next.put(brief);for(int i=0;i<old.length()&&next.length()<200;i++){JSONObject o=old.optJSONObject(i);if(o!=null&&!key(o).equals(key(item)))next.put(o);}p.edit().putString(n,next.toString()).apply();
 }
 public java.util.List<String> recentSearches(){
  java.util.List<String> result=new java.util.ArrayList<>();try{JSONArray a=new JSONArray(p.getString("recent-searches","[]"));for(int i=0;i<a.length();i++){String q=a.optString(i).trim();if(!q.isEmpty())result.add(q);}}catch(Exception ignored){}return result;
 }
 public void rememberSearch(String query){
  String q=query==null?"":query.trim();if(q.isEmpty())return;JSONArray a=new JSONArray();a.put(q);
  for(String old:recentSearches())if(!q.equalsIgnoreCase(old)&&a.length()<10)a.put(old);
  p.edit().putString("recent-searches",a.toString()).apply();
 }
 public void clearSearches(){p.edit().remove("recent-searches").apply();}
 public int position(String key){return p.getInt("position:"+key,0);}public void position(String key,int ms){p.edit().putInt("position:"+key,Math.max(0,ms)).apply();}
 public void clearHistory(){SharedPreferences.Editor e=p.edit().remove("history");for(String k:p.getAll().keySet())if(k.startsWith("position:"))e.remove(k);e.apply();}
}
