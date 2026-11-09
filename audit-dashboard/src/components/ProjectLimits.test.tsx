import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { IProjectMapInfo } from '../types';
import { ProjectLimitsSection } from './ProjectLimits';

const map: IProjectMapInfo = {
  languages: { Java: 120, TypeScript: 80 },
  buildTools: ['Maven'],
  frameworks: ['Spring Boot', 'React'],
  entryPoints: [],
  ciConfigs: ['GitHub Actions'],
  dockerfiles: ['audit-api/Dockerfile'],
  iacFiles: [],
  docs: [],
  lockfiles: ['package-lock.json'],
  sourceFiles: 200,
  testFiles: 50,
  hasReadme: true,
  readmeLines: 40,
  hasGitignore: true,
  hasLicense: false,
  truncated: false,
};

describe('ProjectLimitsSection', () => {
  it('agrupa os pontos não verificados em vez de uma lista corrida', () => {
    render(
      <ProjectLimitsSection
        unverified={['Segurança: autorização em execução', 'Segurança: segredos externos', 'Analisador Trivy não executou (falhou)']}
        map={null}
      />,
    );
    const region = screen.getByRole('region', { name: 'Limites e mapa do projeto' });
    expect(within(region).getByText('Segurança')).toBeInTheDocument();
    expect(within(region).getByText('Analisadores que não rodaram por inteiro')).toBeInTheDocument();
    expect(within(region).getByText('autorização em execução')).toBeInTheDocument();
    expect(within(region).queryByText('Mapa do projeto')).toBeNull();
  });

  it('mostra números, barra de linguagens com texto acessível e etiquetas do mapa', () => {
    render(<ProjectLimitsSection unverified={[]} map={map} />);
    expect(screen.getByText('Nenhum ponto sem cobertura nesta auditoria.')).toBeInTheDocument();
    expect(screen.getByText('arquivos de código').previousSibling).toHaveTextContent('200');
    expect(screen.getByText('testes por arquivo de código').previousSibling).toHaveTextContent('25%');
    expect(screen.getByRole('img', { name: 'Linguagens por arquivos: Java 60%, TypeScript 40%' })).toBeInTheDocument();
    expect(screen.getByText('Spring Boot')).toBeInTheDocument();
    expect(screen.getByText('Licença: não tem')).toBeInTheDocument();
    expect(screen.getByText('README: tem')).toBeInTheDocument();
  });

  it('avisa quando a leitura do repositório foi parcial', () => {
    render(<ProjectLimitsSection unverified={[]} map={{ ...map, truncated: true }} />);
    expect(screen.getByText(/leitura parcial/)).toBeInTheDocument();
  });
});
