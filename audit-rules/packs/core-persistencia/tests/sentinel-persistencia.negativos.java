import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ListaPaginada {
    Pageable pagina() {
        // ok: sentinel-jpa-pageable-unpaged
        return PageRequest.of(0, 20);
    }
}
