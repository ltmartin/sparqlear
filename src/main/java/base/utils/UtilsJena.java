package base.utils;

import base.domain.Example;
import base.domain.ExampleEntry;
import org.apache.commons.validator.routines.UrlValidator;
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
    @Value("${sparqlear.sparql.results.limit}")
    private String resultsLimit;
    @Value("${sparqlear.sparql.timeout}")
    private Integer timeout;

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

    public Set<List<String>> runCompleteQueryForHyperedges(Set<ExampleEntry<String, Triple>> componentCandidateTriples, Set<Example> parsedExamples, int selectedVariablesAmount){
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("SELECT ");
        for (int i = 0; i < selectedVariablesAmount; i++)
            stringBuilder.append("?" + SELECTED_VARIABLE_PATTERN + i + ", ");
        stringBuilder.deleteCharAt(stringBuilder.lastIndexOf(","));
        stringBuilder.append("WHERE { ");
        for (ExampleEntry<String, Triple> cct: componentCandidateTriples)
            stringBuilder.append(getSparqlCompatibleTriple(cct.getValue()) + ". ");
        stringBuilder.append("} LIMIT ");
        stringBuilder.append(resultsLimit);

        String query = stringBuilder.toString();

        return runQuery(query);
    }

    private String getSparqlCompatibleTriple(Triple value) {
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


    public Set<List<String>> runPartialQueryForHyperedges(Set<ExampleEntry<String, Triple>> componentCandidateTriples, Set<Example> parsedExamples, ExampleEntry<String, Triple> cct, int selectedVariablesAmount) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("SELECT ");
        for (int i = 0; i < selectedVariablesAmount; i++)
            stringBuilder.append("?" + SELECTED_VARIABLE_PATTERN + i + ", ");
        stringBuilder.deleteCharAt(stringBuilder.lastIndexOf(","));
        stringBuilder.append("WHERE { ");
        for (ExampleEntry<String, Triple> candidateTriple: componentCandidateTriples) {
            if (!cct.equals(candidateTriple))
                stringBuilder.append(getSparqlCompatibleTriple(candidateTriple.getValue()) + ". ");
        }
        stringBuilder.append("} LIMIT ");
        stringBuilder.append(resultsLimit);

        String query = stringBuilder.toString();

        return runQuery(query);

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
        } catch (QueryParseException e){
            System.out.println("===============================================");
            logger.log(Level.SEVERE, "Error processing the query: \n" + query + "\n");
            System.out.println("===============================================");
        }

        return results;
    }
}
