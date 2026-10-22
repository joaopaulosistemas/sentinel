import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.security.MessageDigest;
import java.util.concurrent.Executor;
import org.springframework.jdbc.core.JdbcTemplate;

class InjectionCases {

    void sql(Connection c, Statement st, String nome) throws Exception {
        // ruleid: sentinel-sql-injection
        st.executeQuery("SELECT * FROM usuario WHERE nome = '" + nome + "'");
        // ruleid: sentinel-sql-injection
        st.executeUpdate(String.format("DELETE FROM usuario WHERE nome = '%s'", nome));
        // ok: sentinel-sql-injection
        PreparedStatement ps = c.prepareStatement("SELECT * FROM usuario WHERE nome = ?");
        ps.setString(1, nome);
        // ok: sentinel-sql-injection
        st.executeQuery("SELECT 1 FROM " + "dual");
    }

    void sqlGenerico(Statement st, JdbcTemplate jdbc, String nome) throws Exception {
        // ruleid: sentinel-sql-injection
        st.execute("DELETE FROM usuario WHERE nome = '" + nome + "'");
        // ruleid: sentinel-sql-injection
        jdbc.update("UPDATE usuario SET ativo = 0 WHERE nome = '" + nome + "'");
        // ruleid: sentinel-sql-injection
        jdbc.query("SELECT * FROM usuario WHERE nome = '" + nome + "'", (rs, i) -> rs.getString(1));
        // ok: sentinel-sql-injection
        jdbc.update("UPDATE usuario SET ativo = 0 WHERE nome = ?", nome);
    }

    interface Servico {
        void update(String id);
        void execute(String tarefa);
    }

    void naoEhSql(Servico servico, MessageDigest md, Executor exec, String id) throws Exception {
        // ok: sentinel-sql-injection
        servico.update(id);
        // ok: sentinel-sql-injection
        servico.execute(id);
        // ok: sentinel-sql-injection
        md.update(id.getBytes("UTF-8"));
        // ok: sentinel-sql-injection
        exec.execute(() -> servico.update(id));
    }

    void comando(Runtime rt, String arquivo) throws Exception {
        // ruleid: sentinel-command-injection
        Runtime.getRuntime().exec("ls " + arquivo);
        // ruleid: sentinel-command-injection
        new ProcessBuilder("sh -c cat " + arquivo);
        // ok: sentinel-command-injection
        Runtime.getRuntime().exec("ls");
    }

    void comandoVariavel(String cmd) throws Exception {
        // ruleid: sentinel-command-injection-variable
        Runtime.getRuntime().exec(cmd);
        // ruleid: sentinel-command-injection-variable
        new ProcessBuilder(cmd);
        // ok: sentinel-command-injection-variable
        Runtime.getRuntime().exec("ls -l");
        // ok: sentinel-command-injection-variable
        Runtime.getRuntime().exec(new String[]{"ls", "-l"});
    }

    void comandoViaShell(String cmd) throws Exception {
        // ruleid: sentinel-command-injection-shell
        new ProcessBuilder("sh", "-c", cmd);
        // ruleid: sentinel-command-injection-shell
        Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
        // ok: sentinel-command-injection-shell
        new ProcessBuilder("sh", "-c", "ls -l");
        // ok: sentinel-command-injection-shell
        new ProcessBuilder("git", "log", "--oneline");
    }

    void ldap(javax.naming.directory.DirContext ctx, String user) throws Exception {
        // ruleid: sentinel-ldap-injection
        ctx.search("dc=empresa", "(uid=" + user + ")", null);
        // ok: sentinel-ldap-injection
        ctx.search("dc=empresa", "(uid={0})", new Object[]{user}, null);
    }

    void xpath(javax.xml.xpath.XPath xp, String id, org.w3c.dom.Document doc) throws Exception {
        // ruleid: sentinel-xpath-injection
        xp.evaluate("//usuario[@id='" + id + "']", doc);
        // ok: sentinel-xpath-injection
        xp.evaluate("//usuario", doc);
    }
}
