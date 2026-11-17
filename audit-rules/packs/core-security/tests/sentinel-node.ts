import crypto from 'crypto'
import fs from 'fs'
import path from 'path'
import vm from 'vm'
import axios from 'axios'
import md5Esm from 'md5'

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

export function historico (req: any, res: any) {
  const userId = req.user.id
  const consulta = `SELECT * FROM chat_history WHERE user_id = ${userId} ORDER BY created_at`
  // ruleid: sentinel-node-sql-injection
  return pool.query(consulta)
}

export function inserirPorSessao (req: any, res: any) {
  // ruleid: sentinel-node-sql-injection
  return pool.query(`INSERT INTO notas (dono) VALUES ('${req.session.userId}')`)
}

export function historicoSeguro (req: any, res: any) {
  // ok: sentinel-node-sql-injection
  return pool.query('SELECT * FROM chat_history WHERE user_id = $1', [req.user.id])
}

export function historicoNumerico (req: any, res: any) {
  const userId = Number(req.user.id)
  // ok: sentinel-node-sql-injection
  return pool.query(`SELECT * FROM chat_history WHERE user_id = ${userId}`)
}

export function perfilEtiquetado (req: any, fastify: any) {
  const { username } = req.query
  // ok: sentinel-node-sql-injection
  return fastify.pg.query(SQL`SELECT id FROM users WHERE username = ${username}`)
}

export function perfilEtiquetadoMinusculo (req: any, pool: any) {
  // ok: sentinel-node-sql-injection
  return pool.query(sql`SELECT id FROM users WHERE id = ${req.params.id}`)
}

export function perfilConcatenadoComSql (req: any, pool: any) {
  // ruleid: sentinel-node-sql-injection
  return pool.query(`SELECT id FROM users WHERE name = '${req.query.name}'`)
}

export function consultaFixa () {
  // ok: sentinel-node-sql-injection
  return models.sequelize.query('SELECT sql FROM sqlite_master')
}

// ---------- NoSQL $where ----------
export function avaliacoes (req: any) {
  const id = req.params.id
  // ruleid: sentinel-node-nosql-where
  return db.reviews.find({ $where: 'this.product == ' + id }) // nosemgrep: sentinel-node-nosql-where-concatenado
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

// ---------- SSRF com needle, superagent e http.request (volta 27) ----------
declare const needle: any
declare const superagent: any
declare const https: any

export function buscarComNeedle (request: any, response: any) {
  const url = request.query['url']
  // ruleid: sentinel-node-ssrf
  needle.get(url, { timeout: 3000 }, (erro: any, resposta: any) => response.send(resposta.body))
}

export function enviarComNeedle (req: any) {
  // ruleid: sentinel-node-ssrf
  return needle.post(req.body.destino, { a: 1 })
}

export function buscarComSuperagent (req: any) {
  // ruleid: sentinel-node-ssrf
  return superagent.get(req.query.alvo)
}

export function buscarComHttpsRequest (req: any) {
  // ruleid: sentinel-node-ssrf
  return https.request(req.query.alvo, () => {})
}

export function needleFixo () {
  // ok: sentinel-node-ssrf
  return needle.get('https://api.exemplo.com/status')
}

export function superagentFixo (req: any) {
  // ok: sentinel-node-ssrf
  return superagent.post('https://api.exemplo.com/eventos').send(req.body)
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

// ---------- cifra ECB e IV fixo ----------
export function cifraEcb (chave: Buffer, dado: Buffer) {
  // ruleid: sentinel-node-cifra-ecb
  const c = crypto.createCipheriv('aes-128-ecb', chave, '')
  return Buffer.concat([c.update(dado), c.final()])
}

export function decifraEcb (chave: Buffer, dado: Buffer) {
  // ruleid: sentinel-node-cifra-ecb
  const d = crypto.createDecipheriv('AES-256-ECB', chave, null)
  return Buffer.concat([d.update(dado), d.final()])
}

export function cifraCbc (chave: Buffer, dado: Buffer, iv: Buffer) {
  // ok: sentinel-node-cifra-ecb
  const c = crypto.createCipheriv('aes-256-cbc', chave, iv)
  return Buffer.concat([c.update(dado), c.final()])
}

export function cifraIvLiteral (chave: Buffer, dado: Buffer) {
  // ruleid: sentinel-node-iv-fixo
  const c = crypto.createCipheriv('aes-128-cbc', chave, 'super_secure_iv2')
  return Buffer.concat([c.update(dado), c.final()])
}

export function cifraIvBufferLiteral (chave: Buffer, dado: Buffer) {
  // ruleid: sentinel-node-iv-fixo
  const c = crypto.createCipheriv('aes-256-gcm', chave, Buffer.from('000000000000'))
  return Buffer.concat([c.update(dado), c.final()])
}

export class Segredos {
  static IV = 'valor-fixo-de-16b'
}

export function cifraIvEstatico (chave: Buffer, dado: Buffer) {
  // ruleid: sentinel-node-iv-fixo
  const c = crypto.createCipheriv('aes-256-cbc', chave, Segredos.IV)
  return Buffer.concat([c.update(dado), c.final()])
}

export function cifraIvAleatorio (chave: Buffer, dado: Buffer) {
  const iv = crypto.randomBytes(16)
  // ok: sentinel-node-iv-fixo
  const c = crypto.createCipheriv('aes-256-cbc', chave, iv)
  return Buffer.concat([iv, c.update(dado), c.final()])
}

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

// ---------- new Function com texto dinâmico ----------
export function montarGancho (codigo: string) {
  // ruleid: sentinel-node-function-dinamico
  return new Function('require', 'process', codigo)
}

export function montarGanchoDeObjeto (lote: any) {
  // ruleid: sentinel-node-function-dinamico
  return new Function(lote.codigo)
}

export function somador () {
  // ok: sentinel-node-function-dinamico
  return new Function('a', 'b', 'return a + b')
}

// ---------- XPath com texto montado ----------
export function buscarNoXml (req: any, doc: any) {
  const valor = req.query.valor
  // ruleid: sentinel-node-xpath-injection
  return xpath.evaluate("//config/*[release/text()='" + valor + "']", doc, null, xpath.XPathResult.ANY_TYPE, null)
}

export function buscarNoXmlComTemplate (valor: string, doc: any) {
  // ruleid: sentinel-node-xpath-injection
  return xpath.select(`//usuario[@nome='${valor}']`, doc)
}

export function buscarNoXmlFixo (doc: any) {
  // ok: sentinel-node-xpath-injection
  return xpath.select('//usuario[@ativo="true"]', doc)
}

export function buscarNoXmlComVariavel (doc: any, variaveis: any) {
  // ok: sentinel-node-xpath-injection
  return xpath.parse('//usuario[@nome=$nome]').select({ node: doc, variables: variaveis })
}

// ---------- LDAP com filtro montado ----------
export async function procurarNoDiretorio (req: any, diretorio: any) {
  const usuario = req.query.user || ''
  // ruleid: sentinel-node-ldap-injection
  const filtro = `(&(objectClass=account)(|(uid=${usuario})(cn=servico)))`
  return diretorio.search(filtro)
}

export async function procurarNoLdapjs (req: any, cliente: any) {
  // ruleid: sentinel-node-ldap-injection
  const opcoes = { filter: '(uid=' + req.body.uid + ')', scope: 'sub' }
  return cliente.search('dc=exemplo,dc=com', opcoes)
}

export async function procurarNoDiretorioEscapado (req: any, diretorio: any) {
  const usuario = escaparFiltroLdap(req.query.user || '')
  // ok: sentinel-node-ldap-injection
  const filtro = `(&(objectClass=account)(uid=${usuario}))`
  return diretorio.search(filtro)
}

export function frase (req: any) {
  // ok: sentinel-node-ldap-injection
  const texto = `Olá (${req.query.nome})`
  return texto
}

// ---------- caminho vindo de argumento GraphQL e de cabeçalho de JWT sem verificar ----------
export const resolvers = {
  Mutation: {
    gravarArquivo: async (parent: any, args: any, context: any, info: any) => {
      const destino = __dirname + '/../../public/uploads/' + context.user + '/' + args.filePath
      // ruleid: sentinel-node-path-traversal
      await fsPromise.writeFile(destino, args.fileContent)
      return { destino }
    },
    gravarArquivoSeguro: async (parent: any, args: any, context: any, info: any) => {
      const destino = __dirname + '/uploads/' + path.basename(args.filePath)
      // ok: sentinel-node-path-traversal
      await fsPromise.writeFile(destino, args.fileContent)
      return { destino }
    }
  }
}

export function carregarChave (token: string) {
  const cabecalho = jwt.decode(token, { complete: true })?.header
  const caminho = path.resolve('/opt/chaves', cabecalho.kid)
  // ruleid: sentinel-node-path-traversal
  return fs.readFileSync(caminho)
}

export async function carregarJwks (token: string) {
  const cabecalho = jwt.decode(token, { complete: true })?.header
  const url = new URL(cabecalho.jku)
  // ruleid: sentinel-node-ssrf
  return fetch(url)
}

export function lerClaims (token: string) {
  const claims = jwt.decode(token)
  // ok: sentinel-node-path-traversal
  return fs.readFileSync('/opt/dados/fixo.json', 'utf8') + String(claims)
}

// ---------- Segredos de sessão, código de autorização e $where montado por texto (volta 24) ----------
export function codigos () {
  // ruleid: sentinel-node-math-random-segredo
  let code = Math.floor(Math.random() * (100000 - 1) + 1)
  // ruleid: sentinel-node-math-random-segredo
  const jti = Math.random().toString(36)
  // ruleid: sentinel-node-math-random-segredo
  let accesstoken = Math.floor(Math.random() * 99999) + ''
  // ok: sentinel-node-math-random-segredo
  const statusCode = Math.floor(Math.random() * 3)
  return [code, jti, accesstoken, statusCode]
}

export function sessao (app: any, expressSession: any, config: any) {
  // ruleid: sentinel-node-session-secret-literal
  app.use(expressSession({
    secret: 'changethistoconfigfile',
    resave: false
  }))
  // ok: sentinel-node-session-secret-literal
  app.use(expressSession({
    secret: process.env.SESSION_SECRET,
    resave: false
  }))
  // ok: sentinel-node-session-secret-literal
  app.use(expressSession({
    secret: config.session.secret
  }))
}

export function refrescar (db: any, mytoken: string) {
  // ruleid: sentinel-node-nosql-where-concatenado
  const query = '{"$where": "function() { return this.token == ' + mytoken + '; }"}'
  return db.collection('refreshtokens').find(JSON.parse(query))
}

export function buscarPorNome (db: any, nome: string) {
  // ruleid: sentinel-node-nosql-where-concatenado
  return db.collection('u').find({ $where: 'this.nome == "' + nome + '"' })
}

export function buscarFixo (db: any) {
  // ok: sentinel-node-nosql-where-concatenado
  return db.collection('u').find({ $where: 'this.ativo == true' })
}

// ---------- Next.js (App Router) e Prisma ----------
declare const Database: any
declare const prisma: any
declare const baseDir: string
declare function readFile (p: string, ...a: any[]): Promise<any>
declare function join (...a: string[]): string
declare function parseBody (r: any, s: any): Promise<any>

export async function buscaProdutos (request: any) {
  const { searchParams } = new URL(request.url)
  const q = searchParams.get('q')
  const sql = `SELECT * FROM products WHERE name LIKE '%${q}%'`
  // ruleid: sentinel-node-prisma-raw-unsafe
  return prisma.$queryRawUnsafe(sql)
}

export async function apagarPorId (id: string) {
  // ruleid: sentinel-node-prisma-raw-unsafe
  await prisma.$executeRawUnsafe('DELETE FROM t WHERE id = ' + id)
}

export async function consultaFixa (id: string) {
  // ok: sentinel-node-prisma-raw-unsafe
  await prisma.$queryRawUnsafe('SELECT 1')
  // ok: sentinel-node-prisma-raw-unsafe
  return prisma.$queryRawUnsafe('SELECT * FROM t WHERE id = $1', id)
}

export function filtrarAutor (autor: string) {
  const db = new Database('x.db')
  const consulta = `SELECT * FROM reviews WHERE author = '${autor}'`
  // ruleid: sentinel-node-sqlite-sql-dinamico
  db.exec(consulta)
  // ruleid: sentinel-node-sqlite-sql-dinamico
  return db.prepare(`SELECT * FROM reviews WHERE author = '${autor}'`).all()
}

export function filtrarAutorSeguro (autor: string) {
  const db = new Database('x.db')
  // ok: sentinel-node-sqlite-sql-dinamico
  return db.prepare('SELECT * FROM reviews WHERE author = ?').all(autor)
}

export async function lerArquivoNext (request: any) {
  const { searchParams } = new URL(request.url)
  const file = searchParams.get('file')
  // ruleid: sentinel-node-path-traversal
  return readFile(join(baseDir, file))
}

export async function lerArquivoBody (request: any) {
  const corpo = await request.json()
  // ruleid: sentinel-node-path-traversal
  return readFile(join(baseDir, corpo.nome))
}

export async function lerArquivoFixoNext () {
  // ok: sentinel-node-path-traversal
  return readFile(join(baseDir, 'leia-me.txt'))
}

export async function capturaTela (request: any) {
  const parsed = await parseBody(request, {})
  const { screenshotUrl } = parsed.data
  // ruleid: sentinel-node-ssrf
  const r = await fetch(screenshotUrl)
  return r.text()
}

export async function capturaFixa () {
  // ok: sentinel-node-ssrf
  const r = await fetch('https://api.exemplo.com/status')
  return r.text()
}

declare function parseQuery (s: any, schema: any): any
declare function useSearchParams (): any

export async function lerArquivoValidado (request: any) {
  const { searchParams } = new URL(request.url)
  const parsed = parseQuery(searchParams, {})
  const file = parsed.data.file
  // ruleid: sentinel-node-path-traversal
  return readFile(join(baseDir, file))
}

export function loginNavegador () {
  const params = useSearchParams()
  const token = params.get('token')
  // ok: sentinel-node-ssrf
  return fetch(`/api/auth/login?token=${token}`)
}

export function loginNavegador2 () {
  const searchParams = useSearchParams()
  const token = searchParams.get('token')
  // ok: sentinel-node-ssrf
  return fetch(`/api/auth/login?token=${token}`)
}

// ---------- Math.random devolvido por função de segurança e TLS desligado (volta 34) ----------
export function generateOTP () {
  // ruleid: sentinel-node-math-random-retorno
  return Math.floor(1000 + Math.random() * 9000).toString()
}

export const gerarToken = () => {
  // ruleid: sentinel-node-math-random-retorno
  return Math.random().toString(36).slice(2)
}

export function sortearCarta () {
  // ok: sentinel-node-math-random-retorno
  return Math.floor(Math.random() * 52)
}

export function criarTransporte (nodemailer: any) {
  return nodemailer.createTransport({
    host: 'smtp.exemplo.com',
    tls: {
      // ruleid: sentinel-node-tls-desligado
      rejectUnauthorized: false
    }
  })
}

export function clienteHttps (https: any) {
  // ruleid: sentinel-node-tls-desligado
  return new https.Agent({ rejectUnauthorized: false })
}

export function desligaGlobal () {
  // ruleid: sentinel-node-tls-desligado
  process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'
}

export function clienteSeguro (https: any) {
  // ok: sentinel-node-tls-desligado
  return new https.Agent({ rejectUnauthorized: true })
}

// ---------- SQL injection em driver sqlite3 (db.get/all/run/each/exec) ----------
export function loginSqlite (req: any, res: any, db: any) {
  const { username, password } = req.body
  const query = `SELECT * FROM users WHERE username = '${username}' AND password = '${password}'`
  // ruleid: sentinel-node-sql-injection
  db.get(query, (err: any, user: any) => res.json(user))
}

export function buscaSqlite (req: any, res: any, db: any) {
  const busca = req.query.search || ''
  // ruleid: sentinel-node-sql-injection
  db.all(`SELECT * FROM products WHERE name LIKE '%${busca}%'`, (err: any, rows: any) => res.json(rows))
}

export function buscaSqliteSegura (req: any, res: any, db: any) {
  // ok: sentinel-node-sql-injection
  db.get('SELECT * FROM users WHERE id = ?', [req.params.id], (err: any, row: any) => res.json(row))
}

export function mapaNaoEhBanco (req: any, cache: Map<string, string>) {
  // ok: sentinel-node-sql-injection
  return cache.get(req.query.chave)
}

// ---------- hash fraco por pacote (md5, sha1) ----------
export function senhaMd5Pacote (senha: string) {
  const md5 = require('md5')
  // ruleid: sentinel-node-hash-fraco-pacote
  return md5(senha)
}

export function assinaturaSha1Pacote (dado: string) {
  const sha1 = require('sha1')
  // ruleid: sentinel-node-hash-fraco-pacote
  return sha1(dado)
}

export function senhaMd5Esm (senha: string) {
  // ruleid: sentinel-node-hash-fraco-pacote
  return md5Esm(senha)
}

export function funcaoLocalMd5 (dado: string) {
  const md5 = (x: string) => x.toUpperCase()
  // ok: sentinel-node-hash-fraco-pacote
  return md5(dado)
}

export function hashPacoteForte (dado: string) {
  const hash = require('hash.js')
  // ok: sentinel-node-hash-fraco-pacote
  return hash.sha256().update(dado).digest('hex')
}

// ---------- jwt.decode sem verificar assinatura ----------
export const verificaToken = (req: any, res: any, next: any) => {
  // ruleid: sentinel-node-jwt-decode-sem-verificar
  const dados = jwt.decode(req.headers.token)
  req.userId = dados.id
  next()
}

export function tokenVerificado (req: any) {
  // ok: sentinel-node-jwt-decode-sem-verificar
  return jwt.verify(req.headers.token, process.env.JWT_SECRET, { algorithms: ['HS256'] })
}

export function decodeDeOutraBiblioteca (valor: string) {
  // ok: sentinel-node-jwt-decode-sem-verificar
  return base64.decode(valor)
}

// ---------- cookie com httpOnly desligado ----------
// ruleid: sentinel-node-cookie-httponly-desligado
const cookieSessaoInseguro = {
  secure: false,
  httpOnly: false,
  sameSite: 'lax'
}

// ok: sentinel-node-cookie-httponly-desligado
export const cookieSessaoSeguro = {
  secure: true,
  httpOnly: true,
  sameSite: 'lax'
}

// ---------- eval passado como função de retorno (código remoto executado) ----------
export function atualizarScript(): void {
  // ruleid: sentinel-node-eval-callback
  fetch('http://example.com/update-script.js').then((r) => r.text()).then(eval);

  // ruleid: sentinel-node-eval-callback
  carregar().then(Function);

  // ok: sentinel-node-eval-callback
  fetch('/config.json').then((r) => r.json()).then(aplicarConfig);
}

// ---------- credencial escrita no código e comparada com o que o usuário digitou ----------
export function checarChave(apiKey: string, senha: string, modo: string): boolean {
  // ruleid: sentinel-node-credencial-literal-comparada
  if (apiKey === 'adminadmin') {
    return true;
  }
  // ruleid: sentinel-node-credencial-literal-comparada
  if ('S3nhaMestra!' == senha) {
    return true;
  }
  // ok: sentinel-node-credencial-literal-comparada
  if (modo === 'administrador') {
    return true;
  }
  // ok: sentinel-node-credencial-literal-comparada
  return apiKey === process.env.API_KEY;
}
