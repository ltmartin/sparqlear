package base.learners;

import base.domain.Example;
import base.domain.ExampleEntry;
import base.domain.Hyperedge;
import base.exceptions.ExampleException;
import base.services.PropertiesService;
import base.utils.DatasetsParser;
import base.utils.ExampleParser;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
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
    // Used to select from which example start the learning process.
    private int initialGroup = -1;

    public Set<String> learn(String examples) throws ParseException, ExampleException, IOException {
        Set<String> derivedQueries = new HashSet<>();
        Set<String> parsedDatasets = null;
        initialGroup++;

        logger.log(Level.INFO, "Parsing examples...");
        Set<Example> parsedExamples = exampleParser.parse(examples);
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

        for (Example c : positiveExampleGroup) {

            Set<ExampleEntry<String, Triple>> componentCandidateTriples = tripleFinder.deriveCandidateTriples(c.getExample(), Optional.empty());

            int selectedVariablesAmount = introduceVariables(componentCandidateTriples, parsedExamples);
            StringBuilder stringBuilder = new StringBuilder();

            stringBuilder.append("SELECT ");
            for (int i = 0; i < selectedVariablesAmount; i++)
                stringBuilder.append("?sv" + i + ", ");
            stringBuilder.deleteCharAt(stringBuilder.lastIndexOf(","));
            stringBuilder.append("WHERE { ");
            for (ExampleEntry<String, Triple> cct: componentCandidateTriples)
                stringBuilder.append(cct.getValue().toString() + ".");
            stringBuilder.append("}");

            String query = stringBuilder.toString();
        }

        return hyperedges;
    }

    private int introduceVariables(Set<ExampleEntry<String, Triple>> componentCandidateTriples, Set<Example> parsedExamples) {
        int svIndex = 0, nsvIndex = 0;
        Map<String, Node> variableNames = new HashMap<>();
        for (ExampleEntry<String, Triple> cct: componentCandidateTriples) {
            boolean isExampleProvidedByUser = parsedExamples.stream().filter(example -> example.getExample().equals(cct.getKey())).count() != 0;

            if (cct.getValue().getSubject().toString().equals(cct.getKey())){
                Node newSubject;
                if (!variableNames.containsKey(cct.getKey())) {
                    if (isExampleProvidedByUser)
                        newSubject = NodeFactory.createVariable("sv" + svIndex++);
                    else
                        newSubject = NodeFactory.createVariable("x" + nsvIndex++);

                    variableNames.put(cct.getKey(), newSubject);
                    cct.setValue(new Triple(newSubject, cct.getValue().getPredicate(), cct.getValue().getObject()));
                } else
                    cct.setValue(new Triple(variableNames.get(cct.getKey()), cct.getValue().getPredicate(), cct.getValue().getObject()));
            } else if (cct.getValue().getPredicate().toString().equals(cct.getKey())){
                Node newPredicate;
                if (!variableNames.containsKey(cct.getKey())) {
                    if (isExampleProvidedByUser)
                        newPredicate = NodeFactory.createVariable("sv" + svIndex++);
                    else
                        newPredicate = NodeFactory.createVariable("x" + nsvIndex++);

                    variableNames.put(cct.getKey(), newPredicate);
                    cct.setValue(new Triple(cct.getValue().getSubject(), newPredicate, cct.getValue().getObject()));
                } else
                    cct.setValue(new Triple(cct.getValue().getSubject(), variableNames.get(cct.getKey()), cct.getValue().getObject()));
            } else if (cct.getValue().getObject().toString().equals(cct.getKey())){
                Node newObject;
                if (!variableNames.containsKey(cct.getKey())) {
                    if (isExampleProvidedByUser)
                        newObject = NodeFactory.createVariable("sv" + svIndex++);
                    else
                        newObject = NodeFactory.createVariable("x" + nsvIndex++);

                    variableNames.put(cct.getKey(), newObject);
                    cct.setValue(new Triple(cct.getValue().getSubject(), cct.getValue().getPredicate(), newObject));
                } else
                    cct.setValue(new Triple(cct.getValue().getSubject(), cct.getValue().getPredicate(), variableNames.get(cct.getKey())));
            }
        }

        return svIndex;
    }

}
