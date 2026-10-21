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
