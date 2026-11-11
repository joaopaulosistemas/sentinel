import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useNow } from './useNow';

describe('useNow', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-10T12:00:00Z'));
  });
  afterEach(() => vi.useRealTimers());

  it('começa na hora atual e avança a cada intervalo', () => {
    const { result } = renderHook(() => useNow(60_000));
    const inicio = result.current;
    expect(inicio).toBe(Date.parse('2026-11-10T12:00:00Z'));

    act(() => {
      vi.advanceTimersByTime(60_000);
    });
    expect(result.current).toBe(inicio + 60_000);
  });

  it('não muda antes do intervalo', () => {
    const { result } = renderHook(() => useNow(60_000));
    const inicio = result.current;
    act(() => {
      vi.advanceTimersByTime(59_000);
    });
    expect(result.current).toBe(inicio);
  });

  it('para o relógio ao desmontar', () => {
    const { unmount } = renderHook(() => useNow(1_000));
    expect(vi.getTimerCount()).toBe(1);
    unmount();
    expect(vi.getTimerCount()).toBe(0);
  });
});
