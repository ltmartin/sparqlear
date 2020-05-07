package base.learners;

import base.Application;
import base.domain.ExampleEntry;
import org.apache.jena.graph.Triple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@SpringBootTest(classes = Application.class)
class QueryLearnerTest {
    @Value("${sparqlear.sparql.endpoint}")
    private String endpoint;
    @Value("${sparqlear.test.example}")
    private String example;
    @Value("${sparqlear.sparql.candidateTriples.limit}")
    private int limit;
    @Autowired
    private TripleFinder tripleFinder;
    @Autowired
    private QueryLearner queryLearner;

    @Test
    void sort() {
        try {
            Set<ExampleEntry<String, Triple>> candidateTriples = tripleFinder.deriveCandidateTriples(example, endpoint, Optional.empty(), limit);
            Map<Object, List<ExampleEntry<String, Triple>>> sortedTriples = queryLearner.sort(candidateTriples);
            FileWriter writer = new FileWriter("output.txt");
            sortedTriples.forEach((k, v) -> {
                try {
                    writer.write(k + "=" + v + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}