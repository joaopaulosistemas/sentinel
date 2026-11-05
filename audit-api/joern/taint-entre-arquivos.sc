// Sentinel — taint entre arquivos com Joern (Java/Spring/Quarkus), sem compilar o projeto.
//
// Uso (o Sentinel chama pelo pacote market-tools, ferramenta "joern-taint"):
//   joern --script taint-entre-arquivos.sc --param inputPath=<projeto> --param outFile=<saida.sarif>
//
// Lê o código-fonte com o frontend javasrc2cpg (não precisa de build nem de dependências), procura caminhos do dado
// que entra por um endpoint (Spring MVC ou JAX-RS) e chega a um ponto perigoso, e grava SARIF 2.1.0 com o caminho.
// Só relata caminhos que atravessam mais de um arquivo: dentro de um arquivo, o Opengrep já cobre.
// Parâmetro opcional: onlyCrossFile=false relata todos os caminhos.

import io.shiftleft.codepropertygraph.generated.nodes.{Call, CfgNode, Expression}
import ujson.{Arr, Obj}

case class Classe(id: String, nome: String, cwe: String, mensagem: String, severidade: String)

val SQLI = Classe("sqli-entre-arquivos", "SQL montada com dado do usuário (entre arquivos)", "CWE-89",
  "Dado vindo da requisição chega a uma consulta SQL montada por texto, passando por mais de um arquivo. Use parâmetros (?) em vez de concatenar.", "error")
val CMDI = Classe("cmdi-entre-arquivos", "Comando do sistema com dado do usuário (entre arquivos)", "CWE-78",
  "Dado vindo da requisição chega à execução de um comando do sistema, passando por mais de um arquivo. Não monte comandos com texto externo; use lista de argumentos fixa e valide.", "error")
val PATH = Classe("path-traversal-entre-arquivos", "Caminho de arquivo com dado do usuário (entre arquivos)", "CWE-22",
  "Dado vindo da requisição chega a um caminho de arquivo, passando por mais de um arquivo. Normalize o caminho e confira se continua dentro do diretório permitido.", "error")

// Entradas: parâmetros de métodos de endpoint (Spring MVC e JAX-RS/Quarkus).
val ANOT_ENDPOINT = "(?i)(Get|Post|Put|Delete|Patch|Request)Mapping|GET|POST|PUT|DELETE|PATCH|Path"
val ANOT_PARAM_ENTRADA = "(?i)RequestParam|PathVariable|RequestBody|RequestHeader|CookieValue|ModelAttribute|QueryParam|PathParam|FormParam|HeaderParam|CookieParam|BeanParam|MatrixParam"

// Passos que tornam o dado inofensivo para aquela classe (conversão para número, booleano ou UUID).
val SANEADORES = "(parseInt|parseLong|parseDouble|parseFloat|parseBoolean|fromString|getInteger|getLong|encodeForSQL|encodeForOS)"

@main def exec(inputPath: String, outFile: String, onlyCrossFile: String = "true"): Unit = {
  importCode(inputPath)
  run.ossdataflow

  def entradas = cpg.method.where(_.annotation.name(ANOT_ENDPOINT)).parameter.where(_.annotation.name(ANOT_PARAM_ENTRADA))

  def sqlSinks = cpg.call.name("(?i)(executeQuery|executeUpdate|execute|prepareStatement|prepareCall|createQuery|createNativeQuery|queryForObject|queryForList|queryForMap|queryForRowSet|query|update|batchUpdate)")
    .argument.argumentIndex(1)
  def cmdSinks = cpg.call.name("(exec|start|<init>)").where(_.methodFullName("(?i).*(Runtime\\.exec|ProcessBuilder).*")).argument.argumentIndex(1, 2)
  def pathSinks = cpg.call.name("(<init>|get|of|readString|readAllBytes|readAllLines|newInputStream|newBufferedReader|copy|write|delete)")
    .where(_.methodFullName("(?i).*(java\\.io\\.File(InputStream|OutputStream|Reader|Writer)?|java\\.nio\\.file\\.(Paths|Path|Files)|FileSystemResource).*"))
    .argument.argumentIndex(1)

  val achados = scala.collection.mutable.ListBuffer[Obj]()
  val vistos = scala.collection.mutable.Set[String]()

  def arquivoDe(n: CfgNode): String = n.method.filename

  for ((classe, sinks) <- List((SQLI, sqlSinks), (CMDI, cmdSinks), (PATH, pathSinks))) {
    sinks.reachableByFlows(entradas).foreach { fluxo =>
      val passos = fluxo.elements.collect { case c: CfgNode => c }
      val arquivos = passos.map(arquivoDe).distinct
      val saneado = fluxo.elements.exists {
        case c: Call => c.name.matches(SANEADORES)
        case _ => false
      }
      val cruza = arquivos.size > 1
      // O Joern trata o objeto da chamada (this.em, this.repo) como se levasse o dado ao argumento. Quando o argumento
      // do sink é só texto fixo (literais e operadores, como "select ... " + "where ..."), não há dado do usuário nele.
      val soTextoFixo = passos.last match {
        case _: Literal => true
        case c: Call => c.ast.isIdentifier.isEmpty && c.ast.isFieldIdentifier.isEmpty && c.ast.isCall.filterNot(_.name.startsWith("<operator>")).isEmpty
        case _ => false
      }
      if (!saneado && !soTextoFixo && (cruza || onlyCrossFile.equalsIgnoreCase("false"))) {
        val fim = passos.last
        val chave = classe.id + "|" + arquivoDe(fim) + "|" + fim.lineNumber.getOrElse(0)
        if (vistos.add(chave)) {
          val locais = passos.map { p =>
            Obj("location" -> Obj("physicalLocation" -> Obj(
              "artifactLocation" -> Obj("uri" -> arquivoDe(p)),
              "region" -> Obj("startLine" -> p.lineNumber.getOrElse(1).asInstanceOf[Int]))),
              "message" -> Obj("text" -> p.code.take(120)))
          }
          achados += Obj(
            "ruleId" -> classe.id,
            "level" -> classe.severidade,
            "message" -> Obj("text" -> (classe.mensagem + " Caminho: " + arquivos.mkString(" → "))),
            "locations" -> Arr(Obj("physicalLocation" -> Obj(
              "artifactLocation" -> Obj("uri" -> arquivoDe(fim)),
              "region" -> Obj("startLine" -> fim.lineNumber.getOrElse(1).asInstanceOf[Int])))),
            "codeFlows" -> Arr(Obj("threadFlows" -> Arr(Obj("locations" -> Arr.from(locais))))))
        }
      }
    }
  }

  val regras = List(SQLI, CMDI, PATH).map { c =>
    Obj("id" -> c.id, "name" -> c.nome,
      "shortDescription" -> Obj("text" -> c.nome),
      "help" -> Obj("text" -> c.mensagem),
      "properties" -> Obj("tags" -> Arr(c.cwe, "security")))
  }
  val sarif = Obj(
    "version" -> "2.1.0",
    "$schema" -> "https://json.schemastore.org/sarif-2.1.0.json",
    "runs" -> Arr(Obj(
      "tool" -> Obj("driver" -> Obj("name" -> "Joern", "informationUri" -> "https://joern.io", "rules" -> Arr.from(regras))),
      "results" -> Arr.from(achados))))
  os.write.over(os.Path(outFile, os.pwd), ujson.write(sarif, indent = 2))
  println(s"joern-taint: ${achados.size} achado(s) em $outFile")
}
