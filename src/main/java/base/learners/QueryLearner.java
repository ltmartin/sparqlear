package base.learners;

import base.domain.Example;
import base.domain.ExampleEntry;
import base.domain.Hyperedge;
import base.exceptions.ExampleException;
import base.services.PropertiesService;
import base.utils.DatasetsParser;
import base.utils.ExampleUtils;
import base.utils.UtilsJena;
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
    private ExampleUtils exampleUtils;
    @Resource
    private DatasetsParser datasetsParser;
    @Resource
    private PropertiesService propertiesService;
    @Resource
    private UtilsJena utilsJena;
    @Value("${sparqlear.sparql.endpoint}")
    private String endpoint;
    @Value("${sparqlear.sparql.candidateTriples.limit}")
    private int limit;
    @Value("${sparqlear.sparql.datasets}")
    private String datasets;
    @Value("${sparqlear.verifyPredicatesRank}")
    private Boolean useRanking;
    // Used to select from which example start the learning process.
    private int initialGroup = -1;

    public Set<String> learn(String examples) throws ParseException, ExampleException, IOException {
        Set<String> derivedQueries = new HashSet<>();
        Set<String> parsedDatasets = null;
        initialGroup++;

        logger.log(Level.INFO, "Parsing examples...");
        Set<Example> parsedExamples = exampleUtils.parseExamples(examples);
        logger.log(Level.INFO, "Examples parsed.");

        int examplesGroupsQuantity = parsedExamples.stream().collect(Collectors.groupingBy(Example::getGroup)).size();
        if (initialGroup >= examplesGroupsQuantity)
            throw new ExampleException("Not enough examples to learn.");

        if (!datasets.isEmpty()) {
            logger.log(Level.INFO, "Parsing datasets...");
            parsedDatasets = datasetsParser.parse(datasets);
            logger.log(Level.INFO, "Datasets parsed.");
        }

        if (null == parsedDatasets) {
            boolean multipleVariableExamples = null != parsedExamples.iterator().next().getGroup();

            Set<Example> positiveExampleGroup = new HashSet<>();
            if (!multipleVariableExamples)
                positiveExampleGroup = Set.of(parsedExamples.iterator().next());
            else {
                positiveExampleGroup.addAll(parsedExamples.stream()
                        .filter(example -> example.getCategory().equals(Example.CATEGORY_POSITIVE))
                        .takeWhile(example -> example.getGroup().equals(initialGroup))
                        .collect(Collectors.toSet()));
            }

            Set<Hyperedge> hyperedges = constructHyperedges(positiveExampleGroup, parsedExamples);

        } else {
            // TODO: Create the flow for learning from multiple datasets.
        }


        return derivedQueries;
    }

    private Set<Hyperedge> constructHyperedges(Set<Example> positiveExampleGroup, Set<Example> parsedExamples) throws IOException {
        Set<Hyperedge> hyperedges = new HashSet<>();
        Map<Boolean, List<Example>> categorizedExamples = parsedExamples.stream().collect(Collectors.groupingBy(Example::getCategory));

        for (Example c : positiveExampleGroup) {

            Set<ExampleEntry<String, Triple>> componentCandidateTriples = tripleFinder.deriveCandidateTriples(c.getExample(), Optional.empty());
            Set<List<String>> completeQueryValuation = utilsJena.runCompleteQueryForHyperedges(componentCandidateTriples, parsedExamples);

            double totalInformationGain = computeInformationGain(completeQueryValuation, categorizedExamples);
        }

        return hyperedges;
    }

    private double computeInformationGain(Set<List<String>> completeQueryValuation, Map<Boolean, List<Example>> examples) {
        Map<Integer, List<Example>> positiveExamplesByGroup = examples.get(Example.CATEGORY_POSITIVE).stream().collect(Collectors.groupingBy(Example::getGroup));
        Map<Integer, List<Example>> negativeExamplesByGroup = examples.get(Example.CATEGORY_NEGATIVE).stream().collect(Collectors.groupingBy(Example::getGroup));

        int positiveExamplesFoundCounter = 0;
        int negativeExamplesFoundCounter = 0;
        for (List<String> row: completeQueryValuation) {
            boolean found = false;
            for (int i = 0; i < positiveExamplesByGroup.size() && !found; i++){
                List<Example> ithGroup = positiveExamplesByGroup.get(i);
                List<String> ithGroupAsList = exampleUtils.getExamplesAsListOfString(ithGroup);
                found = (row.size() == ithGroupAsList.size()) && ((!ithGroupAsList.containsAll(row)) ? false : row.containsAll(ithGroupAsList));
                if (found)
                    positiveExamplesFoundCounter++;
            }
            for (int i = 0; i < negativeExamplesByGroup.size() && !found; i++){
                List<Example> ithGroup = negativeExamplesByGroup.get(i);
                List<String> ithGroupAsList = exampleUtils.getExamplesAsListOfString(ithGroup);
                found = (row.size() == ithGroupAsList.size()) && ((!ithGroupAsList.containsAll(row)) ? false : row.containsAll(ithGroupAsList));
                if (found)
                    negativeExamplesFoundCounter++;
            }
        }

        return Math.log(((double)positiveExamplesFoundCounter) / (positiveExamplesFoundCounter + negativeExamplesFoundCounter));
    }


}
