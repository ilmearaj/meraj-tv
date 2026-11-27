package app.ilmearaj.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * معراج تي في: شاشة كاملة تعرض الصفحة المرفقة في assets/index.html.
 * تُخدَم من نطاق https وهمي (appassets.androidplatform.net) عشان يوتيوب والصوت
 * وملفات الصور والنصوص تشتغل بنفس طريقة الموقع العادي.
 */
public class MainActivity extends Activity {

    /** النسخة الداخلية (تشتغل بدون نت). */
    private static final String START_URL = "https://appassets.androidplatform.net/assets/index.html";
    /** غير مستخدمة: الصفحة مدمجة داخل التطبيق. */
    private static final String REMOTE_URL = "https://classy-dasik-338077.netlify.app/";
    private boolean fellBack = false;

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(0xFF04170F);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        WebView.setWebContentsDebuggingEnabled(true);
        setContentView(web);
        hideBars();

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.setWebViewClient(new WebViewClient() {
            private void fallback(WebView view) {
                if (fellBack) return;
                fellBack = true;
                view.loadUrl(START_URL);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) fallback(view);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse resp) {
                if (request.isForMainFrame() && resp.getStatusCode() >= 400) fallback(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.requestFocus(View.FOCUS_DOWN);
                view.evaluateJavascript("window.focus();", null);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.requestFocus();

        if (savedInstanceState == null) {
            web.loadUrl(START_URL);
        } else {
            web.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.requestFocus(View.FOCUS_DOWN);
    }

    /** زر الرجوع: تغلق الصفحة أعلى شيء مفتوح، وما نخرج من الهوم أبداً. */
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.__tvBack && window.__tvBack();", null);
    }

    /** زر القائمة/الإعدادات بالريموت يفتح إعدادات الصفحة. */
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_MENU || keyCode == android.view.KeyEvent.KEYCODE_SETTINGS) {
            web.evaluateJavascript("window.__tvMenu && window.__tvMenu();", null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) { hideBars(); web.requestFocus(View.FOCUS_DOWN); }
    }

    @SuppressWarnings("deprecation")
    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /** الجسر اللي تستخدمه الصفحة (window.Android) لعرض التطبيقات وفتحها. */
    private class Bridge {

        @JavascriptInterface
        public String apps() {
            try {
                PackageManager pm = getPackageManager();
                List<ResolveInfo> found = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
                for (String cat : cats) {
                    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                    for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                        String pkg = ri.activityInfo.packageName;
                        if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                        found.add(ri);
                    }
                }
                final PackageManager fpm = pm;
                Collections.sort(found, new Comparator<ResolveInfo>() {
                    @Override
                    public int compare(ResolveInfo a, ResolveInfo b) {
                        return a.loadLabel(fpm).toString().compareToIgnoreCase(b.loadLabel(fpm).toString());
                    }
                });
                JSONArray out = new JSONArray();
                for (ResolveInfo ri : found) {
                    JSONObject o = new JSONObject();
                    o.put("p", ri.activityInfo.packageName);
                    o.put("l", ri.loadLabel(pm).toString());
                    o.put("i", iconBase64(ri.loadIcon(pm)));
                    out.put(o);
                }
                return out.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        /** البنر الرسمي للتطبيق (نفس اللي يعرضه قوقل تي في)، أو فاضي إذا ما له. */
        @JavascriptInterface
        public String banner(String pkg) {
            try {
                PackageManager pm = getPackageManager();
                Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (i == null) i = pm.getLaunchIntentForPackage(pkg);
                Drawable d = null;
                if (i != null) d = pm.getActivityBanner(i);
                if (d == null) d = pm.getApplicationBanner(pkg);
                if (d == null) return "";
                Bitmap bm = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bm);
                d.setBounds(0, 0, 480, 270);
                d.draw(c);
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.JPEG, 88, o);
                return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }

        /** أيقونة التطبيق الأصلية (PNG) بحجم 160. */
        @JavascriptInterface
        public String icon(String pkg) {
            try {
                Drawable d = getPackageManager().getApplicationIcon(pkg);
                Bitmap bm = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bm);
                d.setBounds(0, 0, 160, 160);
                d.draw(c);
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.PNG, 100, o);
                return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean installed(String pkg) {
            try {
                getPackageManager().getPackageInfo(pkg, 0);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /** مستوى صوت التلفزيون الحالي (٠–١٠٠). */
        @JavascriptInterface
        public int getVol() {
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                return max <= 0 ? 0 : Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max);
            } catch (Exception e) {
                return 0;
            }
        }

        /** يضبط صوت التلفزيون (٠–١٠٠) — يُستعمل للأذان بمستوى مستقل. */
        @JavascriptInterface
        public void setVol(final int pct) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int p = Math.max(0, Math.min(100, pct));
                        int idx = Math.round(max * p / 100f);
                        if (p > 0 && idx < 1) idx = 1;
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, idx, 0);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(android.provider.Settings.ACTION_SETTINGS);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public void launch(final String pkg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        PackageManager pm = getPackageManager();
                        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
                        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
                        if (i != null) {
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(i);
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
        }
    }

    private static String iconBase64(Drawable d) {
        int size = 96;
        Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        d.setBounds(0, 0, size, size);
        d.draw(c);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, o);
        return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
    }
}
