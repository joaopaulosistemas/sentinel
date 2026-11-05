package demo;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;

public class UserRepository {
    private final JdbcTemplate jdbc;
    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<String> byName(String name) {
        return jdbc.queryForList("select name from users where name = '" + name + "'", String.class);
    }
}
