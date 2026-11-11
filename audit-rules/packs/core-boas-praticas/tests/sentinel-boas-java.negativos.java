import org.springframework.boot.context.properties.ConfigurationProperties;

// ok: sentinel-boas-spring-value-espalhado
@ConfigurationProperties(prefix = "app.mail")
class MailProperties {
    private String host;

    String getHost() {
        return host;
    }
}
