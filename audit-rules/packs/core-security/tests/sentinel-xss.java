class XssJavaCases {
    void resposta(javax.servlet.http.HttpServletResponse resp, String nome) throws Exception {
        // ruleid: sentinel-xss-response-write
        resp.getWriter().println(nome);
        // ruleid: sentinel-xss-response-write
        resp.getWriter().write(nome);
        // ok: sentinel-xss-response-write
        resp.getWriter().println("<h1>Olá</h1>");
    }
}
