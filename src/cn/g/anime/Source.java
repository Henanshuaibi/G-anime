package cn.g.anime;
public final class Source {
 public final String id,name,website,api; public final int[] types;
 public static final String[] CATEGORIES={"日韩动漫","国产动漫","欧美动漫","港台动漫"};
 public static final Source[] ALL={
  
  new Source("feifan","非凡资源","https://ffzy.tv/","https://api.ffzyapi.com/api.php/provide/vod/",new int[]{30,29,31,32}),
  new Source("modu","魔都资源","https://www.moduzy.net/","https://caiji.moduapi.cc/api.php/provide/vod/",new int[]{2,1,3,4})
 };
 private Source(String id,String name,String website,String api,int[] types){this.id=id;this.name=name;this.website=website;this.api=api;this.types=types;}
 public static Source find(String id){for(Source s:ALL)if(s.id.equals(id))return s;throw new IllegalArgumentException("未知资源来源");}
 public boolean accepts(int type){for(int t:types)if(type==t)return true;return false;}
}
