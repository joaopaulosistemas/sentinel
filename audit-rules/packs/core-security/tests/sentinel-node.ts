import crypto from 'crypto'
import fs from 'fs'
import path from 'path'
import vm from 'vm'
import axios from 'axios'

// ---------- SQL injection ----------
export function login (req: any, res: any) {
  // ruleid: sentinel-node-sql-injection
  models.sequelize.query(`SELECT * FROM Users WHERE email = '${req.body.email || ''}'`, { model: UserModel })
}

export function buscar (req: any) {
  const criterio = req.query.q ?? ''
  // ruleid: sentinel-node-sql-injection
  return models.sequelize.query("SELECT * FROM Produtos WHERE nome LIKE '%" + criterio + "%'")
}

export function buscarSeguro (req: any) {
  // ok: sentinel-node-sql-injection
  return models.sequelize.query('SELECT * FROM Produtos WHERE nome LIKE :nome', { replacements: { nome: `%${req.query.q}%` } })
}

export function buscarPorId (req: any) {
  const id = parseInt(req.params.id, 10)
  // ok: sentinel-node-sql-injection
  return db.query(`SELECT * FROM Produtos WHERE id = ${id}`)
}

export function transferir (req: any) {
  const valor = req.body.amount
  // ruleid: sentinel-node-sql-injection
  return { saldo: Sequelize.literal(`balance - ${valor}`) }
}

export function transferirSeguro (req: any) {
  const valor = Number(req.body.amount)
  // ok: sentinel-node-sql-injection
  return { saldo: Sequelize.literal(`balance - ${valor}`) }
}

export function consultaFixa () {
  // ok: sentinel-node-sql-injection
  return models.sequelize.query('SELECT sql FROM sqlite_master')
}

// ---------- NoSQL $where ----------
export function avaliacoes (req: any) {
  const id = req.params.id
  // ruleid: sentinel-node-nosql-where
  return db.reviews.find({ $where: 'this.product == ' + id })
}

export function avaliacoesSeguro (req: any) {
  // ok: sentinel-node-nosql-where
  return db.reviews.find({ product: req.params.id })
}

// ---------- eval ----------
export function calcular (req: any) {
  // ruleid: sentinel-node-eval-requisicao
  return eval(req.body.expressao)
}

export function executar (req: any) {
  // ruleid: sentinel-node-eval-requisicao
  return vm.runInContext(req.body.codigo, vm.createContext({}))
}

export function dinamico (expressao: string) {
  // ruleid: sentinel-node-eval-dinamico
  return eval(expressao).toString()
}

export function fixo () {
  // ok: sentinel-node-eval-dinamico
  return eval('1 + 1')
}

export function semEval (req: any) {
  // ok: sentinel-node-eval-requisicao
  return JSON.parse(req.body.dados)
}

// ---------- redirecionamento aberto ----------
export function redirecionar (req: any, res: any) {
  const destino = req.query.to as string
  // ruleid: sentinel-node-open-redirect
  res.redirect(destino)
}

export function redirecionarSeguro (req: any, res: any) {
  // ok: sentinel-node-open-redirect
  res.redirect('/inicio')
}

// ---------- path traversal ----------
export function baixar (req: any, res: any) {
  const arquivo = req.params.arquivo
  // ruleid: sentinel-node-path-traversal
  res.sendFile(path.resolve('ftp/', arquivo))
}

export function ler (req: any) {
  // ruleid: sentinel-node-path-traversal
  return fs.readFileSync('./dados/' + req.query.nome, 'utf8')
}

export function baixarSeguro (req: any, res: any) {
  const arquivo = path.basename(req.params.arquivo)
  // ok: sentinel-node-path-traversal
  res.sendFile(path.resolve('ftp/', arquivo))
}

export function paginaFixa (res: any) {
  // ok: sentinel-node-path-traversal
  res.sendFile(path.resolve('frontend/dist/index.html'))
}

// ---------- SSRF ----------
export async function importarImagem (req: any) {
  const url = req.body.imageUrl
  // ruleid: sentinel-node-ssrf
  const resposta = await fetch(url)
  return resposta.body
}

export async function chamarServico (req: any) {
  // ruleid: sentinel-node-ssrf
  return axios.get(req.query.alvo as string)
}

export async function chamarFixo () {
  // ok: sentinel-node-ssrf
  return fetch('https://api.exemplo.com/status')
}

export async function chamarComCorpo (req: any) {
  // ok: sentinel-node-ssrf
  return axios.post('https://api.exemplo.com/eventos', req.body)
}

// ---------- hash fraco ----------
export const hashMd5 = (dado: string) =>
  // ruleid: sentinel-node-hash-fraco
  crypto.createHash('md5').update(dado).digest('hex')

export const hashSha1 = (dado: string) =>
  // ruleid: sentinel-node-hash-fraco
  crypto.createHash('sha1').update(dado).digest('hex')

export const hashForte = (dado: string) =>
  // ok: sentinel-node-hash-fraco
  crypto.createHash('sha256').update(dado).digest('hex')

// ---------- XXE ----------
export function lerXml (libxml: any, dados: string) {
  // ruleid: sentinel-node-xxe
  return libxml.parseXml(dados, { noent: true, nocdata: true })
}

export function lerXmlSeguro (libxml: any, dados: string) {
  // ok: sentinel-node-xxe
  return libxml.parseXml(dados, { nocdata: true })
}

// ---------- origem desestruturada: ({ params, query }: Request, res) ----------
export function servirArquivo () {
  return ({ params }: Request, res: Response) => {
    const arquivo = params.arquivo
    // ruleid: sentinel-node-path-traversal
    res.sendFile(path.resolve('ftp/', arquivo))
  }
}

export function irPara () {
  return ({ query }: Request, res: Response) => {
    const destino = query.to as string
    // ruleid: sentinel-node-open-redirect
    res.redirect(destino)
  }
}

export function irParaFixo () {
  return ({ query }: Request, res: Response) => {
    // ok: sentinel-node-open-redirect
    res.redirect('/inicio')
  }
}

// ---------- injeção de comando ----------
export function pingar (req: any) {
  // ruleid: sentinel-node-command-injection
  return child_process.exec('ping -c 2 ' + req.body.address)
}

export function pingarDesestruturado () {
  return ({ query }: Request, res: Response) => {
    // ruleid: sentinel-node-command-injection
    execSync(`nslookup ${query.host}`)
  }
}

export function pingarSeguro (req: any) {
  // ok: sentinel-node-command-injection
  return child_process.execFile('ping', ['-c', '2', String(req.body.address)])
}

export function comandoFixo () {
  // ok: sentinel-node-command-injection
  return child_process.exec('git rev-parse HEAD')
}

// ---------- desserialização ----------
export function carregarPerfil (req: any, serialize: any) {
  // ruleid: sentinel-node-desserializacao
  return serialize.unserialize(req.cookies.perfil)
}

export function carregarPerfilSeguro (req: any) {
  // ok: sentinel-node-desserializacao
  return JSON.parse(req.cookies.perfil)
}

export const handlerLambda = async (event: any) => {
  // ruleid: sentinel-node-desserializacao
  const corpo = serialize.unserialize(event.body)
  // ruleid: sentinel-node-desserializacao
  const cabecalhos = serialize.unserialize(event.headers)
  return [corpo, cabecalhos]
}

export const handlerLambdaSeguro = async (event: any) => {
  // ok: sentinel-node-desserializacao
  return JSON.parse(event.body)
}

export const handlerLambdaEval = async (event: any) => {
  const cmd = event.body.cmd
  // ruleid: sentinel-node-eval-requisicao, sentinel-node-eval-dinamico
  eval(cmd)
}

export const naoEhEvento = (config: any) => {
  // ok: sentinel-node-desserializacao
  return serialize.unserialize(config.body)
}

// ---------- cookie sem httpOnly ----------
export function gravarCookie (res: any, token: string) {
  // ruleid: sentinel-node-cookie-sem-flags
  res.cookie('token', token)
}

export function gravarCookieSemFlag (res: any, token: string) {
  // ruleid: sentinel-node-cookie-sem-flags
  res.cookie('session', token, { maxAge: 3600 })
}

export function gravarCookieSeguro (res: any, token: string) {
  // ok: sentinel-node-cookie-sem-flags
  res.cookie('token', token, { httpOnly: true, secure: true, sameSite: 'lax' })
}

export function gravarPreferencia (res: any) {
  // ok: sentinel-node-cookie-sem-flags
  res.cookie('tema', 'escuro')
}

// ---------- JWT ----------
export function conferirToken (token: string, chave: string) {
  // ruleid: sentinel-node-jwt-sem-algoritmo
  return jwt.verify(token, chave)
}

export function conferirTokenComOpcoes (token: string, chave: string) {
  // ruleid: sentinel-node-jwt-sem-algoritmo
  return jsonwebtoken.verify(token, chave, { issuer: 'sentinel' })
}

export function conferirTokenSeguro (token: string, chave: string) {
  // ok: sentinel-node-jwt-sem-algoritmo
  return jwt.verify(token, chave, { algorithms: ['HS256'] })
}

export function conferirTokenIgnorandoValidade (token: string, chave: string) {
  // ruleid: sentinel-node-jwt-expiracao-ignorada
  return jwt.verify(token, chave, { algorithms: ['HS256'], ignoreExpiration: true })
}

// ---------- Math.random em valor de segurança ----------
export function novoToken () {
  // ruleid: sentinel-node-math-random-segredo
  const token = Math.random().toString(36).slice(2)
  return token
}

export const configuracao = {
  // ruleid: sentinel-node-math-random-segredo
  secret: '' + Math.random()
}

export function sorteio () {
  // ok: sentinel-node-math-random-segredo
  const indice = Math.floor(Math.random() * 10)
  return indice
}

// ---------- SQL montado com texto e variável (camada de dados) ----------
export function entrar (pool: any, usuario: string, senha: string) {
  const q = "SELECT * FROM users WHERE username = '" + usuario + "' AND password = '" + senha + "'"
  // ruleid: sentinel-node-sql-concatenado
  return pool.query(q)
}

export function buscar (db: any, termo: string) {
  // ruleid: sentinel-node-sql-concatenado
  return db.query("SELECT * FROM products WHERE name ILIKE '%" + termo + "%'")
}

export function ordenar (pool: any, usuario: number, coluna: string) {
  // ruleid: sentinel-node-sql-concatenado
  return pool.query(`SELECT * FROM orders WHERE user_id = $1 ORDER BY ${coluna} DESC`, [usuario])
}

export function porId (pool: any, id: number) {
  // ok: sentinel-node-sql-concatenado
  return pool.query('SELECT * FROM users WHERE id = $1', [id])
}

export function fixo (pool: any) {
  // ok: sentinel-node-sql-concatenado
  return pool.query('SELECT * FROM ' + 'products ORDER BY id')
}

// ---------- NoSQL: corpo da requisição direto no filtro ----------
export function filtrar (req: any, col: any) {
  const filtro = { origem: req.body.tags }
  // ruleid: sentinel-node-nosql-operador
  return col.find(filtro).toArray()
}

export function filtrarGet (req: any, col: any) {
  // ruleid: sentinel-node-nosql-operador
  return col.findOne({ email: req.query.email })
}

export function filtrarComTexto (req: any, col: any) {
  // ok: sentinel-node-nosql-operador
  return col.find({ email: String(req.query.email) }).toArray()
}

export function listaLocal (itens: any[], id: number) {
  // ok: sentinel-node-nosql-operador
  return itens.find((i) => i.id === id)
}

// ---------- Prototype pollution: merge recursivo sem bloqueio de chave ----------
// ruleid: sentinel-node-prototype-pollution
function merge (target: any, source: any) {
  for (const key of Object.keys(source)) {
    if (typeof source[key] === 'object' && source[key] !== null) {
      if (!target[key]) target[key] = {}
      merge(target[key], source[key])
    } else {
      target[key] = source[key]
    }
  }
  return target
}

// ok: sentinel-node-prototype-pollution
function mergeSeguro (target: any, source: any) {
  for (const key of Object.keys(source)) {
    if (key === '__proto__' || key === 'constructor' || key === 'prototype') continue
    if (typeof source[key] === 'object' && source[key] !== null) {
      if (!target[key]) target[key] = {}
      mergeSeguro(target[key], source[key])
    } else {
      target[key] = source[key]
    }
  }
  return target
}

// ---------- RegExp montada com dado da requisição (ReDoS) ----------
export function testarPadrao (req: any) {
  const padrao = req.body.pattern
  // ruleid: sentinel-node-regexp-da-requisicao
  const re = new RegExp(padrao)
  return re.test('x')
}

export function testarEscapado (req: any) {
  // ok: sentinel-node-regexp-da-requisicao
  const re = new RegExp(escapeRegExp(req.query.q))
  return re.test('x')
}

export function testarFixo () {
  // ok: sentinel-node-regexp-da-requisicao
  return new RegExp('^a+$').test('aaa')
}
declare function escapeRegExp (s: string): string

// ---------- XSS refletido: HTML montado com dado da requisição ----------
export function saudacao (req: any, res: any) {
  const nome = req.query.name || 'visitante'
  // ruleid: sentinel-node-xss-refletido
  res.send('<h1>Olá ' + nome + '</h1>')
}

export function saudacaoTemplate (req: any, res: any) {
  // ruleid: sentinel-node-xss-refletido
  res.send(`<p>${req.params.nome}</p>`)
}

export function saudacaoEscapada (req: any, res: any) {
  // ok: sentinel-node-xss-refletido
  res.send('<h1>Olá ' + escapeHtml(req.query.name) + '</h1>')
}

export function respostaTexto (req: any, res: any) {
  // ok: sentinel-node-xss-refletido
  res.send('erro: ' + req.query.name)
}
declare function escapeHtml (s: string): string

// ---------- volta 10: XXE com replaceEntities, nome de arquivo enviado e exec dinâmico ----------
export function lerXmlReplaceEntities (libxml: any, dados: string) {
  // ruleid: sentinel-node-xxe
  return libxml.parseXml(dados, { replaceEntities: true, recover: true, nonet: false })
}

export function lerXmlReplaceDesligado (libxml: any, dados: string) {
  // ok: sentinel-node-xxe
  return libxml.parseXml(dados, { replaceEntities: false, nonet: true })
}

export function receberUpload (req: any, fs: any, path: any) {
  // ruleid: sentinel-node-path-traversal
  fs.writeFileSync(path.join(__dirname, req.file.originalname), req.file.buffer)
}

export function receberUploadSeguro (req: any, fs: any, path: any) {
  // ok: sentinel-node-path-traversal
  fs.writeFileSync(path.join(__dirname, path.basename(req.file.originalname)), req.file.buffer)
}

export function rodarComando (comando: string, exec: any) {
  // ruleid: sentinel-node-exec-dinamico
  exec(comando, () => {})
}

export function rodarComandoComTemplate (nome: string, execSync: any) {
  // ruleid: sentinel-node-exec-dinamico
  execSync(`git checkout ${nome}`)
}

export function rodarComandoFixo (exec: any) {
  // ok: sentinel-node-exec-dinamico
  exec('git status', () => {})
}

// ---------- Mass assignment: corpo da requisição define papel ou saldo ----------
export async function cadastrar (req: any, res: any) {
  // ruleid: sentinel-node-mass-assignment-campo
  req.session.role = req.body.role || 'cliente'
  res.send('ok')
}

export async function atualizar (req: any, res: any) {
  // ruleid: sentinel-node-mass-assignment-campo
  const usuario = { nome: 'x', isAdmin: req.body.isAdmin }
  res.send(usuario)
}

export async function criarComTudo (req: any, Usuario: any) {
  // ruleid: sentinel-node-mass-assignment
  return Usuario.create(req.body)
}

export async function atualizarComTudo (req: any, Usuario: any) {
  // ruleid: sentinel-node-mass-assignment
  return Usuario.findByIdAndUpdate(req.params.id, req.body)
}

export async function cadastrarSeguro (req: any, Usuario: any) {
  // ok: sentinel-node-mass-assignment
  return Usuario.create({ nome: req.body.nome, email: req.body.email, role: 'cliente' })
}

export function apenasLeitura (req: any, carteira: any) {
  // ok: sentinel-node-mass-assignment-campo
  if (req.body.role === 'admin') return carteira.balance
  return carteira.balance + req.body.nome
}

export function papelFixo (req: any) {
  // ok: sentinel-node-mass-assignment
  const papel = 'cliente'
  return papel + req.body.nome
}

// ---------- NestJS: parâmetros decorados como origem ----------
export class NestExemplo {
  @Get('goto')
  @Redirect()
  async irPara (@Query('url') url: string) {
    // ruleid: sentinel-node-open-redirect
    return { url }
  }

  @Get('goto-fixo')
  @Redirect()
  async irParaFixo (@Query('url') url: string) {
    // ok: sentinel-node-open-redirect
    return { url: '/inicio' }
  }

  @Post('calcular')
  async calcular (@Body() payload: { expressao: string }) {
    // ruleid: sentinel-node-eval-requisicao, sentinel-node-eval-dinamico
    return eval(payload.expressao)
  }

  @Post('calcular-fixo')
  async calcularFixo (@Body() payload: { expressao: string }) {
    // ok: sentinel-node-eval-requisicao
    return eval('1 + 1')
  }

  @Get('rodar')
  async rodar (@Query('cmd') cmd: string) {
    // ruleid: sentinel-node-command-injection, sentinel-node-exec-dinamico
    return execSync(cmd).toString()
  }

  @Get('rodar-spawn')
  async rodarSpawn (@Query('cmd') cmd: string) {
    // ruleid: sentinel-node-command-injection
    return spawn(cmd, [])
  }

  @Get('rodar-ok')
  async rodarOk (@Query('n') n: string) {
    // ok: sentinel-node-command-injection
    return spawn('ls', ['-la']).pid
  }

  @Get('arquivo')
  async arquivo (@Query('path') caminho: string) {
    // ruleid: sentinel-node-path-traversal
    return fs.createReadStream(caminho)
  }

  @Get('buscar')
  async buscar (@Query('url') alvo: string) {
    // ruleid: sentinel-node-ssrf
    return axios.get(alvo)
  }

  @Get('sem-decorador')
  async semDecorador (@Inject() servico: string, valor: string) {
    // ok: sentinel-node-eval-requisicao
    return JSON.parse(servico)
  }
}

// ---------- JWT: opções montadas à parte ----------
export function opcoesJwtComNone () {
  // ruleid: sentinel-node-jwt-algoritmo-none
  const opcoes = { issuer: 'sentinel', algorithms: ['HS256', 'none'] }
  return opcoes
}

export function opcoesJwtSemNone () {
  // ok: sentinel-node-jwt-algoritmo-none
  return { issuer: 'sentinel', algorithms: ['HS256', 'RS256'] }
}

export function verificarComNone (token: string, chave: string) {
  // ruleid: sentinel-node-jwt-algoritmo-none
  return jwt.verify(token, chave, { algorithms: ['none'] })
}

export function opcoesIgnorandoValidade () {
  // ruleid: sentinel-node-jwt-expiracao-ignorada
  const opcoes = { expiresIn: '2d', algorithms: ['HS256'], ignoreExpiration: true }
  return opcoes
}

export function opcoesRespeitandoValidade () {
  // ok: sentinel-node-jwt-expiracao-ignorada
  return { expiresIn: '2d', algorithms: ['HS256'], ignoreExpiration: false }
}
