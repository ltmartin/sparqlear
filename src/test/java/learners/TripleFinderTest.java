package learners;

import config.Application;
import config.Parameters;
import org.apache.jena.graph.Triple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Optional;
import java.util.Set;

@SpringBootTest(classes = Application.class)
class TripleFinderTest {
    @Autowired
    private TripleFinder tripleFinder;

    @Test
    public void deriveCandidateTriplesTest(){
        try {
            long initTime = System.nanoTime();
            Set<Hashtable<String, Triple>> candidateTriples =  tripleFinder.deriveCandidateTriples(Parameters.example, Parameters.endpoint, Optional.empty(), Parameters.threshold);
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