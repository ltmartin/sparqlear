package base.learners;

import base.domain.Property;
import base.domain.QueueSet;
import base.repository.PropertyRepository;
import base.utils.UtilsJena;
import org.apache.jena.graph.Triple;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Leandro Tabares Martín
 *
 */

@Component
@Lazy
public class TripleFinder {
    @Value("${sparqlear.verifyPredicatesRank}")
    private Boolean verifyPredicatesRank;
    @Value("${sparqlear.propertyWeight.threshold}")
    private float weightThreshold;
    @Resource
    private UtilsJena utilsJena;
    @Resource
    private PropertyRepository propertyRepository;

    public Set<Hashtable<String, Triple>> deriveCandidateTriples(String example, String endpoint, Optional<String> dataset, int threshold) throws IOException {
        Set<Hashtable<String, Triple>> candidateTriples = new HashSet<>();

        Set<Hashtable<String, Triple>> queue;
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
                checkPredicatesRank(example, endpoint, dataset, threshold, candidateTriples, queue, subject);

                String predicate = UtilsJena.getCanonicalExample(triple.getPredicate().toString());
                checkPredicatesRank(example, endpoint, dataset, threshold, candidateTriples, queue, predicate);

                String object = UtilsJena.getCanonicalExample(triple.getObject().toString());
                checkPredicatesRank(example, endpoint, dataset, threshold, candidateTriples, queue, object);
            }

        } catch (IOException e) {
            throw new IOException("Please check the endpoint and dataset parameters.");
        }

        return candidateTriples;
    }

    private void checkPredicatesRank(String example, String endpoint, Optional<String> dataset, int threshold, Set<Hashtable<String, Triple>> candidateTriples, Set<Hashtable<String, Triple>> queue, String object) throws IOException {
        if (verifyPredicatesRank){
            Property property = propertyRepository.findPropertyByLabel(object);
            if ((null != property) && (property.getWeight() >= weightThreshold))
                deriveDirectTriples(example, endpoint, dataset, threshold, candidateTriples, queue, object);
            else if (null == property)
                deriveDirectTriples(example, endpoint, dataset, threshold, candidateTriples, queue, object);
        } else
            deriveDirectTriples(example, endpoint, dataset, threshold, candidateTriples, queue, object);
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
