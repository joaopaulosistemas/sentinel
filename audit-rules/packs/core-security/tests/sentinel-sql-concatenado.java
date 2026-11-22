// Casos de teste de sentinel-sql-concatenado-executado (regra em rules/sentinel-injection.yml).
class SqlConcatenadoExecutado {
    private String conta;

    void variavelMontada(java.sql.Statement st) throws Exception {
        String q = "SELECT * FROM user_data WHERE last_name = '" + conta + "'";
        // ruleid: sentinel-sql-concatenado-executado
        st.executeQuery(q);
    }

    void direto(java.sql.Statement st) throws Exception {
        // ruleid: sentinel-sql-concatenado-executado
        st.executeUpdate("DELETE FROM users WHERE id = " + conta);
    }

    void dentroDeTry(java.sql.Connection c) throws Exception {
        String q = "UPDATE users SET name = '" + conta + "' WHERE id = 1";
        try {
            java.sql.Statement st = c.createStatement();
            // ruleid: sentinel-sql-concatenado-executado
            st.execute(q);
        } finally { }
    }

    void seguros(java.sql.Connection c, java.sql.Statement st) throws Exception {
        // ok: sentinel-sql-concatenado-executado
        st.executeQuery("SELECT * FROM users WHERE x = " + MAX_ID);
        // ok: sentinel-sql-concatenado-executado
        st.executeQuery("SELECT * FROM users " + "WHERE x = 1");
        // ok: sentinel-sql-concatenado-executado
        java.sql.PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE n = ?");
        // ok: sentinel-sql-concatenado-executado
        st.executeQuery("SELECT 1");
        // ok: sentinel-sql-concatenado-executado
        log.info("select the best from " + conta);
    }

    // Spring JdbcTemplate: mesma falha, outro sumidouro (query, update, queryForObject...)
    void jdbcTemplate(org.springframework.jdbc.core.JdbcTemplate jt, String username) {
        String q = "select * from account where username='" + username + "'";
        // ruleid: sentinel-sql-concatenado-executado, sentinel-sql-injection
        jt.query(q, rowMapper);
        // ruleid: sentinel-sql-concatenado-executado
        jt.update("UPDATE creditAccount SET availablebalance='" + conta + "' WHERE id = 1");
        // ruleid: sentinel-sql-concatenado-executado
        jt.queryForObject("SELECT id FROM cashaccount WHERE number = '" + conta + "'", Integer.class);
    }

    void jdbcTemplateSeguro(org.springframework.jdbc.core.JdbcTemplate jt, String username) {
        // ok: sentinel-sql-concatenado-executado
        jt.query("select * from account where username = ?", new Object[] { username }, rowMapper);
        // ok: sentinel-sql-concatenado-executado
        jt.update("UPDATE cashaccount SET availablebalance = ? WHERE number = ?", amount, conta);
        // ok: sentinel-sql-concatenado-executado
        cipher.update("select the best from " + MAX_ID);
    }
}
