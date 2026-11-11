import javax.annotation.Resource;
import javax.sql.DataSource;

class UsaDataSource {
    @Resource(name = "jdbc/principal")
    private DataSource dataSource;

    // ok: sentinel-ee-initialcontext-direto
    DataSource fonte() {
        return dataSource;
    }
}
