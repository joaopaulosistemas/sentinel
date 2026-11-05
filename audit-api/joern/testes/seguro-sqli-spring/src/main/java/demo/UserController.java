package demo;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service = service; }

    @GetMapping("/users")
    public List<String> find(@RequestParam String name) {
        return service.search(name);
    }
}
