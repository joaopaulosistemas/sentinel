-- Contexto do código do achado: as linhas reais do arquivo ao redor de arquivo:linha.
--
-- Fica separado de code_snippet porque este também é a identidade do achado (impressão digital entre auditorias).
-- NULL = achado anterior a esta coluna, ou sem arquivo/linha legível, ou de segredo (nunca se copia o arquivo).
-- Nenhuma linha existente é reescrita e as colunas podem ser ignoradas para voltar ao comportamento anterior.

-- Um ADD COLUMN por comando: scripts/conferir-esquema.py só lê uma coluna por ALTER TABLE.
ALTER TABLE public.finding
    ADD COLUMN code_context varchar(4000);

ALTER TABLE public.finding
    ADD COLUMN code_context_start integer;
