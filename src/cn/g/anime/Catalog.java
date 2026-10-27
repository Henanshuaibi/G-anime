package cn.g.anime;

import java.util.*;
import org.json.JSONObject;

/** Conservative grouping: preserve season, dub/version labels, year, and region. */
public final class Catalog {
    public static String key(JSONObject item) {
        String name = item.optString("vod_name").toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。！？：；、·《》「」【】（）]+", "");
        String type = item.optString("type_name");
        String region = type.contains("国产") || type.contains("中国") ? "cn"
                : type.contains("日本") || type.contains("日韩") ? "jp" : type;
        return name + "|" + item.optString("vod_year") + "|" + region;
    }
    public static List<List<JSONObject>> group(List<JSONObject> items) {
        LinkedHashMap<String,List<JSONObject>> groups = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (JSONObject item : items) {
            String identity = item.optString("_source") + ":" + item.optString("vod_id");
            if (!seen.add(identity)) continue;
            String key = key(item);
            if (!groups.containsKey(key)) groups.put(key, new ArrayList<>());
            groups.get(key).add(item);
        }
        for (List<JSONObject> group : groups.values()) {
            group.sort(Comparator.comparingInt(o -> Source.rank(o.optString("_source"))));
        }
        return new ArrayList<>(groups.values());
    }
}
