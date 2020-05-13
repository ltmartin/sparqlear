package base.learners;

import base.domain.ExampleEntry;
import base.domain.Property;
import base.domain.QueueSet;
import base.services.PropertiesService;
import base.utils.UtilsJena;
import org.apache.jena.graph.Triple;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Leandro Tabares Martín
 *
 */

@Component
@Lazy
public class TripleFinder {
    private final Logger logger = Logger.getLogger(TripleFinder.class.getName());
    @Value("${sparqlear.verifyPredicatesRank}")
    private Boolean verifyPredicatesRank;
    @Value("${sparqlear.propertyWeight.threshold}")
    private float weightThreshold;
    @Value("${sparqlear.sparql.candidateTriples.limit}")
    private int limit;
    @Value("${sparqlear.sparql.endpoint}")
    private String endpoint;
    @Resource
    private UtilsJena utilsJena;
    @Resource
    private PropertiesService propertiesService;

    private Hashtable<Integer, Property> rankedProperties;

    public Set<ExampleEntry<String, Triple>> deriveCandidateTriples(String example, Optional<String> dataset) throws IOException {
        if (verifyPredicatesRank)
            rankedProperties = propertiesService.loadProperties();

        logger.log(Level.INFO, "Starting to derive candidate triples...");
        Set<ExampleEntry<String, Triple>> candidateTriples = new QueueSet<>();
        Set<ExampleEntry<String, Triple>> queue;
        try {
            queue = (Set<ExampleEntry<String, Triple>>) utilsJena.deriveTriples(example, endpoint, dataset, limit).stream()
                    .map(t -> {
                        return new ExampleEntry<String, Triple>(example, (Triple) t);
                    })
                    .collect(Collectors.toCollection(QueueSet::new));

            while ((!queue.isEmpty()) && (candidateTriples.size() < limit)){
                ExampleEntry<String, Triple> pair = (ExampleEntry<String, Triple>) ((QueueSet)queue).poll();
                candidateTriples.add(pair);

                Triple triple = pair.getValue();
                String subject = UtilsJena.getCanonicalExample(triple.getSubject().toString());
                checkPredicatesRank(example, endpoint, dataset, limit, candidateTriples, queue, subject);

                String predicate = UtilsJena.getCanonicalExample(triple.getPredicate().toString());
                checkPredicatesRank(example, endpoint, dataset, limit, candidateTriples, queue, predicate);

                String object = UtilsJena.getCanonicalExample(triple.getObject().toString());
                checkPredicatesRank(example, endpoint, dataset, limit, candidateTriples, queue, object);
            }

        } catch (IOException e) {
            throw new IOException("Please check the endpoint and dataset parameters.");
        }
        logger.log(Level.INFO, "Candidate triples successfully derived.");
        return candidateTriples;
    }


    private void checkPredicatesRank(String example, String endpoint, Optional<String> dataset, int limit, Set<ExampleEntry<String, Triple>> candidateTriples, Set<ExampleEntry<String, Triple>> queue, String label) throws IOException {
        if (verifyPredicatesRank){
            Property property = rankedProperties.get(label.hashCode());
            if ((null != property) && (property.getWeight() >= weightThreshold))
                derive(example, endpoint, dataset, limit, candidateTriples, queue, label);
            else if (null == property)
                derive(example, endpoint, dataset, limit, candidateTriples, queue, label);
        } else
            derive(example, endpoint, dataset, limit, candidateTriples, queue, label);
    }

    private void derive(String example, String endpoint, Optional<String> dataset, int limit, Set<ExampleEntry<String, Triple>> candidateTriples, Set<ExampleEntry<String, Triple>> queue, String item) throws IOException {
        if (!example.equals(item)){
            queue.addAll((Set<ExampleEntry<String, Triple>>) utilsJena.deriveTriples(item, endpoint, dataset, limit - candidateTriples.size()).stream()
                    .map(t -> {
                        return new ExampleEntry<String, Triple>(item, (Triple) t);
                    })
                    .collect(Collectors.toCollection(QueueSet::new)));
        }
    }
}
