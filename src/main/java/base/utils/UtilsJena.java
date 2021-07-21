package base.utils;

import base.domain.BasicGraphPattern;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
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
    @Resource
    private DeriveTriplesQueryExecutor deriveTriplesQueryExecutor;

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

        String exampleObject;

        if (dataset.isPresent()) {
            exampleObject = "SELECT ?s ?p FROM " + dataset.get() + " WHERE { ?s ?p ?o . FILTER regex(?o, " + example + ") } LIMIT " + limit + " OFFSET " + offset;
        } else {
            exampleObject = "SELECT ?s ?p WHERE { ?s ?p ?o . FILTER regex(?o, " + example + ") } LIMIT " + limit + " OFFSET " + offset;
        }

        return deriveTriplesQueryExecutor.runObjectQuery(endpoint, exampleObject, example);
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

    public static String getCanonicalString(String inputString){
        inputString = inputString.replaceAll("<","");
        inputString = inputString.replaceAll(">","");
        inputString = inputString.replaceAll("'", "");
        inputString = inputString.replaceAll("\"", "");

        return inputString;
    }

    public Set<List<String>> getSubjectBindings(BasicGraphPattern bgp) {
        String query = buildSubjectSelectQuery(bgp);
        Set<List<String>> bindings = runQuery(query);

        return bindings;
    }

    private String buildSubjectSelectQuery(BasicGraphPattern bgp){
        Set<String> variables = new HashSet<>();
        for (Triple triple : bgp.getTriplePatterns()) {
            variables.add(triple.getSubject().toString());
        }

        StringBuilder builder = new StringBuilder();
        builder.append("SELECT ");

        for (String variable : variables) {
            builder.append(variable + " ");
        }

        builder.append("WHERE { ");
        for (Triple triple : bgp.getTriplePatterns()) {
            builder.append(getSparqlCompatibleTriple(triple) + " .");
        }

        builder.append(" }");

        return builder.toString();
    }

    public Set<List<String>> getTriplesWithSubject(String subject) {
        StringBuilder builder = new StringBuilder();
        builder.append("SELECT * ");
        builder.append("WHERE { ");

        Triple triple = new Triple(NodeFactory.createURI(subject), NodeFactory.createVariable("p"), NodeFactory.createVariable("o"));
        builder.append(getSparqlCompatibleTriple(triple) + " .");
        builder.append(" }");
        String query = builder.toString();

        return runQuery(query);
    }

    public static String removeLanguageAnnotation(String annotatedString){
        if (annotatedString.contains("@"))
            return annotatedString.substring(0, annotatedString.indexOf("@"));
        return annotatedString;
    }
}
