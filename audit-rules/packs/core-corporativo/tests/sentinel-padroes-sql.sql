CREATE TABLE tar.GrupoIsencao (
    IdGrupo INT NOT NULL,
    -- ruleid: sentinel-sqlnorma-constraint-fora-do-padrao
    CONSTRAINT grupo_pk PRIMARY KEY (IdGrupo),
    -- ok: sentinel-sqlnorma-constraint-fora-do-padrao
    CONSTRAINT PK_GrupoIsencao PRIMARY KEY (IdGrupo)
);
-- ruleid: sentinel-sqlnorma-indice-fora-do-padrao
CREATE INDEX ix_grupo ON tar.GrupoIsencao (IdGrupo);
-- ok: sentinel-sqlnorma-indice-fora-do-padrao
CREATE INDEX IDX_GrupoIsencao_IdGrupo ON tar.GrupoIsencao (IdGrupo);
-- ruleid: sentinel-sqlnorma-trigger-fora-do-padrao
CREATE TRIGGER tr_grupo ON tar.GrupoIsencao AFTER INSERT AS SELECT 1;
-- ok: sentinel-sqlnorma-trigger-fora-do-padrao
CREATE TRIGGER TRG_GrupoIsencao_AI ON tar.GrupoIsencao AFTER INSERT AS SELECT 1;
-- ruleid: sentinel-sql-select-estrela-arquivo
SELECT * FROM tar.GrupoIsencao;
-- ok: sentinel-sql-select-estrela-arquivo
SELECT IdGrupo FROM tar.GrupoIsencao;
-- ruleid: sentinel-sql-like-curinga-inicial-arquivo
SELECT IdGrupo FROM tar.GrupoIsencao WHERE NomeGrupo LIKE '%abc';
-- ok: sentinel-sql-like-curinga-inicial-arquivo
SELECT IdGrupo FROM tar.GrupoIsencao WHERE NomeGrupo LIKE 'abc%';
