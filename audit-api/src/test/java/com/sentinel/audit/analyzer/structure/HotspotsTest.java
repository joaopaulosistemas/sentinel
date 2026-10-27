package com.sentinel.audit.analyzer.structure;

import com.sentinel.audit.analyzer.structure.Hotspots.Cx;
import com.sentinel.audit.analyzer.structure.Hotspots.Hotspot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HotspotsTest {

    @Test
    void leOHistoricoDoGit() {
        String log = "a".repeat(40) + "\nsrc/A.java\nsrc/B.java\n\n" + "b".repeat(40) + "\nsrc/A.java\n";
        Hotspots.Log parsed = Hotspots.parseLog(log);
        assertEquals(2, parsed.commits());
        assertEquals(2, parsed.churn().get("src/A.java"));
        assertEquals(1, parsed.churn().get("src/B.java"));
        assertEquals(0, Hotspots.parseLog(null).commits());
    }

    @Test
    void soApontaOQueMudaMuitoETemFuncaoComplexa() {
        Map<String, Integer> churn = Map.of("src/A.java", 12, "src/B.java", 3, "src/C.java", 20, "src/D.java", 8);
        Map<String, Cx> cx = Map.of("src/A.java", new Cx(30, "f", 5), "src/B.java", new Cx(40, "g", 1),
                "src/C.java", new Cx(8, "h", 1), "src/D.java", new Cx(20, "k", 9));

        List<Hotspot> ranked = Hotspots.rank(churn, cx, 5, 10, 10);

        assertEquals(List.of("src/A.java", "src/D.java"), ranked.stream().map(Hotspot::file).toList());   // 360 e 160
        assertEquals(360, ranked.get(0).score());
        assertEquals(1, Hotspots.rank(churn, cx, 5, 10, 1).size());
    }
}
