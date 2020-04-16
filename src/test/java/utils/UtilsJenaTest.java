package utils;

import config.Application;
import config.Parameters;
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

    @Test
    public void deriveTriples(){
        try {
            Set<Triple> results = utilsJena.deriveTriples(Parameters.example, Parameters.endpoint, Optional.empty(), Parameters.threshold);
            results.forEach((Triple t) -> System.out.println(t));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}