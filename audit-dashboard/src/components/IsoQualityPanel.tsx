import { InlineNotification, SkeletonText } from '@carbon/react';
import { useState } from 'react';
import { useIsoQuality, useIsoTrend } from '../hooks/useAuditRuns';
import type { IAuditRunResponse, IIsoCharacteristic, IIsoQuality, IsoProfile } from '../types';
import { isoDelta, isoDeltaLabel, isoDeltaSr, isoProfileHints, isoProfileLabels, isoSituacao, orderedCharacteristics } from '../utils/isoQuality';
import { scoreBand, scoreColor } from '../utils/scoreBand';

const box = { background: 'var(--s-surface)', border: '1px solid var(--s-border)', borderRadius: 8, padding: '1.25rem 1.5rem' } as const;
const cell = { padding: '8px', textAlign: 'left', verticalAlign: 'top', fontSize: 13 } as const;
const right = { ...cell, textAlign: 'right' } as const;
const num = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

function Row({ c, delta, first }: { c: IIsoCharacteristic; delta: number | null; first: boolean }) {
  const band = scoreBand(c.nota);
  return (
    <tr style={{ borderTop: '1px solid var(--s-border)' }}>
      <th scope="row" style={{ ...cell, fontWeight: first ? 600 : 400 }}>{c.nome}</th>
      <td style={{ ...right, fontWeight: 600, color: scoreColor(c.nota) }}>{c.nota === null ? '—' : num.format(c.nota)}</td>
      <td style={{ ...cell, color: scoreColor(c.nota) }}>{isoSituacao(band)}</td>
      <td style={right}>{c.achados}</td>
      <td style={{ ...right, color: 'var(--s-text-3)' }}>
        <span aria-hidden="true">{isoDeltaLabel(delta)}</span>
        <span className="sentinel-sr-only">{isoDeltaSr(delta)}</span>
      </td>
    </tr>
  );
}

const CABECALHO = [['Característica', cell], ['Nota', right], ['Situação', cell], ['Achados', right], ['Desde a anterior', right]] as const;

function NaoAvaliadas({ lista }: { lista: IIsoCharacteristic[] }) {
  if (lista.length === 0) return null;
  return (
    <details style={{ marginTop: 8, fontSize: 13, color: 'var(--s-text-3)' }}>
      <summary style={{ cursor: 'pointer', padding: '12px 0' }}>
        {lista.length === 1 ? '1 característica ainda não avaliada' : `${lista.length} características ainda não avaliadas`} pelo Sentinel
      </summary>
      <ul style={{ margin: '0 0 4px', paddingLeft: 20, lineHeight: 1.7 }}>
        {lista.map((c) => <li key={c.id}>{c.nome}</li>)}
      </ul>
      <p style={{ margin: 0 }}>Sem regras para elas, o Sentinel não inventa nota.</p>
    </details>
  );
}

interface IResultadoProps {
  notaPonderada: number;
  avaliadas: IIsoCharacteristic[];
  naoAvaliadas: IIsoCharacteristic[];
  trend: Parameters<typeof isoDelta>[0];
}

function Resultado({ notaPonderada, avaliadas, naoAvaliadas, trend }: IResultadoProps) {
  return (
    <div aria-live="polite">
      <p style={{ margin: '0.75rem 0', fontSize: 14, display: 'flex', flexWrap: 'wrap', gap: '4px 24px', alignItems: 'baseline' }}>
        <span>Nota ponderada <strong style={{ fontSize: 20, color: scoreColor(notaPonderada) }}>{num.format(notaPonderada)}</strong></span>
        {avaliadas[0] && (
          <span style={{ background: 'var(--s-selected)', borderRadius: 4, padding: '4px 10px', fontSize: 13 }}>
            <strong>Comece por aqui:</strong> {avaliadas[0].nome}
          </span>
        )}
      </p>
      <table aria-label="Notas por característica de qualidade" style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr>
            {CABECALHO.map(([titulo, estilo]) => (
              <th key={titulo} scope="col" style={{ ...estilo, color: 'var(--s-text-3)', fontWeight: 400 }}>{titulo}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {avaliadas.map((c, i) => <Row key={c.id} c={c} delta={isoDelta(trend, c.id)} first={i === 0} />)}
        </tbody>
      </table>
      <NaoAvaliadas lista={naoAvaliadas} />
    </div>
  );
}

function SeletorDePerfil({ profile, onChange }: { profile: IsoProfile; onChange: (perfil: IsoProfile) => void }) {
  return (
    <label style={{ fontSize: 13, display: 'grid', gap: 4 }}>
      Perfil de pesos
      <select value={profile} onChange={(evento) => onChange(evento.target.value as IsoProfile)} style={{ height: 40, padding: '0 8px' }}>
        {(Object.keys(isoProfileLabels) as IsoProfile[]).map((chave) => <option key={chave} value={chave}>{isoProfileLabels[chave]}</option>)}
      </select>
    </label>
  );
}

function ErroDaQualidade({ error }: { error: unknown }) {
  const mensagem = error instanceof Error ? error.message : 'Erro desconhecido';
  return <InlineNotification kind="error" title="Não foi possível calcular a qualidade ISO" subtitle={mensagem} hideCloseButton />;
}

/** Ordena as características pela prioridade e separa as que têm nota das que não foram avaliadas; nulo se a resposta veio sem a lista. */
function separarCaracteristicas(data: IIsoQuality | undefined) {
  if (!data || !Array.isArray(data.caracteristicas)) return null;
  const ordered = orderedCharacteristics(data.caracteristicas, data.prioridade);
  return {
    avaliadas: ordered.filter((c) => c.avaliada && c.nota !== null),
    naoAvaliadas: ordered.filter((c) => !c.avaliada || c.nota === null),
  };
}

/** Qualidade por característica da ISO/IEC 25010: a que mais merece atenção em destaque e as não avaliadas recolhidas. */
export function IsoQualityPanel({ run }: { run: IAuditRunResponse }) {
  const [profile, setProfile] = useState<IsoProfile>('padrao');
  const done = run.status === 'COMPLETED';
  const { data, isLoading, error } = useIsoQuality({ auditId: run.id, perfil: profile, enabled: done });
  const trend = useIsoTrend({ systemId: run.systemId, enabled: done });
  if (!done) return null;

  const grupos = separarCaracteristicas(data);

  return (
    <section aria-label="Qualidade ISO/IEC 25010" style={box}>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'flex-start', justifyContent: 'space-between' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>Qualidade ISO/IEC 25010</h3>
          <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--s-text-3)' }}>Alinhamento de vocabulário, não certificação.</p>
        </div>
        <SeletorDePerfil profile={profile} onChange={setProfile} />
      </div>
      <p style={{ margin: '8px 0 0', fontSize: 13, color: 'var(--s-text-3)' }}>{isoProfileHints[profile]}</p>
      {isLoading && <SkeletonText paragraph lineCount={4} />}
      {error && <ErroDaQualidade error={error} />}
      {data && grupos && <Resultado notaPonderada={data.notaPonderada} avaliadas={grupos.avaliadas} naoAvaliadas={grupos.naoAvaliadas} trend={trend.data} />}
    </section>
  );
}
