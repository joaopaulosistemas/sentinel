import { create } from 'zustand';
import type { Category, FindingStatus, Severity } from '../types';

interface IFindingsFilter {
  severity?: Severity;
  category?: Category;
  status?: FindingStatus;
}

interface IAuditStore {
  selectedSystemId: number | null;
  selectedAuditId: number | null;
  findingsFilter: IFindingsFilter;
  setSelectedSystem: (id: number | null) => void;
  setSelectedAudit: (id: number | null) => void;
  setFindingsFilter: (filter: IFindingsFilter) => void;
  resetFindingsFilter: () => void;
}

export const useAuditStore = create<IAuditStore>((set) => ({
  selectedSystemId: null,
  selectedAuditId: null,
  findingsFilter: {},
  setSelectedSystem: (id) => set({ selectedSystemId: id }),
  setSelectedAudit: (id) => set({ selectedAuditId: id }),
  setFindingsFilter: (filter) => set({ findingsFilter: filter }),
  resetFindingsFilter: () => set({ findingsFilter: {} }),
}));
