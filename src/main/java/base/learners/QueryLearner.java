package base.learners;

import base.domain.*;
import base.services.MotifsService;
import base.utils.*;
import com.google.common.collect.Sets;
import org.apache.commons.collections.ListUtils;
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
    @Value("${sparqlear.sparql.datasets}")
    private String datasets;

    private Map<String, List<Triple>> triplesBySelectedVariable = new HashMap<>();
    private Map<String, Node> variableNames = new HashMap<>();
    private int nsvIndex = 0;
    private final LinkedList<BindingWrapper> trainingSet = new LinkedList<>();
    private Map<Boolean, List<ExampleWrapper>> categorizedExamples;
    private PriorityQueue<State> states;


    public QueryLearner() {
        // this priority queue will prioritize the states where the sum of information and coverage is bigger.
        this.states = new PriorityQueue<>((state1, state2) -> {
            if (((state1.getCoverage() + state1.getInformation()) < (state2.getCoverage()+ state2.getInformation()))) return 1;
            if (((state1.getCoverage() + state1.getInformation()) > (state2.getCoverage()+ state2.getInformation()))) return -1;

            // if the information is the same, we think that a bgp with more triple patterns will be more explicative.
            if (state1.getBasicGraphPattern().getTriplePatterns().size() < state2.getBasicGraphPattern().getTriplePatterns().size()) return 1;
            if (state1.getBasicGraphPattern().getTriplePatterns().size() > state2.getBasicGraphPattern().getTriplePatterns().size()) return -1;

            return 0;
        });
    }

    public Optional<Set<String>> learn(String examples) throws ParseException, IOException {
        Set<String> derivedQueries = new HashSet<>();
        Set<String> parsedDatasets = null;

        logger.log(Level.INFO, "Parsing examples...");
        Set<ExampleWrapper> parsedExampleWrappers = exampleUtils.parseExamples(examples);
        logger.log(Level.INFO, "Examples parsed.");

        // Creating the training set with the bindings.
        createTrainingSet(parsedExampleWrappers);

        if (!datasets.isEmpty()) {
            logger.log(Level.INFO, "Parsing datasets...");
            parsedDatasets = datasetsParser.parse(datasets);
            logger.log(Level.INFO, "Datasets parsed.");
        }

        // Learning the candidate triples
        categorizedExamples = parsedExampleWrappers.stream()
                .collect(Collectors.groupingBy(ExampleWrapper::getCategory));

        Set<ExampleWrapper> positiveExampleWrappers = new HashSet<>(categorizedExamples.get(ExampleWrapper.CATEGORY_POSITIVE));
        Map<Integer, List<ExampleWrapper>> positiveExamplesByComponent = positiveExampleWrappers.stream().collect(Collectors.groupingBy(ExampleWrapper::getPosition));
        Map<ExampleWrapper, Set<ExampleEntry<String, Triple>>> candidateTriples = deriveCandidateTriples(positiveExamplesByComponent, Optional.empty());

        // Extracting the subjects of the candidate triples
        Set<String> individuals = new HashSet<>();
        assert candidateTriples != null;
        for (Map.Entry<ExampleWrapper, Set<ExampleEntry<String, Triple>>> entry : candidateTriples.entrySet()) {
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
            Set<ExampleWrapper> candidateTriplesKeySet = candidateTriples.keySet();
            for (ExampleWrapper exampleWrapper : candidateTriplesKeySet) {
                introduceVariables(candidateTriples.get(exampleWrapper), parsedExampleWrappers, triplesBySelectedVariable, exampleWrapper.getPosition());
            }

            BasicGraphPattern bgp = constructBasicGraphPattern(candidateTriples, candidateMotifInstances);
            derivedQueries.add(buildQuery(bgp));
        } else {
            // TODO: Create the flow for learning from multiple datasets.
        }
        return Optional.of(derivedQueries);
    }

    private void createTrainingSet(Set<ExampleWrapper> parsedExampleWrappers) {
        // Get the tuple with all the components of the example.
        Map<Integer, List<ExampleWrapper>> examplesByGroup = parsedExampleWrappers.stream().collect(Collectors.groupingBy(ExampleWrapper::getGroup));

        for (Map.Entry<Integer, List<ExampleWrapper>> entry : examplesByGroup.entrySet()) {
            BindingWrapper bindings = new BindingWrapper();
            List<ExampleWrapper> exampleWrappers = entry.getValue();
            // The label (positive or negative)
            bindings.setCategory(exampleWrappers.get(0).getCategory());
            for (ExampleWrapper exampleWrapper : exampleWrappers) {
                // Create a head variable with the structure ?dv + componentIndex (componentIndex is the column).
                String distinguishedVariable = "?" + Constants.HEAD_VARIABLE_PATTERN + exampleWrapper.getPosition();
                bindings.getBindings().put(distinguishedVariable, exampleWrapper.getExample());
            }
            trainingSet.add(bindings);
        }
    }

    private void introduceVariables(Set<ExampleEntry<String, Triple>> componentCandidateTriples, Set<ExampleWrapper> parsedExampleWrappers, Map<String, List<Triple>> triplesBySelectedVariable, Integer componentIndex) {
        for (ExampleEntry<String, Triple> cct : componentCandidateTriples) {
            boolean isExampleProvidedByUser = parsedExampleWrappers.stream().anyMatch(exampleWrapper -> exampleWrapper.getExample().equals(cct.getKey()));

            if (UtilsJena.getCanonicalExample(cct.getValue().getObject().toString()).equals(cct.getKey())) {
                Node newSubject = null, newObject;
                if (!variableNames.containsKey(cct.getKey())) {
                    if (isExampleProvidedByUser)
                        newObject = NodeFactory.createVariable(Constants.HEAD_VARIABLE_PATTERN + componentIndex);
                    else
                        newObject = NodeFactory.createVariable(Constants.EXISTENTIAL_VARIABLE_PATTERN + nsvIndex++);

                    variableNames.put(cct.getKey(), newObject);

                    // Check if the subject has an existential variable assigned already.
                    if (!variableNames.containsKey(cct.getValue().getSubject().toString())) {
                        newSubject = NodeFactory.createVariable(Constants.EXISTENTIAL_VARIABLE_PATTERN + nsvIndex++);
                        variableNames.put(cct.getValue().getSubject().toString(), newSubject);
                    }

                    // If there was already an existential variable for the subject, reuse it.
                    if (null != newSubject)
                        cct.setValue(new Triple(newSubject, cct.getValue().getPredicate(), newObject));
                    else
                        cct.setValue(new Triple(variableNames.get(cct.getValue().getSubject().toString()), cct.getValue().getPredicate(), newObject));

                    if (newObject.toString().contains(Constants.HEAD_VARIABLE_PATTERN)) {
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
                        newSubject = NodeFactory.createVariable(Constants.EXISTENTIAL_VARIABLE_PATTERN + nsvIndex++);
                        variableNames.put(cct.getValue().getSubject().toString(), newSubject);
                    }
                    cct.setValue(new Triple(variableNames.get(cct.getValue().getSubject().toString()), cct.getValue().getPredicate(), variableNames.get(cct.getKey())));

                    if (cct.getValue().getObject().toString().contains(Constants.HEAD_VARIABLE_PATTERN)) {
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
            if (triple.getSubject().toString().contains(Constants.HEAD_VARIABLE_PATTERN))
                selectedVariables.add(triple.getSubject().toString());
            if (triple.getObject().toString().contains(Constants.HEAD_VARIABLE_PATTERN))
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

    private BasicGraphPattern constructBasicGraphPattern(Map<ExampleWrapper, Set<ExampleEntry<String, Triple>>> candidateTriplePatterns, Set<Motif> candidateMotifInstances) {
        BasicGraphPattern bgp = new BasicGraphPattern();
        Map<String, List<Triple>> candidateTriplesByDistinguishedVariable = groupCandidateTriplesByDistinguishedVariable(candidateTriplePatterns);

        // Creating a copy of the training set, so we can return to the original one.
        LinkedList<BindingWrapper> temporaryTrainingSet = new LinkedList<>();
        createDeepCopy(trainingSet, temporaryTrainingSet);

        Set<Triple> bestTriplePatterns = selectBestTriplePatterns(candidateTriplesByDistinguishedVariable, temporaryTrainingSet);
        BasicGraphPattern cbgp = new BasicGraphPattern();
        cbgp.setTriplePatterns(bestTriplePatterns);

        temporaryTrainingSet = new LinkedList<>();
        createDeepCopy(trainingSet, temporaryTrainingSet);
        State state = calculateInformation(cbgp, temporaryTrainingSet);
        computeCoverage(state);
        states.add(state);

        // TODO: Test this method, it seems to be working well, but continue testing it.
        for (Motif motifInstance : candidateMotifInstances) {
            temporaryTrainingSet = new LinkedList<>();
            createDeepCopy(trainingSet, temporaryTrainingSet);
            bgp = tryMotifInstance(motifInstance, cbgp, temporaryTrainingSet);
        }

        return bgp;
    }

    private void computeCoverage(State state) {
        // Obtaining the bindings of the BGP.
        Map<String, List<String>> bindings = utilsJena.getBindings(state.getBasicGraphPattern());

        // Extracting the bindings of the head variables.
        Set<String> bindingsKeys = bindings.keySet();
        Set<String> bindingValues = new HashSet<>();
        for (String key : bindingsKeys) {
            if (key.contains(Constants.HEAD_VARIABLE_PATTERN)){
                List<String> values = bindings.get(key);
                for (String value : values) {
                    bindingValues.add(UtilsJena.getCanonicalString(value));
                }
            }
        }

        // Counting the positive examples covered.
        int positiveExamplesCovered = 0;
        List<ExampleWrapper> positiveExampleWrappers = categorizedExamples.get(ExampleWrapper.CATEGORY_POSITIVE);
        for (ExampleWrapper exampleWrapper : positiveExampleWrappers) {
            for (String binding : bindingValues) {
                if (binding.contains(exampleWrapper.getExample()))
                    positiveExamplesCovered++;
            }

        }
        state.setCoverage((double) positiveExamplesCovered / positiveExampleWrappers.size());
    }

    // This method is necessary to avoid the default Java copy-by-reference behaviour.
    private void createDeepCopy(List<BindingWrapper> trainingSet, LinkedList<BindingWrapper> temporaryTrainingSet) {
        for (BindingWrapper item : trainingSet) {
            temporaryTrainingSet.add(new BindingWrapper(item.getCategory(), SerializationUtils.clone((HashMap) item.getBindings())));
        }
    }

    private BasicGraphPattern tryMotifInstance(Motif motifInstance, BasicGraphPattern cbgp, LinkedList<BindingWrapper> temporaryTrainingSet) {
        Set<base.domain.Triple> motifTriples = motifInstance.getTriples();
        Set<String> constantsInMotif = new HashSet<>();
        // Replace all the individuals that already have a variable assigned by the variable.
        for (base.domain.Triple triple : motifTriples) {
            String subject = UtilsJena.getCanonicalString(triple.getSubject());
            // This line is to take advantage of the loop and store the constant in a collection for later use.
            constantsInMotif.add(subject);

            if (variableNames.containsKey(subject))
                triple.setSubject(variableNames.get(subject).toString());

            // The two lines are to take advantage of the loop and store the constants in a collection for later use.
            String object = UtilsJena.getCanonicalString(triple.getObject());
            constantsInMotif.add(object);
        }

        BasicGraphPattern temporaryBgp = cbgp.clone();
        for (String constant : constantsInMotif) {
            // Keeping a copy of the motif instance for the case I need to restore it.
            Motif savedMotifInstance = motifInstance.clone();

            replaceConstantInMotifTriples(constant, motifInstance);
            Set<Triple> bgpTriplePatterns = temporaryBgp.getTriplePatterns();
            bgpTriplePatterns.addAll(UtilsJena.convertDomainTriplesToJenaTriples(motifInstance.getTriples()));
            temporaryBgp.setTriplePatterns(bgpTriplePatterns);
            State state = calculateInformation(temporaryBgp,temporaryTrainingSet);
            computeCoverage(state);
            states.add(state);

            if (!states.peek().equals(state)) {
                temporaryBgp = states.peek().getBasicGraphPattern().clone();
                motifInstance = savedMotifInstance.clone();
            }
        }
        // return the best bgp in the priority queue.
        return states.peek().getBasicGraphPattern();
    }

    private void replaceConstantInMotifTriples(String constant, Motif motifInstance) {
        if (!variableNames.containsKey(constant)){
            variableNames.put(constant, NodeFactory.createVariable(Constants.EXISTENTIAL_VARIABLE_PATTERN + nsvIndex++));
        }
        Set<base.domain.Triple> motifTriples = motifInstance.getTriples();
        Set<base.domain.Triple> newTriplePatterns = new HashSet<>();

        for (base.domain.Triple triple : motifTriples) {
            String tripleSubject = UtilsJena.getCanonicalString(triple.getSubject());
            if (tripleSubject.equals(constant))
                triple.setSubject(variableNames.get(constant).toString());
            String tripleObject = UtilsJena.getCanonicalString(triple.getObject());
            if (tripleObject.equals(constant))
                triple.setObject(variableNames.get(constant).toString());
            newTriplePatterns.add(triple);
        }
        motifInstance.setTriples(newTriplePatterns);
    }


    private Set<String> getVariablesInCbgp(BasicGraphPattern cbgp) {
        Set<String> variablesInCbgp = new HashSet<>();

        Set<Triple> triplePatterns = cbgp.getTriplePatterns();
        for (Triple triplePattern : triplePatterns) {
            if (triplePattern.getSubject().isVariable())
                variablesInCbgp.add(triplePattern.getSubject().toString());
            if (triplePattern.getObject().isVariable())
                variablesInCbgp.add(triplePattern.getObject().toString());
        }
        return variablesInCbgp;
    }

    private Set<String> getVariablesInMotif(Set<base.domain.Triple> motifTriples) {
        Set<String> variablesInMotif = new HashSet<>();

        for (base.domain.Triple triplePattern : motifTriples) {
            if (UtilsJena.isVariable(triplePattern.getSubject()))
                variablesInMotif.add(triplePattern.getSubject());
            if (UtilsJena.isVariable(triplePattern.getObject()))
                variablesInMotif.add(triplePattern.getObject());
        }

        return variablesInMotif;
    }


    private Set<Triple> selectBestTriplePatterns(Map<String, List<Triple>> candidateTriplesByDistinguishedVariable, List<BindingWrapper> temporaryTrainingSet) {
        Set<Triple> bestTriplePatterns = new HashSet<>();

        Set<String> distinguishedVariablesKeySet = candidateTriplesByDistinguishedVariable.keySet();
        for (String distinguishedVariable : distinguishedVariablesKeySet) {
            List<Triple> triplePatterns = candidateTriplesByDistinguishedVariable.get(distinguishedVariable);
            double bestInformation = 0.0;
            Triple bestTriple = null;
            for (Triple triple : triplePatterns) {
                BasicGraphPattern bgp = new BasicGraphPattern();
                bgp.setTriplePatterns(Stream.of(triple).collect(Collectors.toCollection(HashSet::new)));
                State state = calculateInformation(bgp, temporaryTrainingSet);

                // If the new triple pattern improves the information, then it's the best one.
                if (state.getInformation() > bestInformation) {
                    bestInformation = state.getInformation();
                    bestTriple = triple;
                }
                // If the information is the same, then we will decide based on some heuristics.
                else if (state.getInformation() == bestInformation){
                    bestTriple = Heuristics.chooseTriplePattern(bestTriplePatterns, triple, bestTriple);
                }
            }
            bestTriplePatterns.add(bestTriple);
        }

        return bestTriplePatterns;
    }

    private State calculateInformation(BasicGraphPattern bgp, List<BindingWrapper> temporaryTrainingSet) {
        // Obtaining the bindings of the BGP
        Map<String, List<String>> bindings = utilsJena.getBindings(bgp);

        // Joining the training set with the bindings coming from the BGP
        for (int j = 0; j < temporaryTrainingSet.size(); j++) {
            BindingWrapper exampleBinding = temporaryTrainingSet.get(j);
            Set<String> keySet = exampleBinding.getBindings().keySet();
            Set<String> distinguishedVariableKeys = keySet.stream().filter(key -> key.contains(Constants.HEAD_VARIABLE_PATTERN)).collect(Collectors.toSet());
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

        List<ExampleWrapper> positiveExampleWrappers = categorizedExamples.get(ExampleWrapper.CATEGORY_POSITIVE);
        List<ExampleWrapper> negativeExampleWrappers = categorizedExamples.get(ExampleWrapper.CATEGORY_NEGATIVE);

        int positiveExamplesCovered = 0, negativeExamplesCovered = 0;
        // Take all the bindings from the training set
        List<String> bindingInstances = new LinkedList<>();
        for (BindingWrapper bindingWrapper : temporaryTrainingSet) {
            bindingInstances.addAll(bindingWrapper.getBindings().values());
        }

        // count how many of the examples are in the bindings of the training set
        if (positiveExamplesCovered < positiveExampleWrappers.size())
            positiveExamplesCovered += positiveExampleWrappers.stream().filter(exampleWrapper -> bindingInstances.contains(exampleWrapper.getExample())).count();
        if ((null != negativeExampleWrappers) && (negativeExamplesCovered < negativeExampleWrappers.size()))
            negativeExamplesCovered += negativeExampleWrappers.stream().filter(exampleWrapper -> bindingInstances.contains(exampleWrapper.getExample())).count();

        double information = (double) positiveExamplesCovered / (positiveExamplesCovered + negativeExamplesCovered);

        return new State(information, bgp, temporaryTrainingSet);
    }


    private Map<String, List<Triple>> groupCandidateTriplesByDistinguishedVariable(Map<ExampleWrapper, Set<ExampleEntry<String, Triple>>> candidateTriples) {
        List<ExampleEntry<String, Triple>> allCandidateTriples = new LinkedList<>();

        for (Map.Entry<ExampleWrapper, Set<ExampleEntry<String, Triple>>> entry : candidateTriples.entrySet()) {
            allCandidateTriples.addAll(entry.getValue());
        }

        List<ExampleEntry<String, Triple>> candidateTriplesWithDistinguishedVariables = allCandidateTriples.stream()
                .filter(t -> t.getValue().getObject().toString().contains(Constants.HEAD_VARIABLE_PATTERN))
                .collect(Collectors.toList());

        Map<String, List<Triple>> candidateTriplesByDistinguishedVariable = new HashMap<>();
        for (ExampleEntry<String, Triple> entry : candidateTriplesWithDistinguishedVariables) {
            Triple triple = entry.getValue();

            String key = triple.getObject().toString();
            if (candidateTriplesByDistinguishedVariable.containsKey(key)) {
                List<Triple> triples = new LinkedList<>(candidateTriplesByDistinguishedVariable.get(key));
                triples.add(triple);
                candidateTriplesByDistinguishedVariable.replace(key, triples);
            } else {
                candidateTriplesByDistinguishedVariable.put(key, List.of(triple));
            }
        }
        return candidateTriplesByDistinguishedVariable;
    }

    private Map<ExampleWrapper, Set<ExampleEntry<String, Triple>>> deriveCandidateTriples(Map<Integer, List<ExampleWrapper>> positiveExamplesByComponent, Optional<String> dataset) throws IOException {
        Set<Integer> componentKeys = positiveExamplesByComponent.keySet();

        Map<ExampleWrapper, Set<ExampleEntry<String, Triple>>> candidateTriples = new HashMap<>();
        logger.log(Level.INFO, "Starting to derive candidate triples...");
        for (Integer componentKey : componentKeys) {
            List<ExampleWrapper> componentExampleWrappers = positiveExamplesByComponent.get(componentKey);
            for (ExampleWrapper componentExampleWrapper : componentExampleWrappers) {
                candidateTriples.put(componentExampleWrapper, tripleFinder.deriveCandidateTriples(componentExampleWrapper.getExample(), dataset, 0));
                // this is because there might be examples that are not present on the dataset, so we can't learn anything from them.
                if (candidateTriples.get(componentExampleWrapper).isEmpty())
                    return null;
            }
        }
        logger.log(Level.INFO, "Candidate triples successfully derived.");

        return candidateTriples;
    }

}
