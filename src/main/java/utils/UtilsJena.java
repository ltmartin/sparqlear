package utils;

import org.apache.commons.validator.routines.UrlValidator;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;

import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class UtilsJena {
    public Set deriveTriples(String example, String endpoint, Optional<String> dataset, int threshold) throws IOException {
        if (!UrlValidator.getInstance().isValid(endpoint))
            throw new IOException("Invalid endpoint");

        Set<Triple> results = new HashSet<>();

        // If the example is an URI it should be between <> or between quotes otherwise.
        example = UrlValidator.getInstance().isValid(example)? "<" + example + ">" : "'" + example + "'";

        String exampleSubject = null, examplePredicate = null, exampleObject = null;

        if (dataset.isPresent()) {
            exampleSubject = "SELECT ?p ?o FROM " + dataset.get() + " WHERE {" + example + " ?p ?o .} LIMIT " + threshold;

            // Only URIs are valid predicates according to the SPARQL specification.
            if (UrlValidator.getInstance().isValid(example))
                examplePredicate = "SELECT ?s ?o FROM " + dataset.get() + " WHERE { ?s " + example + " ?o .} LIMIT " + threshold;

            exampleObject = "SELECT ?s ?p FROM " + dataset.get() + " WHERE { ?s ?p " + example + " .} LIMIT " + threshold;
        }else {
            exampleSubject = "SELECT ?p ?o WHERE {" + example + " ?p ?o .} LIMIT " + threshold;

            // Only URIs are valid predicates according to the SPARQL specification.
            if (UrlValidator.getInstance().isValid(example))
                examplePredicate = "SELECT ?s ?o WHERE { ?s " + example + " ?o .} LIMIT " + threshold;

            exampleObject = "SELECT ?s ?p WHERE { ?s ?p " + example + " .} LIMIT " + threshold;
        }

        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, exampleSubject)){
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution row = rs.next();
                Node subject = NodeFactory.createLiteral(example);
                Node predicate = NodeFactory.createLiteral(row.getResource("?p").toString());
                Node object = NodeFactory.createLiteral(row.getResource("?o").toString());
                Triple triple = new Triple(subject,predicate,object);
                results.add(triple);
            }
        }

        // Only URIs are valid predicates according to the SPARQL specification.
        if (UrlValidator.getInstance().isValid(example)) {
            try (QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, examplePredicate)) {
                ResultSet rs = qexec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution row = rs.next();
                    Node subject = NodeFactory.createLiteral(row.getResource("?s").toString());
                    Node predicate = NodeFactory.createLiteral(example);
                    Node object = NodeFactory.createLiteral(row.getResource("?o").toString());
                    Triple triple = new Triple(subject, predicate, object);
                    results.add(triple);
                }
            }
        }

        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, exampleObject)){
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution row = rs.next();
                Node subject = NodeFactory.createLiteral(row.getResource("?s").toString());
                Node predicate = NodeFactory.createLiteral(row.getResource("?p").toString());
                Node object = NodeFactory.createLiteral(example);
                Triple triple = new Triple(subject,predicate,object);
                results.add(triple);
            }
        }

        return results;
    }

}
