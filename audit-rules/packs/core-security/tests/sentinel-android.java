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

    ParcelFileDescriptor openFile(Uri uri, String modo) throws java.io.FileNotFoundException {
        // ruleid: sentinel-android-provider-openfile-caminho
        File arquivo = new File(Environment.getExternalStorageDirectory(), uri.getLastPathSegment());
        return ParcelFileDescriptor.open(arquivo, ParcelFileDescriptor.MODE_READ_WRITE);
    }

    static class Seguro {
        ParcelFileDescriptor openFile(Uri uri, String modo) throws Exception {
            File base = new File("/data/app/pub");
            // ok: sentinel-android-provider-openfile-caminho
            File arquivo = new File(base, uri.getLastPathSegment());
            if (!arquivo.getCanonicalPath().startsWith(base.getCanonicalPath())) {
                throw new SecurityException();
            }
            return ParcelFileDescriptor.open(arquivo, ParcelFileDescriptor.MODE_READ_ONLY);
        }
    }

    void carregaCodigo(android.content.Context ctx) {
        File apk = new File("/sdcard/updater.apk");
        // ruleid: sentinel-android-classloader-armazenamento-externo
        new DexClassLoader(apk.getAbsolutePath(), ctx.getCacheDir().getAbsolutePath(), null, ctx.getClassLoader());
    }

    void carregaCodigoInterno(android.content.Context ctx) {
        File apk = new File(ctx.getFilesDir(), "plugin.apk");
        // ok: sentinel-android-classloader-armazenamento-externo
        new DexClassLoader(apk.getAbsolutePath(), ctx.getCacheDir().getAbsolutePath(), null, ctx.getClassLoader());
    }
}
