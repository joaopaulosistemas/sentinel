import { Tag } from '@carbon/react';
import type { IProjectMapInfo } from '../types';
import { groupLimits, languageShares, testRatioPercent, type ILanguageShare, type ILimitGroup } from '../utils/projectLimits';
import { PageSection } from './PageSection';

const OPEN_LIMIT = 3;
const SEGMENT_COLORS = ['var(--s-link)', 'var(--s-purple-text)', 'var(--s-success-strong)', 'var(--s-warn-text)', 'var(--s-high-text)', 'var(--s-text-3)', 'var(--s-border-strong)'];

const subTitle: React.CSSProperties = { margin: '0 0 4px', fontSize: 16, fontWeight: 600 };
const hintStyle: React.CSSProperties = { margin: '0 0 16px', color: 'var(--s-text-3)', fontSize: 13 };

function LimitGroup({ group }: { group: ILimitGroup }) {
  return (
    <details open={group.items.length <= OPEN_LIMIT} style={{ borderTop: '1px solid var(--s-border)', padding: '10px 0' }}>
      <summary style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 600 }}>
        <span>{group.title}</span>
        <Tag type="gray" size="sm">{group.items.length}</Tag>
      </summary>
      <ul style={{ margin: '8px 0 0', paddingLeft: 18, color: 'var(--s-text-2)', fontSize: 13, display: 'grid', gap: 6 }}>
        {group.items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </details>
  );
}

function UnverifiedColumn({ unverified }: { unverified: string[] }) {
  const groups = groupLimits(unverified);
  return (
    <div>
      <h3 style={subTitle}>Não foi possível verificar</h3>
      <p style={hintStyle}>Análise estática não alcança estes pontos. Não os trate como aprovados.</p>
      {groups.length === 0 ? (
        <p style={{ margin: 0, fontSize: 13, color: 'var(--s-text-2)' }}>Nenhum ponto sem cobertura nesta auditoria.</p>
      ) : (
        <div style={{ borderBottom: '1px solid var(--s-border)' }}>
          {groups.map((group) => (
            <LimitGroup key={group.id} group={group} />
          ))}
        </div>
      )}
    </div>
  );
}

function Counter({ value, label }: { value: string; label: string }) {
  return (
    <div>
      <div style={{ fontSize: 28, fontWeight: 300, lineHeight: 1.1 }}>{value}</div>
      <div style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{label}</div>
    </div>
  );
}

function LanguageBar({ shares }: { shares: ILanguageShare[] }) {
  const summary = shares.map((s) => `${s.name} ${Math.round(s.percent)}%`).join(', ');
  return (
    <div style={{ marginTop: 20 }}>
      <div role="img" aria-label={`Linguagens por arquivos: ${summary}`} style={{ display: 'flex', height: 8, overflow: 'hidden', background: 'var(--s-border)' }}>
        {shares.map((s, index) => (
          <div key={s.name} style={{ width: `${s.percent}%`, background: SEGMENT_COLORS[index % SEGMENT_COLORS.length] }} />
        ))}
      </div>
      <ul style={{ listStyle: 'none', margin: '10px 0 0', padding: 0, display: 'flex', flexWrap: 'wrap', gap: '4px 16px', fontSize: 13 }}>
        {shares.map((s, index) => (
          <li key={s.name} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span aria-hidden style={{ width: 10, height: 10, background: SEGMENT_COLORS[index % SEGMENT_COLORS.length] }} />
            <span>{s.name}</span>
            <span style={{ color: 'var(--s-text-3)' }}>{s.files} · {Math.round(s.percent)}%</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function TagRow({ label, values }: { label: string; values: string[] }) {
  if (values.length === 0) return null;
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '120px 1fr', gap: 8, alignItems: 'start', padding: '8px 0', borderTop: '1px solid var(--s-border)' }}>
      <dt style={{ fontSize: 12, color: 'var(--s-text-3)', paddingTop: 4 }}>{label}</dt>
      <dd style={{ margin: 0, display: 'flex', flexWrap: 'wrap', gap: 4 }}>
        {values.map((value) => (
          <Tag key={value} type="cool-gray" size="sm">{value}</Tag>
        ))}
      </dd>
    </div>
  );
}

function Hygiene({ map }: { map: IProjectMapInfo }) {
  const checks = [
    { label: 'README', ok: map.hasReadme },
    { label: '.gitignore', ok: map.hasGitignore },
    { label: 'Licença', ok: map.hasLicense },
  ];
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '120px 1fr', gap: 8, padding: '8px 0', borderTop: '1px solid var(--s-border)' }}>
      <dt style={{ fontSize: 12, color: 'var(--s-text-3)', paddingTop: 4 }}>Arquivos básicos</dt>
      <dd style={{ margin: 0, display: 'flex', flexWrap: 'wrap', gap: 4 }}>
        {checks.map((check) => (
          <Tag key={check.label} type={check.ok ? 'green' : 'gray'} size="sm">
            {check.label}: {check.ok ? 'tem' : 'não tem'}
          </Tag>
        ))}
      </dd>
    </div>
  );
}

function ProjectMapColumn({ map }: { map: IProjectMapInfo }) {
  const shares = languageShares(map.languages);
  const ratio = testRatioPercent(map.sourceFiles, map.testFiles);
  return (
    <div>
      <h3 style={subTitle}>Mapa do projeto</h3>
      <p style={hintStyle}>O que o Sentinel encontrou no repositório{map.truncated ? ' (leitura parcial: o repositório passou do limite de arquivos)' : ''}.</p>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '16px 40px' }}>
        <Counter value={map.sourceFiles.toLocaleString('pt-BR')} label="arquivos de código" />
        <Counter value={map.testFiles.toLocaleString('pt-BR')} label="arquivos de teste" />
        <Counter value={ratio === null ? '—' : `${ratio}%`} label="testes por arquivo de código" />
      </div>
      {shares.length > 0 ? <LanguageBar shares={shares} /> : <p style={{ margin: '16px 0 0', fontSize: 13, color: 'var(--s-text-2)' }}>Nenhuma linguagem reconhecida.</p>}
      <dl style={{ margin: '20px 0 0' }}>
        <TagRow label="Frameworks" values={map.frameworks} />
        <TagRow label="Build" values={map.buildTools} />
        <TagRow label="Integração contínua" values={map.ciConfigs} />
        <TagRow label="Docker" values={map.dockerfiles} />
        <TagRow label="Infraestrutura" values={map.iacFiles} />
        <TagRow label="Pontos de entrada" values={map.entryPoints} />
        <TagRow label="Lockfiles" values={map.lockfiles} />
        <Hygiene map={map} />
      </dl>
    </div>
  );
}

/** O que a análise não alcança (agrupado) e o mapa do projeto (números, barra de linguagens e etiquetas). */
export function ProjectLimitsSection({ unverified, map }: { unverified: string[]; map: IProjectMapInfo | null }) {
  return (
    <PageSection id="limites" title="Limites e mapa do projeto">
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(360px, 100%), 1fr))', gap: 48 }}>
        <UnverifiedColumn unverified={unverified} />
        {map && <ProjectMapColumn map={map} />}
      </div>
    </PageSection>
  );
}
