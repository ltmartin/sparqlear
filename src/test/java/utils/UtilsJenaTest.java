package utils;

import config.Application;
import org.apache.jena.graph.Triple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@SpringBootTest(classes = Application.class)
class UtilsJenaTest {
    @Autowired
    private UtilsJena utilsJena;
    private static final String endpoint = "https://query.wikidata.org/sparql";

    @Test
    public void deriveTriples(){
        final String example = "Belgium";
        final int threshold = 10000;

        try {
            Set<Triple> results = utilsJena.deriveTriples(example, endpoint, Optional.empty(), threshold);
            results.forEach((Triple t) -> System.out.println(t));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}