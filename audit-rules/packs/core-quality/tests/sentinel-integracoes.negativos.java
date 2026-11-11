import java.sql.Connection;
import javax.sql.DataSource;

class UsaPool {
    Connection abre(DataSource dataSource) throws Exception {
        // ok: sentinel-int-jdbc-driver-manager
        return dataSource.getConnection();
    }
}
