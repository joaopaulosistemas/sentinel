class T {
    Object a(@RequestParam String nome) {
        // ruleid: sentinel-taint-sql-parametro-http
        return jdbc.queryForObject("select * from c where nome = '" + nome + "'", Object.class);
    }
    Object b(@RequestParam String nome) {
        // ok: sentinel-taint-sql-parametro-http
        return jdbc.queryForObject("select * from c where nome = ?", Object.class, nome);
    }
}

class CaminhoPorCabecalho {
    void a(javax.servlet.http.HttpServletRequest request) {
        String nome = request.getHeaderNames().nextElement();
        // ruleid: sentinel-servlet-path-traversal
        new java.io.File(new java.io.File("/dados"), nome);
    }

    void b(javax.servlet.http.HttpServletRequest request) {
        String nome = request.getHeaderNames().nextElement();
        // ok: sentinel-servlet-path-traversal
        new java.io.File("/dados", java.nio.file.Paths.get(nome).getFileName().toString());
    }


    // ---- achados do sentinel-alvo: a defesa recomendada (caminho canônico + conferência do prefixo) não é achado ----
    void caminhoCanonicoConferido(jakarta.servlet.http.HttpServletRequest request) throws Exception {
        java.io.File base = new java.io.File("/dados/relatorios").getCanonicalFile();
        java.io.File alvo = new java.io.File(base, request.getParameter("arquivo")).getCanonicalFile();
        if (!alvo.toPath().startsWith(base.toPath())) {
            throw new SecurityException("fora da pasta");
        }
        // ok: sentinel-servlet-path-traversal
        new java.io.FileInputStream(alvo);
    }

    void caminhoCanonicoSemConferir(jakarta.servlet.http.HttpServletRequest request) throws Exception {
        java.io.File base = new java.io.File("/dados/relatorios").getCanonicalFile();
        java.io.File alvo = new java.io.File(base, request.getParameter("arquivo")).getCanonicalFile();
        // ruleid: sentinel-servlet-path-traversal
        new java.io.FileInputStream(alvo);
    }
}
