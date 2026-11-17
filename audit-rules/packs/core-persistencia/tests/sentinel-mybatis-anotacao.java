// Casos de teste de sentinel-mybatis-anotacao-cifrao
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;

interface UsuarioMapperAnotado {
    // ruleid: sentinel-mybatis-anotacao-cifrao
    @Select("select * from users where user like '%${user}%'")
    List<Object> buscaVulneravel(String user);

    // ok: sentinel-mybatis-anotacao-cifrao
    @Select("select * from users where user like CONCAT('%', #{user}, '%')")
    List<Object> buscaSegura(@Param("user") String user);
}
