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
  onClose: () => void;
}

/** Retira um finding do score, exigindo justificativa; opcionalmente com data de validade. */
export function SuppressModal({ findings, onClose }: Props) {
  const single = useSuppressFinding();
  const batch = useSuppressFindings();
  const bulk = findings.length > 1;
  const mutation = bulk ? batch : single;
  const failures = batch.data?.failures ?? [];
  const [reason, setReason] = useState<SuppressionReason>('FALSE_POSITIVE');
  const [justification, setJustification] = useState('');
  const [createdBy, setCreatedBy] = useState('');
  const [expires, setExpires] = useState('');

  if (findings.length === 0) return null;
  const finding = findings[0];

  const tooShort = justification.trim().length < 10;

  const close = () => {
    single.reset();
    batch.reset();
    setJustification('');
    setExpires('');
    onClose();
  };

  const submit = () => {
    const common = {
      reason,
      justification: justification.trim(),
      createdBy: createdBy.trim() || undefined,
      // <input type="date"> devolve AAAA-MM-DD; vale até o fim desse dia
      expiresAt: expires ? `${expires}T23:59:59` : null,
    };
    if (bulk) {
      batch.mutate(
        { ...common, findingIds: findings.map((f) => f.id) },
        { onSuccess: (r) => (r.failures.length === 0 ? close() : undefined) },
      );
      return;
    }
    single.mutate(
      { findingId: finding.id, body: common },
      { onSuccess: close },
    );
  };

  return (
    <Modal
      open
      modalHeading="Retirar do score"
      modalLabel={bulk ? `${findings.length} findings selecionados` : finding.title}
      primaryButtonText={mutation.isPending ? 'Salvando…' : bulk ? `Suprimir ${findings.length}` : 'Suprimir'}
      secondaryButtonText="Cancelar"
      primaryButtonDisabled={tooShort || mutation.isPending}
      onRequestClose={close}
      onRequestSubmit={submit}
    >
      <p style={{ marginBottom: '1rem', fontSize: '0.875rem' }}>
        A decisão vale para este sistema: auditorias futuras com o mesmo problema já nascem suprimidas
        até a data de validade (se houver). Fica registrado quem decidiu e por quê.
      </p>
      <Select
        id="suppress-reason"
        labelText="Motivo"
        value={reason}
        onChange={(e) => setReason(e.target.value as SuppressionReason)}
        style={{ marginBottom: '1rem' }}
      >
        {(Object.keys(reasonLabels) as SuppressionReason[]).map((r) => (
          <SelectItem key={r} value={r} text={reasonLabels[r]} />
        ))}
      </Select>
      <TextArea
        id="suppress-justification"
        labelText="Justificativa (mínimo 10 caracteres)"
        value={justification}
        maxCount={1000}
        enableCounter
        onChange={(e) => setJustification(e.target.value)}
        invalid={justification.length > 0 && tooShort}
        invalidText="Explique o motivo com pelo menos 10 caracteres"
        style={{ marginBottom: '1rem' }}
      />
      <TextInput
        id="suppress-by"
        labelText="Quem está decidindo (opcional)"
        value={createdBy}
        onChange={(e) => setCreatedBy(e.target.value)}
        style={{ marginBottom: '1rem' }}
      />
      <TextInput
        id="suppress-expires"
        type="date"
        labelText="Válido até (opcional — recomendado para risco aceito)"
        value={expires}
        onChange={(e) => setExpires(e.target.value)}
      />
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
    </Modal>
  );
}
