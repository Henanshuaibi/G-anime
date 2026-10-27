import cn.g.anime.*;
import org.json.*;
import java.util.*;
public final class CatalogTest {
    private static int checks;
    private static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
    private static JSONObject item(String source,String id,String name,String year,String type)throws Exception{
        return new JSONObject().put("_source",source).put("vod_id",id).put("vod_name",name).put("vod_year",year).put("type_name",type);
    }
    public static void main(String[] args)throws Exception{
        JSONObject a=item("modu","1","测试 动漫！","2026","日韩动漫");
        JSONObject b=item("zuidi","2","测试动漫","2026","日本动漫");
        List<List<JSONObject>> grouped=Catalog.group(Arrays.asList(a,b,a));
        check(grouped.size()==1,"combine punctuation/space variants and region aliases");
        check(grouped.get(0).size()==2,"duplicate identity is counted once");
        check(grouped.get(0).get(0).optString("_source").equals("zuidi"),"prefer the source audit order");
        check(!Catalog.key(a).equals(Catalog.key(item("modu","3","测试动漫 第二季","2026","日本动漫"))),"do not combine seasons");
        check(!Catalog.key(a).equals(Catalog.key(item("modu","4","测试动漫","2025","日本动漫"))),"do not combine remakes from different years");
        check(!Catalog.key(a).equals(Catalog.key(item("modu","5","测试动漫 国语版","2026","日本动漫"))),"preserve dub/version labels");
        check(!Catalog.key(a).equals(Catalog.key(item("modu","6","测试动漫","2026","国产动漫"))),"preserve origin region");
        check(b.optString("vod_id").equals("2")&&b.optString("_source").equals("zuidi"),"never replace source-specific identity");
        for(Source s:Source.ALL){check(s.supportsCategory(0)||s.supportsCategory(1),"every source supports an audited anime category");check(s.types[0]!=s.types[1],"use source-specific category IDs");}
        check(Source.find("baofeng").accepts(41)&&!Source.find("baofeng").accepts(40),"exclude the Baofeng Chinese category with verified advertising");
        check(!Source.find("modu").recommended&&!Source.find("feifan").recommended&&!Source.find("youzhi").recommended,"sources with verified ads are disabled by default");
        check(Source.find("zuidi").supportsCategory(0)&&Source.find("zuidi").supportsCategory(1),"default source covers both anime categories");
        check(Api.list(Source.find("baofeng"),1,"",1).items.isEmpty(),"unsupported categories return no records without a network request");
        System.out.println("Catalog/search checks passed: "+checks);
    }
}
