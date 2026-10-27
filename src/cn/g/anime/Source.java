package cn.g.anime;

public final class Source {
    public final String id, name, website, api, review;
    public final int[] types;
    public final boolean recommended;
    public static final String[] CATEGORIES = {"日漫", "国漫"};
    public static final Source[] ALL = {
        new Source("zuidi", "最大资源", "https://api.zuidapi.com/", "https://api.zuidapi.com/api.php/provide/vod/", new int[]{30,29}, true, "10月27日：国漫、日漫样本片头/中/尾未见明显广告"),
        new Source("baofeng", "暴风资源", "https://bfzyapi.com/api.php/provide/vod/", "https://bfzyapi.com/api.php/provide/vod/", new int[]{41,0}, true, "仅接入日漫：抽查未见明显广告；国漫样本有广告，未接入"),
        new Source("feifan", "非凡资源", "https://ffzy.tv/", "https://api.ffzyapi.com/api.php/provide/vod/", new int[]{30,29}, false, "已发现国漫样本片尾博彩广告，默认关闭"),
        new Source("youzhi", "优质资源", "https://api.yzzy-api.com/inc/apijson.php", "https://api.yzzy-api.com/inc/apijson.php", new int[]{67,66}, false, "已发现国漫、日漫样本片尾博彩广告，默认关闭"),
        new Source("modu", "魔都资源", "https://www.moduzy.net/", "https://caiji.moduapi.cc/api.php/provide/vod/", new int[]{2,1}, false, "已发现视频内博彩水印及片尾广告，默认关闭")
    };
    private Source(String id, String name, String website, String api, int[] types, boolean recommended, String review) {
        this.id=id; this.name=name; this.website=website; this.api=api;
        this.types=types; this.recommended=recommended; this.review=review;
    }
    public static Source find(String id) {
        for (Source s : ALL) if (s.id.equals(id)) return s;
        throw new IllegalArgumentException("未知资源来源");
    }
    public static int rank(String id) {
        for (int i=0; i<ALL.length; i++) if (ALL[i].id.equals(id)) return i;
        return ALL.length;
    }
    public boolean supportsCategory(int category) { return category >= 0 && category < types.length && types[category] > 0; }
    public boolean accepts(int type) { for (int t : types) if (t > 0 && type == t) return true; return (id.equals("feifan") && (type == 31 || type == 32)) || (id.equals("modu") && (type == 3 || type == 4)); }
}
