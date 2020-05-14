package base.utils;

import base.domain.Example;
import base.domain.ExampleEntry;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Leandro Tabares Martín
 *
 * */
@Component
@Lazy
public class UtilsJena {
    private final Logger logger = Logger.getLogger(UtilsJena.class.getName());

    @Value("${sparqlear.sparql.endpoint}")
    private String endpoint;

    /**
     * Method to derive the triples directly related with a example.
     *
     * @param example   The example to derive the triples where it appears.
     * @param dataset   (Optional) In the case that the SPARQL endpoint host more that one dataset, it is possible to specify over which dataset the query must run.
     * @param limit Specifies a limit of triples to be retrieved according to the role perfomed by the example (subject, predicate or object).
     * @return Set<Triple> containing the derived triples.
     */
    public Set deriveTriples(String example, Optional<String> dataset, int limit) throws IOException {
        if (!UrlValidator.getInstance().isValid(endpoint))
            throw new IOException("Invalid endpoint");

        // If the example is an URI it should be between <> or between quotes otherwise.
        example = getSparqlCompatibleExample(example);

        String exampleSubject = null, examplePredicate = null, exampleObject = null;

        if (dataset.isPresent()) {
            exampleSubject = "SELECT ?p ?o FROM " + dataset.get() + " WHERE {" + example + " ?p ?o .} LIMIT " + limit;

            // Only URIs are valid predicates according to the SPARQL specification.
            if (UrlValidator.getInstance().isValid(example))
                examplePredicate = "SELECT ?s ?o FROM " + dataset.get() + " WHERE { ?s " + example + " ?o .} LIMIT " + limit;

            exampleObject = "SELECT ?s ?p FROM " + dataset.get() + " WHERE { ?s ?p " + example + " .} LIMIT " + limit;
        } else {
            exampleSubject = "SELECT ?p ?o WHERE {" + example + " ?p ?o .} LIMIT " + limit;

            // Only URIs are valid predicates according to the SPARQL specification.
            if (UrlValidator.getInstance().isValid(UtilsJena.getCanonicalExample(example)))
                examplePredicate = "SELECT ?s ?o WHERE { ?s " + example + " ?o .} LIMIT " + limit;

            exampleObject = "SELECT ?s ?p WHERE { ?s ?p " + example + " .} LIMIT " + limit;
        }

        DeriveTriplesQueryExecutor executorTask = new DeriveTriplesQueryExecutor(endpoint, exampleSubject, examplePredicate, exampleObject, example, DeriveTriplesQueryExecutor.NONE_SELECTOR);

        return new ForkJoinPool().invoke(executorTask);
    }

    public static String getCanonicalExample(String example){
        example = example.replaceAll("<","");
        example = example.replaceAll(">","");
        example = example.replaceAll("'","");
        example = example.replaceAll("\"","");

        return example;
    }

    public static String getSparqlCompatibleExample(String example){
        return UrlValidator.getInstance().isValid(example) ? "<" + example + ">" : "'" + example + "'";
    }

    public Set runCompleteQueryForHyperedges(Set<ExampleEntry<String, Triple>> componentCandidateTriples, Set<Example> parsedExamples){
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

        Set<List<String>> results = new HashSet<>();

        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, query)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution row = rs.next();
                Iterator<String> variableNamesIterator = row.varNames();
                List<String> rowValues = new LinkedList<>();
                while (variableNamesIterator.hasNext()){
                    String variableName = variableNamesIterator.next();
                    String value = row.get(variableName).toString();
                    rowValues.add(value);
                }
            }
        } catch (QueryParseException e){
            System.out.println("===============================================");
            logger.log(Level.SEVERE, "Error processing the query: \n" + query + "\n");
            System.out.println("===============================================");
        }

        return results;
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
