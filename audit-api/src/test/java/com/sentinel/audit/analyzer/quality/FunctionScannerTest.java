package com.sentinel.audit.analyzer.quality;

import com.sentinel.audit.analyzer.quality.FunctionScanner.Fn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FunctionScannerTest {

    private static List<Fn> java(String src) {
        return FunctionScanner.scan(SourceSanitizer.strip(src), false);
    }

    private static List<Fn> ts(String src) {
        return FunctionScanner.scan(SourceSanitizer.strip(src), true);
    }

    private static List<String> names(List<Fn> fns) {
        return fns.stream().map(Fn::name).toList();
    }

    // ------------------------------------------------------------------ Java: o que é função

    @Test
    void encontraMetodosEConstrutoresEIgnoraControleDeFluxo() {
        List<Fn> fns = java("""
                class A {
                    A(int x) { if (x > 0) { run(); } }
                    public void metodo() throws Exception {
                        for (int i = 0; i < 3; i++) { run(); }
                        while (true) { break; }
                        switch (x) { case 1: break; }
                        try { run(); } catch (Exception e) { run(); }
                        synchronized (this) { run(); }
                    }
                    abstract void semCorpo(int a);
                }
                """);
        assertEquals(List.of("A", "metodo"), names(fns));
    }

    @Test
    void naoConfundeClasseAnonimaRecordELambdaComMetodo() {
        List<Fn> fns = java("""
                class A {
                    record Par(int a, int b) { }
                    void m() {
                        Runnable r = new Runnable() { public void run() { go(); } };
                        executor.submit(() -> { go(); });
                    }
                }
                """);
        assertEquals(List.of("m", "run"), names(fns));       // run é método real da classe anônima; Par e Runnable() não
    }

    @Test
    void metodoComGenericosAnotacoesEThrows() {
        List<Fn> fns = java("""
                class A {
                    @Override
                    public <T extends Comparable<T>> List<T> ordena(Map<String, List<T>> mapa, @Size(max = 3) String s)
                            throws IOException, InterruptedException {
                        return null;
                    }
                }
                """);
        assertEquals(List.of("ordena"), names(fns));
        assertEquals(2, fns.get(0).paramCount());
    }

    // ------------------------------------------------------------------ métricas

    @Test
    void contaLinhasDeCodigoSemComentariosNemBrancos() {
        Fn f = java("""
                class A {
                    void m() {
                        // comentário

                        int a = 1;
                        /* bloco
                           longo */
                        int b = 2;
                    }
                }
                """).get(0);
        assertEquals(4, f.codeLines());            // assinatura, a, b e a chave final
        assertEquals(2, f.startLine());
        assertEquals(9, f.endLine());
    }

    @Test
    void complexidadeContaDecisoesEnaoTextoNemGenericos() {
        Fn f = java("""
                class A {
                    int m(Map<?, ?> a, List<? extends Number> b) {
                        String s = "if (x) && || ?";      // if (y)
                        if (a == null || b == null) { return 0; }
                        else if (a.isEmpty() && b.isEmpty()) { return 1; }
                        for (int i = 0; i < 3; i++) { while (i > 1) { i--; } }
                        switch (i) { case 1: return 1; case 2: return 2; default: break; }
                        try { go(); } catch (IOException e) { fail(); }
                        return a.size() > 2 ? 1 : 2;
                    }
                }
                """).get(0);
        // 1 + if + || + else-if + && + for + while + case + case + catch + ternário = 11
        assertEquals(11, f.complexity());
    }

    @Test
    void aninhamentoContaNiveisDeBlocos() {
        assertEquals(0, java("class A { void m() { run(); } }").get(0).nesting());
        assertEquals(1, java("class A { void m() { if (a) { run(); } } }").get(0).nesting());
        assertEquals(5, java("""
                class A {
                    void m() {
                        if (a) { for (;;) { while (b) { try { if (c) { run(); } } finally { } } } }
                    }
                }
                """).get(0).nesting());
    }

    @Test
    void contaParametrosNoNivelDeTopo() {
        assertEquals(0, FunctionScanner.countParams(""));
        assertEquals(0, FunctionScanner.countParams("   "));
        assertEquals(1, FunctionScanner.countParams("int a"));
        assertEquals(2, FunctionScanner.countParams("Map<String, List<Integer>> a, int b"));
        assertEquals(2, FunctionScanner.countParams("@Size(max = 3, min = 1) String a, int b"));
        assertEquals(1, FunctionScanner.countParams("{ a, b, c }: Props"));
        assertEquals(2, FunctionScanner.countParams("cb: (x: number, y: number) => void, z: string"));
        assertEquals(2, FunctionScanner.countParams("a: number, b: string,"));
        assertEquals(3, FunctionScanner.countParams("a, b = [1, 2], c"));
    }

    // ------------------------------------------------------------------ TypeScript

    @Test
    void encontraFuncoesMetodosEArrowsNomeadas() {
        List<Fn> fns = ts("""
                export function soma(a: number, b: number): number { return a + b; }

                export class Servico {
                  constructor(private http: HttpClient) { }
                  private async carrega(id: string): Promise<Item[]> { return []; }
                  get nome(): string { return 'x'; }
                }

                const dobra = (n: number): number => { return n * 2; };
                export const buscar = async (url: string) => { return fetch(url); };
                let curta = x => { return x; };
                const expressao = (n: number) => n * 2;
                const lista = itens.map((i) => { return i; });
                """);
        assertEquals(List.of("soma", "constructor", "carrega", "nome", "dobra", "buscar", "curta"), names(fns));
    }

    @Test
    void callbacksAnonimosContamNaFuncaoQueOsContem() {
        Fn f = ts("""
                export function processa(itens: Item[]) {
                  itens.forEach((i) => {
                    if (i.ativo && i.valor > 0) { run(i); }
                  });
                }
                """).get(0);
        assertEquals("processa", f.name());
        assertEquals(3, f.complexity());           // 1 + if + &&
        assertEquals(2, f.nesting());              // callback + if
    }

    @Test
    void opcionalEEncadeamentoOpcionalNaoSaoTernario() {
        Fn f = ts("""
                function m(a?: number, b?: string): void {
                  const x = obj?.prop?.outro;
                  const y = a ?? 5;
                  const z = a ? 1 : 2;
                }
                """).get(0);
        assertEquals(3, f.complexity());           // 1 + ?? + ternário
        assertEquals(2, f.paramCount());
    }

    @Test
    void parametrosDestructuringNoReact() {
        Fn f = ts("export const Lista = ({ itens, onSelect, filtro }: Props) => { return null; };").get(0);
        assertEquals("Lista", f.name());
        assertEquals(1, f.paramCount());
    }

    // ------------------------------------------------------------------ robustez

    @Test
    void codigoDesbalanceadoOuVazioNaoQuebra() {
        assertTrue(java("").isEmpty());
        assertDoesNotThrow(() -> java("class A { void m() { if (x) { "));
        assertDoesNotThrow(() -> ts("function f( { ((( "));
        assertTrue(java("class A { void m() { run(); ").isEmpty());   // chave não fecha: ignora em vez de chutar
    }

    @Test
    void classeQueEstendeChamadaNaoEFuncao() {
        // "class X extends Base('jwt') {" não é a função "Base"
        assertTrue(names(ts("class Guard extends AuthGuard('jwt') { run() { return 1; } }")).stream()
                .noneMatch("AuthGuard"::equals));
        assertTrue(names(ts("class Guard extends AuthGuard('jwt') { run() { return 1; } }")).contains("run"));
    }

    @Test
    void objetoLiteralNaoContaComoAninhamento() {
        // esquema de documentação: 4 níveis de objeto, nenhum bloco de lógica
        assertEquals(0, ts("""
                function schema() {
                    return { a: { b: { c: { d: 1 } } } };
                }
                """).get(0).nesting());
        // objeto como argumento e como tipo anônimo também não contam
        assertEquals(0, ts("function f() { g({ x: { y: 1 } }); const t: { a: string } = h(); }").get(0).nesting());
        // blocos de lógica continuam contando
        assertEquals(2, ts("function f() { if (a) { for (const x of y) { g({ z: 1 }); } } }").get(0).nesting());
    }
}
