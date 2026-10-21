import { Button, InlineNotification, Select, SelectItem, Tile } from '@carbon/react';
import { useEffect, useState } from 'react';
import { usePacks, useUpdateProfile } from '../hooks/useLifecycle';
import type { SystemSummaryResponse } from '../types';

/** Perfil de regras do sistema: quais pacotes valem, severidades ajustadas, modo sombra e limites de clean code. */
export function ProfileForm({ system }: { system: SystemSummaryResponse | undefined }) {
  const { data: packs, isLoading, error } = usePacks();
  const update = useUpdateProfile();
  const [profile, setProfile] = useState('default');

  useEffect(() => {
    if (system) setProfile(system.profile || 'default');
  }, [system?.id, system?.profile]);

  if (!system) return null;

  const profiles = packs?.profiles ?? [];
  const selected = profiles.find((p) => p.id === profile);
  const unknown = profile !== 'default' && !selected && !!packs;
  const current = system.profile || 'default';

  return (
    <Tile style={{ marginBottom: '2rem', padding: '1.25rem' }}>
      <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.25rem' }}>Perfil de regras</h3>
      <p style={{ fontSize: '0.875rem', color: '#525252', marginBottom: '1rem' }}>
        Define quais pacotes de regras valem para este sistema. Vale a partir da próxima auditoria.
      </p>
      {error && (
        <InlineNotification
          kind="error"
          title="Não foi possível carregar os perfis"
          subtitle={error instanceof Error ? error.message : 'Erro desconhecido'}
          hideCloseButton
        />
      )}
      <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', alignItems: 'flex-end' }}>
        <Select
          id="profile-select"
          labelText="Perfil"
          value={profile}
          disabled={isLoading}
          onChange={(e) => setProfile(e.target.value)}
        >
          <SelectItem value="default" text="default (pacotes habilitados por padrão)" />
          {unknown && <SelectItem value={profile} text={`${profile} (não encontrado)`} />}
          {profiles
            .filter((p) => p.id !== 'default')
            .map((p) => (
              <SelectItem key={p.id} value={p.id} text={p.name && p.name !== p.id ? `${p.id} — ${p.name}` : p.id} />
            ))}
        </Select>
        <Button
          disabled={update.isPending || profile === current || unknown}
          onClick={() => update.mutate({ systemId: system.id, profile: profile === 'default' ? '' : profile })}
        >
          Salvar
        </Button>
      </div>
      {selected?.description && (
        <p style={{ fontSize: '0.875rem', color: '#525252', marginTop: '0.75rem' }}>{selected.description}</p>
      )}
      {unknown && (
        <InlineNotification
          kind="warning"
          title="Perfil não encontrado"
          subtitle="Este sistema aponta para um perfil que não está mais carregado; as auditorias usam o perfil default."
          hideCloseButton
          style={{ marginTop: '0.75rem' }}
        />
      )}
      {update.isError && (
        <InlineNotification
          kind="error"
          title="Não foi possível salvar"
          subtitle={update.error instanceof Error ? update.error.message : 'Erro desconhecido'}
          style={{ marginTop: '0.75rem' }}
        />
      )}
    </Tile>
  );
}
