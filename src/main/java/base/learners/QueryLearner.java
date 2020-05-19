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

        int numberOfPositiveExamples = categorizedExamples.get(Example.CATEGORY_POSITIVE).size();
        for (int initialGroup = 0; initialGroup < numberOfPositiveExamples; initialGroup++) {
            if (null == parsedDatasets) {
                final int ig = initialGroup;
                Set<Example> positiveExampleGroup = categorizedExamples.get(Example.CATEGORY_POSITIVE).stream()
                        .filter(example -> example.getGroup().equals(ig))
                        .collect(Collectors.toSet());

                Set<Hyperedge> hyperedges = constructHyperedges(positiveExampleGroup, parsedExamples);

                Set<Example> positiveExamples = new HashSet<>(categorizedExamples.get(Example.CATEGORY_POSITIVE));

                String query = buildQuery(positiveExamples, hyperedges);
                double queryInformationGain = computeInformationGain(utilsJena.runQuery(query), categorizedExamples);
                if (informationGainThreshold <= queryInformationGain) {
                    derivedQueries.add(query);
                    if (learnMultipleQueries)
                        break;
                }

            } else {
                // TODO: Create the flow for learning from multiple datasets.
            }
        }

        return derivedQueries;
    }

    /**
     * @param positiveExamples a set of positive examples.
     * @param hyperedges       a set of hyperedges.
     * @return a query joining the most of the examples.
     */
    private String buildQuery(Set<Example> positiveExamples, Set<Hyperedge> hyperedges) {
        String query = "";
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
        stringBuilder.append("SELECT WHERE {}");
        for (Hyperedge e: hyperedges) {
            if (e.getTriple().getSubject().toString().startsWith(NodeFactory.createVariable(UtilsJena.SELECTED_VARIABLE_PATTERN).toString()))
                stringBuilder.insert(stringBuilder.indexOf("WHERE"), e.getTriple().getSubject().toString() + " ");
            if (e.getTriple().getPredicate().toString().startsWith(NodeFactory.createVariable(UtilsJena.SELECTED_VARIABLE_PATTERN).toString()))
                stringBuilder.insert(stringBuilder.indexOf("WHERE"), e.getTriple().getPredicate().toString() + " ");
            if (e.getTriple().getObject().toString().startsWith(NodeFactory.createVariable(UtilsJena.SELECTED_VARIABLE_PATTERN).toString()))
                stringBuilder.insert(stringBuilder.indexOf("WHERE"), e.getTriple().getObject().toString() + " ");

            stringBuilder.insert(stringBuilder.indexOf("}"), e.getTriple().toString() + ". ");
            String candidateQuery = stringBuilder.toString();
            Set<List<String>> valuation = utilsJena.runQuery(candidateQuery);

            boolean areThereDisjointElements = joinSets(valuation, disjointSets);
            if (areThereDisjointElements)
                query = candidateQuery;
            if (disjointSets.size() == 1)
                break;
        }

        return query;
    }

    private boolean joinSets(Set<List<String>> valuation, List<Set<String>> disjointSets) {
        boolean areThereDisjointElements = false;
        for (List<String> row : valuation){
            for (String value: row) {
                Set<String> setToJoinElements = new HashSet<>();
                for (int i = 0; i < disjointSets.size(); i++) {
                    Set<String> elementsInDisjointSet = disjointSets.get(i);
                    if (elementsInDisjointSet.contains(value)){
                        setToJoinElements.addAll(elementsInDisjointSet);
                        disjointSets.remove(i--);
                        areThereDisjointElements = true;
                        break;
                    }
                }
                if (areThereDisjointElements)
                    disjointSets.add(setToJoinElements);
                if (disjointSets.size() == 1)
                    return areThereDisjointElements;
            }
        }

        return areThereDisjointElements;
    }


    /**
     * @param positiveExampleGroup A single positive example. It could be a single variable example (e.g. +Cuba) or a multiple variables example (e.g. +<Jose, Cuba, Writer>)
     * @param parsedExamples       the set of parsed examples.
     */
    private Set<Hyperedge> constructHyperedges(Set<Example> positiveExampleGroup, Set<Example> parsedExamples) throws IOException {
        Set<Hyperedge> hyperedges = new HashSet<>();
        Map<Boolean, List<Example>> categorizedExamples = parsedExamples.stream().collect(Collectors.groupingBy(Example::getCategory));

        for (Example c : positiveExampleGroup) {

            Set<ExampleEntry<String, Triple>> componentCandidateTriples = tripleFinder.deriveCandidateTriples(c.getExample(), Optional.empty());

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

            componentCandidateTriples.parallelStream().forEach(cct -> {
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

            if (cct.getValue().getSubject().toString().equals(cct.getKey())) {
                Node newSubject;
                if (!variableNames.containsKey(cct.getKey())) {
                    if (isExampleProvidedByUser)
                        newSubject = NodeFactory.createVariable(UtilsJena.SELECTED_VARIABLE_PATTERN + svIndex++);
                    else
                        newSubject = NodeFactory.createVariable("x" + nsvIndex++);

                    variableNames.put(cct.getKey(), newSubject);
                    cct.setValue(new Triple(newSubject, cct.getValue().getPredicate(), cct.getValue().getObject()));

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
                    cct.setValue(new Triple(variableNames.get(cct.getKey()), cct.getValue().getPredicate(), cct.getValue().getObject()));

                    if (cct.getValue().getSubject().toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN)) {
                        List<Triple> tripleList = triplesBySelectedVariable.get(cct.getValue().getSubject().toString());
                        tripleList.add(cct.getValue());
                        triplesBySelectedVariable.replace(cct.getValue().getSubject().toString(), tripleList);
                    }
                }
            } else if (cct.getValue().getPredicate().toString().equals(cct.getKey())) {
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
            } else if (cct.getValue().getObject().toString().equals(cct.getKey())) {
                Node newObject;
                if (!variableNames.containsKey(cct.getKey())) {
                    if (isExampleProvidedByUser)
                        newObject = NodeFactory.createVariable(UtilsJena.SELECTED_VARIABLE_PATTERN + svIndex++);
                    else
                        newObject = NodeFactory.createVariable("x" + nsvIndex++);

                    variableNames.put(cct.getKey(), newObject);
                    cct.setValue(new Triple(cct.getValue().getSubject(), cct.getValue().getPredicate(), newObject));

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
                    cct.setValue(new Triple(cct.getValue().getSubject(), cct.getValue().getPredicate(), variableNames.get(cct.getKey())));

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
        Map<Integer, List<Example>> negativeExamplesByGroup = examples.get(Example.CATEGORY_NEGATIVE).stream().collect(Collectors.groupingBy(Example::getGroup));

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
