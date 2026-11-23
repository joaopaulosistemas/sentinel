// Dados simulados da API do Sentinel, só para desenhar as telas. Seguem os tipos de audit-dashboard/src/types/index.ts.
// Nada aqui vem de um sistema real: nomes, repositórios, achados e notas são inventados.

const agora = Date.now();
const haHoras = (h) => new Date(agora - h * 3_600_000).toISOString();
const haMinutos = (m) => new Date(agora - m * 60_000).toISOString();

export const sistemas = [
  {
    id: 1, name: 'conta-corrente-api', stack: 'JAVA', team: 'Squad Contas', lastAuditScore: 61, lastAuditDate: haHoras(3),
    openCritical: 2, openHigh: 7, scoreDelta: 4, auditCount: 18, profile: 'default', repoUrl: 'https://github.com/acme/conta-corrente-api',
    sourceType: 'GIT_URL', description: 'API de contas e lançamentos (Spring Boot).', scheduledBranch: 'main', auditIntervalHours: 24,
    auditOnPush: true, disabledSides: [], gitTokenSet: true, gitTokenHosts: 'github.com', webhookSecretSet: true, gitHistoryDepth: 100,
    notifyUrlSet: true, notifyFormat: 'slack', notifyMinScoreDrop: 5, notifyMinSeverity: 'HIGH',
  },
  {
    id: 2, name: 'portal-cooperado', stack: 'JAVASCRIPT', team: 'Squad Digital', lastAuditScore: 78, lastAuditDate: haHoras(9),
    openCritical: 0, openHigh: 3, scoreDelta: -2, auditCount: 12, profile: 'default', repoUrl: 'https://github.com/acme/portal-cooperado',
    sourceType: 'GIT_URL', description: 'Portal Angular do cooperado.', scheduledBranch: 'main', auditIntervalHours: 0, auditOnPush: false,
    disabledSides: ['back'], gitTokenSet: false, webhookSecretSet: false, gitHistoryDepth: 100,
  },
  {
    id: 3, name: 'cartoes-core', stack: 'JAVA', team: 'Squad Cartões', lastAuditScore: 43, lastAuditDate: haHoras(30),
    openCritical: 5, openHigh: 12, scoreDelta: -6, auditCount: 9, profile: 'default', repoUrl: 'https://github.com/acme/cartoes-core',
    sourceType: 'GIT_URL', description: 'Núcleo de processamento de cartões.', scheduledBranch: 'main', auditIntervalHours: 168, auditOnPush: false,
    disabledSides: [], gitTokenSet: true, webhookSecretSet: false, gitHistoryDepth: 100,
  },
  {
    id: 4, name: 'antifraude-regras', stack: 'JAVA', team: 'Squad Risco', lastAuditScore: 88, lastAuditDate: haHoras(52),
    openCritical: 0, openHigh: 1, scoreDelta: 7, auditCount: 24, profile: 'default', sourceType: 'LOCAL_PATH', localPath: 'antifraude-regras',
    localSnapshot: 'TREE', description: 'Motor de regras antifraude (pasta local).', scheduledBranch: 'main', auditIntervalHours: 0, auditOnPush: false,
    disabledSides: [], gitTokenSet: false, webhookSecretSet: false, gitHistoryDepth: 100,
  },
  {
    id: 5, name: 'app-mobile-bff', stack: 'JAVASCRIPT', team: 'Squad Digital', lastAuditScore: null, lastAuditDate: null,
    openCritical: 0, openHigh: 0, scoreDelta: null, auditCount: 0, profile: 'default', repoUrl: 'https://github.com/acme/app-mobile-bff',
    sourceType: 'GIT_URL', description: 'BFF do aplicativo (ainda sem auditoria).', scheduledBranch: 'main', auditIntervalHours: 0, auditOnPush: false,
    disabledSides: [], gitTokenSet: false, webhookSecretSet: false, gitHistoryDepth: 100,
  },
];

const risco = (geral, seg, qual, arq, dep, obs, perf) => ({
  overall: geral, security: seg, quality: qual, architecture: arq, dependencies: dep, observability: obs, performance: perf,
});

const auditoriaBase = (id, sistema, nota, extra = {}) => ({
  id, systemId: sistema.id, systemName: sistema.name, branch: 'main', status: 'COMPLETED',
  riskScore: nota,
  findingsSummary: { CRITICAL: sistema.openCritical, HIGH: sistema.openHigh, MEDIUM: 18, LOW: 31, INFO: 9 },
  startedAt: haHoras(3.2), completedAt: haHoras(3), triggeredBy: 'dashboard', profile: 'default', commitSha: '9f3c2a1d7b04e5a6c8d1f0e2b3a49c7d65e8f012',
  linesOfCode: 48210, totalFindings: 59, newFindings: 6, resolvedFindings: 10, previousAuditId: id - 1, scoreDelta: 4,
  incomplete: false, incompleteReasons: [], ...extra,
});

export const auditorias = [
  auditoriaBase(101, sistemas[0], risco(61, 48, 66, 58, 55, 72, 70)),
  auditoriaBase(100, sistemas[0], risco(57, 44, 63, 55, 51, 70, 68), { startedAt: haHoras(27.2), completedAt: haHoras(27), scoreDelta: -1, newFindings: 4, resolvedFindings: 2, previousAuditId: 99 }),
  auditoriaBase(99, sistemas[1], risco(78, 82, 75, 80, 70, 79, 81), { startedAt: haHoras(9.2), completedAt: haHoras(9), scoreDelta: -2 }),
  auditoriaBase(98, sistemas[2], risco(43, 31, 48, 40, 38, 55, 60), { startedAt: haHoras(30.2), completedAt: haHoras(30), scoreDelta: -6 }),
  auditoriaBase(97, sistemas[3], risco(88, 90, 86, 89, 84, 91, 90), { startedAt: haHoras(52.2), completedAt: haHoras(52), scoreDelta: 7 }),
  auditoriaBase(96, sistemas[0], null, { status: 'FAILED', riskScore: null, completedAt: haHoras(70), startedAt: haHoras(70.1), errorMessage: 'Falha ao clonar o repositório: autenticação recusada.', findingsSummary: { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0, INFO: 0 } }),
];

/** Auditoria em andamento (tela de execução). */
export const auditoriaEmExecucao = {
  id: 102, systemId: 3, systemName: 'cartoes-core', branch: 'main', status: 'RUNNING', riskScore: null,
  findingsSummary: { CRITICAL: 1, HIGH: 4, MEDIUM: 9, LOW: 14, INFO: 2 }, startedAt: haMinutos(4), completedAt: null,
  triggeredBy: 'dashboard', profile: 'default',
  progress: {
    phase: 'ANALISE', repoUrl: 'https://github.com/acme/cartoes-core', filesRead: 1284, linesOfCode: 61930,
    severities: { CRITICAL: 1, HIGH: 4, MEDIUM: 9, LOW: 14, INFO: 2 },
    analyzers: [
      { name: 'Opengrep', status: 'OK', findings: 21, durationMs: 41200, note: null, description: 'Procura padrões inseguros e erros comuns no código.' },
      { name: 'PMD', status: 'OK', findings: 9, durationMs: 28900, note: null, description: 'Aponta código morto, complexidade e más práticas em Java.' },
      { name: 'Trivy', status: 'EXECUTANDO', findings: 0, durationMs: 0, note: null, description: 'Procura vulnerabilidades conhecidas em dependências e imagens.' },
      { name: 'Gitleaks', status: 'OK', findings: 0, durationMs: 6100, note: null, description: 'Procura segredos (senhas, chaves) no código e no histórico.' },
      { name: 'Joern', status: 'ESPERANDO', findings: 0, durationMs: 0, note: null, description: 'Segue o caminho dos dados entre arquivos (injeção de SQL e similares).' },
      { name: 'Checkstyle', status: 'PARCIAL', findings: 3, durationMs: 19000, note: '2 arquivos não puderam ser lidos', description: 'Confere o estilo do código Java.' },
      { name: 'Hadolint', status: 'NAO_SUPORTADO', findings: 0, durationMs: 0, note: 'Sem Dockerfile no repositório', description: 'Confere boas práticas em Dockerfile.' },
    ],
    events: [
      { at: haMinutos(4), message: 'Auditoria na fila' },
      { at: haMinutos(3.8), message: 'Clonando cartoes-core (branch main)' },
      { at: haMinutos(3.2), message: 'Clone concluído: 1.284 arquivos, 61.930 linhas' },
      { at: haMinutos(3), message: 'Opengrep iniciado' },
      { at: haMinutos(2.3), message: 'Opengrep concluído: 21 achados' },
      { at: haMinutos(1.1), message: 'Trivy iniciado' },
    ],
  },
};

const achado = (id, categoria, severidade, titulo, arquivo, linha, regra, ferramenta, extra = {}) => ({
  id, category: categoria, severity: severidade, title: titulo,
  description: 'Descrição do achado: explica o risco, onde ocorre e por que importa para o sistema.',
  filePath: arquivo, lineNumber: linha, codeSnippet: 'String sql = "SELECT * FROM conta WHERE id = " + id;\nstatement.executeQuery(sql);',
  ruleId: regra, toolSource: ferramenta, cveId: null, aiRecommendation: null, status: 'OPEN', fingerprint: `fp-${id}`,
  isNew: id % 3 === 0, shadow: false, confidence: 'HIGH', confidenceReason: null,
  remediation: {
    summary: 'Use consulta parametrizada em vez de concatenar texto.',
    before: 'String sql = "SELECT * FROM conta WHERE id = " + id;',
    after: 'PreparedStatement ps = con.prepareStatement("SELECT * FROM conta WHERE id = ?");\nps.setLong(1, id);',
    references: ['https://owasp.org/www-community/attacks/SQL_Injection'],
  },
  ...extra,
});

export const achados = [
  achado(1, 'SECURITY', 'CRITICAL', 'Injeção de SQL por concatenação de texto', 'src/main/java/br/acme/conta/ContaRepository.java', 88, 'java.sql-injection', 'Opengrep'),
  achado(2, 'SECURITY', 'CRITICAL', 'Segredo gravado no código (chave de API)', 'src/main/resources/application.properties', 14, 'secrets.api-key', 'Gitleaks'),
  achado(3, 'DEPENDENCY', 'HIGH', 'Dependência com vulnerabilidade conhecida (CVE-2025-48924)', 'pom.xml', 112, 'trivy.cve', 'Trivy', { cveId: 'CVE-2025-48924' }),
  achado(4, 'ARCHITECTURE', 'HIGH', 'Controller acessa o repositório diretamente', 'src/main/java/br/acme/conta/ContaController.java', 41, 'arq.camadas', 'Sentinel'),
  achado(5, 'QUALITY', 'MEDIUM', 'Método com complexidade ciclomática 27', 'src/main/java/br/acme/lancamento/LancamentoService.java', 203, 'pmd.cyclomatic', 'PMD'),
  achado(6, 'OBSERVABILITY', 'MEDIUM', 'Exceção engolida sem registro em log', 'src/main/java/br/acme/extrato/ExtratoService.java', 77, 'obs.catch-vazio', 'PMD'),
  achado(7, 'PERFORMANCE', 'LOW', 'Consulta dentro de laço (N+1)', 'src/main/java/br/acme/conta/ContaService.java', 156, 'perf.n-mais-1', 'Opengrep'),
  achado(8, 'QUALITY', 'LOW', 'Nome de variável abreviado', 'src/main/java/br/acme/util/Fmt.java', 12, 'estilo.nomes', 'Checkstyle'),
];

export const diferenca = {
  auditId: 101, baseAuditId: 100, hasBaseline: true, overall: 61, baseOverall: 57, scoreDelta: 4,
  newFindings: achados.slice(0, 3), resolvedFindings: achados.slice(5, 8), persistingCount: 49,
};

export const frescor = { auditedCommit: '9f3c2a1d7b04e5a6c8d1f0e2b3a49c7d65e8f012', currentCommit: '9f3c2a1d7b04e5a6c8d1f0e2b3a49c7d65e8f012', stale: false };

export const tendencia = (sistema) => {
  const base = sistema.lastAuditScore ?? 50;
  return Array.from({ length: 14 }, (_, i) => {
    const nota = Math.max(10, Math.min(98, Math.round(base - 14 + i + Math.sin(i * 1.7) * 3)));
    return {
      auditId: 80 + i, branch: 'main', completedAt: haHoras((14 - i) * 24), overall: nota, security: nota - 6, quality: nota + 3,
      architecture: nota - 2, dependencies: nota - 5, observability: nota + 8, performance: nota + 5,
      findings: { CRITICAL: Math.max(0, 5 - Math.floor(i / 3)), HIGH: 12 - Math.floor(i / 2), MEDIUM: 20, LOW: 30, INFO: 9 },
    };
  });
};

const itemDivida = (id, problema, area, areaLabel, severidade, prioridade, ocorrencias) => ({
  id, problem: problema, area, areaLabel, severity: severidade, priority: prioridade, priorityLabel: { P0: 'Agora', P1: 'Próximo ciclo', P2: 'Planejar', P3: 'Quando der' }[prioridade],
  occurrences: ocorrencias, impact: 'Exposição de dados de contas a quem consegue alterar a entrada.', effort: 'M', effortEstimated: true,
  regressionRisk: 'Baixo, com teste de integração cobrindo as consultas.', recommendation: 'Trocar a concatenação por consulta parametrizada.', example: 'ContaRepository.java:88',
});

const areaVazia = (area, rotulo, estado, total) => ({
  area, label: rotulo, state: estado, currentState: `${rotulo}: ${total} achado(s) abertos.`, bySeverity: { CRITICAL: 0, HIGH: 0, MEDIUM: total, LOW: 0, INFO: 0 },
  total, problems: ['Padrão repetido em vários módulos.'], risks: ['Falha em produção sem alerta.'], recommendations: ['Aplicar o padrão corrigido e cobrir com teste.'], notVerified: [],
});

const dividas = [
  itemDivida('d1', 'Injeção de SQL em consultas de conta', 'SECURITY', 'Segurança', 'CRITICAL', 'P0', 3),
  itemDivida('d2', 'Segredo gravado em application.properties', 'SECURITY', 'Segurança', 'CRITICAL', 'P0', 1),
  itemDivida('d3', 'Dependências com CVE conhecido', 'DEPENDENCY', 'Dependências', 'HIGH', 'P1', 7),
  itemDivida('d4', 'Controllers acessando repositórios', 'ARCHITECTURE', 'Arquitetura', 'HIGH', 'P1', 12),
  itemDivida('d5', 'Métodos muito complexos', 'QUALITY', 'Qualidade', 'MEDIUM', 'P2', 9),
];

export const insights = {
  executiveSummary: 'O sistema tem 2 riscos críticos de segurança que precisam de correção imediata; o restante é dívida técnica de média prioridade.',
  verdict: 'Atenção: corrigir os críticos antes da próxima entrega.',
  bySeverity: { CRITICAL: 2, HIGH: 7, MEDIUM: 18, LOW: 31, INFO: 9 }, activeFindings: 59,
  areas: [
    areaVazia('SECURITY', 'Segurança', 'CRITICO', 6), areaVazia('QUALITY', 'Qualidade', 'ATENCAO', 14), areaVazia('ARCHITECTURE', 'Arquitetura', 'ATENCAO', 12),
    areaVazia('DEPENDENCY', 'Dependências', 'ATENCAO', 7), areaVazia('OBSERVABILITY', 'Observabilidade', 'SEM_ACHADOS', 0),
  ],
  debtTable: dividas, topProblems: dividas.slice(0, 3), topImprovements: dividas.slice(2, 5), quickWins: dividas.slice(1, 3), criticalRisks: dividas.slice(0, 2),
  roadmap: [
    { number: 1, name: 'Estancar o risco', goal: 'Remover os críticos de segurança.', total: 2, items: dividas.slice(0, 2) },
    { number: 2, name: 'Reduzir a superfície', goal: 'Atualizar dependências e separar camadas.', total: 2, items: dividas.slice(2, 4) },
  ],
  unverified: ['Cobertura de testes não foi medida.'],
  analyzers: [
    { name: 'Opengrep', status: 'OK', findings: 21, durationMs: 41200, note: null },
    { name: 'PMD', status: 'OK', findings: 18, durationMs: 28900, note: null },
    { name: 'Trivy', status: 'OK', findings: 7, durationMs: 52000, note: null },
  ],
  projectMap: {
    languages: { Java: 41200, XML: 4100, YAML: 1900 }, buildTools: ['Maven'], frameworks: ['Spring Boot'], entryPoints: ['ContaApplication.java'],
    ciConfigs: ['.github/workflows/ci.yml'], dockerfiles: ['Dockerfile'], iacFiles: [], docs: ['README.md'], lockfiles: [], sourceFiles: 412, testFiles: 96,
    hasReadme: true, readmeLines: 64, hasGitignore: true, hasLicense: false, truncated: false,
  },
};

export const planoDeCorrecao = {
  scoreNow: 61, scoreIfCriticalFixed: 72, criticalCount: 2,
  items: [
    { ruleId: 'java.sql-injection', title: 'Injeção de SQL por concatenação de texto', severity: 'CRITICAL', category: 'SECURITY', occurrences: 3, scoreGain: 7, exampleFile: 'ContaRepository.java', exampleLine: 88, howToFix: 'Use PreparedStatement com parâmetros.' },
    { ruleId: 'secrets.api-key', title: 'Segredo gravado no código', severity: 'CRITICAL', category: 'SECURITY', occurrences: 1, scoreGain: 4, exampleFile: 'application.properties', exampleLine: 14, howToFix: 'Mova para variável de ambiente e rode a rotação da chave.' },
    { ruleId: 'trivy.cve', title: 'Dependência com CVE conhecido', severity: 'HIGH', category: 'DEPENDENCY', occurrences: 7, scoreGain: 5, exampleFile: 'pom.xml', exampleLine: 112, howToFix: 'Atualize para a versão corrigida.' },
  ],
};

export const explicacaoDaNota = {
  curve: 'exponencial', sizeFactor: 1.2, linesOfCode: 48210, overallBefore: 61, overall: 61, removedRules: [],
  dimensions: ['SECURITY', 'QUALITY', 'ARCHITECTURE', 'DEPENDENCY', 'OBSERVABILITY', 'PERFORMANCE'].map((categoria, i) => ({
    category: categoria, weight: [0.3, 0.2, 0.2, 0.15, 0.1, 0.05][i], score: [48, 66, 58, 55, 72, 70][i], penalty: [52, 34, 42, 45, 28, 30][i], hidden: 0,
    groups: [{ ruleId: 'regra.exemplo', severity: 'HIGH', count: 4, raw: 20, counted: 14, capped: true }],
  })),
};

export const visaoDeAchados = {
  total: 59,
  bySystem: sistemas.slice(0, 4).map((s, i) => ({
    systemId: s.id, systemName: s.name, auditId: 101 - i, count: [59, 21, 74, 12][i], stack: s.stack, critical: s.openCritical, high: s.openHigh, auditedAt: s.lastAuditDate,
  })),
  items: achados.map((f) => ({ systemId: 1, systemName: 'conta-corrente-api', auditId: 101, finding: f })),
};

export const pacotes = {
  packs: [
    { id: 'java-spring', name: 'Java e Spring Boot', version: '12.123.0', description: 'Regras de segurança, qualidade e arquitetura.', enabledByDefault: true, semgrepRules: 214, remediations: 96 },
    { id: 'angular-ts', name: 'Angular e TypeScript', version: '12.123.0', description: 'Regras do Angular Style Guide e TypeScript.', enabledByDefault: true, semgrepRules: 132, remediations: 61 },
    { id: 'docker-k8s', name: 'Docker e Kubernetes', version: '12.123.0', description: 'Boas práticas de contêiner e manifestos.', enabledByDefault: true, semgrepRules: 58, remediations: 30 },
  ],
  profiles: [{ id: 'default', name: 'Padrão', description: 'Perfil padrão do Sentinel.', extendsId: null, packs: null, ruleOverrides: 0, thresholds: {} }],
  rejected: {}, warnings: [], loadedAt: haHoras(5),
};

export const configuracaoDoMotor = {
  profile: 'default', profileDescription: 'Perfil padrão do Sentinel.', adjustments: 2,
  tuning: { rules: [], thresholds: {}, enablePacks: [], disablePacks: [], disableAnalyzers: [], ignorePaths: ['**/generated/**'], disableSides: [] },
  profileRules: [],
  packs: pacotes.packs.map((p) => ({ id: p.id, name: p.name, description: p.description, inProfile: true, active: true })),
  analyzers: [
    { name: 'Opengrep', description: 'Procura padrões inseguros no código.', disabled: false, side: 'common' },
    { name: 'PMD', description: 'Aponta más práticas em Java.', disabled: false, side: 'back' },
    { name: 'ESLint', description: 'Confere o estilo e erros em TypeScript.', disabled: false, side: 'front' },
  ],
  thresholds: [], knownRules: [{ id: 'java.sql-injection', count: 3 }, { id: 'arq.camadas', count: 12 }],
  catalog: [
    { id: 'java.sql-injection', pack: 'java-spring', message: 'Injeção de SQL', severity: 'CRITICAL', category: 'SECURITY', side: 'back' },
    { id: 'angular.nomes-privados', pack: 'angular-ts', message: 'Membro privado sem sublinhado', severity: 'LOW', category: 'QUALITY', side: 'front' },
  ],
};

export const acessoMcp = {
  enabled: true, hasKey: true, keyPrefix: 'sk_snt_9a3f', keyCreatedAt: haHoras(120), dataLevel: 'TITLES',
  tools: [
    { name: 'listar_achados', description: 'Lista os achados da última auditoria.', writes: false, enabled: true },
    { name: 'ver_nota', description: 'Mostra a nota e as dimensões.', writes: false, enabled: true },
  ],
};

export const revisaoDoClaude = {
  openSuggestions: 2,
  suggestions: [
    { id: 1, title: 'Agrupar consultas de conta em um único repositório', detail: 'Há três classes com a mesma consulta.', ruleRef: 'arq.camadas', file: 'ContaService.java', line: 120, status: 'OPEN', createdAt: haHoras(6) },
    { id: 2, title: 'Cobrir ExtratoService com teste', detail: null, ruleRef: null, file: 'ExtratoService.java', line: null, status: 'OPEN', createdAt: haHoras(8) },
  ],
  proposals: [],
};

export const metricasDaRevisao = {
  suggestionsTotal: 14, suggestionsOpen: 2, suggestionsAddressed: 9, suggestionsDismissed: 3, suggestionAdoption: 0.75,
  rulesTotal: 3, rulesInProgress: 1, rulesPromoted: 1, rulesDiscarded: 1, rulePromotion: 0.5,
};

const configuracao = (chave, secao, grupo, rotulo, ajuda, tipo, padrao, valor, extra = {}) => ({
  key: chave, section: secao, group: grupo, label: rotulo, help: ajuda, kind: tipo, def: padrao, options: [], unit: '', min: 0, max: 0,
  editable: true, secret: false, value: valor, savedValue: null, source: 'padrão', pendingRestart: false, ...extra,
});

export const configuracoes = {
  restartRequired: false,
  settings: [
    configuracao('sentinel.audit.timeout', 'ferr', 'Limites', 'Tempo máximo de uma auditoria', 'Depois disso a auditoria é cancelada.', 'number', '30', '30', { unit: 'min', min: 5, max: 240 }),
    configuracao('sentinel.joern.enabled', 'ferr', 'Analisadores', 'Usar o Joern (fluxo entre arquivos)', 'Procura injeções que cruzam arquivos. Pede mais memória.', 'bool', 'false', 'true', { savedValue: 'true', source: 'tela' }),
    configuracao('sentinel.opengrep.enabled', 'ferr', 'Analisadores', 'Usar o Opengrep', 'Procura padrões inseguros no código.', 'bool', 'true', 'true'),
    configuracao('sentinel.packs.disabled', 'pacotes', 'Pacotes', 'Pacotes desativados', 'Ids separados por vírgula.', 'text', '', ''),
    configuracao('sentinel.git.timeout', 'origem', 'Git', 'Tempo limite do clone', 'Em segundos.', 'number', '300', '300', { unit: 's', min: 30, max: 3600 }),
    configuracao('sentinel.evidence.min', 'origem', 'Evidências', 'Cobertura mínima de testes', 'Abaixo disso o relatório avisa.', 'number', '60', '60', { unit: '%', min: 0, max: 100 }),
    configuracao('sentinel.api.key', 'seg', 'Acesso', 'Chave da API', 'Só se muda no ambiente.', 'text', '', '••••••••', { editable: false, secret: true, source: 'ambiente' }),
    configuracao('sentinel.db.url', 'bd', 'Conexão', 'Endereço do banco', 'Só se muda no ambiente.', 'text', '', 'jdbc:postgresql://db:5432/sentinel', { editable: false, source: 'ambiente' }),
  ],
};

export const guia = {
  stacks: [
    {
      id: 'java-spring', name: 'Java e Spring Boot', kind: 'backend', summary: 'Camadas, transação nos serviços e consultas parametrizadas.', tools: ['Opengrep', 'PMD', 'Checkstyle'],
      gaps: ['Cobertura de testes'], commands: ['mvn -q verify'], commandsVerified: true, review: { docs: true, team: true, ai: true },
      practices: [{ title: 'Camadas', doIt: 'Controller chama serviço; serviço chama repositório.', avoid: 'Controller falando com o repositório.' }],
      baseLanguage: false, ownRules: 18, inheritedRules: 40, rules: [{ id: 'java.sql-injection', own: true }],
    },
    {
      id: 'angular', name: 'Angular', kind: 'frontend', summary: 'Componentes pequenos, tipagem forte e sem chamada direta à API.', tools: ['ESLint', 'Opengrep'],
      gaps: [], commands: ['npm run lint'], commandsVerified: true, review: { docs: true, team: false, ai: true },
      practices: [{ title: 'Serviços', doIt: 'Acesse a API por serviços.', avoid: 'HttpClient dentro do componente.' }],
      baseLanguage: false, ownRules: 12, inheritedRules: 30, rules: [],
    },
  ],
  rules: [
    { id: 'java.sql-injection', pack: 'java-spring', title: 'Injeção de SQL', action: 'Use consulta parametrizada.', severity: 'alta', cwe: 'CWE-89', before: 'String sql = "..." + id;', after: 'ps.setLong(1, id);' },
  ],
  process: [{ title: 'Commit e PR', doIt: 'Commits pequenos, PR com contexto.', avoid: 'PR gigante sem descrição.' }],
};
