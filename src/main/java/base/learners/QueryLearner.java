package base.learners;

import base.domain.BasicGraphPattern;
import base.domain.Example;
import base.domain.ExampleEntry;
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
    private PropertiesService propertiesService;
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
    @Value("${sparqlear.verifyPredicatesRank}")
    private Boolean useRanking;
    @Value("${sparqlear.informationGain.threshold}")
    private double informationGainThreshold;
    @Value("${sparqlear.learnMultipleQueries}")
    private Boolean learnMultipleQueries;

    private Map<String, List<Triple>> triplesBySelectedVariable = new HashMap<>();
    private Map<String, Node> variableNames = new HashMap<>();
    private int distinguishedVariablesCount = 0, nsvIndex = 0;
    private Set<Example> parsedExamples;
    private Set<Example> positiveExamples;
    private Map<Integer, List<Example>> positiveExamplesByComponent;

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

        Map<Boolean, List<Example>> categorizedExamples = parsedExamples.stream()
                .collect(Collectors.groupingBy(Example::getCategory));

        positiveExamples = new HashSet<>(categorizedExamples.get(Example.CATEGORY_POSITIVE));
        positiveExamplesByComponent = positiveExamples.stream().collect(Collectors.groupingBy(Example::getPosition));
        Map<Example, Set<ExampleEntry<String, Triple>>> candidateTriples = deriveCandidateTriples(positiveExamplesByComponent, Optional.empty(), 0);

        if (null == parsedDatasets) {
            int i = 1;
            boolean[] distinguishedVariableInComponent = new boolean[positiveExamplesByComponent.size()];
            Arrays.fill(distinguishedVariableInComponent, false);

            do {
                Set<Example> candidateTriplesKeySet = candidateTriples.keySet();
                for (Example example : candidateTriplesKeySet) {
                    introduceVariables(candidateTriples.get(example), parsedExamples, triplesBySelectedVariable, example, distinguishedVariableInComponent);
                }

                for (int j = 0; j < distinguishedVariableInComponent.length; j++)
                    if (distinguishedVariableInComponent[j])
                        distinguishedVariablesCount++;

                if (distinguishedVariablesCount < positiveExamplesByComponent.size()) {
                    Map<Example, Set<ExampleEntry<String, Triple>>> moreCandidateTriples = deriveCandidateTriples(positiveExamplesByComponent, Optional.empty(), i * limit);

                    if (null == moreCandidateTriples || moreCandidateTriples.isEmpty())
                        return Optional.empty();

                    assert candidateTriples != null;
                    candidateTriples.putAll(moreCandidateTriples);
                    i++;
                }
            } while (distinguishedVariablesCount < positiveExamplesByComponent.size());

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
        BasicGraphPattern bestBgp = new BasicGraphPattern();
        String bestIndexes = "";

        Set<Integer> componentsKeySet = positiveExamplesByComponent.keySet();

        List<ExampleEntry<String, Triple>> allTriplesInComponent = new ArrayList<>();
        for (Integer component : componentsKeySet) {
            List<Example> examplesInComponent = positiveExamplesByComponent.get(component);
            for (Example example : examplesInComponent) {
                allTriplesInComponent.addAll(candidateTriples.get(example));
            }

            List<List<String>> combinationIndexes = CombinationsUtil.generateCombinations(allTriplesInComponent.size());
            combinationIndexes.parallelStream()
                    .forEach(row -> {
                        row.sort((c1, c2) -> (c1.length() < c2.length()) ? -1 : (c1.length() > c2.length()) ? 1 : 0);
                    });

            for (List<String> rowCombinationIndexes : combinationIndexes) {
                for (String combination : rowCombinationIndexes) {
                    if (!bestIndexes.isEmpty() && !combination.contains(bestIndexes))
                        continue;

                    BasicGraphPattern bgp = new BasicGraphPattern();
                    String[] stringIndexes = combination.split(" ");
                    for (String stringIndex : stringIndexes) {
                        Integer index = Integer.valueOf(stringIndex);
                        bgp.getTriples().add(allTriplesInComponent.get(index).getValue());
                    }

                    Map<Boolean, Integer> results = utilsJena.verifyBasicGraphPattern(bgp.getTriples(), categorizedExamples);
                    bgp.setInformation(computeInformation(results.get(Example.CATEGORY_POSITIVE)));

                    if (bestBgp.getTriples().isEmpty() || (bgp.getInformation() < bestBgp.getInformation())) {
                        bestBgp.setTriples(bgp.getTriples());
                        bestBgp.setInformation(bgp.getInformation());
                        bestIndexes = combination;
                    }

                    if (bestBgp.getInformation() >= informationGainThreshold)
                        return bestBgp;
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

    private Map<Example, Set<ExampleEntry<String, Triple>>> filterCommonTriples(Map<Example, Set<ExampleEntry<String, Triple>>> candidateTriples, Map<Integer, List<Example>> positiveExamplesByComponent) {
        Map<Example, Set<ExampleEntry<String, Triple>>> commonTriples = new HashMap<>();

        if (null == candidateTriples)
            return commonTriples;

        Set<Integer> keys = positiveExamplesByComponent.keySet();
        for (Integer key : keys) {
            List<Example> examples = positiveExamplesByComponent.get(key);
            // the structure of this map is <UserProvidedExample, Set of triples that belongs to that example>
            Map<Example, Set<ExampleEntry<String, Triple>>> componentCandidateTriples = new HashMap<>();

            for (Example example : examples) {
                Set<Example> candidateTriplesKeySet = candidateTriples.keySet();
                candidateTriplesKeySet.stream().forEach(ctKey -> {
                    if (example.equals(ctKey))
                        componentCandidateTriples.put(ctKey, candidateTriples.get(ctKey));
                });
            }

            // this is the first example on each component
            Example example = examples.get(0);

            Set<ExampleEntry<String, Triple>> exampleCandidateTriples = componentCandidateTriples.get(example);

            Map<Example, Set<ExampleEntry<String, Triple>>> otherTriplesInTheComponent = new HashMap<>();
            Set<Example> componentTriplesKeySet = componentCandidateTriples.keySet();
            componentTriplesKeySet.stream().forEach(ctKey -> {
                if (!ctKey.equals(example))
                    otherTriplesInTheComponent.put(ctKey, componentCandidateTriples.get(ctKey));
            });

            for (ExampleEntry<String, Triple> ect : exampleCandidateTriples) {
                boolean common = false;
                Set<Example> exampleKeys = otherTriplesInTheComponent.keySet();
                for (Example ek : exampleKeys) {
                    Set<ExampleEntry<String, Triple>> triples = otherTriplesInTheComponent.get(ek);
                    for (ExampleEntry<String, Triple> entry : triples) {
                        if (entry.getValue().getPredicate().equals(ect.getValue().getPredicate())) {
                            common = true;
                            break;
                        } else
                            common = false;
                    }
                }
                if (common) {
                    Set<ExampleEntry<String, Triple>> triples;
                    if (commonTriples.containsKey(example)) {
                        triples = commonTriples.get(example);
                        boolean alreadyAdded = false;

                        for (ExampleEntry<String, Triple> entry : triples) {
                            if (entry.getValue().getPredicate().equals(ect.getValue().getPredicate())) {
                                alreadyAdded = true;
                                break;
                            }
                        }

                        if (!alreadyAdded) {
                            triples.add(ect);
                            commonTriples.replace(example, triples);
                        }
                    } else {
                        triples = new HashSet<>();
                        triples.add(ect);
                        commonTriples.put(example, triples);
                    }
                }
            }

        }
        return commonTriples;
    }


    /**
     * @param componentCandidateTriples set of candidate triples to find a variable value. The structure is <Example, Triple>
     * @param parsedExamples            the parsed examples set.
     * @param triplesBySelectedVariable required by the constructHyperedges algorithm to know if the selected variable is present in more than one triple.
     */

    private void introduceVariables(Set<ExampleEntry<String, Triple>> componentCandidateTriples, Set<Example> parsedExamples, Map<String, List<Triple>> triplesBySelectedVariable, Example example, boolean[] distinguishedVariableInComponent) {
        for (ExampleEntry<String, Triple> cct : componentCandidateTriples) {
            boolean isExampleProvidedByUser = parsedExamples.stream().anyMatch(e -> e.getExample().equals(cct.getKey()));

            if (UtilsJena.getCanonicalExample(cct.getValue().getSubject().toString()).equals(cct.getKey())) {
                Node newSubject, newObject;
                if (!variableNames.containsKey(cct.getKey())) {
                    if (isExampleProvidedByUser) {
                        newSubject = NodeFactory.createVariable(UtilsJena.SELECTED_VARIABLE_PATTERN + example.getPosition());
                        distinguishedVariableInComponent[example.getPosition()] = true;
                    } else
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
            } else if (UtilsJena.getCanonicalExample(cct.getValue().getPredicate().toString()).equals(cct.getKey())) {
                Node newPredicate;
                if (!variableNames.containsKey(cct.getKey())) {
                    if (isExampleProvidedByUser) {
                        newPredicate = NodeFactory.createVariable(UtilsJena.SELECTED_VARIABLE_PATTERN + example.getPosition());
                        distinguishedVariableInComponent[example.getPosition()] = true;
                    } else
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
            } else if (UtilsJena.getCanonicalExample(cct.getValue().getObject().toString()).equals(cct.getKey())) {
                Node newSubject, newObject;
                if (!variableNames.containsKey(cct.getKey())) {
                    if (isExampleProvidedByUser) {
                        newObject = NodeFactory.createVariable(UtilsJena.SELECTED_VARIABLE_PATTERN + example.getPosition());
                        distinguishedVariableInComponent[example.getPosition()] = true;
                    } else
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
    }

    private double computeInformation(Integer positiveExamplesCovered) {
        return -(Math.log(((double) positiveExamplesCovered) / parsedExamples.size()) / Math.log(2));
    }

}
