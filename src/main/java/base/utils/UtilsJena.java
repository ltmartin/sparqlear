package base.utils;

import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

/**
 * @author Leandro Tabares Martín
 *
 * */
@Component
@Lazy
public class UtilsJena {

    /**
     * Method to derive the triples directly related with a example.
     *
     * @param example   The example to derive the triples where it appears.
     * @param endpoint  The SPARQL endpoint where to run the query.
     * @param dataset   (Optional) In the case that the SPARQL endpoint host more that one dataset, it is possible to specify over which dataset the query must run.
     * @param limit Specifies a limit of triples to be retrieved according to the role perfomed by the example (subject, predicate or object).
     * @return Set<Triple> containing the derived triples.
     */
    public Set deriveTriples(String example, String endpoint, Optional<String> dataset, int limit) throws IOException {
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

        QueryExecutor executorTask = new QueryExecutor(endpoint, exampleSubject, examplePredicate, exampleObject, example, QueryExecutor.NONE_SELECTOR);

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

}
