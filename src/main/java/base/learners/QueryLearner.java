package base.learners;

import base.domain.Example;
import base.domain.ExampleEntry;
import base.domain.Hyperedge;
import base.exceptions.ExampleException;
import base.services.PropertiesService;
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

    public Set<String> learn(String examples) throws ParseException, ExampleException, IOException {
        Set<String> derivedQueries = new HashSet<>();
        Set<String> parsedDatasets = null;

        logger.log(Level.INFO, "Parsing examples...");
        Set<Example> parsedExamples = exampleUtils.parseExamples(examples);
        logger.log(Level.INFO, "Examples parsed.");

        if (!datasets.isEmpty()) {
            logger.log(Level.INFO, "Parsing datasets...");
            parsedDatasets = datasetsParser.parse(datasets);
            logger.log(Level.INFO, "Datasets parsed.");
        }

        Map<Boolean, List<Example>> categorizedExamples = parsedExamples.stream()
                .collect(Collectors.groupingBy(Example::getCategory));

        Set<Example> positiveExamples = new HashSet<>(categorizedExamples.get(Example.CATEGORY_POSITIVE));
        Map<Integer, List<Example>> positiveExamplesByComponent = positiveExamples.stream().collect(Collectors.groupingBy(Example::getPosition));
        Set<Integer> componentKeys = positiveExamplesByComponent.keySet();

        Map<Example, Set<ExampleEntry<String, Triple>>> candidateTriples = new HashMap<>();
        logger.log(Level.INFO, "Starting to derive candidate triples...");
        for (Integer componentKey : componentKeys) {
            List<Example> componentExamples = positiveExamplesByComponent.get(componentKey);
            for (Example componentExample : componentExamples) {
                candidateTriples.put(componentExample, tripleFinder.deriveCandidateTriples(componentExample.getExample(), Optional.empty()));
            }

        }
        logger.log(Level.INFO, "Candidate triples successfully derived.");

        if (null == parsedDatasets) {
            candidateTriples = filterCommonTriples(candidateTriples, positiveExamplesByComponent);
            Set<Hyperedge> hyperedges = constructHyperedges(candidateTriples, parsedExamples, positiveExamplesByComponent);
            String query = buildQuery(positiveExamples, hyperedges);
            derivedQueries.add(query);
        } else {
            // TODO: Create the flow for learning from multiple datasets.
        }


        return derivedQueries;
    }

    private Map<Example, Set<ExampleEntry<String, Triple>>> filterCommonTriples(Map<Example, Set<ExampleEntry<String, Triple>>> candidateTriples, Map<Integer, List<Example>> positiveExamplesByComponent) {
        Map<Example, Set<ExampleEntry<String, Triple>>> commonTriples = new HashMap<>();

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

            Map<Example, Set<ExampleEntry<String, Triple>>> otherComponentTriples = new HashMap<>();
            Set<Example> componentTriplesKeySet = componentCandidateTriples.keySet();
            componentTriplesKeySet.stream().forEach(ctKey -> {
                if (!ctKey.equals(example))
                    otherComponentTriples.put(ctKey, componentCandidateTriples.get(ctKey));
            });

            for (ExampleEntry<String, Triple> ect : exampleCandidateTriples) {
                boolean common = false;
                Set<Example> exampleKeys = otherComponentTriples.keySet();
                for (Example ek : exampleKeys) {
                    Set<ExampleEntry<String, Triple>> triples = otherComponentTriples.get(ek);
                    for (ExampleEntry<String, Triple> entry : triples) {
                        if (entry.getValue().getPredicate().equals(ect.getValue().getPredicate())) {
                            common = true;
                            break;
                        } else
                            common = false;
                    }
                }
                if (common) {
                    Set<ExampleEntry<String, Triple>> triples = null;
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
     * @param positiveExamples a set of positive examples.
     * @param hyperedges       a set of hyperedges.
     * @return a query joining the most of the examples.
     */
    private String buildQuery(Set<Example> positiveExamples, Set<Hyperedge> hyperedges) {
        // Make disjoint sets
        List<Set<String>> disjointSets = new LinkedList<>();
        for (Example e : positiveExamples) {
            Set<String> elements = new HashSet<>();
            elements.add(e.getExample());
            disjointSets.add(elements);
        }

        hyperedges = hyperedges.stream()
                .sorted((Comparator.comparing(Hyperedge::getInformationGain).reversed()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("SELECT DISTINCT WHERE {} LIMIT " + resultsLimit);
        for (Hyperedge e : hyperedges) {

            if (e.getTriple().getSubject().toString().startsWith(NodeFactory.createVariable(UtilsJena.SELECTED_VARIABLE_PATTERN).toString()) && !stringBuilder.toString().contains(e.getTriple().getSubject().toString()))
                stringBuilder.insert(stringBuilder.indexOf("WHERE"), e.getTriple().getSubject().toString() + " ");
            if (e.getTriple().getPredicate().toString().startsWith(NodeFactory.createVariable(UtilsJena.SELECTED_VARIABLE_PATTERN).toString()) && !stringBuilder.toString().contains(e.getTriple().getPredicate().toString()))
                stringBuilder.insert(stringBuilder.indexOf("WHERE"), e.getTriple().getPredicate().toString() + " ");
            if (e.getTriple().getObject().toString().startsWith(NodeFactory.createVariable(UtilsJena.SELECTED_VARIABLE_PATTERN).toString()) && !stringBuilder.toString().contains(e.getTriple().getObject().toString()))
                stringBuilder.insert(stringBuilder.indexOf("WHERE"), e.getTriple().getObject().toString() + " ");

            stringBuilder.insert(stringBuilder.indexOf("}"), utilsJena.getSparqlCompatibleTriple(e.getTriple()) + ". ");
            String candidateQuery = stringBuilder.toString();
            Set<List<String>> valuation = utilsJena.runQuery(candidateQuery);

            boolean areThereDisjointElements = joinSets(valuation, disjointSets);
            if (!areThereDisjointElements)
                return candidateQuery;

        }
        return null;
    }

    private boolean joinSets(Set<List<String>> valuation, List<Set<String>> disjointSets) {
        boolean areThereDisjointElements = false;
        Set<String> setToJoinElements = new HashSet<>();
        for (List<String> row : valuation) {
            for (String value : row) {
                for (int i = 0; i < disjointSets.size(); i++) {
                    Set<String> elementsInDisjointSet = disjointSets.get(i);
                    if (elementsInDisjointSet.contains(value)) {
                        setToJoinElements.addAll(elementsInDisjointSet);
                        disjointSets.remove(i--);
                        areThereDisjointElements = true;
                        break;
                    }
                }
            }
        }
        if (areThereDisjointElements)
            disjointSets.add(setToJoinElements);

        return disjointSets.size() > 1;
    }


    /**
     * @param candidateTriples A set of common candidate triples for each component of the examples.
     * @param parsedExamples   the set of parsed examples.
     */
    private Set<Hyperedge> constructHyperedges(Map<Example, Set<ExampleEntry<String, Triple>>> candidateTriples, Set<Example> parsedExamples, Map<Integer, List<Example>> positiveExamplesByComponent) throws IOException {
        Set<Hyperedge> hyperedges = new HashSet<>();
        Map<Boolean, List<Example>> categorizedExamples = parsedExamples.stream().collect(Collectors.groupingBy(Example::getCategory));
        List<Example> positiveExamples = categorizedExamples.get(Example.CATEGORY_POSITIVE);

        Set<Integer> positiveExamplesByComponentKeySet = positiveExamplesByComponent.keySet();
        for (Integer positiveExamplesByComponentKey : positiveExamplesByComponentKeySet) {
            // construct the hyperedge that joins all the examples of the component from the triples of the first example.
            Example c = positiveExamplesByComponent.get(positiveExamplesByComponentKey).get(0);

            Set<ExampleEntry<String, Triple>> componentCandidateTriples = candidateTriples.get(c);

            Map<String, List<Triple>> triplesBySelectedVariable = new HashMap<>();

            int selectedVariablesAmount = introduceVariables(componentCandidateTriples, parsedExamples, triplesBySelectedVariable);

            Set<List<String>> completeQueryValuation = utilsJena.runCompleteQueryForHyperedges(componentCandidateTriples, parsedExamples, selectedVariablesAmount);

            // Piece of code to protect those triples that are the only ones extracting a selected variable.
            for (String key : triplesBySelectedVariable.keySet()) {
                if (triplesBySelectedVariable.get(key).size() == 1) {
                    hyperedges.add(new Hyperedge(triplesBySelectedVariable.get(key).get(0), Float.MAX_VALUE));
                    Iterator<ExampleEntry<String, Triple>> componentCandidateTriplesIterator = componentCandidateTriples.iterator();
                    while (componentCandidateTriplesIterator.hasNext()) {
                        Triple triple = componentCandidateTriplesIterator.next().getValue();
                        if (triple.equals(triplesBySelectedVariable.get(key).get(0))) {
                            componentCandidateTriplesIterator.remove();
                            break;
                        }
                    }
                }
            }

            double totalInformationGain = computeInformationGain(completeQueryValuation, categorizedExamples);

            // FIXME: Make this parallel
            componentCandidateTriples.stream().forEach(cct -> {
                Set<List<String>> partialQueryValuation = utilsJena.runPartialQueryForHyperedges(componentCandidateTriples, parsedExamples, cct, selectedVariablesAmount);
                double cctInformationGain = totalInformationGain - computeInformationGain(partialQueryValuation, categorizedExamples);
                hyperedges.add(new Hyperedge(cct.getValue(), cctInformationGain));
            });
        }

        return hyperedges;
    }

    /**
     * @param componentCandidateTriples set of candidate triples to find a variable value. The structure is <Example, Triple>
     * @param parsedExamples            the parsed examples set.
     * @param triplesBySelectedVariable required by the constructHyperedges algorithm to know if the selected variable is present in more than one triple.
     */

    private int introduceVariables(Set<ExampleEntry<String, Triple>> componentCandidateTriples, Set<Example> parsedExamples, Map<String, List<Triple>> triplesBySelectedVariable) {
        int svIndex = 0, nsvIndex = 0;
        Map<String, Node> variableNames = new HashMap<>();
        for (ExampleEntry<String, Triple> cct : componentCandidateTriples) {
            boolean isExampleProvidedByUser = parsedExamples.stream().filter(example -> example.getExample().equals(cct.getKey())).count() != 0;

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
            } else if (UtilsJena.getCanonicalExample(cct.getValue().getPredicate().toString()).equals(cct.getKey())) {
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
            } else if (UtilsJena.getCanonicalExample(cct.getValue().getObject().toString()).equals(cct.getKey())) {
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

    private double computeInformationGain(Set<List<String>> queryValuation, Map<Boolean, List<Example>> examples) {
        Map<Integer, List<Example>> positiveExamplesByGroup = examples.get(Example.CATEGORY_POSITIVE).stream().collect(Collectors.groupingBy(Example::getGroup));
        Map<Integer, List<Example>> negativeExamplesByGroup = new HashMap<>();
        if (null != examples.get(Example.CATEGORY_NEGATIVE))
            negativeExamplesByGroup = examples.get(Example.CATEGORY_NEGATIVE).stream().collect(Collectors.groupingBy(Example::getGroup));

        int positiveExamplesFoundCounter = 0;
        int negativeExamplesFoundCounter = 0;
        for (List<String> row : queryValuation) {
            boolean found = false;
            for (int i = 0; i < positiveExamplesByGroup.size() && !found; i++) {
                List<Example> ithGroup = positiveExamplesByGroup.get(i);
                List<String> ithGroupAsList = exampleUtils.getExamplesAsListOfString(ithGroup);
                found = (row.size() == ithGroupAsList.size()) && ((!ithGroupAsList.containsAll(row)) ? false : row.containsAll(ithGroupAsList));
                if (found)
                    positiveExamplesFoundCounter++;
            }
            for (int i = 0; i < negativeExamplesByGroup.size() && !found; i++) {
                List<Example> ithGroup = negativeExamplesByGroup.get(i);
                List<String> ithGroupAsList = exampleUtils.getExamplesAsListOfString(ithGroup);
                found = (row.size() == ithGroupAsList.size()) && ((!ithGroupAsList.containsAll(row)) ? false : row.containsAll(ithGroupAsList));
                if (found)
                    negativeExamplesFoundCounter++;
            }
        }

        return Math.log(((double) positiveExamplesFoundCounter) / (positiveExamplesFoundCounter + negativeExamplesFoundCounter));
    }


}
