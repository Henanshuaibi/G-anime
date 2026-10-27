package cn.g.anime;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class PlayerActivity extends Activity {
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler();
    private Store store;
    private Source source;
    private JSONObject item;
    private List<Playback.Route> routes;
    private int routeIndex, episodeIndex, playToken, fetchToken, resumeTarget, pendingPosition = -1;
    private TrackingVideoView video;
    private TextView title, status;
    private LinearLayout root, info;
    private FrameLayout frame;
    private Button previous, next, retry, speedButton, fullButton;
    private String key = "";
    private boolean ready, seeking, active, fetching, failed, focusGranted, resumeAfterFocus;
    private boolean wantsPlay = true;
    private float speed = 1f, appliedSpeed = 1f;
    private MediaPlayer media;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private Future<?> request;
    private final Runnable ticker = new Runnable() {
        public void run() { if (active) { save(); handler.postDelayed(this, 4000); } }
    };
    private final Runnable timeout = () -> {
        if (active && !ready && !isFinishing()) error("视频连接超时，可以重新连接或换源。");
    };
    private final Runnable seekTimeout = () -> {
        if (active && seeking) error("恢复进度超时，可以重新连接视频。");
    };
    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            resumeAfterFocus = false;
            if (video != null && ready) video.pause();
        }
    };
    private boolean receiverRegistered;

    private final class TrackingVideoView extends VideoView {
        TrackingVideoView(Context context) { super(context); }
        @Override public void start() {
            wantsPlay = true;
            if (!active || !ready || seeking || failed || !PlayerActivity.this.requestFocus()) return;
            applySpeed(); super.start();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        @Override public void pause() {
            wantsPlay = false; super.pause();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new Store(this);
        audio = (AudioManager)getSystemService(AUDIO_SERVICE);
        try { source = Source.find(getIntent().getStringExtra("source")); }
        catch (Exception e) { finish(); return; }
        try { speed = Float.parseFloat(store.setting("speed", "1.0")); }
        catch (Exception ignored) { speed = 1f; }
        if (speed != .75f && speed != 1f && speed != 1.25f && speed != 1.5f && speed != 2f) speed = 1f;
        if (state != null) {
            pendingPosition = state.getInt("position", 0); wantsPlay = state.getBoolean("wantsPlay", true);
            getIntent().putExtra("route", state.getString("route"));
            getIntent().putExtra("episode", state.getString("episode"));
        }
        build(); fetch();
    }
    private void build() {
        root = Ui.column(this); root.setBackgroundColor(Ui.BG); setContentView(root); Ui.inset(this, root);
        LinearLayout bar = Ui.row(this); Ui.pad(bar, 6);
        Button back = Ui.button(this, "‹", false);
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 44)));
        back.setOnClickListener(v -> onBackPressed());
        title = Ui.text(this, "正在获取选集…", 14, Ui.TEXT); title.setMaxLines(2);
        title.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 6), 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        speedButton = Ui.button(this, speedLabel(), false); speedButton.setTextSize(12);
        bar.addView(speedButton, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 44)));
        speedButton.setOnClickListener(v -> chooseSpeed());
        fullButton = Ui.button(this, "全屏", false); fullButton.setTextSize(12);
        bar.addView(fullButton, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 44)));
        fullButton.setOnClickListener(v -> toggleFull()); root.addView(bar);
        frame = new FrameLayout(this); frame.setBackgroundColor(0xff000000);
        root.addView(frame, new LinearLayout.LayoutParams(-1, Ui.dp(this, 230)));
        video = new TrackingVideoView(this);
        frame.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        MediaController controller = new MediaController(this); controller.setAnchorView(frame); video.setMediaController(controller);
        ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        info = Ui.column(this); Ui.pad(info, 16); scroll.addView(info);
        status = Ui.text(this, "正在连接 " + source.name + "…", 14, Ui.MUTED); info.addView(status); Ui.space(info, 10);
        LinearLayout actions = Ui.row(this);
        previous = Ui.button(this, "上一集", false); Ui.weighted(actions, previous);
        Button choose = Ui.button(this, "选集", false); Ui.weighted(actions, choose);
        next = Ui.button(this, "下一集", true); Ui.weighted(actions, next); info.addView(actions);
        previous.setOnClickListener(v -> change(-1)); next.setOnClickListener(v -> change(1)); choose.setOnClickListener(v -> choose());
        LinearLayout recovery = Ui.row(this);
        retry = Ui.button(this, "重新连接", false); Ui.weighted(recovery, retry);
        retry.setOnClickListener(v -> { save(); pendingPosition = store.position(key); wantsPlay = true; stopVideo(); fetch(); });
        Button switchSource = Ui.button(this, "换播放源", false); Ui.weighted(recovery, switchSource);
        switchSource.setOnClickListener(v -> {
            save(); Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("search", item == null ? getIntent().getStringExtra("title") : item.optString("vod_name"));
            intent.putExtra("desired_episode", getIntent().getStringExtra("episode"));
            if (item != null) intent.putExtra("desired_catalog", Catalog.key(item));
            startActivity(intent); finish();
        });
        info.addView(recovery); Ui.space(info, 16);
        info.addView(Ui.text(this, "资源来源：" + source.name + "\n" + source.review + "\n\n抽查不能保证每部、每集都没有广告。", 12, Ui.MUTED));
        Button origin = Ui.button(this, "查看来源网站 ↗", false); info.addView(origin);
        origin.setOnClickListener(v -> Ui.open(this, source.website)); layout();
    }
    private void fetch() {
        if (fetching) return;
        fetching = true; failed = false; final int token = ++fetchToken;
        status.setText("正在从 " + source.name + " 更新播放地址…");
        previous.setEnabled(false); next.setEnabled(false); retry.setEnabled(false);
        if (request != null) request.cancel(true);
        request = network.submit(() -> {
            try {
                JSONObject data = Api.detail(source, getIntent().getStringExtra("id"));
                List<Playback.Route> parsed = Playback.parse(data.optString("vod_play_from"), data.optString("vod_play_url"));
                if (parsed.isEmpty()) throw new Exception("没有可直接播放的视频地址");
                runOnUiThread(() -> {
                    if (token != fetchToken || isDestroyed()) return;
                    fetching = false; retry.setEnabled(true); item = data; routes = parsed;
                    String route = getIntent().getStringExtra("route"), episode = getIntent().getStringExtra("episode");
                    routeIndex = 0;
                    for (int i = 0; i < routes.size(); i++) if (routes.get(i).name.equals(route)) routeIndex = i;
                    episodeIndex = Math.max(0, Playback.findEpisode(routes.get(routeIndex).episodes, episode));
                    if (active) play();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (token != fetchToken || isDestroyed()) return;
                    fetching = false; retry.setEnabled(true);
                    error("更新播放地址失败，请重试或换源。\n" + (e.getMessage() == null ? "来源暂不可用" : e.getMessage()));
                });
            }
        });
    }
    private void stopVideo() {
        playToken++; handler.removeCallbacks(timeout); handler.removeCallbacks(seekTimeout);
        ready = false; seeking = false; media = null;
        if (video != null) video.stopPlayback();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void play() {
        if (!active || item == null) return;
        stopVideo(); failed = false; appliedSpeed = 1f;
        final int token = playToken;
        Playback.Route route = routes.get(routeIndex); Playback.Episode episode = route.episodes.get(episodeIndex);
        key = Playback.progressKey(source.id, item.optString("vod_id"), route.name, episode.title);
        final int saved = pendingPosition >= 0 ? pendingPosition : store.position(key); pendingPosition = -1; resumeTarget = saved;
        title.setText(item.optString("vod_name") + " · " + episode.title);
        status.setText("正在连接视频…\n来源：" + source.name);
        previous.setEnabled(episodeIndex > 0); next.setEnabled(episodeIndex + 1 < route.episodes.size());
        getIntent().putExtra("route", route.name); getIntent().putExtra("episode", episode.title);
        video.setOnPreparedListener(mp -> {
            if (token != playToken || !active) return;
            handler.removeCallbacks(timeout); media = mp; ready = true;
            try { item.put("_episode", episode.title); item.put("_route", route.name); } catch (Exception ignored) { }
            store.put("history", item);
            int position = Playback.resumePosition(saved, video.getDuration()); resumeTarget = position;
            if (position > 0) {
                seeking = true; status.setText("正在恢复播放进度… · " + source.name);
                mp.setOnSeekCompleteListener(player -> {
                    if (token != playToken || player != media || !seeking) return;
                    seeking = false; handler.removeCallbacks(seekTimeout); startIfWanted();
                });
                video.seekTo(position); handler.postDelayed(seekTimeout, 8000);
            } else startIfWanted();
        });
        video.setOnInfoListener((mp, what, extra) -> {
            if (token != playToken) return false;
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) status.setText("视频缓冲中… · " + source.name);
            else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) status.setText("来源：" + source.name);
            return false;
        });
        video.setOnErrorListener((mp, what, extra) -> {
            if (token == playToken) { error("播放失败（" + what + " / " + extra + "）\n可重新获取地址，或换一个播放源。"); }
            return true;
        });
        video.setOnCompletionListener(mp -> {
            if (token != playToken) return;
            store.position(key, 0); resumeTarget = 0; pendingPosition = -1;
            if (episodeIndex + 1 < route.episodes.size()) { episodeIndex++; wantsPlay = true; play(); }
            else { wantsPlay = false; stopVideo(); status.setText("本线路已播放完毕 · " + source.name); }
        });
        video.setVideoURI(Uri.parse(episode.url)); handler.postDelayed(timeout, 30000);
    }
    private void startIfWanted() {
        status.setText("来源：" + source.name + "\n点击画面显示播放控制，退出后自动保存进度。");
        if (active && ready && !seeking && wantsPlay) video.start();
    }
    private boolean requestFocus() {
        if (focusGranted) return true;
        if (focus == null) focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
            .setOnAudioFocusChangeListener(change -> {
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    focusGranted = false;
                    resumeAfterFocus = change != AudioManager.AUDIOFOCUS_LOSS && ready && video.isPlaying();
                    if (ready) video.pause();
                } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                    focusGranted = true;
                    if (resumeAfterFocus && active && ready && !seeking) { resumeAfterFocus = false; video.start(); }
                }
            }).build();
        focusGranted = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!focusGranted) status.setText("音频正被其他应用使用，稍后点击播放。\n来源：" + source.name);
        return focusGranted;
    }
    private String speedLabel() { return String.format(Locale.ROOT, "%s×", new java.math.BigDecimal(String.valueOf(speed)).stripTrailingZeros().toPlainString()); }
    private void chooseSpeed() {
        final float[] values = {.75f, 1f, 1.25f, 1.5f, 2f}; final String[] labels = {"0.75×", "1×", "1.25×", "1.5×", "2×"};
        int selected = 1; for (int i = 0; i < values.length; i++) if (speed == values[i]) selected = i;
        new AlertDialog.Builder(this).setTitle("播放速度").setSingleChoiceItems(labels, selected, (dialog, which) -> {
            speed = values[which]; store.setSetting("speed", String.valueOf(speed)); speedButton.setText(speedLabel());
            if (ready && !seeking && video.isPlaying()) applySpeed(); dialog.dismiss();
        }).show();
    }
    private void applySpeed() {
        if (media == null || speed == appliedSpeed) return;
        try { media.setPlaybackParams(media.getPlaybackParams().setSpeed(speed)); appliedSpeed = speed; }
        catch (Exception e) {
            speed = appliedSpeed; store.setSetting("speed", String.valueOf(speed)); speedButton.setText(speedLabel());
            Toast.makeText(this, "当前视频暂不支持这个播放速度", Toast.LENGTH_SHORT).show();
        }
    }
    private void change(int delta) {
        if (routes == null) return;
        int target = episodeIndex + delta;
        if (target < 0 || target >= routes.get(routeIndex).episodes.size()) return;
        save(); episodeIndex = target; pendingPosition = -1; wantsPlay = true; play();
    }
    private void choose() {
        if (routes == null) return;
        if (routes.size() == 1) { chooseRange(0); return; }
        String[] labels = new String[routes.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = "线路 " + (i + 1) + " · " + routes.get(i).episodes.size() + " 集";
        new AlertDialog.Builder(this).setTitle("播放线路").setItems(labels, (d, n) -> chooseRange(n)).show();
    }
    private void chooseRange(int route) {
        int count = routes.get(route).episodes.size();
        if (count <= 90) { chooseEpisodes(route, 0, count); return; }
        String[] ranges = new String[(count + 59) / 60];
        for (int i = 0; i < ranges.length; i++) ranges[i] = (i * 60 + 1) + "–" + Math.min(count, (i + 1) * 60);
        new AlertDialog.Builder(this).setTitle("选集范围").setItems(ranges, (d, n) -> chooseEpisodes(route, n * 60, Math.min(count, (n + 1) * 60))).show();
    }
    private void chooseEpisodes(int route, int start, int end) {
        Playback.Route selected = routes.get(route); String[] labels = new String[end - start];
        for (int i = start; i < end; i++) labels[i - start] = selected.episodes.get(i).title;
        new AlertDialog.Builder(this).setTitle("选集").setSingleChoiceItems(labels, route == routeIndex && episodeIndex >= start && episodeIndex < end ? episodeIndex - start : -1, (dialog, index) -> {
            save(); routeIndex = route; episodeIndex = start + index; pendingPosition = -1; wantsPlay = true; dialog.dismiss(); play();
        }).show();
    }
    private void error(String message) { failed = true; stopVideo(); status.setText(message + "\n资源来源：" + source.name); }
    private void save() { if (ready && !seeking && !failed && !key.isEmpty()) { resumeTarget = video.getCurrentPosition(); store.position(key, resumeTarget); } }
    private void toggleFull() {
        setRequestedOrientation(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
    private void layout() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)frame.getLayoutParams();
        params.height = landscape ? 0 : Ui.dp(this, 230); params.weight = landscape ? 1 : 0; frame.setLayoutParams(params);
        ((View)info.getParent()).setVisibility(landscape ? View.GONE : View.VISIBLE); fullButton.setText(landscape ? "竖屏" : "全屏");
        getWindow().getDecorView().setSystemUiVisibility(landscape ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION : View.SYSTEM_UI_FLAG_VISIBLE);
    }
    @Override public void onConfigurationChanged(Configuration configuration) { super.onConfigurationChanged(configuration); layout(); }
    @Override protected void onResume() {
        super.onResume(); if (video == null) return; active = true; handler.post(ticker);
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, 4); else registerReceiver(noisy, filter);
        receiverRegistered = true;
        if (item != null && !fetching && !failed) play();
    }
    @Override protected void onPause() {
        active = false;
        if (video != null) {
            save(); if (ready && !seeking) { wantsPlay = video.isPlaying(); pendingPosition = video.getCurrentPosition(); }
            else if (!key.isEmpty()) pendingPosition = resumeTarget;
            stopVideo();
        }
        handler.removeCallbacks(ticker);
        if (receiverRegistered) { unregisterReceiver(noisy); receiverRegistered = false; }
        if (focus != null) audio.abandonAudioFocusRequest(focus);
        focusGranted = false; resumeAfterFocus = false; super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("position", ready && !seeking ? video.getCurrentPosition() : pendingPosition >= 0 ? pendingPosition : resumeTarget); state.putBoolean("wantsPlay", wantsPlay);
        state.putString("route", getIntent().getStringExtra("route")); state.putString("episode", getIntent().getStringExtra("episode")); super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        fetchToken++; handler.removeCallbacksAndMessages(null); if (video != null) stopVideo();
        if (request != null) request.cancel(true); network.shutdownNow(); super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) { toggleFull(); return; }
        super.onBackPressed();
    }
}
