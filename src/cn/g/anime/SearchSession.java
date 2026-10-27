package cn.g.anime;

import java.util.*;
import org.json.JSONObject;

/** Each provider owns its next page; a failure must not advance that provider. */
public final class SearchSession {
    private static final class Cursor {
        int next = 1, pages = 1;
        boolean pending, failed;
        Request active;
    }
    public static final class Request {
        public final Source source;
        public final int page;
        private Request(Source source, int page) { this.source = source; this.page = page; }
    }
    private final LinkedHashMap<Source,Cursor> cursors = new LinkedHashMap<>();
    private final LinkedHashMap<String,JSONObject> records = new LinkedHashMap<>();
    public SearchSession(List<Source> sources) {
        for (Source source : sources) cursors.put(source, new Cursor());
    }
    public List<Request> begin(boolean retryOnly) {
        List<Request> requests = new ArrayList<>();
        for (Map.Entry<Source,Cursor> entry : cursors.entrySet()) {
            Cursor cursor = entry.getValue();
            if (!cursor.pending && cursor.next <= cursor.pages && (!retryOnly || cursor.failed)) {
                cursor.pending = true;
                cursor.active = new Request(entry.getKey(), cursor.next);
                requests.add(cursor.active);
            }
        }
        return requests;
    }
    public void success(Request request, Api.Page page) {
        Cursor cursor = cursors.get(request.source);
        if (cursor == null || !cursor.pending || cursor.active != request || cursor.next != request.page) return;
        cursor.pending = false; cursor.failed = false;
        cursor.pages = Math.max(request.page, page.count); cursor.next = request.page + 1;
        for (JSONObject item : page.items) {
            records.put(item.optString("_source") + ":" + item.optString("vod_id"), item);
        }
    }
    public void failure(Request request) {
        Cursor cursor = cursors.get(request.source);
        if (cursor != null && cursor.pending && cursor.active == request && cursor.next == request.page) {
            cursor.pending = false; cursor.failed = true;
        }
    }
    public void cancelPending() { for (Cursor cursor : cursors.values()) { cursor.pending = false; cursor.active = null; } }
    public boolean busy() { for (Cursor cursor : cursors.values()) if (cursor.pending) return true; return false; }
    public boolean hasMore() { for (Cursor cursor : cursors.values()) if (cursor.next <= cursor.pages) return true; return false; }
    public List<JSONObject> items() { return new ArrayList<>(records.values()); }
    public Set<String> errors() {
        Set<String> errors = new LinkedHashSet<>();
        for (Map.Entry<Source,Cursor> entry : cursors.entrySet()) if (entry.getValue().failed) errors.add(entry.getKey().name);
        return errors;
    }
}
