package demo;

public class PingService {
    public String run(String count) throws Exception {
        int n = Integer.parseInt(count);
        Process p = Runtime.getRuntime().exec("ping -c " + n + " 127.0.0.1");
        return new String(p.getInputStream().readAllBytes());
    }
}
