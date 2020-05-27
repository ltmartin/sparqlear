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
import java.util.Optional;
import java.util.Set;

@SpringBootTest(classes = Application.class)
class TripleFinderTest {
    @Value("${sparqlear.test.example}")
    private String example;
    @Autowired
    private TripleFinder tripleFinder;

    @Test
    public void deriveCandidateTriplesTest(){
        try {
            long initTime = System.nanoTime();
            Set<ExampleEntry<String, Triple>> candidateTriples = tripleFinder.deriveCandidateTriples(example, Optional.empty(), 0);
            long endTime = System.nanoTime();

            long elapsedTimeMinutes = ((endTime - initTime)/1000000)/60000;

            FileWriter writer = new FileWriter("output.txt");

            candidateTriples.forEach((e) -> {
                try {
                    writer.write(e.toString() + "\n");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
            writer.write("=======================================================================\n");
            writer.write("Total derivation time: " + elapsedTimeMinutes + " minutes.\n");
            writer.write("Derived Triples: " + candidateTriples.size() + "\n");
            writer.write("=======================================================================\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}