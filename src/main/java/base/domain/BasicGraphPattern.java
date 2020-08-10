package base.domain;

import org.apache.jena.graph.Triple;

import java.util.HashSet;
import java.util.Set;

public class BasicGraphPattern {
    private Set<Triple> triples;
    private double information;

    public BasicGraphPattern() {
        triples = new HashSet<>();
        information = 0.0;
    }

    public Set<Triple> getTriples() {
        return triples;
    }

    public void setTriples(Set<Triple> triples) {
        this.triples = triples;
    }

    public double getInformation() {
        return information;
    }

    public void setInformation(double information) {
        this.information = information;
    }
}
