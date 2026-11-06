import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebSettings;

class AndroidTeste extends android.app.Activity {
    String token;

    void logs(String accessToken, String nome) {
        // ruleid: sentinel-android-log-segredo
        Log.d("accesstoken", accessToken);
        // ruleid: sentinel-android-log-segredo
        Log.e("login", "valor: " + accessToken);
        // ok: sentinel-android-log-segredo
        Log.d("login", "usuário autenticado");
        // ok: sentinel-android-log-segredo
        Log.d("token", nome);
    }

    void web(WebView view) {
        String destino = getIntent().getData().getQueryParameter("url");
        // ruleid: sentinel-android-webview-url-do-intent
        view.loadUrl(destino);
        // ok: sentinel-android-webview-url-do-intent
        view.loadUrl("https://www.exemplo.com/");
        String extra = getIntent().getStringExtra("pagina");
        // ruleid: sentinel-android-webview-url-do-intent
        view.loadUrl(extra);
        WebSettings ajustes = view.getSettings();
        // ruleid: sentinel-android-webview-javascript
        ajustes.setJavaScriptEnabled(true);
        // ok: sentinel-android-webview-javascript
        ajustes.setJavaScriptEnabled(false);
    }

    void armazenamento(android.content.Context ctx) throws Exception {
        // ruleid: sentinel-android-modo-mundo
        ctx.getSharedPreferences("prefs", Context.MODE_WORLD_READABLE);
        // ruleid: sentinel-android-modo-mundo
        ctx.openFileOutput("a.txt", Context.MODE_WORLD_WRITEABLE);
        // ok: sentinel-android-modo-mundo
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        // ok: sentinel-android-modo-mundo
        ctx.getSharedPreferences("prefs", 0);
    }
}
