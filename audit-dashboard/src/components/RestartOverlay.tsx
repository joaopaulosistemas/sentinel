import { CheckmarkFilled, CircleDash } from '@carbon/icons-react';
import { Button, InlineLoading, Loading } from '@carbon/react';
import { useEffect, useRef, useState } from 'react';

/** Depois disso, a tela deixa de prometer que a API volta sozinha e oferece uma saída. */
export const RESTART_TIMEOUT_SECONDS = 90;

export type RestartPhase = 'requesting' | 'waiting';

interface Props {
  /** requesting: o pedido de reinício está a caminho; waiting: a API foi encerrada e a tela espera ela voltar. */
  phase: RestartPhase;
  /** Pede mais um período de espera (reinicia o cronômetro). */
  onKeepWaiting: () => void;
  /** Libera a tela sem esperar a API voltar. */
  onClose: () => void;
}

type StepState = 'done' | 'active' | 'pending';

function Step({ state, label, detail }: { state: StepState; label: string; detail?: string }) {
  return (
    <li style={{ display: 'flex', gap: 12, alignItems: 'flex-start', padding: '6px 0' }} data-state={state}>
      <span aria-hidden="true" style={{ flex: '0 0 20px', display: 'flex', justifyContent: 'center', paddingTop: 1 }}>
        {state === 'done' && <CheckmarkFilled size={18} style={{ fill: '#24a148' }} />}
        {state === 'active' && <InlineLoading status="active" />}
        {state === 'pending' && <CircleDash size={18} style={{ fill: 'var(--s-text-3)' }} />}
      </span>
      <span style={{ minWidth: 0 }}>
        <span style={{ fontWeight: state === 'active' ? 600 : 400, color: state === 'pending' ? 'var(--s-text-3)' : 'var(--s-text-1, inherit)' }}>{label}</span>
        {detail && <span style={{ display: 'block', fontSize: 13, color: 'var(--s-text-3)' }}>{detail}</span>}
      </span>
    </li>
  );
}

function useElapsedSeconds(): number {
  const [elapsed, setElapsed] = useState(0);
  useEffect(() => {
    const id = window.setInterval(() => setElapsed((s) => s + 1), 1000);
    return () => window.clearInterval(id);
  }, []);
  return elapsed;
}

/** Trava a rolagem da página por trás enquanto o aviso está aberto. */
function useBodyScrollLock(): void {
  useEffect(() => {
    const anterior = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = anterior;
    };
  }, []);
}

/** O foco entra no aviso e, ao fechar, volta para quem o tinha. */
function useFocusOnMount(box: React.RefObject<HTMLDivElement>): void {
  useEffect(() => {
    const anterior = document.activeElement as HTMLElement | null;
    box.current?.focus();
    return () => anterior?.focus?.();
  }, [box]);
}

/** Mantém o Tab dentro do aviso: nada do que está por trás pode ser acionado pelo teclado. */
function trapTab(e: React.KeyboardEvent<HTMLDivElement>, box: HTMLDivElement | null): void {
  const focaveis = Array.from(box?.querySelectorAll<HTMLElement>('button:not([disabled])') ?? []);
  if (focaveis.length === 0) {
    e.preventDefault();
    box?.focus();
    return;
  }
  const primeiro = focaveis[0];
  const ultimo = focaveis[focaveis.length - 1];
  const ativo = document.activeElement;
  if (e.shiftKey && (ativo === primeiro || ativo === box)) {
    e.preventDefault();
    ultimo.focus();
  } else if (!e.shiftKey && ativo === ultimo) {
    e.preventDefault();
    primeiro.focus();
  }
}

function TimedOutText() {
  return (
    <>
      <p style={{ margin: '0 0 8px' }}>
        Já se passaram mais de {RESTART_TIMEOUT_SECONDS} segundos e a API ainda não respondeu. O normal são alguns segundos.
      </p>
      <p style={{ margin: '0 0 8px' }}>Causas mais comuns:</p>
      <ul style={{ margin: '0 0 8px', paddingLeft: 20 }}>
        <li>O contêiner da API não tem reinício automático. No Docker Compose, confira <code>restart: unless-stopped</code> no serviço <code>audit-api</code>.</li>
        <li>Um valor salvo impede a API de subir. Veja os logs com <code>docker compose logs audit-api</code>.</li>
      </ul>
      <p style={{ margin: 0 }}>Os valores continuam salvos. A tela segue conferindo e libera sozinha assim que a API responder.</p>
    </>
  );
}

function RestartingText() {
  return (
    <>
      <p style={{ margin: '0 0 8px' }}>
        As configurações que você salvou só são lidas quando a API sobe. Por isso ela está sendo encerrada e iniciada de novo, e fica fora do ar por alguns segundos.
      </p>
      <p style={{ margin: 0 }}>
        A tela está travada de propósito: qualquer ação agora cairia numa API desligada. Auditorias em andamento são interrompidas. Ela libera sozinha quando a API voltar.
      </p>
    </>
  );
}

function RestartSteps({ phase, elapsed }: { phase: RestartPhase; elapsed: number }) {
  const requesting = phase === 'requesting';
  return (
    <ol aria-label="Etapas do reinício" style={{ listStyle: 'none', margin: '20px 0 0', padding: '12px 0 0', borderTop: '1px solid var(--s-border)' }}>
      <Step state={requesting ? 'active' : 'done'} label={requesting ? 'Enviando o pedido de reinício' : 'Pedido de reinício enviado'} />
      <Step
        state={requesting ? 'pending' : 'active'}
        label="API encerrando e subindo de novo"
        detail={requesting ? undefined : `Aguardando há ${elapsed} s`}
      />
      <Step state="pending" label="Conferindo se as novas configurações entraram em uso" />
    </ol>
  );
}

function statusMessage(phase: RestartPhase, timedOut: boolean): string {
  if (timedOut) return 'A API está demorando para voltar.';
  return phase === 'requesting' ? 'Enviando o pedido de reinício.' : 'Aguardando a API voltar.';
}

/**
 * Trava a tela inteira enquanto a API reinicia e explica o que está acontecendo.
 * Sem isso, quem usa a tela continua clicando numa API que está fora do ar e vê erros sem sentido.
 */
export function RestartOverlay({ phase, onKeepWaiting, onClose }: Props) {
  const elapsed = useElapsedSeconds();
  const box = useRef<HTMLDivElement>(null);
  const timedOut = phase === 'waiting' && elapsed >= RESTART_TIMEOUT_SECONDS;

  useBodyScrollLock();
  useFocusOnMount(box);

  const onKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Escape' && timedOut) onClose();
    else if (e.key === 'Tab') trapTab(e, box.current);
  };

  const titulo = timedOut ? 'A API está demorando para voltar' : 'Reiniciando a API';

  return (
    <div
      data-testid="restart-overlay"
      style={{ position: 'fixed', inset: 0, zIndex: 9500, background: 'rgba(22, 22, 22, 0.78)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16 }}
    >
      <div
        ref={box}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="restart-overlay-title"
        aria-describedby="restart-overlay-desc"
        tabIndex={-1}
        onKeyDown={onKeyDown}
        style={{ width: '100%', maxWidth: 560, maxHeight: '100%', overflowY: 'auto', background: 'var(--s-surface)', color: 'var(--s-text-2)', border: '1px solid var(--s-border)', borderTop: `4px solid ${timedOut ? '#f1c21b' : '#0f62fe'}`, padding: 32, outline: 'none' }}
      >
        <div style={{ display: 'flex', gap: 16, alignItems: 'center', marginBottom: 16 }}>
          {!timedOut && <Loading withOverlay={false} small description="Reiniciando a API" />}
          <h2 id="restart-overlay-title" style={{ margin: 0, fontSize: 24, fontWeight: 400 }}>{titulo}</h2>
        </div>

        <div id="restart-overlay-desc" style={{ fontSize: 14, lineHeight: 1.5 }}>
          {timedOut ? <TimedOutText /> : <RestartingText />}
        </div>

        <RestartSteps phase={phase} elapsed={elapsed} />

        <div role="status" aria-live="polite" style={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' }}>
          {statusMessage(phase, timedOut)}
        </div>

        {timedOut && (
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 24 }}>
            <Button kind="secondary" onClick={onClose}>Fechar este aviso</Button>
            <Button kind="primary" onClick={onKeepWaiting}>Continuar esperando</Button>
          </div>
        )}
      </div>
    </div>
  );
}
