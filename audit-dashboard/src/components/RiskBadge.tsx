import { Tag } from '@carbon/react';
import type { Severity } from '../types';

interface RiskBadgeProps {
  severity: Severity;
}

type TagType = 'red' | 'magenta' | 'warm-gray' | 'green' | 'blue';

const severityTagTypes: Record<Severity, TagType> = {
  CRITICAL: 'red',
  HIGH: 'magenta',
  MEDIUM: 'warm-gray',
  LOW: 'green',
  INFO: 'blue',
};

const severityLabels: Record<Severity, string> = {
  CRITICAL: 'Crítico',
  HIGH: 'Alto',
  MEDIUM: 'Médio',
  LOW: 'Baixo',
  INFO: 'Info',
};

export function RiskBadge({ severity }: RiskBadgeProps) {
  return (
    <Tag type={severityTagTypes[severity]} size="sm">
      {severityLabels[severity]}
    </Tag>
  );
}
