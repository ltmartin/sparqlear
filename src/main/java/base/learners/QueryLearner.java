package base.learners;

import base.domain.*;
import base.services.MotifsService;
import base.utils.DatasetsParser;
import base.utils.ExampleUtils;
import base.utils.UtilsJena;
import org.apache.commons.lang3.SerializationUtils;
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
    private LinkedList<BindingWrapper> trainingSet = new LinkedList();

    public Optional<Set<String>> learn(String examples) throws ParseException, IOException {
        Set<String> derivedQueries = new HashSet<>();
        Set<String> parsedDatasets = null;

        logger.log(Level.INFO, "Parsing examples...");
        parsedExamples = exampleUtils.parseExamples(examples);
        logger.log(Level.INFO, "Examples parsed.");

        // Creating the training set with the bindings.
        createTrainingSet(parsedExamples);

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

            BasicGraphPattern bgp = constructBasicGraphPattern(candidateTriples, categorizedExamples, candidateMotifInstances);
            if (null != bgp)
                derivedQueries.add(buildQuery(bgp));
        } else {
            // TODO: Create the flow for learning from multiple datasets.
        }
        return Optional.of(derivedQueries);
    }

    private void createTrainingSet(Set<Example> parsedExamples) {
        Map<Integer, List<Example>> examplesByGroup = parsedExamples.stream().collect(Collectors.groupingBy(Example::getGroup));

        for (Map.Entry<Integer, List<Example>> entry : examplesByGroup.entrySet()) {
            BindingWrapper bindings = new BindingWrapper();
            List<Example> examples = entry.getValue();
            bindings.setCategory(examples.get(0).getCategory());
            for (Example example : examples) {
                String distinguishedVariable = "?" + UtilsJena.SELECTED_VARIABLE_PATTERN + example.getPosition();
                bindings.getBindings().put(distinguishedVariable, example.getExample());
            }
            trainingSet.add(bindings);
        }
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

    private BasicGraphPattern constructBasicGraphPattern(Map<Example, Set<ExampleEntry<String, Triple>>> candidateTriplePatterns, Map<Boolean, List<Example>> categorizedExamples, Set<Motif> candidateMotifInstances) {
        BasicGraphPattern bgp = new BasicGraphPattern();
        Map<String, List<Triple>> candidateTriplesByDistinguishedVariable = groupCandidateTriplesByDistinguishedVariable(candidateTriplePatterns);

        // Creating a copy of the training set, so we can return to the original one.
        LinkedList<BindingWrapper> temporaryTrainingSet = new LinkedList<>();
        createDeepCopy(trainingSet, temporaryTrainingSet);

        Set<Triple> bestTriplePatterns = selectBestTriplePatterns(candidateTriplesByDistinguishedVariable, categorizedExamples, temporaryTrainingSet);

        BasicGraphPattern cbgp = new BasicGraphPattern();
        cbgp.setTriplePatterns(bestTriplePatterns);
        calculateInformation(cbgp, categorizedExamples, temporaryTrainingSet);

        for (Motif motifInstance : candidateMotifInstances) {
            cbgp = tryMotifInstance(motifInstance, cbgp);
        }

        return bgp;
    }

    // This method is necessary to avoid the default Java copy-by-reference behaviour.
    private void createDeepCopy(List<BindingWrapper> trainingSet, LinkedList<BindingWrapper> temporaryTrainingSet) {
        for (BindingWrapper item : trainingSet) {
            temporaryTrainingSet.add(new BindingWrapper(item.getCategory(), SerializationUtils.clone((HashMap)item.getBindings())));
        }
    }

    private BasicGraphPattern tryMotifInstance(Motif motifInstance, BasicGraphPattern cbgp) {
        Set<base.domain.Triple> motifTriples = motifInstance.getTriples();
        for (base.domain.Triple triple : motifTriples) {
            String subject = triple.getSubject();
            String object =  triple.getObject();

            // creating a variable for the subject of the triple
            if (!variableNames.containsKey(subject)){
                Node newSubjectNode = NodeFactory.createVariable("x" + nsvIndex++);
                variableNames.put(subject, newSubjectNode);
            }

            // creating a variable for the object of the triple
            if (!variableNames.containsKey(object)){
                Node newObjectNode = NodeFactory.createVariable("x" + nsvIndex++);
                variableNames.put(subject, newObjectNode);
            }
        }
        // TODO: CONTINUE HERE.
        return cbgp;
    }


    private Set<Triple> selectBestTriplePatterns(Map<String, List<Triple>> candidateTriplesByDistinguishedVariable, Map<Boolean, List<Example>> categorizedExamples, List<BindingWrapper> temporaryTrainingSet) {
        Set<Triple> bestTriplePatterns = new HashSet<>();

        Set<String> distinguishedVariablesKeySet = candidateTriplesByDistinguishedVariable.keySet();
        for (String distinguishedVariable : distinguishedVariablesKeySet) {
            List<Triple> triplePatterns = candidateTriplesByDistinguishedVariable.get(distinguishedVariable);
            Double bestInformation = 0.0;
            Triple bestTriple = null;
            for (Triple triple : triplePatterns) {
                BasicGraphPattern bgp = new BasicGraphPattern();
                bgp.setTriplePatterns(Stream.of(triple).collect(Collectors.toCollection(HashSet::new)));
                calculateInformation(bgp, categorizedExamples, temporaryTrainingSet);
                if (bgp.getInformation() > bestInformation){
                    bestInformation = bgp.getInformation();
                    bestTriple = triple;
                }
            }
            bestTriplePatterns.add(bestTriple);
        }

        return bestTriplePatterns;
    }

    private void calculateInformation(BasicGraphPattern bgp, Map<Boolean, List<Example>> categorizedExamples, List<BindingWrapper> temporaryTrainingSet) {
        // Obtaining the bindings of the BGP
        Map<String, List<String>> bindings = utilsJena.getBindings(bgp);

        // Joining the training set with the bindings coming from the BGP
        for (int j = 0; j < temporaryTrainingSet.size(); j++) {
            BindingWrapper exampleBinding = temporaryTrainingSet.get(j);
            Set<String> keySet = exampleBinding.getBindings().keySet();
            Set<String> distinguishedVariableKeys = keySet.stream().filter(key -> key.contains(UtilsJena.SELECTED_VARIABLE_PATTERN)).collect(Collectors.toSet());
            for (String key : distinguishedVariableKeys) {
                String bindingInTrainingSet = exampleBinding.getBindings().get(key);
                List<String> bgpBindingsInstances = bindings.get(key);
                if (null != bgpBindingsInstances) {
                    boolean bindingInTrainingSetFound = false;
                    for (int i = 0; i < bgpBindingsInstances.size(); i++) {
                        if (bgpBindingsInstances.get(i).contains(bindingInTrainingSet)) {
                            bindingInTrainingSetFound = true;
                            Map<String, String> bindingsInTrainingSet = exampleBinding.getBindings();
                            Set<String> bindingsKeys = bindings.keySet();
                            for (String bindingKey : bindingsKeys) {
                                if (!bindingsInTrainingSet.containsKey(bindingKey))
                                    bindingsInTrainingSet.put(bindingKey, bindings.get(bindingKey).get(i));
                            }
                            exampleBinding.setBindings(bindingsInTrainingSet);
                        }
                    }
                    if (!bindingInTrainingSetFound)
                        temporaryTrainingSet.remove(exampleBinding);
                }
            }
        }

        Set<String> distinguishedVariablesInBgp = new HashSet<>();
        bgp.getTriplePatterns().stream().forEach(triplePattern -> {
            if (triplePattern.getObject().toString().contains(UtilsJena.SELECTED_VARIABLE_PATTERN))
                distinguishedVariablesInBgp.add(triplePattern.getObject().toString());
        });

        List<Example> positiveExamples = categorizedExamples.get(Example.CATEGORY_POSITIVE);
        List<Example> negativeExamples = categorizedExamples.get(Example.CATEGORY_NEGATIVE);

        int positiveExamplesCovered = 0, negativeExamplesCovered = 0;
        List<String> distinguishedVariableBindingInstances = new LinkedList<>();
        for (BindingWrapper bindingWrapper : temporaryTrainingSet) {
            distinguishedVariablesInBgp.stream().forEach(dv -> {
                if (bindingWrapper.getBindings().containsKey(dv))
                    distinguishedVariableBindingInstances.add(bindingWrapper.getBindings().get(dv));
            });
        }

        if (positiveExamplesCovered < positiveExamples.size())
            positiveExamplesCovered += positiveExamples.stream().filter(example -> distinguishedVariableBindingInstances.contains(example.getExample())).count();
        if ((null != negativeExamples) && (negativeExamplesCovered < negativeExamples.size()))
            negativeExamplesCovered += negativeExamples.stream().filter(example -> distinguishedVariableBindingInstances.contains(example.getExample())).count();

        double information = (double) positiveExamplesCovered / (positiveExamplesCovered + negativeExamplesCovered);
        bgp.setInformation(information);
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
