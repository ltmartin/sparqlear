import org.apache.jena.graph.Triple;
import org.junit.Before;
import org.junit.Test;
import utils.UtilsJena;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

public class UtilsJenaTests {
    private UtilsJena instance;
    private static final String endpoint = "https://query.wikidata.org/sparql";

    @Before
    public void setup(){
        instance = new UtilsJena();
    }

    @Test
    public void deriveTriplesTest(){
        final String example = "Belgium";
        final int threshold = 10000;

        try {
            Set<Triple> results = instance.deriveTriples(example, endpoint, Optional.empty(), threshold);
            results.forEach((Triple t) -> System.out.println(t));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
