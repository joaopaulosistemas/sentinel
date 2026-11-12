-- Escopo da auditoria: quais lados (back-end / front-end) estavam desligados quando ela começou.
--
-- Valores gravados: 'back', 'front' ou '' (os dois lados ligados). NULL = auditoria anterior a esta coluna, escopo
-- desconhecido: nenhuma linha existente é reescrita e a coluna pode ser ignorada para voltar ao comportamento anterior.

ALTER TABLE public.audit_run
    ADD COLUMN disabled_sides varchar(20);
