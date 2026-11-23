package com.sentinel.audit.resource;

import com.sentinel.audit.service.ConflictException;
import com.sentinel.audit.service.DomainException;
import com.sentinel.audit.service.InvalidInputException;
import com.sentinel.audit.service.ResourceNotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Converte os erros de negócio dos serviços em resposta HTTP, no mesmo formato que {@link ApiErrorMapper} já usa
 * ({@code {"message": "..."}}). É aqui, na borda, que o status HTTP é escolhido; os serviços não o conhecem.
 */
@Provider
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {

    @Override
    public Response toResponse(DomainException e) {
        return Response.status(statusOf(e))
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("message", e.getMessage() == null ? "" : e.getMessage()))
                .build();
    }

    static Response.Status statusOf(DomainException e) {
        if (e instanceof ResourceNotFoundException) {
            return Response.Status.NOT_FOUND;
        }
        if (e instanceof ConflictException) {
            return Response.Status.CONFLICT;
        }
        if (e instanceof InvalidInputException) {
            return Response.Status.BAD_REQUEST;
        }
        return Response.Status.INTERNAL_SERVER_ERROR;
    }
}
