/**
 * Evidências de execução (docs/evidencias/README.md): o que o Sentinel aceita, como reconhece cada arquivo e
 * por que recusa os outros. Espelha o EvidenceAnalyzer da API: o tipo vem do conteúdo e do nome.
 */

export type EvidenceKind = 'schema' | 'statements' | 'tables' | 'indexes' | 'explain' | 'jacoco' | 'lcov' | 'heap';

type EvidenceArea = 'Banco de dados' | 'Testes' | 'Memória';

export interface IEvidenceType {
  kind: EvidenceKind;
  /** Nome sugerido do arquivo */
  file: string;
  area: EvidenceArea;
  title: string;
  /** O que o Sentinel aponta quando o arquivo existe */
  reveals: string;
  /** Como gerar o arquivo, no ambiente de quem tem acesso */
  howTo: string;
  /** Cuidado a tomar antes de entregar */
  care: string;
}

export const EVIDENCE_TYPES: IEvidenceType[] = [
  {
    kind: 'schema', file: 'schema.sql', area: 'Banco de dados', title: 'Estrutura do banco',
    reveals: 'Tabela sem chave primária, chave estrangeira sem índice, índice duplicado, timestamp sem fuso, chave int que vai esgotar.',
    howTo: 'pg_dump --schema-only "postgresql://usuario@servidor:5432/banco" > schema.sql',
    care: 'Traz só definições, nenhum dado de tabela.',
  },
  {
    kind: 'statements', file: 'pg_stat_statements.csv', area: 'Banco de dados', title: 'Consultas que mais pesam',
    reveals: 'Consulta que consome mais tempo do banco, disco temporário, leitura excessiva e padrão N+1.',
    howTo: './coletar-evidencias.sh "postgresql://usuario@servidor:5432/banco"',
    care: 'Confira se alguma consulta tem valores sensíveis escritos no texto antes de entregar.',
  },
  {
    kind: 'tables', file: 'pg_stat_user_tables.csv', area: 'Banco de dados', title: 'Uso das tabelas',
    reveals: 'Varredura sequencial em tabela grande e tabela inchada por linhas mortas.',
    howTo: './coletar-evidencias.sh "postgresql://usuario@servidor:5432/banco"',
    care: 'As estatísticas valem para um período: colete depois de um ciclo completo de uso.',
  },
  {
    kind: 'indexes', file: 'pg_stat_user_indexes.csv', area: 'Banco de dados', title: 'Uso dos índices',
    reveals: 'Índice nunca usado, que custa escrita e espaço.',
    howTo: './coletar-evidencias.sh "postgresql://usuario@servidor:5432/banco"',
    care: 'Um índice "nunca usado" pode servir um fechamento mensal. Evite pg_stat_reset() antes da coleta.',
  },
  {
    kind: 'explain', file: 'explain-consulta.json', area: 'Banco de dados', title: 'Plano de execução',
    reveals: 'Varredura que descarta milhões de linhas, ordenação em disco, estimativa errada.',
    howTo: 'EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT ...;',
    care: 'EXPLAIN ANALYZE executa a consulta: use só com SELECT, de preferência em homologação.',
  },
  {
    kind: 'jacoco', file: 'jacoco.xml', area: 'Testes', title: 'Cobertura Java (JaCoCo)',
    reveals: 'Cobertura baixa e classes grandes sem teste.',
    howTo: 'Use o relatório do JaCoCo que o seu CI já gera (jacoco.xml ou jacoco.csv).',
    care: 'O Sentinel não executa seus testes; só lê o relatório.',
  },
  {
    kind: 'lcov', file: 'lcov.info', area: 'Testes', title: 'Cobertura TypeScript (lcov)',
    reveals: 'Cobertura baixa em código TypeScript.',
    howTo: 'jest --coverage  (ou Karma / Vitest com o formato lcov)',
    care: 'Use o relatório do seu CI.',
  },
  {
    kind: 'heap', file: 'class_histogram.txt', area: 'Memória', title: 'Histograma de memória (JVM)',
    reveals: 'Maiores consumidores do heap, classes suas com milhões de instâncias, mapas gigantes.',
    howTo: 'jcmd <pid> GC.class_histogram > class_histogram.txt',
    care: 'Capture duas vezes, com alguns minutos de carga entre elas; o Sentinel lê uma captura por vez.',
  },
];

export const MAX_FILE_BYTES = 200 * 1024 * 1024;
export const MAX_FILES = 50;
/** Quanto do início de cada arquivo é lido para reconhecer o formato. */
export const SNIFF_BYTES = 4096;

export type EvidenceVerdict =
  | { ok: true; kind: EvidenceKind; note?: string }
  | { ok: false; reason: string; hint: string };

const GENERIC_BAD_NAME: Array<[RegExp, string]> = [
  [/\.(env|pem|key|p12|pfx|jks)$/i, 'Parece um arquivo de segredo ou credencial. Nunca entregue isso ao Sentinel.'],
  [/\.(zip|tar|gz|7z|rar)$/i, 'Arquivo compactado. Envie os arquivos soltos, já extraídos.'],
  [/\.(dump|bak|backup)$/i, 'Dump de dados. O Sentinel não aceita dados: gere só a estrutura com pg_dump --schema-only.'],
];

function csvKind(head: string): EvidenceKind | null {
  const first = head.split(/\r?\n/, 1)[0]?.toLowerCase() ?? '';
  const cols = first.split(/[,;\t]/).map((c) => c.replace(/["\s]/g, ''));
  const has = (c: string) => cols.includes(c);
  if (has('query') && has('calls')) return 'statements';
  if (has('seq_scan') && has('n_live_tup')) return 'tables';
  if (has('idx_scan') && (has('indexrelname') || has('indexname'))) return 'indexes';
  return null;
}

const recusa = (reason: string, hint: string): EvidenceVerdict => ({ ok: false, reason, hint });

function byExtension(name: string, head: string): EvidenceVerdict {
  if (name.endsWith('.sql')) {
    if (/\binsert\s+into\b|^\s*copy\s+\S+\s+(\(|from)/im.test(head)) {
      return recusa('O .sql contém dados (INSERT ou COPY).', 'Gere só a estrutura: pg_dump --schema-only.');
    }
    return /\bcreate\s+(table|index|unique\s+index)\b/i.test(head)
      ? { ok: true, kind: 'schema' }
      : recusa('O .sql não tem CREATE TABLE nem CREATE INDEX no começo.', 'Use a saída de pg_dump --schema-only.');
  }
  if (name.endsWith('.csv')) {
    const kind = csvKind(head);
    return kind ? { ok: true, kind } : recusa('Cabeçalho do CSV não é de pg_stat_statements, pg_stat_user_tables nem pg_stat_user_indexes.', 'Gere com ./coletar-evidencias.sh; as colunas precisam estar na primeira linha.');
  }
  if (name.endsWith('.json')) {
    return /^\s*[[{]/.test(head) && /"Plan"/.test(head)
      ? { ok: true, kind: 'explain' }
      : recusa('O JSON não é um plano do PostgreSQL (não tem "Plan").', 'Use EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON).');
  }
  if (name.endsWith('.xml')) {
    return /<report[\s>]/.test(head) ? { ok: true, kind: 'jacoco' } : recusa('O XML não é um relatório do JaCoCo (falta <report>).', 'Envie o jacoco.xml gerado pelo seu CI.');
  }
  if (name.endsWith('.info') || name.startsWith('lcov')) {
    return /^SF:/m.test(head) ? { ok: true, kind: 'lcov' } : recusa('O arquivo não está no formato lcov (falta SF:).', 'Gere com jest --coverage no formato lcov.');
  }
  if (name.endsWith('.txt') || name.endsWith('.histo')) {
    return /num\s+#instances\s+#bytes/i.test(head) ? { ok: true, kind: 'heap' } : recusa('O texto não é um histograma de memória da JVM.', 'Use jcmd <pid> GC.class_histogram.');
  }
  return recusa(`Tipo de arquivo não aceito (${name.includes('.') ? name.slice(name.lastIndexOf('.')) : 'sem extensão'}).`, 'Aceitos: .sql, .csv, .json, .xml, .info e .txt, no formato da lista ao lado.');
}

/** Reconhece um arquivo pelo nome, tamanho e começo do conteúdo; recusa o que o Sentinel ignoraria. */
export function classifyEvidence(fileName: string, size: number, head: string): EvidenceVerdict {
  const name = fileName.toLowerCase();
  const bad = GENERIC_BAD_NAME.find(([re]) => re.test(name));
  if (bad) return recusa(bad[1], 'Veja "Como gerar" de cada tipo.');
  if (size === 0) return recusa('Arquivo vazio.', 'Gere o arquivo de novo.');
  if (size > MAX_FILE_BYTES) return recusa('Passa de 200 MB.', 'Divida ou reduza o arquivo (para estatísticas, exporte só as linhas necessárias).');
  return byExtension(name, head.slice(0, SNIFF_BYTES));
}

export interface IEvidenceCoverage {
  received: number;
  total: number;
  byArea: Array<{ area: EvidenceArea; received: number; total: number }>;
  missing: IEvidenceType[];
}

/** Quantos dos tipos já têm um arquivo reconhecido e o que falta (sem verificar). */
export function evidenceCoverage(kinds: Iterable<EvidenceKind>): IEvidenceCoverage {
  const got = new Set(kinds);
  const areas: EvidenceArea[] = ['Banco de dados', 'Testes', 'Memória'];
  return {
    received: EVIDENCE_TYPES.filter((t) => got.has(t.kind)).length,
    total: EVIDENCE_TYPES.length,
    byArea: areas.map((area) => {
      const ofArea = EVIDENCE_TYPES.filter((t) => t.area === area);
      return { area, total: ofArea.length, received: ofArea.filter((t) => got.has(t.kind)).length };
    }),
    missing: EVIDENCE_TYPES.filter((t) => !got.has(t.kind)),
  };
}

/** Estatísticas do banco valem para um período: depois disto o arquivo pode já não representar o uso atual. */
export const STALE_DAYS = 30;

/** Idade do arquivo em dias inteiros (data de modificação do arquivo no computador de quem entrega). */
export function evidenceAgeDays(lastModified: number, now: number = Date.now()): number {
  return Math.max(0, Math.floor((now - lastModified) / 86_400_000));
}

export const isStaleEvidence = (lastModified: number, now: number = Date.now()): boolean => evidenceAgeDays(lastModified, now) > STALE_DAYS;
