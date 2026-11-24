#!/usr/bin/env node
// Gera a pasta docs/figma/: uma imagem (PNG) e um HTML autônomo de cada tela do dashboard, nos temas claro e escuro.
// Roda o dashboard de verdade (vite dev) com a API simulada por docs/figma/dados-simulados.mjs: não precisa de backend.
//
// Uso (na raiz do projeto, depois de `npm ci` em audit-dashboard):
//   node docs/figma/capturar-telas.mjs
// Variáveis: E2E_CHROMIUM = caminho do Chrome/Chromium (senão procura um instalado), DASHBOARD_URL = servidor já em pé.
import { spawn } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import * as dados from './dados-simulados.mjs';

const pastaFigma = dirname(fileURLToPath(import.meta.url));
const raiz = resolve(pastaFigma, '..', '..');
const pastaDashboard = join(raiz, 'audit-dashboard');
const requireDoDashboard = createRequire(join(pastaDashboard, 'package.json'));
const { chromium } = requireDoDashboard('@playwright/test');

const URL_BASE = process.env.DASHBOARD_URL || 'http://localhost:3000';
const LARGURA = 1440;
const ALTURA = 900;
const TEMAS = ['light', 'dark'];
const PORTA_DO_SERVIDOR_PRONTA_MS = 90_000;

/** Telas do dashboard, na ordem do menu. `depois` prepara um estado da tela (painel aberto, aba etc.) antes da captura. */
const telas = [
  { id: '01-visao-geral', titulo: 'Visão geral', rota: '/' },
  {
    id: '01b-nova-auditoria', titulo: 'Visão geral (janela Nova auditoria)', rota: '/',
    depois: async (page) => { await page.getByRole('button', { name: 'Nova auditoria' }).first().click(); await page.waitForTimeout(400); },
  },
  { id: '02-sistemas', titulo: 'Sistemas (lista)', rota: '/systems' },
  {
    id: '03-sistemas-novo', titulo: 'Sistemas (painel de cadastro)', rota: '/systems',
    depois: async (page) => { await page.getByRole('button', { name: /novo sistema|adicionar sistema|cadastrar/i }).first().click(); },
  },
  ...[['04-sistemas-editar-cadastro', 'Cadastro e Git'], ['05-sistemas-editar-motor-de-regras', 'Motor de regras'],
    ['06-sistemas-editar-analisadores', 'Analisadores'], ['07-sistemas-editar-claude-mcp', 'Claude (MCP)']].map(([id, aba]) => ({
    id, titulo: `Sistemas (painel de edição: ${aba})`, rota: '/systems',
    depois: async (page) => {
      await page.getByRole('row', { name: /conta-corrente-api/ }).getByText('Editar').click();
      await page.getByRole('tab', { name: aba }).click();
      await page.waitForTimeout(500);
    },
  })),
  { id: '08-detalhe-do-sistema', titulo: 'Detalhe do sistema', rota: '/systems/1' },
  { id: '09-auditoria-em-execucao', titulo: 'Auditoria em execução', rota: '/audits/102' },
  { id: '10-auditoria-resultado', titulo: 'Auditoria concluída (resultado)', rota: '/audits/101' },
  { id: '11-achados', titulo: 'Achados', rota: '/findings' },
  { id: '12-ranking', titulo: 'Ranking de sistemas', rota: '/ranking' },
  { id: '13-relatorios', titulo: 'Relatórios', rota: '/reports' },
  { id: '14-guia', titulo: 'Guia de código', rota: '/guide' },
  { id: '15-configuracoes', titulo: 'Configurações', rota: '/settings' },
];

const idDaUrl = (caminho, regex) => Number(caminho.match(regex)?.[1]);

/** Responde cada chamada da API com os dados simulados; o que não tem dado devolve lista vazia. */
function responder(caminho, metodo) {
  const sistemaPorId = (id) => dados.sistemas.find((s) => s.id === id) ?? dados.sistemas[0];
  if (/\/api\/v1\/me$/.test(caminho)) return { username: 'joao', roles: ['sentinel-admin'], admin: true, canExecute: true };
  if (/\/api\/v1\/systems\/local-source/.test(caminho)) return { enabled: true };
  if (/\/api\/v1\/systems\/\d+\/audits/.test(caminho)) return dados.auditorias.filter((a) => a.systemId === idDaUrl(caminho, /systems\/(\d+)/));
  if (/\/api\/v1\/systems\/\d+\/trend/.test(caminho)) return dados.tendencia(sistemaPorId(idDaUrl(caminho, /systems\/(\d+)/)));
  if (/\/api\/v1\/systems\/\d+\/suppressions/.test(caminho)) return [];
  if (/\/api\/v1\/systems\/\d+\/evidence/.test(caminho)) return { enabled: false, files: [] };
  if (/\/api\/v1\/systems\/\d+$/.test(caminho)) return sistemaPorId(idDaUrl(caminho, /systems\/(\d+)/));
  if (/\/api\/v1\/systems(\?|$)/.test(caminho)) return dados.sistemas;
  if (/\/api\/v1\/audits\/\d+\/findings/.test(caminho)) return dados.achados;
  if (/\/api\/v1\/audits\/\d+\/diff/.test(caminho)) return dados.diferenca;
  if (/\/api\/v1\/audits\/\d+\/freshness/.test(caminho)) return dados.frescor;
  if (/\/api\/v1\/audits\/\d+\/insights/.test(caminho)) return dados.insights;
  if (/\/api\/v1\/audits\/\d+\/fix-plan/.test(caminho)) return dados.planoDeCorrecao;
  if (/\/api\/v1\/audits\/\d+\/score-explanation/.test(caminho)) return dados.explicacaoDaNota;
  if (/\/api\/v1\/audits\/102$/.test(caminho)) return dados.auditoriaEmExecucao;
  if (/\/api\/v1\/audits\/\d+$/.test(caminho)) return dados.auditorias.find((a) => a.id === idDaUrl(caminho, /audits\/(\d+)/)) ?? dados.auditorias[0];
  if (/\/api\/v1\/audits(\?|$)/.test(caminho)) return [dados.auditoriaEmExecucao, ...dados.auditorias];
  if (/\/api\/v1\/findings(\?|$)/.test(caminho)) return dados.visaoDeAchados;
  if (/\/api\/v1\/packs/.test(caminho)) return dados.pacotes;
  if (/\/api\/v1\/engine-config\//.test(caminho)) return dados.configuracaoDoMotor;
  if (/\/api\/v1\/mcp-access\/\d+\/calls/.test(caminho)) return [];
  if (/\/api\/v1\/mcp-access\//.test(caminho)) return dados.acessoMcp;
  if (/\/api\/v1\/claude-review\/\d+\/metrics/.test(caminho)) return dados.metricasDaRevisao;
  if (/\/api\/v1\/claude-review\//.test(caminho)) return dados.revisaoDoClaude;
  if (/\/api\/v1\/pending-actions\//.test(caminho)) return [];
  if (/\/api\/v1\/rules\/quality/.test(caminho)) return [];
  if (/\/api\/v1\/guide$/.test(caminho)) return dados.guia;
  if (/\/api\/v1\/settings$/.test(caminho)) return dados.configuracoes;
  return metodo === 'GET' ? [] : {};
}

async function servidorRespondendo() {
  try { return (await fetch(URL_BASE)).ok; } catch { return false; }
}

async function subirServidorSeFaltar() {
  if (await servidorRespondendo()) return null;
  const processo = spawn('npm', ['run', 'dev', '--', '--port', '3000', '--strictPort'], { cwd: pastaDashboard, stdio: 'ignore', detached: true });
  const limite = Date.now() + PORTA_DO_SERVIDOR_PRONTA_MS;
  while (Date.now() < limite) {
    if (await servidorRespondendo()) return processo;
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error('O dashboard não subiu em tempo (npm run dev).');
}

function acharChrome() {
  const candidatos = [process.env.E2E_CHROMIUM, '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'];
  return candidatos.find((c) => c && existsSync(c));
}

/**
 * Devolve o HTML da tela como está na janela, sem scripts: só o CSS que a tela usa, num <style> único, com as
 * fontes apontando para docs/figma/html/fonts/. Abre offline e serve de entrada para importar no Figma (html.to.design e similares).
 * `fontesUsadas` são os caminhos (depois de "~@ibm/plex/") que o navegador de fato baixou.
 */
async function htmlAutonomo(page, fontesUsadas) {
  return page.evaluate(async (usadas) => {
    const prefixo = '~@ibm/plex/';
    const caminhoDaFonte = (texto) => {
      const achou = texto.match(/url\(["']?([^"')]*~@ibm\/plex\/[^"')]+)["']?\)/);
      return achou ? achou[1].slice(achou[1].indexOf(prefixo) + prefixo.length) : null;
    };
    const seletorUsado = (seletor) => {
      if (seletor.includes('&')) return true;
      // Tira estados e pseudo-elementos (:hover, ::before, :not(...)) para testar só o que existe na página.
      let base = seletor.replace(/::?[\w-]+(\([^)]*\))?/g, '').trim();
      if (base === '' || /[>+~]$/.test(base)) base += '*';
      try { return document.querySelector(base) !== null; } catch { return true; }
    };
    const poda = (regras) => {
      let css = '';
      for (const regra of regras) {
        if (regra.type === CSSRule.STYLE_RULE) {
          if (regra.selectorText.split(',').some((s) => seletorUsado(s))) css += `${regra.cssText}\n`;
        } else if (regra.type === CSSRule.FONT_FACE_RULE) {
          const caminho = caminhoDaFonte(regra.cssText);
          if (caminho && usadas.includes(caminho)) css += `${regra.cssText.replace(/url\(["']?[^"')]*~@ibm\/plex\/[^"')]*\/([^/"')]+)["']?\)/, 'url("fonts/$1")')}\n`;
        } else if (regra.cssRules && regra.conditionText !== undefined) {
          const dentro = poda(Array.from(regra.cssRules));
          if (dentro.trim()) css += `@${regra instanceof CSSMediaRule ? 'media' : 'supports'} ${regra.conditionText} {\n${dentro}}\n`;
        } else if (regra.type === CSSRule.KEYFRAMES_RULE || regra.type === CSSRule.IMPORT_RULE) {
          css += `${regra.cssText}\n`;
        } else {
          css += `${regra.cssText}\n`;
        }
      }
      return css;
    };
    let css = '';
    for (const folha of Array.from(document.styleSheets)) {
      try { css += poda(Array.from(folha.cssRules)); } catch { /* folha de outra origem */ }
    }
    const copia = document.documentElement.cloneNode(true);
    copia.querySelectorAll('script, link[rel="stylesheet"], link[rel="modulepreload"], style, noscript').forEach((n) => n.remove());
    const estilo = document.createElement('style');
    estilo.textContent = css;
    copia.querySelector('head').appendChild(estilo);
    return `<!DOCTYPE html>\n${copia.outerHTML}`;
  }, fontesUsadas);
}

async function main() {
  const chrome = acharChrome();
  if (!chrome) throw new Error('Chrome/Chromium não encontrado. Defina E2E_CHROMIUM com o caminho do executável.');
  for (const sub of ['telas', 'html']) { rmSync(join(pastaFigma, sub), { recursive: true, force: true }); mkdirSync(join(pastaFigma, sub), { recursive: true }); }
  mkdirSync(join(pastaFigma, 'html', 'fonts'), { recursive: true });
  const fontesUsadas = new Set();

  const servidor = await subirServidorSeFaltar();
  const navegador = await chromium.launch({ executablePath: chrome });
  const erros = [];
  const indice = [];
  try {
    for (const tema of TEMAS) {
      const contexto = await navegador.newContext({ viewport: { width: LARGURA, height: ALTURA }, deviceScaleFactor: 1, locale: 'pt-BR', timezoneId: 'America/Sao_Paulo' });
      await contexto.addInitScript((t) => {
        try { localStorage.setItem('sentinel.theme', t); localStorage.setItem('sentinel.palette', 'suave'); localStorage.setItem('sentinel.textsize', 'normal'); } catch { /* sem armazenamento */ }
      }, tema);
      // O Carbon pede as fontes em "~@ibm/plex/...", caminho que o Vite em desenvolvimento não resolve (o navegador cairia na fonte do sistema).
      // Aqui elas vêm de node_modules, para a imagem sair com o IBM Plex Sans do desenho original.
      await contexto.route('**/~@ibm/plex/**', async (rota) => {
        const caminho = decodeURIComponent(new URL(rota.request().url()).pathname).split('~@ibm/plex/')[1];
        const arquivo = join(pastaDashboard, 'node_modules', '@ibm', 'plex', caminho);
        if (!existsSync(arquivo)) return rota.fulfill({ status: 404, body: '' });
        fontesUsadas.add(caminho);
        const nome = caminho.split('/').pop();
        copyFileSync(arquivo, join(pastaFigma, 'html', 'fonts', nome));
        return rota.fulfill({ path: arquivo, contentType: 'font/woff2' });
      });
      await contexto.route('**/api/v1/**', async (rota) => {
        const req = rota.request();
        const { pathname, search } = new URL(req.url());
        const corpo = responder(pathname + search, req.method());
        await rota.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(corpo) });
      });
      for (const tela of telas) {
        const page = await contexto.newPage();
        page.on('pageerror', (e) => erros.push(`[${tema}] ${tela.id}: ${e.message}`));
        await page.goto(`${URL_BASE}${tela.rota}`, { waitUntil: 'networkidle' });
        await page.evaluate(() => document.fonts.ready);
        if (tela.depois) { try { await tela.depois(page); } catch (e) { erros.push(`[${tema}] ${tela.id}: estado não aplicado (${e.message.split('\n')[0]})`); } }
        // Animações e esqueletos de carregamento: espera assentar e desliga o movimento para a imagem sair estável.
        await page.addStyleTag({ content: '*,*::before,*::after{animation:none!important;transition:none!important}' });
        await page.waitForTimeout(600);
        // A janela cresce até a altura da página: assim a barra lateral e o que usa 100vh acompanham a tela inteira.
        for (let volta = 0; volta < 2; volta += 1) {
          const altura = await page.evaluate(() => Math.max(document.documentElement.scrollHeight, document.body.scrollHeight));
          await page.setViewportSize({ width: LARGURA, height: Math.max(ALTURA, altura) });
          await page.waitForTimeout(250);
        }
        const nome = `${tela.id}-${tema === 'light' ? 'claro' : 'escuro'}`;
        await page.screenshot({ path: join(pastaFigma, 'telas', `${nome}.png`), fullPage: true });
        writeFileSync(join(pastaFigma, 'html', `${nome}.html`), await htmlAutonomo(page, [...fontesUsadas]));
        indice.push({ id: tela.id, titulo: tela.titulo, rota: tela.rota, tema, arquivo: nome });
        await page.close();
      }
      await contexto.close();
    }
  } finally {
    await navegador.close();
    if (servidor) { try { process.kill(-servidor.pid); } catch { /* já encerrou */ } }
  }
  writeFileSync(join(pastaFigma, 'indice.json'), `${JSON.stringify(indice, null, 2)}\n`);
  if (erros.length) console.warn(`Avisos:\n${erros.join('\n')}`);
  console.log(`${indice.length} capturas em ${pastaFigma}`);
}

main().catch((e) => { console.error(e); process.exit(1); });
