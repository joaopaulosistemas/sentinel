package demo;

import org.springframework.web.bind.annotation.*;

class Exemplos {

    // ruleid: sentinel-spring-requestmapping-sem-method
    @RequestMapping("/contas")
    public String listar() {
        return "x";
    }

    // ok: sentinel-spring-requestmapping-sem-method
    @RequestMapping(value = "/contas", method = RequestMethod.GET)
    public String listarGet() {
        return "x";
    }

    // ok: sentinel-spring-requestmapping-sem-method
    @GetMapping("/contas")
    public String listarNovo() {
        return "x";
    }

    // ruleid: sentinel-spring-requestbody-sem-valid
    public void criar(@RequestBody ContaDto dto) {
    }

    // ok: sentinel-spring-requestbody-sem-valid
    public void criarValido(@Valid @RequestBody ContaDto dto) {
    }

    // ok: sentinel-spring-requestbody-sem-valid
    public void criarValido2(@RequestBody @Valid ContaDto dto) {
    }

    void regras(HttpSecurity http) throws Exception {
        // ruleid: sentinel-spring-permitall-geral
        http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
        // ok: sentinel-spring-permitall-geral
        http.authorizeHttpRequests(a -> a.anyRequest().authenticated());
        // ruleid: sentinel-spring-frameoptions-desligado
        http.headers().frameOptions().disable();
        // ok: sentinel-spring-frameoptions-desligado
        http.headers().frameOptions().sameOrigin();
    }

    class Conta {
        // ruleid: sentinel-spring-fetch-eager
        @ManyToOne(fetch = FetchType.EAGER)
        Pessoa titular;

        // ok: sentinel-spring-fetch-eager
        @ManyToOne(fetch = FetchType.LAZY)
        Pessoa gerente;
    }

    void erro(Exception e) {
        // ruleid: sentinel-spring-print-stack-trace
        e.printStackTrace();
        // ok: sentinel-spring-print-stack-trace
        log.error("falhou", e);
    }
}

// ruleid: sentinel-spring-entidade-com-data
@Data
@Entity
class ContaRuim {
}

// ok: sentinel-spring-entidade-com-data
@Getter
@Setter
@Entity
class ContaBoa {
}

// ok: sentinel-spring-entidade-com-data
@Data
class DtoSimples {
}

class SenhaSemHash {
    @Bean
    public PasswordEncoder semHash() {
        // ruleid: sentinel-spring-senha-sem-hash
        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public PasswordEncoder semHashNovo() {
        // ruleid: sentinel-spring-senha-sem-hash
        return new NoOpPasswordEncoder();
    }

    @Bean
    public PasswordEncoder comHash() {
        // ok: sentinel-spring-senha-sem-hash
        return new BCryptPasswordEncoder();
    }
}
