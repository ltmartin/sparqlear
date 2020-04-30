package base.utils;

import base.Application;
import org.apache.jena.graph.Triple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@SpringBootTest(classes = Application.class)
class UtilsJenaTest {
    @Value("${sparqlear.sparql.endpoint}")
    private String endpoint;
    @Value("${sparqlear.test.example}")
    private String example;
    @Value("${sparqlear.sparql.candidateTriples.limit}")
    private int limit;
    @Autowired
    private UtilsJena utilsJena;

    @Test
    public void deriveTriples(){
        try {
            Set<Triple> results = utilsJena.deriveTriples(example, endpoint, Optional.empty(), limit);
            results.forEach((Triple t) -> System.out.println(t));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}