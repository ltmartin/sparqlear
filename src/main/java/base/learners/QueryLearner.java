package base.learners;

import base.domain.BasicGraphPattern;
import base.domain.Example;
import base.domain.ExampleEntry;
import base.domain.Motif;
import base.services.MotifsService;
import base.services.PropertiesService;
import base.utils.CombinationsUtil;
import base.utils.DatasetsParser;
import base.utils.ExampleUtils;
import base.utils.UtilsJena;
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
    private ExampleUtils exampleUtils;
    @Resource
    private DatasetsParser datasetsParser;
    @Resource
    private MotifsService motifsService;
    @Resource
    private UtilsJena utilsJena;
    @Value("${sparqlear.sparql.endpoint}")
    private String endpoint;
    @Value("${sparqlear.sparql.candidateTriples.limit}")
    private int limit;
    @Value("${sparqlear.sparql.results.limit}")
    private String resultsLimit;
    @Value("${sparqlear.sparql.datasets}")
    private String datasets;
    @Value("${sparqlear.informationGain.threshold}")
    private double informationGainThreshold;
    @Value("${sparqlear.learnMultipleQueries}")
    private Boolean learnMultipleQueries;

    private int selectedVariablesAmount;
    private Map<String, List<Triple>> triplesBySelectedVariable = new HashMap<>();
    private Map<String, Node> variableNames = new HashMap<>();
    private int svIndex = 0, nsvIndex = 0;
    private Set<Example> parsedExamples;

    public Optional<Set<String>> learn(String examples) throws ParseException, IOException {
        Set<String> derivedQueries = new HashSet<>();
        Set<String> parsedDatasets = null;

        logger.log(Level.INFO, "Parsing examples...");
        parsedExamples = exampleUtils.parseExamples(examples);
        logger.log(Level.INFO, "Examples parsed.");

        if (!datasets.isEmpty()) {
            logger.log(Level.INFO, "Parsing datasets...");
            parsedDatasets = datasetsParser.parse(datasets);
            logger.log(Level.INFO, "Datasets parsed.");
        }

        // Learning the candidate triples
        Map<Boolean, List<Example>> categorizedExamples = parsedExamples.stream()
                .collect(Collectors.groupingBy(Example::getCategory));

        Set<Example> positiveExamples = new HashSet<>(categorizedExamples.get(Example.CATEGORY_POSITIVE));
        Map<Integer, List<Example>> positiveExamplesByComponent = positiveExamples.stream().collect(Collectors.groupingBy(Example::getPosition));
        Map<Example, Set<ExampleEntry<String, Triple>>> candidateTriples = deriveCandidateTriples(positiveExamplesByComponent, Optional.empty(), 0);

        // Extracting the subjects of the candidate triples
        Set<String> individuals = new HashSet<>();
        for (Map.Entry<Example, Set<ExampleEntry<String, Triple>>> entry: candidateTriples.entrySet()) {
            Set<ExampleEntry<String, Triple>> value = entry.getValue();
            for (ExampleEntry<String, Triple> exampleEntry : value) {
                individuals.add(exampleEntry.getValue().getSubject().toString());
            }
        }

        // Finding the candidate motif instances involving individuals from the candidate triples
        Set<Motif> candidateMotifInstances = new HashSet<>();
        for (String ind: individuals) {
            candidateMotifInstances.addAll(motifsService.findMotifsInvolvingIndividual(ind));
        }

        if (null == parsedDatasets) {
           // Creating the candidate triple patterns
            do {
                Set<Example> candidateTriplesKeySet = candidateTriples.keySet();
                for (Example example : candidateTriplesKeySet) {
                    selectedVariablesAmount += introduceVariables(candidateTriples.get(example), parsedExamples, triplesBySelectedVariable);
                }

            } while (selectedVariablesAmount < positiveExamplesByComponent.size());

            // FIXME: Continue here.
            BasicGraphPattern bgp = constructBasicGraphPattern(candidateTriples, categorizedExamples, positiveExamplesByComponent.size());
            if (null != bgp)
                derivedQueries.add(buildQuery(bgp));
        } else {
            // TODO: Create the flow for learning from multiple datasets.
        }
        return Optional.of(derivedQueries);
    }

    private String buildQuery(BasicGraphPattern bgp) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("SELECT DISTINCT ");

        Set<String> selectedVariables = new HashSet<>();
        for (Triple triple : bgp.getTriples()) {
            if (triple.getSubject().toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN))
                selectedVariables.add(triple.getSubject().toString());
            if (triple.getObject().toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN))
                selectedVariables.add(triple.getObject().toString());
        }
        List<String> selectedVariablesSorted = new ArrayList<>(selectedVariables);
        Collections.sort(selectedVariablesSorted);
        for (String sv : selectedVariablesSorted) {
            stringBuilder.append(sv).append(" ");
        }

        stringBuilder.append("WHERE { ");
        for (Triple triple : bgp.getTriples()) {
            stringBuilder.append(utilsJena.getSparqlCompatibleTriple(triple)).append(" . ");
        }
        stringBuilder.append("}");

        return stringBuilder.toString();
    }

    private BasicGraphPattern constructBasicGraphPattern(Map<Example, Set<ExampleEntry<String, Triple>>> candidateTriples, Map<Boolean, List<Example>> categorizedExamples, int numberOfSelectedVariables) {
        List<ExampleEntry<String, Triple>> allCommonTriples = new LinkedList<>();

        for (Map.Entry<Example, Set<ExampleEntry<String, Triple>>> entry : candidateTriples.entrySet()) {
            allCommonTriples.addAll(entry.getValue());
        }

        allCommonTriples = allCommonTriples.stream()
                .sorted((e1, e2) -> {
                    if (e1.getValue().toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN) && !e2.getValue().toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN))
                        return -1;
                    else if (!e1.getValue().toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN) && e2.getValue().toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN))
                        return 1;
                    else
                        return 0;
                })
                .collect(Collectors.toList());

        List<List<String>> combinationIndexes = CombinationsUtil.generateCombinations(allCommonTriples.size());
        for (List<String> rowCombinationIndexes : combinationIndexes) {
            for (String combination : rowCombinationIndexes) {
                String[] stringIndexes = combination.split(" ");
                // this is to avoid testing combinations that have a low possibility of being successful
                if (stringIndexes.length < numberOfSelectedVariables)
                    continue;

                Set<Node> selectedVariablesIncluded = new HashSet<>();

                List<Integer> indexes = new ArrayList<>();
                for (String stringIndex : stringIndexes) {
                    indexes.add(Integer.valueOf(stringIndex));
                }
                BasicGraphPattern bgp = new BasicGraphPattern();
                for (Integer index : indexes) {
                    Triple triple = allCommonTriples.get(index).getValue();

                    if (triple.getSubject().toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN))
                        selectedVariablesIncluded.add(triple.getSubject());
                    if (triple.getObject().toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN))
                        selectedVariablesIncluded.add(triple.getObject());

                    bgp.getTriples().add(triple);
                }

                if (selectedVariablesIncluded.size() < numberOfSelectedVariables)
                    continue;

                Map<Boolean, Integer> results = utilsJena.verifyBasicGraphPattern(bgp.getTriples(), categorizedExamples);
                if (0 == results.get(Example.CATEGORY_NEGATIVE)) {
                    bgp.setInformation(computeInformation(results.get(Example.CATEGORY_POSITIVE)));
                    if (bgp.getInformation() >= informationGainThreshold)
                        return bgp;
                }
            }
        }
        return null;
    }

    private Map<Example, Set<ExampleEntry<String, Triple>>> deriveCandidateTriples(Map<Integer, List<Example>> positiveExamplesByComponent, Optional<String> dataset, int offset) throws IOException {
        Set<Integer> componentKeys = positiveExamplesByComponent.keySet();

        Map<Example, Set<ExampleEntry<String, Triple>>> candidateTriples = new HashMap<>();
        logger.log(Level.INFO, "Starting to derive candidate triples...");
        for (Integer componentKey : componentKeys) {
            List<Example> componentExamples = positiveExamplesByComponent.get(componentKey);
            for (Example componentExample : componentExamples) {
                candidateTriples.put(componentExample, tripleFinder.deriveCandidateTriples(componentExample.getExample(), dataset, offset));
                // this is because there might be examples that are not present on the dataset, so we can't learn anything from them.
                if (candidateTriples.get(componentExample).isEmpty())
                    return null;
            }
        }
        logger.log(Level.INFO, "Candidate triples successfully derived.");

        return candidateTriples;
    }

    private int introduceVariables(Set<ExampleEntry<String, Triple>> componentCandidateTriples, Set<Example> parsedExamples, Map<String, List<Triple>> triplesBySelectedVariable) {
        for (ExampleEntry<String, Triple> cct : componentCandidateTriples) {
            boolean isExampleProvidedByUser = parsedExamples.stream().anyMatch(example -> example.getExample().equals(cct.getKey()));

            // If the subject is an example
            if (UtilsJena.getCanonicalExample(cct.getValue().getSubject().toString()).equals(cct.getKey())) {
                Node newSubject, newObject;
                if (!variableNames.containsKey(cct.getKey())) {
                    if (isExampleProvidedByUser)
                        newSubject = NodeFactory.createVariable(UtilsJena.SELECTED_VARIABLE_PATTERN + svIndex++);
                    else
                        newSubject = NodeFactory.createVariable("x" + nsvIndex++);

                    newObject = NodeFactory.createVariable("x" + nsvIndex++);

                    variableNames.put(cct.getKey(), newSubject);
                    variableNames.put(cct.getValue().getObject().toString(), newObject);

                    cct.setValue(new Triple(newSubject, cct.getValue().getPredicate(), newObject));

                    if (newSubject.toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN)) {
                        if (!triplesBySelectedVariable.containsKey(newSubject.toString())) {
                            List<Triple> tripleList = new LinkedList<>();
                            tripleList.add(cct.getValue());
                            triplesBySelectedVariable.put(newSubject.toString(), tripleList);
                        } else {
                            List<Triple> tripleList = triplesBySelectedVariable.get(newSubject.toString());
                            tripleList.add(cct.getValue());
                            triplesBySelectedVariable.replace(newSubject.toString(), tripleList);
                        }
                    }
                } else {
                    if (!variableNames.containsKey(cct.getValue().getObject().toString())) {
                        newObject = NodeFactory.createVariable("x" + nsvIndex++);
                        variableNames.put(cct.getValue().getObject().toString(), newObject);
                    }
                    cct.setValue(new Triple(variableNames.get(cct.getKey()), cct.getValue().getPredicate(), variableNames.get(cct.getValue().getObject().toString())));

                    if (cct.getValue().getSubject().toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN)) {
                        List<Triple> tripleList = triplesBySelectedVariable.get(cct.getValue().getSubject().toString());
                        tripleList.add(cct.getValue());
                        triplesBySelectedVariable.replace(cct.getValue().getSubject().toString(), tripleList);
                    }
                }
            }
            // If the predicate is an example
            else if (UtilsJena.getCanonicalExample(cct.getValue().getPredicate().toString()).equals(cct.getKey())) {
                Node newPredicate;
                if (!variableNames.containsKey(cct.getKey())) {
                    if (isExampleProvidedByUser)
                        newPredicate = NodeFactory.createVariable(UtilsJena.SELECTED_VARIABLE_PATTERN + svIndex++);
                    else
                        newPredicate = NodeFactory.createVariable("x" + nsvIndex++);

                    variableNames.put(cct.getKey(), newPredicate);

                    cct.setValue(new Triple(cct.getValue().getSubject(), newPredicate, cct.getValue().getObject()));

                    if (newPredicate.toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN)) {
                        if (!triplesBySelectedVariable.containsKey(newPredicate.toString())) {
                            List<Triple> tripleList = new LinkedList<>();
                            tripleList.add(cct.getValue());
                            triplesBySelectedVariable.put(newPredicate.toString(), tripleList);
                        } else {
                            List<Triple> tripleList = triplesBySelectedVariable.get(newPredicate.toString());
                            tripleList.add(cct.getValue());
                            triplesBySelectedVariable.replace(newPredicate.toString(), tripleList);
                        }
                    }
                } else {
                    cct.setValue(new Triple(cct.getValue().getSubject(), variableNames.get(cct.getKey()), cct.getValue().getObject()));

                    if (cct.getValue().getPredicate().toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN)) {
                        List<Triple> tripleList = triplesBySelectedVariable.get(cct.getValue().getPredicate().toString());
                        tripleList.add(cct.getValue());
                        triplesBySelectedVariable.replace(cct.getValue().getPredicate().toString(), tripleList);
                    }
                }
            }
            // If the object is an example.
            else if (UtilsJena.getCanonicalExample(cct.getValue().getObject().toString()).equals(cct.getKey())) {
                Node newSubject, newObject;
                if (!variableNames.containsKey(cct.getKey())) {
                    if (isExampleProvidedByUser)
                        newObject = NodeFactory.createVariable(UtilsJena.SELECTED_VARIABLE_PATTERN + svIndex++);
                    else
                        newObject = NodeFactory.createVariable("x" + nsvIndex++);

                    newSubject = NodeFactory.createVariable("x" + nsvIndex++);

                    variableNames.put(cct.getKey(), newObject);
                    variableNames.put(cct.getValue().getSubject().toString(), newSubject);

                    cct.setValue(new Triple(newSubject, cct.getValue().getPredicate(), newObject));

                    if (newObject.toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN)) {
                        if (!triplesBySelectedVariable.containsKey(newObject.toString())) {
                            List<Triple> tripleList = new LinkedList<>();
                            tripleList.add(cct.getValue());
                            triplesBySelectedVariable.put(newObject.toString(), tripleList);
                        } else {
                            List<Triple> tripleList = triplesBySelectedVariable.get(newObject.toString());
                            tripleList.add(cct.getValue());
                            triplesBySelectedVariable.replace(newObject.toString(), tripleList);
                        }
                    }
                } else {
                    if (!variableNames.containsKey(cct.getValue().getSubject().toString())) {
                        newSubject = NodeFactory.createVariable("x" + nsvIndex++);
                        variableNames.put(cct.getValue().getSubject().toString(), newSubject);
                    }
                    cct.setValue(new Triple(variableNames.get(cct.getValue().getSubject().toString()), cct.getValue().getPredicate(), variableNames.get(cct.getKey())));

                    if (cct.getValue().getObject().toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN)) {
                        List<Triple> tripleList = triplesBySelectedVariable.get(cct.getValue().getObject().toString());
                        tripleList.add(cct.getValue());
                        triplesBySelectedVariable.replace(cct.getValue().getObject().toString(), tripleList);
                    }
                }
            }
        }
        return svIndex;
    }

    private double computeInformation(Integer positiveExamplesCovered) {
        return -(Math.log(((double) positiveExamplesCovered) / parsedExamples.size()) / Math.log(2));
    }

}
