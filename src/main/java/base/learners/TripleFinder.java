package base.learners;

import base.domain.ExampleEntry;
import base.utils.UtilsJena;
import org.apache.jena.graph.Triple;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Leandro Tabares Martín
 */

@Component
@Lazy
public class TripleFinder {
    private final Logger logger = Logger.getLogger(TripleFinder.class.getName());

    @Value("${sparqlear.sparql.candidateTriples.limit}")
    private int limit;
    @Resource
    private UtilsJena utilsJena;


    public Set<ExampleEntry<String, Triple>> deriveCandidateTriples(String example, Optional<String> dataset, int offset) throws IOException {
        Set<ExampleEntry<String, Triple>> candidateTriples;
        try {
            candidateTriples = (Set<ExampleEntry<String, Triple>>) utilsJena.deriveTriples(example, dataset, limit, offset).stream()
                    .map(t -> new ExampleEntry<>(example, (Triple) t))
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (IOException e) {
            e.printStackTrace();
            throw new IOException("Please check the endpoint and dataset parameters.");
        }

        return candidateTriples;
    }

}
