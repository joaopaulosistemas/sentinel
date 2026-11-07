import { describe, expect, it } from 'vitest';
import { MAX_FILE_BYTES, classifyEvidence, evidenceCoverage } from './evidenceFiles';

describe('classifyEvidence', () => {
  it('reconhece a estrutura do banco', () => {
    expect(classifyEvidence('schema.sql', 900, 'CREATE TABLE orders (id int);')).toEqual({ ok: true, kind: 'schema' });
  });
  it('recusa .sql com dados', () => {
    const v = classifyEvidence('dump.sql', 900, "CREATE TABLE t(a int);\nINSERT INTO t VALUES (1);");
    expect(v.ok).toBe(false);
  });
  it('diferencia os três CSV do Postgres pelo cabeçalho', () => {
    expect(classifyEvidence('a.csv', 10, 'query,calls,total_exec_time\n')).toMatchObject({ ok: true, kind: 'statements' });
    expect(classifyEvidence('b.csv', 10, 'relname,seq_scan,n_live_tup\n')).toMatchObject({ ok: true, kind: 'tables' });
    expect(classifyEvidence('c.csv', 10, '"indexrelname";"idx_scan"\n')).toMatchObject({ ok: true, kind: 'indexes' });
  });
  it('recusa CSV qualquer', () => {
    const v = classifyEvidence('clientes.csv', 10, 'nome,email\nAna,a@b.c\n');
    expect(v.ok).toBe(false);
  });
  it('reconhece plano, JaCoCo, lcov e histograma', () => {
    expect(classifyEvidence('p.json', 10, '[{"Plan":{"Node Type":"Seq Scan"}}]')).toMatchObject({ kind: 'explain' });
    expect(classifyEvidence('j.xml', 10, '<?xml version="1.0"?><report name="x">')).toMatchObject({ kind: 'jacoco' });
    expect(classifyEvidence('lcov.info', 10, 'TN:\nSF:src/a.ts\n')).toMatchObject({ kind: 'lcov' });
    expect(classifyEvidence('h.txt', 10, ' num   #instances    #bytes  class name\n')).toMatchObject({ kind: 'heap' });
  });
  it('recusa segredo, compactado, vazio e grande demais', () => {
    expect(classifyEvidence('prod.env', 10, 'X=1').ok).toBe(false);
    expect(classifyEvidence('tudo.zip', 10, '').ok).toBe(false);
    expect(classifyEvidence('schema.sql', 0, '').ok).toBe(false);
    expect(classifyEvidence('schema.sql', MAX_FILE_BYTES + 1, 'create table t()').ok).toBe(false);
  });
  it('recusa extensão desconhecida e JSON que não é plano', () => {
    expect(classifyEvidence('foto.png', 10, '').ok).toBe(false);
    expect(classifyEvidence('config.json', 10, '{"a":1}').ok).toBe(false);
  });
});

describe('evidenceCoverage', () => {
  it('conta por área e lista o que falta', () => {
    const c = evidenceCoverage(['schema', 'jacoco']);
    expect(c.received).toBe(2);
    expect(c.total).toBe(8);
    expect(c.byArea.find((a) => a.area === 'Memória')).toMatchObject({ received: 0, total: 1 });
    expect(c.missing.map((m) => m.kind)).not.toContain('schema');
  });
});
