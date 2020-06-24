package base.utils;

import base.domain.Example;
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
import java.util.concurrent.TimeUnit;
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
    public static final String SELECTED_VARIABLE_PATTERN = "sv";

    @Value("${sparqlear.sparql.endpoint}")
    private String endpoint;
    @Value("${sparqlear.sparql.timeout}")
    private Integer timeout;

    /**
     * Method to derive the triples directly related with a example.
     *
     * @param example The example to derive the triples where it appears.
     * @param dataset (Optional) In the case that the SPARQL endpoint host more that one dataset, it is possible to specify over which dataset the query must run.
     * @param limit   Specifies a limit of triples to be retrieved according to the role perfomed by the example (subject, predicate or object).
     * @return Set<Triple> containing the derived triples.
     */
    public Set deriveTriples(String example, Optional<String> dataset, int limit, int offset) throws IOException {
        if (!UrlValidator.getInstance().isValid(endpoint))
            throw new IOException("Invalid endpoint");

        // If the example is an URI it should be between <> or between quotes otherwise.
        example = getSparqlCompatibleExample(example);

        String exampleSubject = null, examplePredicate = null, exampleObject = null;

        if (dataset.isPresent()) {
            exampleSubject = "SELECT ?p ?o FROM " + dataset.get() + " WHERE {" + example + " ?p ?o .} LIMIT " + limit + " OFFSET " + offset;

            // Only URIs are valid predicates according to the SPARQL specification.
            if (UrlValidator.getInstance().isValid(example))
                examplePredicate = "SELECT ?s ?o FROM " + dataset.get() + " WHERE { ?s " + example + " ?o .} LIMIT " + limit + " OFFSET " + offset;

            exampleObject = "SELECT ?s ?p FROM " + dataset.get() + " WHERE { ?s ?p " + example + " .} LIMIT " + limit + " OFFSET " + offset;
        } else {
            exampleSubject = "SELECT ?p ?o WHERE {" + example + " ?p ?o .} LIMIT " + limit + " OFFSET " + offset;

            // Only URIs are valid predicates according to the SPARQL specification.
            if (UrlValidator.getInstance().isValid(UtilsJena.getCanonicalExample(example)))
                examplePredicate = "SELECT ?s ?o WHERE { ?s " + example + " ?o .} LIMIT " + limit + " OFFSET " + offset;

            exampleObject = "SELECT ?s ?p WHERE { ?s ?p " + example + " .} LIMIT " + limit + " OFFSET " + offset;
        }

        DeriveTriplesQueryExecutor executorTask = new DeriveTriplesQueryExecutor(endpoint, exampleSubject, examplePredicate, exampleObject, example, DeriveTriplesQueryExecutor.NONE_SELECTOR);

        return new ForkJoinPool().invoke(executorTask);
    }

    public static String getCanonicalExample(String example){
        example = example.replaceAll("<","");
        example = example.replaceAll(">","");
        example = example.replaceAll("'", "");
        example = example.replaceAll("\"", "");

        return example;
    }

    public static String getSparqlCompatibleExample(String example) {
        return UrlValidator.getInstance().isValid(example) ? "<" + example + ">" : "'" + example + "'";
    }


    public Map<Boolean, Integer> verifyBasicGraphPattern(Set<Triple> triples, Map<Boolean, List<Example>> categorizedExamples) {
        Map<Boolean, Integer> results = new HashMap<>();
        results.put(Example.CATEGORY_POSITIVE, 0);
        results.put(Example.CATEGORY_NEGATIVE, 0);

        if (null != categorizedExamples.get(Example.CATEGORY_POSITIVE)) {
            for (Example example : categorizedExamples.get(Example.CATEGORY_POSITIVE)) {
                String query = constructAskQuery(triples, example);
                if ((query.contains(example.getExample())) && (runAskQuery(query))) {
                    Integer value = results.get(Example.CATEGORY_POSITIVE);
                    results.replace(Example.CATEGORY_POSITIVE, ++value);
                }
            }
        }

        if (null != categorizedExamples.get(Example.CATEGORY_NEGATIVE)) {
            for (Example example : categorizedExamples.get(Example.CATEGORY_NEGATIVE)) {
                String query = constructAskQuery(triples, example);
                if ((query.contains(example.getExample())) && (runAskQuery(query))) {
                    Integer value = results.get(Example.CATEGORY_NEGATIVE);
                    results.replace(Example.CATEGORY_NEGATIVE, ++value);
                }
            }
        }

        return results;
    }

    private String constructAskQuery(Set<Triple> triples, Example example) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("ASK ");
        stringBuilder.append("WHERE { ");
        for (Triple triple : triples) {
            if (triple.getSubject().toString().contains(SELECTED_VARIABLE_PATTERN)) {
                String subjectString = triple.getSubject().toString();
                String selectedVariableIndexString = subjectString.substring(3);
                if (example.getPosition().equals(Integer.valueOf(selectedVariableIndexString))) {
                    Node newSubject = NodeFactory.createLiteral(example.getExample());
                    triple = new Triple(newSubject, triple.getPredicate(), triple.getObject());
                }
            } else if (triple.getObject().toString().contains(SELECTED_VARIABLE_PATTERN)) {
                String objectString = triple.getObject().toString();
                String selectedVariableIndexString = objectString.substring(3);
                if (example.getPosition().equals(Integer.valueOf(selectedVariableIndexString))) {
                    Node newObject = NodeFactory.createLiteral(example.getExample());
                    triple = new Triple(triple.getSubject(), triple.getPredicate(), newObject);
                }
            }

            stringBuilder.append(getSparqlCompatibleTriple(triple) + ". ");
        }
        stringBuilder.append("}");

        return stringBuilder.toString();
    }

    public String getSparqlCompatibleTriple(Triple value) {
        String subject = value.getSubject().toString();
        String predicate = value.getPredicate().toString();
        String object = value.getObject().toString();

        final String HTTP_PREFIX = "http://";
        final String HTTPS_PREFIX = "https://";
        subject = insertBrackets(subject, HTTP_PREFIX, HTTPS_PREFIX);

        predicate = insertBrackets(predicate, HTTP_PREFIX, HTTPS_PREFIX);

        object = insertBrackets(object, HTTP_PREFIX, HTTPS_PREFIX);

        return subject + " " + predicate + " " + object + " ";
    }

    private String insertBrackets(String element, String HTTP_PREFIX, String HTTPS_PREFIX) {
        if (element.contains(HTTP_PREFIX)){
            StringBuilder stringBuilder = new StringBuilder(element);
            stringBuilder.insert(stringBuilder.indexOf(HTTP_PREFIX),"<");
            stringBuilder.append(">");
            element = stringBuilder.toString();
        } else if (element.contains(HTTPS_PREFIX)){
            StringBuilder stringBuilder = new StringBuilder(element);
            stringBuilder.insert(stringBuilder.indexOf(HTTPS_PREFIX),"<");
            stringBuilder.append(">");
            element = stringBuilder.toString();
        }
        return element;
    }


    public boolean runAskQuery(String query) {
        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, query)) {
            return qexec.execAsk();
        }
    }

    public Set<List<String>> runQuery(String query) {
        Set<List<String>> results = new HashSet<>();

        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, query)) {
            qexec.setTimeout(timeout, TimeUnit.MINUTES);
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
                results.add(rowValues);
            }
        } catch (QueryParseException e) {
            System.out.println("===============================================");
            logger.log(Level.SEVERE, "Error processing the query: \n" + query + "\n");
            System.out.println("===============================================");
        } catch (Exception e) {
            System.out.println("===============================================");
            logger.log(Level.SEVERE, "It has occurred an external error processing the query: \n" + query + "\n");
            System.out.println("===============================================");
        }

        return results;
    }
}
