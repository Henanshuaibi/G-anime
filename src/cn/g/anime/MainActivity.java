package cn.g.anime;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.Html;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService network = Executors.newFixedThreadPool(6);
    private final Handler ui = new Handler();
    private Store store;
    private Source source;
    private int category, tab, page = 1, generation, pending, finished, batchSize;
    private String query = "";
    private boolean detail, loading, dirty;
    private LinearLayout root, content, controls;
    private ScrollView scroll;
    private EditText search;
    private final List<JSONObject> items = new ArrayList<>();
    private final Map<String,Integer> sourcePages = new HashMap<>();
    private final Set<String> errors = new LinkedHashSet<>();
    private final Runnable renderLater = () -> { if (!detail && tab == 0 && !isDestroyed()) render(); };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new Store(this);
        try { source = Source.find(store.setting("source", Source.ALL[0].id)); }
        catch (Exception e) { source = Source.ALL[0]; }
        if (state != null) {
            tab = state.getInt("tab", 0);
            category = Math.max(0, Math.min(1, state.getInt("category", 0)));
            query = state.getString("query", "");
        }
        String incoming = getIntent().getStringExtra("search");
        if (incoming != null && !incoming.trim().isEmpty()) { query = incoming.trim(); tab = 0; }
        home(true);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        String name = intent.getStringExtra("search");
        if (name != null && !name.trim().isEmpty()) { query = name.trim(); tab = 0; home(true); }
    }
    @Override protected void onSaveInstanceState(Bundle b) {
        b.putInt("tab", tab); b.putInt("category", category); b.putString("query", query);
        super.onSaveInstanceState(b);
    }
    @Override protected void onDestroy() {
        generation++; ui.removeCallbacksAndMessages(null); network.shutdownNow(); super.onDestroy();
    }
    @Override protected void onResume() {
        super.onResume();
        if (store != null && !detail && (tab == 1 || tab == 2)) shelf();
    }
    private void shell() {
        root = Ui.column(this); root.setBackgroundColor(Ui.BG);
        setContentView(root); Ui.inset(this, root);
    }
    private boolean enabled(Source s) {
        return Boolean.parseBoolean(store.setting("enabled:" + s.id, String.valueOf(s.recommended)));
    }
    private List<Source> enabledSources() {
        List<Source> result = new ArrayList<>();
        for (Source s : Source.ALL) if (enabled(s)) result.add(s);
        if (result.isEmpty()) result.add(Source.ALL[0]);
        return result;
    }
    private void home(boolean reload) {
        generation++; ui.removeCallbacks(renderLater); loading = false; detail = false;
        if (!enabled(source) || !source.supportsCategory(category)) {
            for (Source s : enabledSources()) if (s.supportsCategory(category)) { source = s; break; }
        }
        shell();
        LinearLayout header = Ui.row(this); Ui.pad(header, 14);
        TextView brand = Ui.text(this, "G 动漫", 24, Ui.TEXT); brand.setTypeface(null, 1);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button settings = Ui.button(this, "来源", false);
        header.addView(settings, new LinearLayout.LayoutParams(Ui.dp(this, 70), Ui.dp(this, 42)));
        settings.setOnClickListener(v -> { tab = 3; home(false); }); root.addView(header);
        controls = Ui.column(this); controls.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 4));
        root.addView(controls);
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = Ui.column(this); Ui.pad(content, 12); scroll.addView(content);
        LinearLayout nav = Ui.row(this); Ui.pad(nav, 6);
        String[] labels = {"找番", "追番", "记录"};
        for (int i = 0; i < labels.length; i++) {
            final int n = i; Button b = Ui.button(this, labels[i], i == tab); Ui.weighted(nav, b);
            b.setOnClickListener(v -> { tab = n; home(dirty); dirty = false; });
        }
        root.addView(nav);
        if (tab == 0) {
            discoverControls();
            if (reload || items.isEmpty() || finished < batchSize) load(true); else render();
        } else {
            controls.setVisibility(View.GONE);
            if (tab == 3) sources(); else shelf();
        }
    }
    private void discoverControls() {
        LinearLayout line = Ui.row(this);
        search = new EditText(this); search.setSingleLine(true); search.setTextColor(Ui.TEXT);
        search.setHintTextColor(Ui.MUTED); search.setHint("搜名字，一次找多个来源"); search.setText(query);
        search.setTextSize(15); search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackground(Ui.bg(this, Ui.CARD, 12)); Ui.pad(search, 12);
        line.addView(search, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        Button go = Ui.button(this, "搜索", true);
        line.addView(go, new LinearLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 48)));
        controls.addView(line); go.setOnClickListener(v -> doSearch());
        search.setOnEditorActionListener((t, id, event) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) { doSearch(); return true; } return false;
        });
        Ui.space(controls, 6);
        LinearLayout filters = Ui.row(this);
        String[] names = {"日漫", "国漫"};
        for (int i = 0; i < 2; i++) {
            final int cat = i;
            Button b = Ui.button(this, names[i], query.isEmpty() && category == i);
            Ui.weighted(filters, b);
            b.setOnClickListener(v -> { category = cat; query = ""; home(true); });
        }
        Button provider = Ui.button(this, query.isEmpty() ? source.name + " ▾" : "清除搜索", false);
        Ui.weighted(filters, provider);
        provider.setTextSize(12);
        provider.setOnClickListener(v -> {
            if (!query.isEmpty()) { query = ""; home(true); return; }
            List<Source> choices = new ArrayList<>();
            for (Source s : enabledSources()) if (s.supportsCategory(category)) choices.add(s);
            String[] labels = new String[choices.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = choices.get(i).name;
            new AlertDialog.Builder(this).setTitle("浏览来源").setItems(labels, (d, n) -> {
                source = choices.get(n); store.setSetting("source", source.id); home(true);
            }).show();
        });
        controls.addView(filters);
    }
    private void doSearch() {
        query = search.getText().toString().trim();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus(); home(true);
    }
    private void load(boolean reset) {
        if (loading && !reset) return;
        if (reset) { page = 1; items.clear(); sourcePages.clear(); errors.clear(); }
        List<Source> targets = new ArrayList<>();
        for (Source s : query.isEmpty() ? Collections.singletonList(source) : enabledSources()) {
            if (page == 1 || page <= sourcePages.getOrDefault(s.id, 0)) targets.add(s);
        }
        if (targets.isEmpty()) { render(); return; }
        loading = true; finished = 0; pending = batchSize = targets.size();
        final int token = ++generation, requested = page, cat = category;
        final String q = query; render();
        for (Source s : targets) network.execute(() -> {
            Api.Page result = null; Exception failure = null;
            try { result = Api.list(s, cat, q, requested); } catch (Exception e) { failure = e; }
            final Api.Page data = result; final Exception error = failure;
            runOnUiThread(() -> {
                if (token != generation || isDestroyed()) return;
                if (data != null) {
                    sourcePages.put(s.id, data.count); errors.remove(s.name);
                    Set<String> seen = new HashSet<>();
                    for (JSONObject o : items) seen.add(Store.key(o));
                    for (JSONObject o : data.items) if (seen.add(Store.key(o))) items.add(o);
                } else { errors.add(s.name); sourcePages.putIfAbsent(s.id, requested); }
                pending--; finished++; loading = pending > 0;
                ui.removeCallbacks(renderLater);
                if (!loading || items.size() == (data == null ? 0 : data.items.size())) render();
                else ui.postDelayed(renderLater, 180);
            });
        });
    }
    private String friendly(Exception e) {
        if (e instanceof java.net.SocketTimeoutException) return "来源连接超时";
        if (e instanceof java.net.UnknownHostException) return "暂时无法连接来源";
        if (e instanceof javax.net.ssl.SSLException) return "来源安全连接失败";
        return e.getMessage() == null ? "来源暂时不可用" : e.getMessage();
    }
    private void render() {
        int previousScroll = scroll.getScrollY();
        content.removeAllViews();
        List<List<JSONObject>> groups = Catalog.group(items);
        String caption = query.isEmpty() ? (category == 0 ? "日漫更新" : "国漫更新")
                : "找到 " + groups.size() + " 部 · 已查 " + finished + "/" + batchSize + " 个来源";
        if (loading) caption += " · 查找中…";
        content.addView(Ui.text(this, caption, 12, Ui.MUTED)); Ui.space(content, 10);
        for (List<JSONObject> variants : groups) card(variants.get(0), variants);
        if (!loading && groups.isEmpty()) {
            content.addView(Ui.text(this, "暂时没找到，试试简称或其他名字。", 15, Ui.MUTED));
            Ui.space(content, 16);
        }
        boolean hasMore = false;
        for (int count : sourcePages.values()) if (page < count) hasMore = true;
        Button more = Ui.button(this, loading ? "查找中…" : hasMore ? "加载更多" : "重新查找", false);
        more.setEnabled(!loading); final boolean nextPage = hasMore;
        more.setOnClickListener(v -> { if (nextPage) { page++; load(false); } else load(true); });
        content.addView(more, new LinearLayout.LayoutParams(-1, Ui.dp(this, 46)));
        if (!errors.isEmpty()) {
            Ui.space(content, 8);
            TextView error = Ui.text(this, "暂不可用：" + String.join("、", errors) + " · 点击重试", 12, Ui.MUTED);
            error.setOnClickListener(v -> load(true)); content.addView(error);
        }
        scroll.post(() -> scroll.scrollTo(0, previousScroll));
    }
    private void card(JSONObject item, List<JSONObject> variants) {
        Source s;
        try { s = Source.find(item.optString("_source")); } catch (Exception e) { return; }
        LinearLayout card = Ui.row(this); Ui.pad(card, 10); card.setBackground(Ui.bg(this, Ui.CARD, 14));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, Ui.dp(this, 9)); content.addView(card, cp);
        card.addView(Ui.poster(this, item.optString("vod_pic")), new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 92)));
        LinearLayout text = Ui.column(this); text.setPadding(Ui.dp(this, 12), 0, 0, 0);
        card.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = Ui.text(this, item.optString("vod_name"), 16, Ui.TEXT);
        title.setTypeface(null, 1); title.setMaxLines(2); text.addView(title); Ui.space(text, 5);
        text.addView(Ui.text(this, item.optString("vod_year") + " · " + item.optString("vod_remarks"), 12, Ui.MUTED));
        Ui.space(text, 5);
        text.addView(Ui.text(this, variants.size() > 1 ? variants.size() + " 个播放源 · " + s.name : "来源：" + s.name, 11, Ui.MINT));
        if (item.has("_episode")) text.addView(Ui.text(this, "上次：" + item.optString("_episode"), 12, Ui.ACCENT));
        card.setOnClickListener(v -> openDetail(item));
        if (tab == 1 || tab == 2) card.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle("移除这条记录？").setMessage(item.optString("vod_name"))
                .setPositiveButton("移除", (d, w) -> { store.remove(tab == 1 ? "favorites" : "history", item); shelf(); })
                .setNegativeButton("取消", null).show(); return true;
        });
    }
    private void shelf() {
        content.removeAllViews(); String name = tab == 1 ? "favorites" : "history";
        content.addView(Ui.text(this, tab == 1 ? "我的追番" : "观看记录", 21, Ui.TEXT)); Ui.space(content, 12);
        JSONArray list = store.entries(name);
        if (list.length() == 0) content.addView(Ui.text(this, tab == 1 ? "在动漫详情点“追番”，就能收藏到这里。" : "看过的动漫与播放进度会保存在本机。", 14, Ui.MUTED));
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i); if (o != null) card(o, Collections.singletonList(o));
        }
        if (tab == 2 && list.length() > 0) {
            Button clear = Ui.button(this, "清空记录与进度", false); content.addView(clear);
            clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("清空全部观看记录？")
                .setMessage("同时删除本机保存的播放进度。")
                .setPositiveButton("清空", (d, w) -> { store.clearHistory(); shelf(); }).setNegativeButton("取消", null).show());
        }
    }
    private void sources() {
        content.removeAllViews();
        content.addView(Ui.text(this, "播放来源", 22, Ui.TEXT)); Ui.space(content, 8);
        content.addView(Ui.text(this, "搜索会同时查找已开启的来源。抽样结果只代表已检查的视频片段。", 13, Ui.MUTED));
        Ui.space(content, 14);
        for (Source s : Source.ALL) {
            LinearLayout box = Ui.column(this); Ui.pad(box, 13); box.setBackground(Ui.bg(this, Ui.CARD, 14));
            Switch toggle = new Switch(this); toggle.setText(s.name); toggle.setTextColor(Ui.TEXT); toggle.setTextSize(17);
            toggle.setChecked(enabled(s)); box.addView(toggle);
            toggle.setOnCheckedChangeListener((button, checked) -> {
                if (!checked) {
                    boolean jp = false, cn = false;
                    for (Source other : enabledSources()) if (other != s) {
                        jp |= other.supportsCategory(0); cn |= other.supportsCategory(1);
                    }
                    if (!jp || !cn) {
                        button.setChecked(true); Toast.makeText(this, "日漫和国漫都需要保留可用来源", Toast.LENGTH_SHORT).show(); return;
                    }
                }
                store.setSetting("enabled:" + s.id, String.valueOf(checked)); dirty = true;
            });
            box.addView(Ui.text(this, s.review, 12, s.recommended ? Ui.MINT : Ui.MUTED));
            TextView website = Ui.text(this, "原站：" + s.website + " ↗", 12, Ui.MUTED);
            website.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4)); website.setOnClickListener(v -> Ui.open(this, s.website));
            box.addView(website); content.addView(box); Ui.space(content, 10);
        }
        content.addView(Ui.text(this, "应用免费，不含广告和追踪 SDK，不加载来源网页播放器。第三方视频仍可能自带广告；来源标注不代表内容授权。", 12, Ui.MUTED));
        Ui.space(content, 10); content.addView(Ui.text(this, "G 动漫 0.2 · 收藏与记录仅存本机", 12, Ui.MUTED));
    }
    private void openDetail(JSONObject brief) {
        detail = true; ui.removeCallbacks(renderLater); final int token = ++generation; shell();
        LinearLayout area = Ui.column(this); Ui.pad(area, 16); root.addView(area);
        Button back = Ui.button(this, "‹ 返回", false); area.addView(back); back.setOnClickListener(v -> home(false));
        area.addView(Ui.text(this, "正在获取选集…", 17, Ui.TEXT));
        network.execute(() -> {
            try {
                JSONObject item = Api.detail(Source.find(brief.optString("_source")), brief.optString("vod_id"));
                runOnUiThread(() -> { if (token == generation && !isDestroyed()) showDetail(item); });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (token != generation || isDestroyed()) return;
                    area.addView(Ui.text(this, "加载失败：" + friendly(e), 14, Ui.MUTED));
                    Button retry = Ui.button(this, "重试", true); area.addView(retry); retry.setOnClickListener(v -> openDetail(brief));
                    Button others = Ui.button(this, "搜索其他来源", false); area.addView(others);
                    others.setOnClickListener(v -> searchAlternatives(brief));
                });
            }
        });
    }
    private void searchAlternatives(JSONObject item) {
        query = item.optString("vod_name"); tab = 0; home(true);
    }
    private void alternatives(JSONObject item) {
        List<JSONObject> matches = new ArrayList<>();
        for (List<JSONObject> group : Catalog.group(items)) {
            if (Catalog.key(group.get(0)).equals(Catalog.key(item))) matches.addAll(group);
        }
        if (matches.size() < 2) { searchAlternatives(item); return; }
        String[] names = new String[matches.size()];
        for (int i = 0; i < names.length; i++) {
            JSONObject o = matches.get(i);
            names[i] = Source.find(o.optString("_source")).name + " · " + o.optString("vod_remarks");
        }
        new AlertDialog.Builder(this).setTitle("切换播放来源").setItems(names, (d, n) -> openDetail(matches.get(n))).show();
    }
    private void showDetail(JSONObject item) {
        detail = true; shell();
        ScrollView sv = new ScrollView(this); root.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout c = Ui.column(this); Ui.pad(c, 16); sv.addView(c);
        Button back = Ui.button(this, "‹ 返回", false); c.addView(back); back.setOnClickListener(v -> home(false)); Ui.space(c, 12);
        LinearLayout top = Ui.row(this);
        top.addView(Ui.poster(this, item.optString("vod_pic")), new LinearLayout.LayoutParams(Ui.dp(this, 90), Ui.dp(this, 126)));
        LinearLayout meta = Ui.column(this); meta.setPadding(Ui.dp(this, 14), 0, 0, 0);
        top.addView(meta, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = Ui.text(this, item.optString("vod_name"), 21, Ui.TEXT); title.setTypeface(null, 1); meta.addView(title);
        Ui.space(meta, 8); meta.addView(Ui.text(this, item.optString("type_name") + " · " + item.optString("vod_year") + "\n" + item.optString("vod_remarks"), 13, Ui.MUTED));
        c.addView(top); Ui.space(c, 12);
        Source s = Source.find(item.optString("_source"));
        TextView attribution = Ui.text(this, "来源：" + s.name + " · " + s.website + " ↗", 12, Ui.MUTED);
        attribution.setOnClickListener(v -> Ui.open(this, s.website)); c.addView(attribution);
        LinearLayout actions = Ui.row(this);
        Button favorite = Ui.button(this, store.contains("favorites", item) ? "已追番 ♥" : "追番 ♡", false);
        Ui.weighted(actions, favorite);
        favorite.setOnClickListener(v -> {
            if (store.contains("favorites", item)) store.remove("favorites", item); else store.put("favorites", item);
            favorite.setText(store.contains("favorites", item) ? "已追番 ♥" : "追番 ♡");
        });
        Button other = Ui.button(this, "切换播放源", false); Ui.weighted(actions, other); other.setOnClickListener(v -> alternatives(item));
        c.addView(actions); Ui.space(c, 10);
        List<Playback.Route> routes = Playback.parse(item.optString("vod_play_from"), item.optString("vod_play_url"));
        if (routes.isEmpty()) c.addView(Ui.text(this, "这个来源没有可直接播放的地址，请切换播放源。", 14, Ui.MUTED));
        String lastRoute = "", lastEpisode = "";
        JSONArray history = store.entries("history");
        for (int i = 0; i < history.length(); i++) {
            JSONObject o = history.optJSONObject(i);
            if (o != null && Store.key(o).equals(Store.key(item))) { lastRoute = o.optString("_route"); lastEpisode = o.optString("_episode"); break; }
        }
        if (!routes.isEmpty()) {
            int selected = 0;
            for (int i = 0; i < routes.size(); i++) if (routes.get(i).name.equals(lastRoute)) selected = i;
            final int[] routeIndex = {selected}; final String previousEpisode = lastEpisode;
            Button play = Ui.button(this, lastEpisode.isEmpty() ? "开始观看 ▶" : "继续观看 · " + lastEpisode, true);
            c.addView(play, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
            play.setOnClickListener(v -> {
                Playback.Route r = routes.get(routeIndex[0]); int ep = 0;
                for (int i = 0; i < r.episodes.size(); i++) if (r.episodes.get(i).title.equals(previousEpisode)) ep = i;
                launch(item, r, r.episodes.get(ep));
            });
            Ui.space(c, 10); LinearLayout episodes = Ui.column(this);
            if (routes.size() > 1) {
                Button route = Ui.button(this, "播放线路 ▾", false); c.addView(route);
                route.setOnClickListener(v -> {
                    String[] labels = new String[routes.size()]; for (int i = 0; i < labels.length; i++) labels[i] = "线路 " + (i + 1) + " · " + routes.get(i).episodes.size() + " 集";
                    new AlertDialog.Builder(this).setTitle("选择线路").setItems(labels, (d, n) -> { routeIndex[0] = n; episodeButtons(episodes, item, routes.get(n)); }).show();
                });
            }
            c.addView(episodes); episodeButtons(episodes, item, routes.get(selected));
        }
        Ui.space(c, 16);
        String desc = Html.fromHtml(item.optString("vod_content"), Html.FROM_HTML_MODE_LEGACY).toString().trim();
        if (!desc.isEmpty()) c.addView(Ui.text(this, desc, 14, Ui.MUTED));
    }
    private void episodeButtons(LinearLayout box, JSONObject item, Playback.Route route) {
        box.removeAllViews();
        // Long-running shows use ranges so opening a title does not create thousands of views.
        int total = route.episodes.size();
        if (total > 90) {
            String[] ranges = new String[(total + 59) / 60];
            for (int i = 0; i < ranges.length; i++) ranges[i] = (i * 60 + 1) + "–" + Math.min(total, (i + 1) * 60);
            Button choose = Ui.button(this, "选集范围：" + ranges[0] + " ▾", false); box.addView(choose);
            LinearLayout grid = Ui.column(this); box.addView(grid); episodeRange(grid, item, route, 0, Math.min(60, total));
            choose.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("跳转集数范围").setItems(ranges, (d, n) -> {
                choose.setText("选集范围：" + ranges[n] + " ▾"); episodeRange(grid, item, route, n * 60, Math.min(total, (n + 1) * 60));
            }).show());
        } else episodeRange(box, item, route, 0, total);
    }
    private void episodeRange(LinearLayout box, JSONObject item, Playback.Route route, int start, int end) {
        box.removeAllViews(); LinearLayout row = null;
        for (int i = start; i < end; i++) {
            if ((i - start) % 3 == 0) { row = Ui.row(this); box.addView(row); }
            Playback.Episode ep = route.episodes.get(i); Button b = Ui.button(this, ep.title, false); b.setTextSize(12);
            Ui.weighted(row, b); b.setOnClickListener(v -> launch(item, route, ep));
        }
        if (row != null) for (int i = (end - start) % 3; i > 0 && i < 3; i++) Ui.weighted(row, new View(this));
    }
    private void launch(JSONObject item, Playback.Route route, Playback.Episode ep) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("source", item.optString("_source")); i.putExtra("id", item.optString("vod_id"));
        i.putExtra("route", route.name); i.putExtra("episode", ep.title); i.putExtra("title", item.optString("vod_name"));
        startActivity(i);
    }
    @Override public void onBackPressed() {
        if (detail) { home(dirty); dirty = false; return; }
        if (tab != 0) { tab = 0; home(dirty); dirty = false; return; }
        super.onBackPressed();
    }
}
