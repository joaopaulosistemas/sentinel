import { DataTableSkeleton, InlineNotification } from '@carbon/react';
import { useState } from 'react';
import { PageShell } from '../../components/PageShell';
import { useGuide } from '../../hooks/useGuide';
import type { GuideResponse, GuideStack } from '../../types';
import { rulesOfStack } from '../../utils/guide';
import { AiPanel } from './AiPanel';
import { AudienceSwitch, GapsNotice, GradePanel, PracticeGrid, RulesList, StackPicker, SummaryCards } from './GuideParts';

const DEFAULT_STACK = 'quarkus';

function PeopleView({ stack, guide }: { stack: GuideStack; guide: GuideResponse }) {
  return (
    <>
      <PracticeGrid stack={stack} />
      <GapsNotice stack={stack} />
      <RulesList key={stack.id} rules={rulesOfStack(stack, guide.rules)} />
      <section aria-labelledby="guia-comandos" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)', padding: '1rem 1.25rem' }}>
        <h2 id="guia-comandos" style={{ margin: '0 0 8px', fontSize: '1rem', fontWeight: 600 }}>Comandos de {stack.name}</h2>
        {stack.commands.map((c) => <code key={c} style={{ display: 'block', padding: '6px 10px', marginBottom: 4, background: 'var(--s-chrome)', color: '#f4f4f4' }}>{c}</code>)}
        <p style={{ margin: '6px 0 0', fontSize: '0.8125rem', color: 'var(--s-text-3)' }}>
          {stack.commandsVerified ? 'Conferidos no CI do próprio Sentinel.' : 'Padrão do ecossistema; confirme no seu sistema antes de usar no CLAUDE.md.'}
        </p>
      </section>
    </>
  );
}

/** Guia de código: boas práticas e regras do Sentinel por tecnologia, para pessoas e para a IA (CLAUDE.md). */
export function GuidePage() {
  const { data, isLoading, error } = useGuide();
  const [stackId, setStackId] = useState(DEFAULT_STACK);
  const [ai, setAi] = useState(false);

  if (isLoading) return <DataTableSkeleton columnCount={4} rowCount={6} />;
  if (error || !data) {
    return <InlineNotification kind="error" title="Erro ao carregar o guia" subtitle={error instanceof Error ? error.message : 'Erro desconhecido'} />;
  }
  const stack = data.stacks.find((s) => s.id === stackId) ?? data.stacks[0];
  return (
    <PageShell crumbs={[{ label: 'Início', to: '/' }, { label: 'Guia de código' }]}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: '1rem', flexWrap: 'wrap' }}>
        <div>
          <h1 style={{ margin: '0 0 0.25rem', fontSize: 26, fontWeight: 400 }}>Guia de código</h1>
          <p style={{ color: 'var(--s-text-3)' }}>O que o Sentinel cobra em cada tecnologia, para a equipe ler e para a IA seguir.</p>
        </div>
        <AudienceSwitch ai={ai} onChange={setAi} />
      </div>
      {stack ? (
        <>
          <StackPicker stacks={data.stacks} current={stack.id} onPick={setStackId} />
          <SummaryCards stack={stack} />
          {ai ? <AiPanel stack={stack} /> : <PeopleView stack={stack} guide={data} />}
          <GradePanel stack={stack} />
        </>
      ) : (
        <InlineNotification kind="warning" title="Nenhuma tecnologia cadastrada" subtitle="O arquivo guia/stacks.yml está vazio ou inválido." hideCloseButton />
      )}
    </PageShell>
  );
}
