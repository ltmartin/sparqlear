package base.utils;

import base.domain.BasicGraphPattern;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.*;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public Set<Triple> deriveTriples(String example, Optional<String> dataset, int limit, int offset) throws IOException {
        /*if (!UrlValidator.getInstance().isValid(endpoint))
            throw new IOException("Invalid endpoint");*/

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
        example = example.replaceAll("\\\\", "");

        return example;
    }

    public static String getSparqlCompatibleExample(String example) {
        return UrlValidator.getInstance().isValid(example) ? "<" + example + ">" : example.contains("\"")? example : "'" + example + "'";
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
        if (element.contains(HTTP_PREFIX) && !(element.startsWith("<"))){
            StringBuilder stringBuilder = new StringBuilder(element);
            stringBuilder.insert(stringBuilder.indexOf(HTTP_PREFIX),"<");
            stringBuilder.append(">");
            element = stringBuilder.toString();
        } else if (element.contains(HTTPS_PREFIX) && !(element.startsWith("<"))){
            StringBuilder stringBuilder = new StringBuilder(element);
            stringBuilder.insert(stringBuilder.indexOf(HTTPS_PREFIX),"<");
            stringBuilder.append(">");
            element = stringBuilder.toString();
        }
        return element;
    }


    public Set<List<String>> runQuery(String query) {
        Set<List<String>> results = new HashSet<>();

        try (QueryExecution qexec = QueryExecution.service(endpoint).query(query).timeout(timeout, TimeUnit.SECONDS).build()) {
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
            logger.log(Level.INFO, "Query timed out: \n" + query + "\n");
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
        String query = buildSelectQuery(bgp);
        return runQuery(query);
    }

    public Map<String, List<String>> getBindingsValues(BasicGraphPattern bgp, Map<String, List<String>> values){
        String query = buildSelectQueryValues(bgp, values);

        Map<String, List<String>> results = new HashMap<>();

        try (QueryExecution qexec = QueryExecution.service(endpoint).query(query).timeout(timeout, TimeUnit.SECONDS).build()) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                Binding binding = rs.nextBinding();
                Iterator<Var> varIterator = binding.vars();
                while (varIterator.hasNext()){
                    Var variable = varIterator.next();
                    if (!results.containsKey(variable.toString())){
                        String variableBinding = binding.get(variable).toString();
                        results.put(variable.toString(), Stream.of(variableBinding)
                                        .map(b -> b.replaceAll("\"", ""))
                                        .collect(Collectors.toList()));
                    } else {
                        List<String> variableBindings = results.get(variable.toString());
                        variableBindings.add(binding.get(variable).toString().replaceAll("\"", ""));
                        results.replace(variable.toString(), variableBindings);
                    }
                }
            }
        } catch (QueryParseException e) {
            System.out.println("===============================================");
            logger.log(Level.SEVERE, "Error processing the bindings of the query: \n" + query + "\n");
            System.out.println("===============================================");
            throw e;
        } catch (Exception e) {
            System.out.println("===============================================");
            logger.log(Level.INFO, "Query timed out: \n" + query + "\n");
            System.out.println("===============================================");
        }

        return results;
    }

    private String buildSelectQuery(BasicGraphPattern bgp){
        Set<String> variables = new HashSet<>();
        for (Triple triple : bgp.getTriplePatterns()) {
            String tripleSubject = triple.getSubject().toString();
            String tripleObject = triple.getObject().toString();

            if (tripleSubject.contains("?"+ Constants.HEAD_VARIABLE_PATTERN) || tripleSubject.contains("?"+ Constants.EXISTENTIAL_VARIABLE_PATTERN))
                variables.add(tripleSubject);
            if (tripleObject.contains("?"+ Constants.HEAD_VARIABLE_PATTERN) || tripleObject.contains("?"+ Constants.EXISTENTIAL_VARIABLE_PATTERN))
                variables.add(tripleObject);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("SELECT ");

        for (String variable : variables) {
            builder.append("(str(" + variable + ") as " + variable + Constants.PROJECTION_PATTERN + ") ");
        }

        builder.append("WHERE { ");
        for (Triple triple : bgp.getTriplePatterns()) {
            builder.append(getSparqlCompatibleTriple(triple) + " .");
        }

        builder.append(" }");

        return builder.toString();
    }

    private String buildSelectQueryValues(BasicGraphPattern bgp, Map<String, List<String>> values){
        Set<String> variables = new HashSet<>();
        for (Triple triple : bgp.getTriplePatterns()) {
            String tripleSubject = triple.getSubject().toString();
            String tripleObject = triple.getObject().toString();

            if (tripleSubject.contains("?"+ Constants.HEAD_VARIABLE_PATTERN) || tripleSubject.contains("?"+ Constants.EXISTENTIAL_VARIABLE_PATTERN))
                variables.add(tripleSubject);
            if (tripleObject.contains("?"+ Constants.HEAD_VARIABLE_PATTERN) || tripleObject.contains("?"+ Constants.EXISTENTIAL_VARIABLE_PATTERN))
                variables.add(tripleObject);
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

        builder.append(" VALUES (");

        List<Tuple<String>> tuples = new LinkedList<>();

        for (Map.Entry<String, List<String>> entry : values.entrySet())
            if (variables.contains(entry.getKey())) {
                builder.append(entry.getKey() + " ");
                List<String> elements = entry.getValue();
                if (tuples.isEmpty())
                    elements.forEach( e -> tuples.add(new Tuple<>(e)));
                else {
                    int entryIndex = 0;
                    for (String e : elements) {
                        tuples.get(entryIndex++).addItem(e);
                    }
                }
            }

        builder.append(") { ");

        for (Tuple t : tuples) {
            builder.append(t.toString() + " ");
        }

        builder.append("}");
        return builder.toString();
    }

    public Set<List<String>> getObjectsOfTriplesWithSubject(String subject) {
        StringBuilder builder = new StringBuilder();
        builder.append("SELECT ?o ");
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

    public static boolean isVariable(String element){
        return element.startsWith("?");
    }

    public static Set<Triple> convertDomainTriplesToJenaTriples(Set<base.domain.Triple> domainTriples){
        Set<Triple> triples = new HashSet<>();
        for (base.domain.Triple domainTriple : domainTriples) {
            Node subject, object;
            if (UtilsJena.isVariable(domainTriple.getSubject())){
                subject = NodeFactory.createVariable(domainTriple.getSubject().substring(1));
            } else
                try {
                    String subjectString = domainTriple.getSubject();
                    new URL(getCanonicalString(subjectString));
                    subject = NodeFactory.createURI(subjectString);
                } catch (MalformedURLException e) {
                    subject = NodeFactory.createLiteral(domainTriple.getSubject());
                }

            if (UtilsJena.isVariable(domainTriple.getObject())){
                object = NodeFactory.createVariable(domainTriple.getObject().substring(1));
            } else try {
                String objectString = domainTriple.getObject();
                new URL(getCanonicalString(objectString));
                object = NodeFactory.createURI(objectString);
            } catch (MalformedURLException e) {
                object = NodeFactory.createLiteral(domainTriple.getObject());
            }

            triples.add(new Triple(subject, NodeFactory.createURI(domainTriple.getPredicate()), object));
        }
        return triples;
    }

    public static Set<base.domain.Triple> convertJenaTriplesToDomainTriples(Set<Triple> jenaTriples){
        Set<base.domain.Triple> triples = new HashSet<>();

        for (Triple triple : jenaTriples) {
            triples.add(new base.domain.Triple(triple.getSubject().toString(), triple.getPredicate().toString(), triple.getObject().toString()));
        }

        return triples;
    }
}
