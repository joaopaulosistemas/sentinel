import java.io.File;
import java.security.KeyPairGenerator;
import javax.script.ScriptEngine;
import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

class HardeningJava {

    // ---- XXE em outros parsers --------------------------------------------------------------
    XMLReader leitor() throws Exception {
        // ruleid: sentinel-xxe-outros-parsers
        XMLReader r = XMLReaderFactory.createXMLReader();
        return r;
    }

    SchemaFactory esquema() throws Exception {
        // ruleid: sentinel-xxe-outros-parsers
        SchemaFactory f = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        return f;
    }

    XMLReader leitorSeguro() throws Exception {
        // ok: sentinel-xxe-outros-parsers
        XMLReader r = XMLReaderFactory.createXMLReader();
        r.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return r;
    }

    SchemaFactory esquemaSeguro() throws Exception {
        // ok: sentinel-xxe-outros-parsers
        SchemaFactory f = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        f.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        return f;
    }

    // ---- SnakeYAML ---------------------------------------------------------------------------
    Object yaml(String texto) {
        // ruleid: sentinel-snakeyaml-construtor-padrao
        Yaml y = new Yaml();
        return y.load(texto);
    }

    Object yamlSeguro(String texto) {
        // ok: sentinel-snakeyaml-construtor-padrao
        Yaml y = new Yaml(new SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()));
        return y.load(texto);
    }

    // ---- ScriptEngine ------------------------------------------------------------------------
    Object avalia(ScriptEngine engine, String expressao) throws Exception {
        // ruleid: sentinel-script-engine-eval-dinamico
        return engine.eval(expressao);
    }

    Object avaliaFixo(ScriptEngine engine) throws Exception {
        // ok: sentinel-script-engine-eval-dinamico
        return engine.eval("1 + 1");
    }

    // ---- Arquivo temporário -----------------------------------------------------------------
    File temporario() throws Exception {
        // ruleid: sentinel-arquivo-temporario-inseguro
        return File.createTempFile("relatorio", ".tmp");
    }

    java.nio.file.Path temporarioSeguro() throws Exception {
        // ok: sentinel-arquivo-temporario-inseguro
        return java.nio.file.Files.createTempFile("relatorio", ".tmp");
    }

    // ---- Fixação de sessão --------------------------------------------------------------------
    void sessao(HttpSecurity http) throws Exception {
        // ruleid: sentinel-session-fixation-desligada
        http.sessionManagement(s -> s.sessionFixation().none());
    }

    void sessaoSegura(HttpSecurity http) throws Exception {
        // ok: sentinel-session-fixation-desligada
        http.sessionManagement(s -> s.sessionFixation().migrateSession());
    }

    // ---- Tamanho de chave --------------------------------------------------------------------
    void chaves() throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        // ruleid: sentinel-chave-assimetrica-curta
        rsa.initialize(1024);
        // ok: sentinel-chave-assimetrica-curta
        KeyPairGenerator rsaForte = KeyPairGenerator.getInstance("RSA");
        rsaForte.initialize(3072);
        // ok: sentinel-chave-assimetrica-curta
        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(256);
    }
}
