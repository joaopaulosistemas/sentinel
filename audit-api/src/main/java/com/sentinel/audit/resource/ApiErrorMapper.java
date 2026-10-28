package com.sentinel.audit.resource;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Devolve a mensagem dos erros 4xx lançados pela API (ex.: {@code BadRequestException("Para guardar o token…")}) em JSON.
 * Sem isto o corpo vem vazio e a tela só consegue mostrar "HTTP 400: Bad Request". Erros 5xx e respostas que já trazem
 * corpo seguem como estavam, para não expor detalhe interno.
 */
@Provider
public class ApiErrorMapper implements ExceptionMapper<WebApplicationException> {

    private static final int CLIENT_ERROR_MIN = 400;
    private static final int CLIENT_ERROR_END = 500;


    @Override
    public Response toResponse(WebApplicationException e) {
        Response original = e.getResponse();
        int status = original.getStatus();
        if (status < CLIENT_ERROR_MIN || status >= CLIENT_ERROR_END || original.hasEntity()) {
            return original;
        }
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return original;
        }
        return Response.fromResponse(original)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("message", message))
                .build();
    }
}
