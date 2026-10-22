import {
  DataTable,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TableExpandRow,
  TableExpandedRow,
  TableExpandHeader,
  Tag,
  Button,
  Checkbox,
} from '@carbon/react';
import type { DataTableRenderProps, DataTableRow } from '@carbon/react';
import React, { useState } from 'react';
import type { FindingResponse, FindingStatus } from '../types';
import { RemediationBlock } from './RemediationBlock';
import { RiskBadge } from './RiskBadge';
import { SuppressModal } from './SuppressModal';

interface FindingsTableProps {
  findings: FindingResponse[];
  /** Sistema ao qual a auditoria pertence; mostrado em cada linha para não haver dúvida de onde vem o achado. */
  systemName?: string;
  /** Sistema de cada achado, quando a lista mistura vários sistemas. Tem prioridade sobre systemName. */
  systemOf?: (finding: FindingResponse) => { name: string; onClick?: () => void } | undefined;
}

type TagType = 'red' | 'warm-gray' | 'green' | 'purple';

const statusLabels: Record<FindingStatus, string> = {
  OPEN: 'Aberto',
  ACKNOWLEDGED: 'Reconhecido',
  FIXED: 'Resolvido',
  SUPPRESSED: 'Suprimido',
};

const statusTypes: Record<FindingStatus, TagType> = {
  OPEN: 'red',
  ACKNOWLEDGED: 'warm-gray',
  FIXED: 'green',
  SUPPRESSED: 'purple',
};

const headers = [
  { key: 'severity', header: 'Severidade' },
  { key: 'system', header: 'Sistema' },
  { key: 'title', header: 'Título' },
  { key: 'filePath', header: 'Arquivo' },
  { key: 'lineNumber', header: 'Linha' },
  { key: 'toolSource', header: 'Ferramenta' },
  { key: 'status', header: 'Status' },
];

type RowType = {
  id: string;
  severity: string;
  system: string;
  title: string;
  filePath: string;
  lineNumber: number;
  toolSource: string;
  status: string;
  _finding: FindingResponse;
};

export function FindingsTable({ findings, systemName, systemOf }: FindingsTableProps) {
  const [toSuppress, setToSuppress] = useState<FindingResponse[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const selectable = findings.filter((f) => f.status !== 'SUPPRESSED');
  const selectedFindings = selectable.filter((f) => selected.has(f.id));
  const allSelected = selectable.length > 0 && selectedFindings.length === selectable.length;
  const toggle = (id: number, on: boolean) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
  const toggleAll = (on: boolean) => setSelected(on ? new Set(selectable.map((f) => f.id)) : new Set());
  const rows: RowType[] = findings.map((f) => ({
    id: String(f.id),
    severity: f.severity,
    system: systemOf?.(f)?.name ?? systemName ?? '',
    title: f.title,
    filePath: f.filePath,
    lineNumber: f.lineNumber,
    toolSource: f.toolSource,
    status: f.status,
    _finding: f,
  }));

  return (
    <>
    {selectedFindings.length > 0 && (
      <div
        role="region"
        aria-label="Ações em lote"
        style={{ display: 'flex', alignItems: 'center', gap: '1rem', padding: '0.5rem 1rem', background: '#edf5ff', marginBottom: '0.5rem' }}
      >
        <strong>{selectedFindings.length} selecionado(s)</strong>
        <Button kind="primary" size="sm" onClick={() => setToSuppress(selectedFindings)}>
          Suprimir selecionados…
        </Button>
        <Button kind="ghost" size="sm" onClick={() => setSelected(new Set())}>
          Limpar seleção
        </Button>
      </div>
    )}
    <DataTable rows={rows} headers={headers}>
      {(renderProps: DataTableRenderProps<RowType, string[]>) => {
        const {
          rows: tableRows,
          headers: tableHeaders,
          getTableProps,
          getHeaderProps,
          getRowProps,
        } = renderProps;

        return (
          <TableContainer>
            <Table {...getTableProps()}>
              <TableHead>
                <TableRow>
                  <TableExpandHeader aria-label="Expandir linha" />
                  <TableHeader aria-label="Seleção">
                    <Checkbox
                      id="select-all-findings"
                      labelText="Selecionar todos os findings desta lista"
                      hideLabel
                      checked={allSelected}
                      indeterminate={selectedFindings.length > 0 && !allSelected}
                      disabled={selectable.length === 0}
                      onChange={(_, { checked }) => toggleAll(checked)}
                    />
                  </TableHeader>
                  {tableHeaders.map((header) => {
                    const { key: hKey, ...headerProps } = getHeaderProps({ header });
                    return (
                      <TableHeader key={hKey as string} {...headerProps}>
                        {header.header}
                      </TableHeader>
                    );
                  })}
                </TableRow>
              </TableHead>
              <TableBody>
                {tableRows.map((row: DataTableRow<string[]> & RowType) => {
                  const finding = rows.find((r) => r.id === row.id)?._finding;
                  if (!finding) return null;

                  // `key` não pode ser espalhado no JSX; o Fragment já tem a sua
                  const { key: _rowKey, ...rowProps } = getRowProps({ row });
                  void _rowKey;

                  return (
                    <React.Fragment key={row.id}>
                      <TableExpandRow
                        {...rowProps}
                        aria-label={`Expandir ${finding.title}`}
                        onExpand={rowProps.onExpand ?? (() => undefined)}
                      >
                        <TableCell>
                          {finding.status !== 'SUPPRESSED' && (
                            <Checkbox
                              id={`select-finding-${finding.id}`}
                              labelText={`Selecionar ${finding.title}`}
                              hideLabel
                              checked={selected.has(finding.id)}
                              onChange={(_, { checked }) => toggle(finding.id, checked)}
                            />
                          )}
                        </TableCell>
                        <TableCell>
                          <RiskBadge severity={finding.severity} />
                        </TableCell>
                        <TableCell>
                          {(() => {
                            const sys = systemOf?.(finding);
                            if (sys?.onClick) {
                              return (
                                <button
                                  type="button"
                                  onClick={sys.onClick}
                                  style={{ background: 'none', border: 0, padding: 0, color: '#0f62fe', cursor: 'pointer', fontSize: 'inherit' }}
                                >
                                  {sys.name}
                                </button>
                              );
                            }
                            return sys?.name ?? systemName ?? '—';
                          })()}
                        </TableCell>
                        <TableCell>
                          {finding.title}{' '}
                          {finding.shadow === true && (
                            <Tag type="outline" size="sm" title="Regra em modo sombra: aparece, mas não conta no score nem derruba o pipeline">
                              Sombra
                            </Tag>
                          )}{' '}
                          {finding.isNew === true && (
                            <Tag type="magenta" size="sm" title="Não existia na auditoria anterior">
                              Novo
                            </Tag>
                          )}
                        </TableCell>
                        <TableCell>
                          <code style={{ fontSize: '0.75rem' }}>{finding.filePath}</code>
                        </TableCell>
                        <TableCell>{finding.lineNumber}</TableCell>
                        <TableCell>{finding.toolSource}</TableCell>
                        <TableCell>
                          <Tag type={statusTypes[finding.status]} size="sm">
                            {statusLabels[finding.status]}
                          </Tag>
                        </TableCell>
                      </TableExpandRow>
                      <TableExpandedRow colSpan={tableHeaders.length + 2} className="">
                        <div style={{ padding: '1rem' }}>
                          <p>
                            <strong>Descrição:</strong> {finding.description}
                          </p>
                          {finding.codeSnippet && (
                            <pre
                              style={{
                                background: '#161616',
                                color: '#f4f4f4',
                                padding: '1rem',
                                borderRadius: '4px',
                                overflow: 'auto',
                                fontSize: '0.8125rem',
                                marginTop: '0.5rem',
                              }}
                            >
                              <code>{finding.codeSnippet}</code>
                            </pre>
                          )}
                          {finding.cveId && (
                            <p style={{ marginTop: '0.5rem' }}>
                              <strong>Identificador:</strong> <code>{finding.cveId}</code>
                            </p>
                          )}
                          {finding.remediation && <RemediationBlock remediation={finding.remediation} />}
                          {finding.aiRecommendation && (
                            <div
                              style={{
                                marginTop: '0.5rem',
                                padding: '0.75rem',
                                background: '#e8f5e9',
                                borderLeft: '4px solid #24a148',
                                borderRadius: '2px',
                              }}
                            >
                              <strong>Recomendação da IA:</strong>
                              <p style={{ marginTop: '0.25rem' }}>{finding.aiRecommendation}</p>
                            </div>
                          )}
                          {finding.status !== 'SUPPRESSED' && (
                            <div style={{ marginTop: '0.75rem' }}>
                              <Button kind="tertiary" size="sm" onClick={() => setToSuppress([finding])}>
                                Falso positivo / aceitar risco…
                              </Button>
                            </div>
                          )}
                        </div>
                      </TableExpandedRow>
                    </React.Fragment>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        );
      }}
    </DataTable>
      <SuppressModal
        findings={toSuppress}
        onClose={() => {
          setToSuppress([]);
          setSelected(new Set());
        }}
      />
    </>
  );
}
