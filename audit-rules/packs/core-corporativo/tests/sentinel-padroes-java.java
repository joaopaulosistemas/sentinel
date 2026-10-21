class T {
    void a() {
        try { x(); }
        catch (Exception e) {
            // ruleid: sentinel-ee-catch-relanca-sem-causa
            throw new NegocioException(e.getMessage());
        }
    }
    void b() {
        try { x(); }
        catch (Exception e) {
            // ok: sentinel-ee-catch-relanca-sem-causa
            throw new NegocioException("falha", e);
        }
    }
    void c() {
        // ruleid: sentinel-ee-lanca-excecao-generica
        throw new Exception("erro");
    }
    void d() {
        // ok: sentinel-ee-lanca-excecao-generica
        throw new SaldoInsuficienteException("erro");
    }
}
// ruleid: sentinel-ee-ejb-sem-interface
@Stateless
class ContaServicoEJB {
}
// ok: sentinel-ee-ejb-sem-interface
@Stateless
class OkServicoEJB implements OkServico {
}
// ok: sentinel-ee-ejb-sem-interface
@Stateless
class Ok2ServicoEJB extends BaseEJB implements Ok2Servico {
}
class U {
    void e() throws Exception {
        // ruleid: sentinel-ee-initialcontext-direto
        Object o = new InitialContext().lookup("java:comp/env/x");
    }
    // ruleid: sentinel-ee-dinheiro-ponto-flutuante
    private double saldoConta;
    // ok: sentinel-ee-dinheiro-ponto-flutuante
    private BigDecimal valorTotal;
    // ok: sentinel-ee-dinheiro-ponto-flutuante
    private double latitude;
    void f(String cpf, String id) {
        // ruleid: sentinel-ee-log-dado-pessoal
        LOG.info("cliente", cpf);
        // ok: sentinel-ee-log-dado-pessoal
        LOG.info("cliente", id);
    }
}
