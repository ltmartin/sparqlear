package base.learners;

import base.domain.Example;
import base.domain.ExampleEntry;
import base.domain.Property;
import base.services.PropertiesService;
import base.utils.DatasetsParser;
import base.utils.ExampleParser;
import org.apache.jena.graph.Triple;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Component
@Lazy
public class QueryLearner {
    private final Logger logger = Logger.getLogger(QueryLearner.class.getName());
    @Resource
    private TripleFinder tripleFinder;
    @Resource
    private ExampleParser exampleParser;
    @Resource
    private DatasetsParser datasetsParser;
    @Resource
    private PropertiesService propertiesService;
    @Value("${sparqlear.sparql.endpoint}")
    private String endpoint;
    @Value("${sparqlear.sparql.candidateTriples.limit}")
    private int limit;
    @Value("${sparqlear.sparql.datasets}")
    private String datasets;
    @Value("${sparqlear.verifyPredicatesRank}")
    private Boolean useRanking;

    public Set<String> learn(String examples) throws ParseException {
        Set<String> derivedQueries = new HashSet<>();
        Set<String> parsedDatasets = null;

        logger.log(Level.INFO, "Parsing examples...");
        Set<Example> parsedExamples = exampleParser.parse(examples);
        logger.log(Level.INFO, "Examples parsed.");

        if (!datasets.isEmpty()) {
            logger.log(Level.INFO, "Parsing datasets...");
            parsedDatasets = datasetsParser.parse(datasets);
            logger.log(Level.INFO, "Datasets parsed.");
        }

        boolean singleVariableQuery = parsedExamples.iterator().next().getGroup() == null;

        if (singleVariableQuery) {
            for (Example example : parsedExamples) {
                if (null == parsedDatasets) {
                    try {
                        logger.log(Level.INFO, "Single variable and single dataset query derivation started.");
                        Set<ExampleEntry<String, Triple>> derivedTriples = tripleFinder.deriveCandidateTriples(example.getExample(), endpoint, Optional.empty(), limit);
                        logger.log(Level.INFO, "Single variable and single dataset query derivation finished.");
                        Map<Object, List<ExampleEntry<String, Triple>>> sortedTriples = sort(derivedTriples);
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "Single variable and single dataset query derivation failed.");
                        logger.log(Level.SEVERE, e.getMessage());
                    }

                } else {

                }
            }
        } else {

        }

        return derivedQueries;
    }

    public Map<Object, List<ExampleEntry<String, Triple>>> sort(Set<ExampleEntry<String, Triple>> derivedTriples) {
        Map<Object, List<ExampleEntry<String, Triple>>> sortedTriples = new HashMap<>();

        Map<Object, List<ExampleEntry<String, Triple>>> groupedTriples = derivedTriples.stream()
                .collect(Collectors.groupingBy(entry -> entry.getKey()));

        Set<Object> keySet = groupedTriples.keySet();
        if (useRanking) {
            Hashtable<Integer, Property> rankedProperties = propertiesService.loadProperties();
            for (Object key : keySet) {
                List<ExampleEntry<String, Triple>> triplesByExample = groupedTriples.get(key);
                triplesByExample.sort((e1, e2) -> {
                    String predicate1 = e1.getValue().getPredicate().toString();
                    String predicate2 = e2.getValue().getPredicate().toString();
                    if ((null != rankedProperties.get(predicate1.hashCode())) && (null != rankedProperties.get(predicate2.hashCode())))
                        return (rankedProperties.get(predicate1.hashCode()).getWeight() > rankedProperties.get(predicate2.hashCode()).getWeight())? -1 : (rankedProperties.get(predicate1.hashCode()).getWeight() == rankedProperties.get(predicate2.hashCode()).getWeight())? 0 : 1;
                    else if ((null != rankedProperties.get(predicate1.hashCode())))
                        return (rankedProperties.get(predicate1.hashCode()).getWeight() > 0)? -1 : 1;
                    else if ((null != rankedProperties.get(predicate2.hashCode())))
                        return (rankedProperties.get(predicate2.hashCode()).getWeight() > 0)? 1 : -1;
                    else
                        return 0;
                });
                sortedTriples.put(key, triplesByExample);
            }
            return sortedTriples;
        }

        return groupedTriples;
    }
}
