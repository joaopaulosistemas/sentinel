import { Button, InlineNotification, SkeletonText } from '@carbon/react';
import { useState } from 'react';
import { useGuideClaudeMd } from '../../hooks/useGuide';
import type { GuideStack } from '../../types';

const card = { background: 'var(--s-surface)', border: '1px solid var(--s-border)', padding: '1rem 1.25rem' } as const;
const muted = { color: 'var(--s-text-3)', fontSize: '0.875rem' } as const;

/** Salva o texto como arquivo; fora do navegador (ou se o download for bloqueado) não faz nada. */
export function downloadText(name: string, text: string): void {
  try {
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([text], { type: 'text/markdown' }));
    link.download = name;
    link.click();
    URL.revokeObjectURL(link.href);
  } catch {
    // sem download no ambiente: o botão de copiar continua valendo
  }
}

function Aside() {
  return (
    <aside aria-label="Como a IA usa este arquivo" style={{ display: 'grid', gap: 12, alignContent: 'start' }}>
      <div style={{ ...card, ...muted }}>
        <strong style={{ display: 'block', color: 'var(--s-text)', marginBottom: 4 }}>Por que é curto</strong>
        O arquivo entra no contexto de toda tarefa da IA. Por isso só leva o que ela não deduz sozinha: comandos, armadilhas e o que não se negocia. O catálogo completo fica no guia para pessoas.
      </div>
      <div style={{ ...card, ...muted }}>
        <strong style={{ display: 'block', color: 'var(--s-text)', marginBottom: 4 }}>Onde colocar</strong>
        Na raiz do repositório do sistema, com o nome CLAUDE.md. O que não puder falhar vai também para o CI ou para um hook: texto sozinho não impede nada.
      </div>
      <div style={{ ...card, ...muted, background: 'var(--s-warn-bg)' }}>
        <strong style={{ display: 'block', color: 'var(--s-warn-text)', marginBottom: 4 }}>Antes de adotar</strong>
        Rode uma tarefa real com uma IA seguindo este arquivo e corrija o que ela errar.
      </div>
    </aside>
  );
}

export function AiPanel({ stack }: { stack: GuideStack }) {
  const { data, isLoading, error } = useGuideClaudeMd(stack.id, true);
  const [copied, setCopied] = useState(false);
  const copy = () => {
    void navigator.clipboard?.writeText(data ?? '').then(() => setCopied(true), () => setCopied(false));
  };
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'flex-start' }}>
      <section aria-labelledby="guia-md" style={{ ...card, flex: '999 1 560px', minWidth: 0, display: 'grid', gap: 12 }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
          <h2 id="guia-md" style={{ margin: 0, fontSize: '1.25rem', fontWeight: 600 }}>CLAUDE.md para {stack.name}</h2>
          <div style={{ display: 'flex', gap: 8 }}>
            <Button size="md" disabled={!data} onClick={copy}>{copied ? 'Copiado' : 'Copiar'}</Button>
            <Button size="md" kind="tertiary" disabled={!data} onClick={() => downloadText('CLAUDE.md', data ?? '')}>Baixar arquivo</Button>
          </div>
        </div>
        {isLoading && <SkeletonText paragraph lineCount={8} />}
        {error && <InlineNotification kind="error" title="Erro ao gerar o CLAUDE.md" subtitle={error instanceof Error ? error.message : ''} hideCloseButton />}
        {data && (
          <>
            <span style={muted}>{data.trimEnd().split('\n').length} linhas (meta: abaixo de 200)</span>
            <pre style={{ margin: 0, padding: 16, background: 'var(--s-chrome)', color: '#f4f4f4', fontSize: '0.8125rem', lineHeight: 1.6, whiteSpace: 'pre-wrap', overflow: 'auto', maxHeight: 640 }}>{data}</pre>
          </>
        )}
      </section>
      <div style={{ flex: '1 1 260px', minWidth: 0 }}><Aside /></div>
    </div>
  );
}
