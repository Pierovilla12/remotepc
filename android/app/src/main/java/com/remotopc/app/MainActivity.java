package com.remotopc.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * RemotoPC para Android: abre la misma página que el navegador, pero a pantalla
 * completa y sin Chrome alrededor, y le da a la página cosas que el navegador no
 * deja: girar la pantalla, mantenerla encendida, vibrar y el botón Atrás.
 * La página se carga de GitHub, así que se actualiza sola (sin reinstalar).
 */
public class MainActivity extends Activity {

    static final String PAGINA = "https://pierovilla12.github.io/remotepc/";
    static final int ELEGIR_ARCHIVO = 7;

    WebView web;
    ValueCallback<Uri[]> alElegir;
    boolean completa = false;

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0b0e13"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false); // el sonido de la PC suena sin tocar nada
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100); // que el tamaño de letra del sistema no desarme la página
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(s.getUserAgentString() + " RemotoPCAndroid/" + version());

        web.addJavascriptInterface(new Puente(), "RemotoAndroid");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String host = u.getHost() == null ? "" : u.getHost();
                // La página de RemotoPC y las PCs de la casa se abren aquí; lo demás, en el navegador.
                if (host.equals("pierovilla12.github.io") || host.startsWith("192.168.") || host.startsWith("10.")
                        || host.equals("localhost") || host.startsWith("127.")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException e) {
                    // nada que hacer
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    v.loadDataWithBaseURL(null,
                            "<html><body style='margin:0;height:100vh;display:grid;place-items:center;background:#0b0e13;color:#e8ecf2;font-family:sans-serif;text-align:center'>"
                                    + "<div><h2>Sin conexión</h2><p style='color:#8f9bad'>Revisa el WiFi o los datos y toca para reintentar.</p>"
                                    + "<button onclick=\"location.href='" + PAGINA + "'\" style='font-size:17px;padding:12px 26px;border:0;border-radius:12px;background:#4c8dff;color:#fff'>Reintentar</button></div></body></html>",
                            "text/html", "utf-8", null);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (alElegir != null) alElegir.onReceiveValue(null);
                alElegir = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(Intent.createChooser(i, "Enviar a la PC"), ELEGIR_ARCHIVO);
                } catch (ActivityNotFoundException e) {
                    alElegir = null;
                    return false;
                }
                return true;
            }
        });

        if (guardado != null) {
            web.restoreState(guardado);
        } else {
            web.loadUrl(PAGINA + "?app=android");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        web.saveState(b);
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent datos) {
        super.onActivityResult(pedido, resultado, datos);
        if (pedido != ELEGIR_ARCHIVO || alElegir == null) return;
        Uri[] r = null;
        if (resultado == RESULT_OK && datos != null) {
            if (datos.getClipData() != null) {
                int n = datos.getClipData().getItemCount();
                r = new Uri[n];
                for (int k = 0; k < n; k++) r[k] = datos.getClipData().getItemAt(k).getUri();
            } else if (datos.getData() != null) {
                r = new Uri[]{datos.getData()};
            }
        }
        alElegir.onReceiveValue(r);
        alElegir = null;
    }

    // Atrás: primero le pregunta a la página (cierra menús, abre la burbuja…).
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.alAtrasAndroid && window.alAtrasAndroid()) ? 1 : 0", valor -> {
            if ("1".equals(valor)) return;
            if (web.canGoBack()) web.goBack();
            else moveTaskToBack(true);
        });
    }

    @Override
    public void onWindowFocusChanged(boolean foco) {
        super.onWindowFocusChanged(foco);
        if (foco && completa) aplicarCompleta(true); // al volver, Android muestra las barras otra vez
    }

    void aplicarCompleta(boolean si) {
        completa = si;
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(!si);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                if (si) {
                    c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    c.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            }
        } else {
            View d = w.getDecorView();
            d.setSystemUiVisibility(si
                    ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    : View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    String version() {
        try {
            PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
            return p.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Lo que la página puede pedirle al celular (window.RemotoAndroid). */
    class Puente {
        @JavascriptInterface
        public String version() {
            return MainActivity.this.version();
        }

        @JavascriptInterface
        public void pantallaCompleta(boolean si) {
            runOnUiThread(() -> aplicarCompleta(si));
        }

        // "horizontal", "vertical" o "libre"
        @JavascriptInterface
        public void orientacion(String o) {
            runOnUiThread(() -> setRequestedOrientation(
                    "horizontal".equals(o) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            : "vertical".equals(o) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
        }

        @JavascriptInterface
        public void mantenerEncendida(boolean si) {
            runOnUiThread(() -> {
                if (si) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void vibrar(int ms) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(Math.max(1, Math.min(ms, 500)), VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(Math.max(1, Math.min(ms, 500)));
        }
    }
}
