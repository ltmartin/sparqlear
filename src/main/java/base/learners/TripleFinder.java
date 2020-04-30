package base.learners;

import base.domain.QueueSet;
import org.apache.jena.graph.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import base.utils.UtilsJena;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Leandro Tabares Martín
 *
 */

@Component
@Lazy
public class TripleFinder {
    @Autowired
    private UtilsJena utilsJena;

    public Set<Hashtable<String, Triple>> deriveCandidateTriples(String example, String endpoint, Optional<String> dataset, int threshold) throws IOException {
        Set<Hashtable<String, Triple>> candidateTriples = new HashSet<>();

        Set<Hashtable<String, Triple>> queue = new QueueSet<>();
        try {
            queue = (Set<Hashtable<String, Triple>>) utilsJena.deriveTriples(example, endpoint, dataset, threshold).stream()
                    .map(t -> {
                        Hashtable<String, Triple> p = new Hashtable<>();
                        p.put(example, (Triple) t);
                        return p;
                    })
                    .collect(Collectors.toCollection(QueueSet::new));

            while ((!queue.isEmpty()) && (candidateTriples.size() < threshold)){
                Hashtable<String, Triple> pair = (Hashtable<String, Triple>) ((QueueSet)queue).poll();
                candidateTriples.add(pair);

                Triple triple = pair.values().iterator().next();
                String subject = UtilsJena.getCanonicalExample(triple.getSubject().toString());
                deriveDirectTriples(example, endpoint, dataset, threshold, candidateTriples, queue, subject);

                String predicate = UtilsJena.getCanonicalExample(triple.getPredicate().toString());
                deriveDirectTriples(example, endpoint, dataset, threshold, candidateTriples, queue, predicate);

                String object = UtilsJena.getCanonicalExample(triple.getObject().toString());
                deriveDirectTriples(example, endpoint, dataset, threshold, candidateTriples, queue, object);
            }

        } catch (IOException e) {
            throw new IOException("Please check the endpoint and dataset parameters.");
        }

        return candidateTriples;
    }

    private void deriveDirectTriples(String example, String endpoint, Optional<String> dataset, int threshold, Set<Hashtable<String, Triple>> candidateTriples, Set<Hashtable<String, Triple>> queue, String item) throws IOException {
        if (!example.equals(item)){
            queue.addAll((Set<Hashtable<String, Triple>>) utilsJena.deriveTriples(item, endpoint, dataset, threshold - candidateTriples.size()).stream()
                    .map(t -> {
                        Hashtable<String, Triple> p = new Hashtable<>();
                        p.put(item, (Triple) t);
                        return p;
                    })
                    .collect(Collectors.toCollection(QueueSet::new)));
        }
    }
}
