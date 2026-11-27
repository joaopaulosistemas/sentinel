-- Desfazer decisões do Claude: hora em que cada decisão foi tomada e o que preciso guardar para voltar atrás.
--
--  * review_suggestion.decided_at: quando a sugestão foi tratada ou dispensada (NULL = aberta).
--  * rule_proposal.previous_stage: etapa em que a regra estava antes de ser descartada (para restaurar).
--  * pending_action.result_suppression_id: supressão criada ao aprovar "suprimir achado" (para desfazer).
--  * pending_action.undone_at: quando uma pessoa desfez a decisão de um pedido aprovado.
-- Nenhuma linha existente é reescrita: tudo começa NULL e o código trata NULL como "sem dado".

-- Um ADD COLUMN por comando: scripts/conferir-esquema.py só lê uma coluna por ALTER TABLE.
ALTER TABLE public.review_suggestion
    ADD COLUMN decided_at timestamp(6) with time zone;

ALTER TABLE public.rule_proposal
    ADD COLUMN previous_stage character varying(20);

ALTER TABLE public.pending_action
    ADD COLUMN result_suppression_id bigint;

ALTER TABLE public.pending_action
    ADD COLUMN undone_at timestamp(6) with time zone;
