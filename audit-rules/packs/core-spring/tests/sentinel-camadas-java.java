// ruleid: sentinel-camadas-controller-usa-repository
@RestController
class ContaController {
    private final ContaRepository repo;
}

// ok: sentinel-camadas-controller-usa-repository
@RestController
class OkController {
    private final ContaService service;
}

// ruleid: sentinel-camadas-controller-expoe-entidade
@RestController
class EntController {
    public ContaEntity buscar(Long id) { return null; }
}

// ok: sentinel-camadas-controller-expoe-entidade
@RestController
class DtoController {
    public ContaDto buscar(Long id) { return null; }
}
