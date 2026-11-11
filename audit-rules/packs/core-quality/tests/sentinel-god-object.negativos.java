import jakarta.inject.Inject;

// ok: sentinel-too-many-injections
class ServicoEnxuto {
    @Inject RepositorioA a;
    @Inject RepositorioB b;
    @Inject RepositorioC c;
}
