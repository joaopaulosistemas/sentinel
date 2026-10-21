package demo;

import java.util.List;
import org.springframework.data.domain.Pageable;

class Exemplos {
    private ContaRepository contaRepository;
    private ClienteRepository repo;
    private final List<Long> ids = List.of(1L);

    void nMaisUm(List<Long> ids) {
        for (Long id : ids) {
            // ruleid: sentinel-jpa-n-mais-um
            contaRepository.findById(id);
        }
        ids.forEach(id -> {
            // ruleid: sentinel-jpa-n-mais-um
            repo.findByCpf("x" + id);
        });
        // ok: sentinel-jpa-n-mais-um
        for (Object c : contaRepository.findByAtivoTrue()) {
            System.out.println(c);
        }
        // ok: sentinel-jpa-n-mais-um
        contaRepository.findAtivas().stream().map(c -> c.getId()).toList();
        // ok: sentinel-jpa-n-mais-um
        contaRepository.findAtivas().forEach(c -> System.out.println(c));
        // ok: sentinel-jpa-n-mais-um
        contaRepository.findAllById(ids);
        // ok: sentinel-jpa-n-mais-um
        contaRepository.findById(1L);
    }

    void escrita(List<Conta> contas) {
        for (Conta c : contas) {
            // ruleid: sentinel-jpa-escrita-em-loop
            contaRepository.save(c);
        }
        // ok: sentinel-jpa-escrita-em-loop
        contaRepository.saveAll(contas);
        for (Conta c : contas) {
            // ok: sentinel-jpa-escrita-em-loop
            contaRepository.saveAll(List.of(c));
        }
    }

    void todos(Pageable p) {
        // ruleid: sentinel-jpa-findall-sem-paginacao
        contaRepository.findAll();
        // ok: sentinel-jpa-findall-sem-paginacao
        contaRepository.findAll(p);
        // ruleid: sentinel-jpa-pageable-unpaged
        contaRepository.findAll(Pageable.unpaged());
    }

    // ruleid: sentinel-jpa-muitos-para-um-eager-padrao
    @ManyToOne
    private Cliente cliente;

    // ruleid: sentinel-jpa-muitos-para-um-eager-padrao
    @OneToOne(optional = false)
    private Conta conta;

    // ok: sentinel-jpa-muitos-para-um-eager-padrao
    @ManyToOne(fetch = FetchType.LAZY)
    private Cliente lazy;

    // ok: sentinel-jpa-muitos-para-um-eager-padrao
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Cliente lazy2;

    void sql() {
        // ruleid: sentinel-sql-select-estrela
        String a = "SELECT * FROM conta";
        // ok: sentinel-sql-select-estrela
        String b = "SELECT id, saldo FROM conta";
        // ruleid: sentinel-sql-like-curinga-inicial
        String c = "select id from conta where nome like '%silva'";
        // ruleid: sentinel-sql-like-curinga-inicial
        String d = "select c from Conta c where c.nome like %:nome";
        // ok: sentinel-sql-like-curinga-inicial
        String e = "select id from conta where nome like 'silva%'";
        // ruleid: sentinel-sql-order-by-aleatorio
        String f = "select id from conta order by random()";
        // ok: sentinel-sql-order-by-aleatorio
        String g = "select id from conta order by id";
        // ruleid: sentinel-sql-delete-sem-where
        String h = "DELETE FROM conta";
        // ok: sentinel-sql-delete-sem-where
        String i = "DELETE FROM conta WHERE id = ?";
        // ruleid: sentinel-sql-update-sem-where
        String j = "update conta set saldo = 0";
        // ok: sentinel-sql-update-sem-where
        String k = "update conta set saldo = 0 where id = ?";
    }
}
