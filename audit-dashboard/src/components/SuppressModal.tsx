import { InlineNotification, Modal, Select, SelectItem, TextArea, TextInput } from '@carbon/react';
import { useState } from 'react';
import { useSuppressFinding, useSuppressFindings } from '../hooks/useLifecycle';
import type { FindingResponse, SuppressionReason } from '../types';

const reasonLabels: Record<SuppressionReason, string> = {
  FALSE_POSITIVE: 'Falso positivo (a ferramenta errou)',
  ACCEPTED_RISK: 'Risco aceito (é um problema real, mas aceitamos)',
  WONT_FIX: 'Não será corrigido (ex.: código em desativação)',
};

interface Props {
  /** Um ou mais findings; vazio = modal fechado. */
  findings: FindingResponse[];
  /** Achados visíveis na lista: dali saem os "parecidos" (mesma regra) que podem ser suprimidos junto. */
  candidates?: FindingResponse[];
  onClose: () => void;
}

/** Retira um finding do score, exigindo justificativa; opcionalmente com data de validade. */
/** Mesma regra e ferramenta, ainda abertos e fora da seleção. */
export function similarTo(selected: FindingResponse[], candidates: FindingResponse[]): FindingResponse[] {
  const ids = new Set(selected.map((f) => f.id));
  const keys = new Set(selected.filter((f) => f.ruleId).map((f) => `${f.toolSource}|${f.ruleId}`));
  return candidates.filter((c) => !ids.has(c.id) && c.status !== 'SUPPRESSED' && c.ruleId && keys.has(`${c.toolSource}|${c.ruleId}`));
}

type Mutation = { isPending: boolean; isError: boolean; error: unknown };

function commonBody(reason: SuppressionReason, justification: string, createdBy: string, expires: string) {
  return {
    reason,
    justification: justification.trim(),
    createdBy: createdBy.trim() || undefined,
    // <input type="date"> devolve AAAA-MM-DD; vale até o fim desse dia
    expiresAt: expires ? `${expires}T23:59:59` : null,
  };
}

function primaryLabel(pending: boolean, bulk: boolean, total: number): string {
  if (pending) return 'Salvando…';
  return bulk ? `Suprimir ${total}` : 'Suprimir';
}

interface FieldsProps {
  similarCount: number;
  withSimilar: boolean;
  onWithSimilar: (v: boolean) => void;
  reason: SuppressionReason;
  onReason: (v: SuppressionReason) => void;
  justification: string;
  onJustification: (v: string) => void;
  createdBy: string;
  onCreatedBy: (v: string) => void;
  expires: string;
  onExpires: (v: string) => void;
}

function SuppressFields(p: FieldsProps) {
  const tooShort = p.justification.trim().length < 10;
  return (
    <>
      <p style={{ marginBottom: '1rem', fontSize: '0.875rem' }}>
        A decisão vale para este sistema: auditorias futuras com o mesmo problema já nascem suprimidas
        até a data de validade (se houver). Fica registrado quem decidiu e por quê.
      </p>
      {p.similarCount > 0 && (
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: '1rem', fontSize: '0.875rem' }}>
          <input type="checkbox" checked={p.withSimilar} onChange={(e) => p.onWithSimilar(e.target.checked)} />
          Suprimir também os {p.similarCount} parecidos (mesma regra) desta lista
        </label>
      )}
      <Select
        id="suppress-reason"
        labelText="Motivo"
        value={p.reason}
        onChange={(e) => p.onReason(e.target.value as SuppressionReason)}
        style={{ marginBottom: '1rem' }}
      >
        {(Object.keys(reasonLabels) as SuppressionReason[]).map((r) => (
          <SelectItem key={r} value={r} text={reasonLabels[r]} />
        ))}
      </Select>
      <TextArea
        id="suppress-justification"
        labelText="Justificativa (mínimo 10 caracteres)"
        value={p.justification}
        maxCount={1000}
        enableCounter
        onChange={(e) => p.onJustification(e.target.value)}
        invalid={p.justification.length > 0 && tooShort}
        invalidText="Explique o motivo com pelo menos 10 caracteres"
        style={{ marginBottom: '1rem' }}
      />
      <TextInput
        id="suppress-by"
        labelText="Quem está decidindo (opcional)"
        value={p.createdBy}
        onChange={(e) => p.onCreatedBy(e.target.value)}
        style={{ marginBottom: '1rem' }}
      />
      <TextInput
        id="suppress-expires"
        type="date"
        labelText="Válido até (opcional — recomendado para risco aceito)"
        value={p.expires}
        onChange={(e) => p.onExpires(e.target.value)}
      />
    </>
  );
}

function SuppressFeedback({ failures, mutation }: { failures: Array<{ findingId: number; message: string }>; mutation: Mutation }) {
  return (
    <>
      {failures.length > 0 && (
        <InlineNotification
          kind="warning"
          title={`${failures.length} não puderam ser suprimidos`}
          subtitle={failures.map((f) => `#${f.findingId}: ${f.message}`).join(' · ')}
          style={{ marginTop: '1rem' }}
          hideCloseButton
        />
      )}
      {mutation.isError && (
        <InlineNotification
          kind="error"
          title="Não foi possível suprimir"
          subtitle={mutation.error instanceof Error ? mutation.error.message : 'Erro desconhecido'}
          style={{ marginTop: '1rem' }}
          hideCloseButton
        />
      )}
    </>
  );
}

export function SuppressModal({ findings, candidates = [], onClose }: Props) {
  const single = useSuppressFinding();
  const batch = useSuppressFindings();
  const [withSimilar, setWithSimilar] = useState(false);
  const similar = similarTo(findings, candidates);
  const targets = withSimilar ? [...findings, ...similar] : findings;
  const bulk = targets.length > 1;
  const mutation = bulk ? batch : single;
  const [reason, setReason] = useState<SuppressionReason>('FALSE_POSITIVE');
  const [justification, setJustification] = useState('');
  const [createdBy, setCreatedBy] = useState('');
  const [expires, setExpires] = useState('');

  if (findings.length === 0) return null;
  const finding = findings[0];

  const close = () => {
    single.reset();
    batch.reset();
    setJustification('');
    setExpires('');
    setWithSimilar(false);
    onClose();
  };

  const submit = () => {
    const common = commonBody(reason, justification, createdBy, expires);
    if (bulk) {
      batch.mutate(
        { ...common, findingIds: targets.map((f) => f.id) },
        { onSuccess: (r) => (r.failures.length === 0 ? close() : undefined) },
      );
      return;
    }
    single.mutate({ findingId: finding.id, body: common }, { onSuccess: close });
  };

  return (
    <Modal
      open
      modalHeading="Retirar do score"
      modalLabel={bulk ? `${targets.length} findings selecionados` : finding.title}
      primaryButtonText={primaryLabel(mutation.isPending, bulk, targets.length)}
      secondaryButtonText="Cancelar"
      primaryButtonDisabled={justification.trim().length < 10 || mutation.isPending}
      onRequestClose={close}
      onRequestSubmit={submit}
    >
      <SuppressFields
        similarCount={similar.length}
        withSimilar={withSimilar}
        onWithSimilar={setWithSimilar}
        reason={reason}
        onReason={setReason}
        justification={justification}
        onJustification={setJustification}
        createdBy={createdBy}
        onCreatedBy={setCreatedBy}
        expires={expires}
        onExpires={setExpires}
      />
      <SuppressFeedback failures={batch.data?.failures ?? []} mutation={mutation} />
    </Modal>
  );
}
