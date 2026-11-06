import { InlineNotification, Modal, Select, SelectItem, TextArea, TextInput } from '@carbon/react';
import { useState } from 'react';
import { useSuppressFinding, useSuppressFindings } from '../hooks/useLifecycle';
import type { IFindingResponse, SuppressionReason } from '../types';

const reasonLabels: Record<SuppressionReason, string> = {
  FALSE_POSITIVE: 'Falso positivo (a ferramenta errou)',
  ACCEPTED_RISK: 'Risco aceito (é um problema real, mas aceitamos)',
  WONT_FIX: 'Não será corrigido (ex.: código em desativação)',
};

interface IProps {
  /** Um ou mais findings; vazio = modal fechado. */
  findings: IFindingResponse[];
  /** Achados visíveis na lista: dali saem os "parecidos" (mesma regra) que podem ser suprimidos junto. */
  candidates?: IFindingResponse[];
  onClose: () => void;
}

/** Retira um finding do score, exigindo justificativa; opcionalmente com data de validade. */
/** Mesma regra e ferramenta, ainda abertos e fora da seleção. */
export function similarTo(selected: IFindingResponse[], candidates: IFindingResponse[]): IFindingResponse[] {
  const ids = new Set(selected.map((f) => f.id));
  const keys = new Set(selected.filter((f) => f.ruleId).map((f) => `${f.toolSource}|${f.ruleId}`));
  return candidates.filter((c) => !ids.has(c.id) && c.status !== 'SUPPRESSED' && c.ruleId && keys.has(`${c.toolSource}|${c.ruleId}`));
}

type Mutation = { isPending: boolean; isError: boolean; error: unknown };

interface ICommonBodyInput {
  reason: SuppressionReason;
  justification: string;
  createdBy: string;
  expires: string;
}

export function commonBody({ reason, justification, createdBy, expires }: ICommonBodyInput) {
  return {
    reason,
    justification: justification.trim(),
    createdBy: createdBy.trim() || undefined,
    // <input type="date"> devolve AAAA-MM-DD; vale até o fim desse dia
    expiresAt: expires ? `${expires}T23:59:59` : null,
  };
}

/** Mínimo de caracteres (sem espaços nas pontas) para a justificativa valer. */
export const MIN_JUSTIFICATION_LENGTH = 10;

export const justificationTooShort = (justification: string): boolean => justification.trim().length < MIN_JUSTIFICATION_LENGTH;

interface IPrimaryLabelInput {
  pending: boolean;
  bulk: boolean;
  total: number;
}

export function primaryLabel({ pending, bulk, total }: IPrimaryLabelInput): string {
  if (pending) return 'Salvando…';
  return bulk ? `Suprimir ${total}` : 'Suprimir';
}

interface IFieldsProps {
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

function SuppressFields(p: IFieldsProps) {
  const tooShort = justificationTooShort(p.justification);
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

export function SuppressModal({ findings, candidates = [], onClose }: IProps) {
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
    const common = commonBody({ reason, justification, createdBy, expires });
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
      primaryButtonText={primaryLabel({ pending: mutation.isPending, bulk, total: targets.length })}
      secondaryButtonText="Cancelar"
      primaryButtonDisabled={justificationTooShort(justification) || mutation.isPending}
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
