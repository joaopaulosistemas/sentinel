package demo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

@Path("/ping")
public class PingResource {
    private final PingService service = new PingService();

    @GET
    public String ping(@QueryParam("count") String host) throws Exception {
        return service.run(host);
    }
}
