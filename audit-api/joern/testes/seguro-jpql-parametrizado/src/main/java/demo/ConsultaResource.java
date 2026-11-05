package demo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/consultas")
public class ConsultaResource {
    private final ConsultaService service = new ConsultaService();

    @GET
    @Path("/{id}")
    public long contar(@PathParam("id") Long id) {
        return service.contar(id);
    }
}
