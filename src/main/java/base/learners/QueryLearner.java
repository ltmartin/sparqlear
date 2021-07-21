package base.learners;

import base.domain.BasicGraphPattern;
import base.domain.Example;
import base.domain.ExampleEntry;
import base.domain.Motif;
import base.services.MotifsService;
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
import java.util.stream.Stream;

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
        for (Map.Entry<Example, Set<ExampleEntry<String, Triple>>> entry : candidateTriples.entrySet()) {
            Set<ExampleEntry<String, Triple>> value = entry.getValue();
            for (ExampleEntry<String, Triple> exampleEntry : value) {
                individuals.add(exampleEntry.getValue().getSubject().toString());
            }
        }

        // Finding the candidate motif instances involving individuals from the candidate triples
        Set<Motif> candidateMotifInstances = new HashSet<>();
        for (String ind : individuals) {
            candidateMotifInstances.addAll(motifsService.findMotifsInvolvingIndividual(ind));
        }

        if (null == parsedDatasets) {
            // Creating the candidate triple patterns
            Set<Example> candidateTriplesKeySet = candidateTriples.keySet();
            for (Example example : candidateTriplesKeySet) {
                introduceVariables(candidateTriples.get(example), parsedExamples, triplesBySelectedVariable, example.getPosition());
            }

            BasicGraphPattern bgp = constructBasicGraphPattern(candidateTriples, categorizedExamples, positiveExamplesByComponent.size());
            if (null != bgp)
                derivedQueries.add(buildQuery(bgp));
        } else {
            // TODO: Create the flow for learning from multiple datasets.
        }
        return Optional.of(derivedQueries);
    }

    private void introduceVariables(Set<ExampleEntry<String, Triple>> componentCandidateTriples, Set<Example> parsedExamples, Map<String, List<Triple>> triplesBySelectedVariable, Integer componentIndex) {
        for (ExampleEntry<String, Triple> cct : componentCandidateTriples) {
            boolean isExampleProvidedByUser = parsedExamples.stream().anyMatch(example -> example.getExample().equals(cct.getKey()));

            if (UtilsJena.getCanonicalExample(cct.getValue().getObject().toString()).equals(cct.getKey())) {
                Node newSubject, newObject;
                if (!variableNames.containsKey(cct.getKey())) {
                    if (isExampleProvidedByUser)
                        newObject = NodeFactory.createVariable(UtilsJena.SELECTED_VARIABLE_PATTERN + componentIndex);
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
    }

    private String buildQuery(BasicGraphPattern bgp) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("SELECT DISTINCT ");

        Set<String> selectedVariables = new HashSet<>();
        for (Triple triple : bgp.getTriplePatterns()) {
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
        for (Triple triple : bgp.getTriplePatterns()) {
            stringBuilder.append(utilsJena.getSparqlCompatibleTriple(triple)).append(" . ");
        }
        stringBuilder.append("}");

        return stringBuilder.toString();
    }

    private BasicGraphPattern constructBasicGraphPattern(Map<Example, Set<ExampleEntry<String, Triple>>> candidateTriplePatterns, Map<Boolean, List<Example>> categorizedExamples, int numberOfSelectedVariables) {
        Map<String, List<Triple>> candidateTriplesByDistinguishedVariable = groupCandidateTriplesByDistinguishedVariable(candidateTriplePatterns);
        Set<Triple> bestTriplePatterns = selectBestTriplePatterns(candidateTriplesByDistinguishedVariable, categorizedExamples);
        BasicGraphPattern cbgp = new BasicGraphPattern();
        cbgp.setTriplePatterns(bestTriplePatterns);
        calculateInformation(cbgp, categorizedExamples);
        return null;
    }

    private Set<Triple> selectBestTriplePatterns(Map<String, List<Triple>> candidateTriplesByDistinguishedVariable, Map<Boolean, List<Example>> categorizedExamples) {
        Set<Triple> bestTriplePatterns = new HashSet<>();

        Set<String> distinguishedVariablesKeySet = candidateTriplesByDistinguishedVariable.keySet();
        for (String distinguishedVariable : distinguishedVariablesKeySet) {
            List<Triple> triplePatterns = candidateTriplesByDistinguishedVariable.get(distinguishedVariable);
            Double bestInformation = 0.0;
            Triple bestTriple = null;
            for (Triple triple : triplePatterns) {
                BasicGraphPattern bgp = new BasicGraphPattern();
                bgp.setTriplePatterns(Stream.of(triple).collect(Collectors.toCollection(HashSet::new)));
                calculateInformation(bgp, categorizedExamples);
                if (bgp.getInformation() > bestInformation){
                    bestInformation = bgp.getInformation();
                    bestTriple = triple;
                }
            }
            bestTriplePatterns.add(bestTriple);
        }

        return bestTriplePatterns;
    }

    private void calculateInformation(BasicGraphPattern bgp, Map<Boolean, List<Example>> categorizedExamples) {
        Set<List<String>> bindings = utilsJena.getBindings(bgp);
        Set<String> subjects = extractSubjectFromBindings(bindings);

        Set<List<String>> naturalJoin = new HashSet<>();
        for (String subject : subjects) {
            Set<List<String>> triples = utilsJena.getTriplesWithSubject(subject);
            naturalJoin.addAll(triples);
        }

        Set<String> objects = naturalJoin.stream()
                .map(result -> {
                    return result.get(1);
                })
                .collect(Collectors.toSet());

        List<Example> positiveExamples = categorizedExamples.get(Example.CATEGORY_POSITIVE);
        List<Example> negativeExamples = categorizedExamples.get(Example.CATEGORY_NEGATIVE);

        int positiveExamplesCovered = 0, negativeExamplesCovered = 0;
        for (String object : objects) {
            if (positiveExamplesCovered < positiveExamples.size())
                positiveExamplesCovered += positiveExamples.stream().filter(example -> example.getExample().contains(UtilsJena.removeLanguageAnnotation(object))).count();
            if ((null != negativeExamples) && (negativeExamplesCovered < negativeExamples.size()))
                negativeExamplesCovered += negativeExamples.stream().filter(example -> example.getExample().contains(UtilsJena.removeLanguageAnnotation(object))).count();
        }
        double information = (double) positiveExamplesCovered / (positiveExamplesCovered + negativeExamplesCovered);
        bgp.setInformation(information);
    }

    private Set<String> extractSubjectFromBindings(Set<List<String>> bindings) {
        Set<String> subjects = new HashSet<>();

        for (List<String> results : bindings) {
            subjects.add(results.get(1));
        }

        return subjects;
    }


    private Map<String, List<Triple>> groupCandidateTriplesByDistinguishedVariable(Map<Example, Set<ExampleEntry<String, Triple>>> candidateTriples) {
        List<ExampleEntry<String, Triple>> allCandidateTriples = new LinkedList<>();

        for (Map.Entry<Example, Set<ExampleEntry<String, Triple>>> entry : candidateTriples.entrySet()) {
            allCandidateTriples.addAll(entry.getValue());
        }

        List<ExampleEntry<String, Triple>> candidateTriplesWithDistinguishedVariables = allCandidateTriples.stream()
                .filter(t -> t.getValue().getObject().toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN))
                .collect(Collectors.toList());

        Map<String, List<Triple>> candidateTriplesByDistinguishedVariable = new HashMap<>();
        for (ExampleEntry<String, Triple> entry : candidateTriplesWithDistinguishedVariables) {
            Triple triple = entry.getValue();

            String key = triple.getObject().toString();
            if (candidateTriplesByDistinguishedVariable.containsKey(key)) {
                List<Triple> triples = new LinkedList<>();
                triples.addAll(candidateTriplesByDistinguishedVariable.get(key));
                triples.add(triple);
                candidateTriplesByDistinguishedVariable.replace(key, triples);
            } else {
                candidateTriplesByDistinguishedVariable.put(key, Arrays.asList(triple));
            }
        }
        return candidateTriplesByDistinguishedVariable;
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

}
