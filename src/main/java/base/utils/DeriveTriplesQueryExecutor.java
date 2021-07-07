package base.utils;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
@Lazy
public class DeriveTriplesQueryExecutor {
    private final Logger logger = Logger.getLogger(DeriveTriplesQueryExecutor.class.getName());

    public Set<Triple> runObjectQuery(String endpoint, String exampleObjectQuery, String example){
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
