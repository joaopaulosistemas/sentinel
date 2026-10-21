class P {
    void a(java.util.List<Long> ids) {
        // ruleid: sentinel-perf-consulta-em-laco
        for (Long id : ids) {
            repo.findById(id);
        }
    }

    void b(java.util.List<Long> ids) {
        // ruleid: sentinel-perf-consulta-em-laco
        ids.forEach(id -> userRepository.findById(id));
    }

    void c(java.util.List<Long> ids) {
        // ok: sentinel-perf-consulta-em-laco
        repo.findAllById(ids);
        // ok: sentinel-perf-consulta-em-laco
        for (Long id : ids) {
            total += id;
        }
    }
}
