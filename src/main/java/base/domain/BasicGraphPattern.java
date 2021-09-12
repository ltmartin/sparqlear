package base.domain;

import org.apache.jena.graph.Triple;

import java.util.HashSet;
import java.util.Set;

public class BasicGraphPattern implements Cloneable {
    private Set<Triple> triplePatterns;

    public BasicGraphPattern() {
        triplePatterns = new HashSet<>();
    }

    public Set<Triple> getTriplePatterns() {
        return triplePatterns;
    }

    public void setTriplePatterns(Set<Triple> triplePatterns) {
        this.triplePatterns = triplePatterns;
    }

    public BasicGraphPattern clone(){
        BasicGraphPattern cloned = new BasicGraphPattern();
        cloned.setTriplePatterns(new HashSet<>(triplePatterns));
        return cloned;
    }
}
