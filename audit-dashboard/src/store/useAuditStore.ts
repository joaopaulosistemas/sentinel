import { create } from 'zustand';
import type { Category, FindingStatus, Severity } from '../types';

interface FindingsFilter {
  severity?: Severity;
  category?: Category;
  status?: FindingStatus;
}

interface AuditStore {
  selectedSystemId: number | null;
  selectedAuditId: number | null;
  findingsFilter: FindingsFilter;
  setSelectedSystem: (id: number | null) => void;
  setSelectedAudit: (id: number | null) => void;
  setFindingsFilter: (filter: FindingsFilter) => void;
  resetFindingsFilter: () => void;
}

export const useAuditStore = create<AuditStore>((set) => ({
  selectedSystemId: null,
  selectedAuditId: null,
  findingsFilter: {},
  setSelectedSystem: (id) => set({ selectedSystemId: id }),
  setSelectedAudit: (id) => set({ selectedAuditId: id }),
  setFindingsFilter: (filter) => set({ findingsFilter: filter }),
  resetFindingsFilter: () => set({ findingsFilter: {} }),
}));
