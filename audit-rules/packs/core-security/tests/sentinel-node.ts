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
