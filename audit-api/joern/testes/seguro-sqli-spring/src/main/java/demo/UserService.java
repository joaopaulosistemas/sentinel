package demo;

import java.util.List;

public class UserService {
    private final UserRepository repo;
    public UserService(UserRepository repo) { this.repo = repo; }

    public List<String> search(String name) {
        return repo.byName(name.trim());
    }
}
