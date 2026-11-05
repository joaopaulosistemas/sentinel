package demo;

public class PingService {
    public String run(String host) throws Exception {
        Process p = Runtime.getRuntime().exec("ping -c 1 " + host);
        return new String(p.getInputStream().readAllBytes());
    }
}
