import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;

// ruleid: sentinel-owasp-a01-endpoint-sem-autorizacao
@Path("/aberto")
class RecursoAberto {
    public String ola() { return "oi"; }
}

// ok: sentinel-owasp-a01-endpoint-sem-autorizacao
@RolesAllowed("auditor")
@Path("/protegido")
class RecursoProtegido {
}

// ok: sentinel-owasp-a01-endpoint-sem-autorizacao
@PermitAll
@Path("/publico")
class RecursoPublico {
}

// ok: sentinel-owasp-a01-endpoint-sem-autorizacao
class NaoEhEndpoint {
}
