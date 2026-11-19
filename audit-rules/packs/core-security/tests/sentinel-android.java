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
        // ruleid: sentinel-android-modo-mundo
        getSharedPreferences("prefs", MODE_WORLD_READABLE);
        // ruleid: sentinel-android-modo-mundo
        ctx.openFileOutput("b.txt", MODE_WORLD_WRITEABLE);
        // ruleid: sentinel-android-modo-mundo
        ctx.openOrCreateDatabase("d.db", Context.MODE_WORLD_READABLE, null);
        // ruleid: sentinel-android-modo-mundo
        openOrCreateDatabase("d.db", MODE_WORLD_WRITEABLE, null);
        // ruleid: sentinel-android-modo-mundo
        ctx.openFileOutput("lockfile", ctx.MODE_WORLD_WRITEABLE);
        // ruleid: sentinel-android-modo-mundo
        ctx.getSharedPreferences("prefs", this.MODE_WORLD_READABLE);
        // ok: sentinel-android-modo-mundo
        getSharedPreferences("prefs", MODE_PRIVATE);
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

    static final String TABELA = "notas";
    static final String COL_ID = "_id";

    void consultas(android.database.sqlite.SQLiteDatabase db, android.widget.EditText busca, String id) {
        // ruleid: sentinel-android-sqlite-concatenado
        db.rawQuery("SELECT * FROM u WHERE user = '" + busca.getText().toString() + "'", null);
        // ruleid: sentinel-android-sqlite-concatenado
        db.execSQL("INSERT INTO u VALUES ('" + busca.getText().toString() + "', 'x');");
        // ruleid: sentinel-android-sqlite-concatenado
        db.rawQuery("SELECT * FROM n WHERE " + COL_ID + " = " + id, null);
        // ok: sentinel-android-sqlite-concatenado
        db.rawQuery("SELECT * FROM u WHERE user = ?", new String[] { busca.getText().toString() });
        // ok: sentinel-android-sqlite-concatenado
        db.execSQL("INSERT INTO " + TABELA + "(" + COL_ID + ") VALUES (1)");
        // ok: sentinel-android-sqlite-concatenado
        db.execSQL("CREATE TABLE IF NOT EXISTS " + "u(user VARCHAR);");
    }

    void consultaEmVariavel(android.database.sqlite.SQLiteDatabase db, String fname, String id) {
        String q1 = "Select * FROM contacts WHERE fname = " + "\"" + fname + "\"";
        // ruleid: sentinel-android-sqlite-concatenado
        db.rawQuery(q1, null);
        String q2 = "DELETE FROM contacts WHERE _id = " + id;
        // ruleid: sentinel-android-sqlite-concatenado
        db.execSQL(q2);
        String q3 = "SELECT * FROM " + TABELA + " WHERE " + COL_ID + " = 1";
        // ok: sentinel-android-sqlite-concatenado
        db.rawQuery(q3, null);
        String q4 = "SELECT * FROM contacts WHERE fname = ?";
        // ok: sentinel-android-sqlite-concatenado
        db.rawQuery(q4, new String[] { fname });
    }

    void construtor(android.database.sqlite.SQLiteQueryBuilder qb, Uri uri) {
        // ruleid: sentinel-android-sqlite-concatenado
        qb.appendWhere(COL_ID + "=" + uri.getLastPathSegment());
        // ok: sentinel-android-sqlite-concatenado
        qb.appendWhere(COL_ID + "=" + TABELA);
        // ok: sentinel-android-sqlite-concatenado
        qb.appendWhere("_id = ?");
    }

    void tabelaComDelete(android.database.sqlite.SQLiteDatabase db, String id, String[] args) {
        // ruleid: sentinel-android-sqlite-concatenado
        db.delete(TABELA, COL_ID + " = " + id, args);
        // ok: sentinel-android-sqlite-concatenado
        db.delete(TABELA, COL_ID + " = ?", new String[] { id });
    }

    void caminhoDoIntent(android.content.Intent intent, android.content.Context ctx) throws Exception {
        String nome = intent.getStringExtra("new_file_name");
        // ruleid: sentinel-android-intent-caminho-arquivo
        java.io.FileOutputStream out = new java.io.FileOutputStream(ctx.getFilesDir() + "/" + nome);
        android.os.Bundle extras = intent.getExtras();
        String origem = extras.get("temp_file_path").toString();
        // ruleid: sentinel-android-intent-caminho-arquivo
        java.io.FileInputStream in = new java.io.FileInputStream(new java.io.File(origem));
        // ok: sentinel-android-intent-caminho-arquivo
        java.io.File fixo = new java.io.File(ctx.getFilesDir(), "dados.bin");
        // ok: sentinel-android-intent-caminho-arquivo
        java.io.File seguro = new java.io.File(ctx.getFilesDir(), new java.io.File(nome).getName());
        String canonico = new java.io.File(ctx.getFilesDir(), "x").getCanonicalPath();
        // ok: sentinel-android-intent-caminho-arquivo
        java.io.File ok2 = new java.io.File(canonico);
    }

    void segredoLiteral(android.widget.EditText chave, String nome) {
        // ruleid: sentinel-android-segredo-literal-comparado
        if (chave.getText().toString().equals("vendorsecretkey")) { }
        // ruleid: sentinel-android-segredo-literal-comparado
        if ("AdminPassword2024".equals(chave.getText().toString())) { }
        // ok: sentinel-android-segredo-literal-comparado
        if ("password".equals(nome)) { }
        // ok: sentinel-android-segredo-literal-comparado
        if ("access_token".equals(nome)) { }
        // ok: sentinel-android-segredo-literal-comparado
        if (chave.getText().toString().equals("sim")) { }
    }

    void guardaSenha(android.content.SharedPreferences sp, String usuario, String senha) {
        // ruleid: sentinel-android-prefs-senha-em-claro
        sp.edit().putString("password", senha).commit();
        // ruleid: sentinel-android-prefs-senha-em-claro
        sp.edit().putString("user_pwd", senha).apply();
        android.content.SharedPreferences.Editor ed = sp.edit();
        // ruleid: sentinel-android-prefs-senha-em-claro
        ed.putString("secret", senha);
        // ok: sentinel-android-prefs-senha-em-claro
        ed.putString("user", usuario);
        // ok: sentinel-android-prefs-senha-em-claro
        ed.putString("password", "");
        // ok: sentinel-android-prefs-senha-em-claro
        ed.putString("password_hint", usuario);
    }

    void webviewArquivoETexto(WebView web, WebSettings cfg, android.widget.EditText campo, String fixo) {
        // ruleid: sentinel-android-webview-acesso-arquivo
        cfg.setAllowFileAccess(true);
        // ruleid: sentinel-android-webview-acesso-arquivo
        cfg.setAllowFileAccessFromFileURLs(true);
        // ruleid: sentinel-android-webview-acesso-arquivo
        cfg.setAllowUniversalAccessFromFileURLs(true);
        // ok: sentinel-android-webview-acesso-arquivo
        cfg.setAllowFileAccess(false);
        // ok: sentinel-android-webview-acesso-arquivo
        cfg.setAllowUniversalAccessFromFileURLs(false);

        // ruleid: sentinel-android-webview-texto-da-tela
        web.loadUrl(campo.getText().toString());
        String conteudo = campo.getText().toString();
        // ruleid: sentinel-android-webview-texto-da-tela
        web.loadData(conteudo, "text/html", "UTF-8");
        // ruleid: sentinel-android-webview-texto-da-tela
        web.loadDataWithBaseURL(null, conteudo, "text/html", "UTF-8", null);
        // ok: sentinel-android-webview-texto-da-tela
        web.loadUrl("https://exemplo.com/ajuda");
        // ok: sentinel-android-webview-texto-da-tela
        web.loadUrl(fixo);
        // ok: sentinel-android-webview-texto-da-tela
        web.loadData("<p>ola</p>", "text/html", "UTF-8");
    }
}
