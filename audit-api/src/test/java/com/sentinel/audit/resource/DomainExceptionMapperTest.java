package com.sentinel.audit.resource;

import com.sentinel.audit.service.ConflictException;
import com.sentinel.audit.service.DomainException;
import com.sentinel.audit.service.InvalidInputException;
import com.sentinel.audit.service.ResourceNotFoundException;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A borda REST é quem escolhe o status HTTP dos erros de negócio; os serviços não o conhecem. */
class DomainExceptionMapperTest {

    @Test
    void cadaTipoViraOStatusCerto() {
        assertEquals(Status.NOT_FOUND, DomainExceptionMapper.statusOf(new ResourceNotFoundException("x")));
        assertEquals(Status.BAD_REQUEST, DomainExceptionMapper.statusOf(new InvalidInputException("x")));
        assertEquals(Status.CONFLICT, DomainExceptionMapper.statusOf(new ConflictException("x")));
    }

    @Test
    void tipoDeDominioSemMapeamentoViraErroInterno() {
        DomainException desconhecida = new DomainException("x") { };

        assertEquals(Status.INTERNAL_SERVER_ERROR, DomainExceptionMapper.statusOf(desconhecida));
    }
}
