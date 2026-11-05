package demo;

import jakarta.persistence.EntityManager;

/** Consulta de texto fixo com parâmetro nomeado: o dado do usuário nunca entra no texto da consulta. */
public class ConsultaService {
    private EntityManager em;

    public long contar(Long id) {
        return em.createQuery("select count(r) from Registro r where r.dono = :dono "
                        + "and r.ativo = true", Long.class)
                .setParameter("dono", id)
                .getSingleResult();
    }
}
