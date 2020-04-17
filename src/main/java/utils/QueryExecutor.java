package utils;

import org.apache.commons.validator.routines.UrlValidator;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RecursiveTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QueryExecutor extends RecursiveTask<Set<Triple>> {
    private final Logger logger = Logger.getLogger(UtilsJena.class.getName());
    public static final byte NONE_SELECTOR = 0;
    private final byte SUBJECT_SELECTOR = 1;
    private final byte PREDICATE_SELECTOR = 2;
    private final byte OBJECT_SELECTOR = 3;

    private String endpoint;
    private String exampleSubjectQuery;
    private String examplePredicateQuery;
    private String exampleObjectQuery;
    private String example;

    private byte selector;

    @Override
    protected Set<Triple> compute() {
        Set<Triple> results = new HashSet<>();
        switch (selector) {
            case NONE_SELECTOR : { break; }
            case SUBJECT_SELECTOR : { return runSubjectQuery(); }
            case PREDICATE_SELECTOR : { return runPredicateQuery(); }
            case OBJECT_SELECTOR : { return runObjectQuery(); }
        }

        QueryExecutor subjectQueryTask = new QueryExecutor(endpoint, exampleSubjectQuery, examplePredicateQuery, exampleObjectQuery, example, SUBJECT_SELECTOR);
        QueryExecutor predicateQueryTask = new QueryExecutor(endpoint, exampleSubjectQuery, examplePredicateQuery, exampleObjectQuery, example, PREDICATE_SELECTOR);
        QueryExecutor objectQueryTask = new QueryExecutor(endpoint, exampleSubjectQuery, examplePredicateQuery, exampleObjectQuery, example, OBJECT_SELECTOR);

        subjectQueryTask.fork();
        predicateQueryTask.fork();

        results = objectQueryTask.compute();

        results.addAll(subjectQueryTask.join());
        results.addAll(predicateQueryTask.join());

        return results;
    }

    public QueryExecutor(String endpoint, String exampleSubjectQuery, String examplePredicateQuery, String exampleObjectQuery, String example, byte selector) {
        this.endpoint = endpoint;
        this.exampleSubjectQuery = exampleSubjectQuery;
        this.examplePredicateQuery = examplePredicateQuery;
        this.exampleObjectQuery = exampleObjectQuery;
        this.example = example;
        this.selector = selector;
    }

    private Set<Triple> runSubjectQuery(){
        Set<Triple> results = new HashSet<>();
        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, exampleSubjectQuery)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution row = rs.next();
                Node subject = NodeFactory.createLiteral(example);
                Node predicate = row.get("?p").asNode();
                Node object = row.get("?o").asNode();
                Triple triple = new Triple(subject, predicate, object);
                results.add(triple);
            }
        } catch (QueryParseException e){
            System.out.println("===============================================");
            logger.log(Level.SEVERE, "Error processing the query: \n" + exampleSubjectQuery + "\n");
            System.out.println("===============================================");
        }
        return results;
    }

    private Set<Triple> runPredicateQuery(){
        Set<Triple> results = new HashSet<>();
        // Only URIs are valid predicates according to the SPARQL specification.
        if (UrlValidator.getInstance().isValid(example)) {
            try (QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, examplePredicateQuery)) {
                ResultSet rs = qexec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution row = rs.next();
                    Node subject = row.get("?s").asNode();
                    Node predicate = NodeFactory.createLiteral(example);
                    Node object = row.get("?o").asNode();
                    Triple triple = new Triple(subject, predicate, object);
                    results.add(triple);
                }
            } catch (QueryParseException e){
                System.out.println("===============================================");
                logger.log(Level.SEVERE, "Error processing the query: \n" + examplePredicateQuery + "\n");
                System.out.println("===============================================");
            }
        }
        return results;
    }

    private Set<Triple> runObjectQuery(){
        Set<Triple> results = new HashSet<>();

        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, exampleObjectQuery)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution row = rs.next();

                Node subject = row.get("?s").asNode();
                Node predicate = row.get("?p").asNode();
                Node object = NodeFactory.createLiteral(example);
                Triple triple = new Triple(subject, predicate, object);
                results.add(triple);
            }
        } catch (QueryParseException e){
            System.out.println("===============================================");
            logger.log(Level.SEVERE, "Error processing the query: \n" + exampleObjectQuery + "\n");
            System.out.println("===============================================");
        }
        return results;
    }
}
