package base.utils;

import base.Application;
import base.domain.BasicGraphPattern;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootTest(classes = Application.class)
class UtilsJenaTest {
    @Value("${sparqlear.sparql.candidateTriples.limit}")
    private int limit;
    @Autowired
    private UtilsJena utilsJena;

    @Test
    public void deriveTriples(){
        try {
            String example = "Rafi";
            Set<Triple> results = utilsJena.deriveTriples(example, Optional.empty(), limit, 0);
            results.forEach(System.out::println);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void getBindings() {
        BasicGraphPattern bgp = new BasicGraphPattern();
        Triple triple1 = new Triple(NodeFactory.createVariable("x1"), NodeFactory.createURI("http://dbpedia.org/property/citizenship"), NodeFactory.createVariable("citizenship"));
        bgp.setTriples(Stream.of(triple1).collect(Collectors.toSet()));
        utilsJena.getBindings(bgp);
    }
}