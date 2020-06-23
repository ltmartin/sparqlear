package base.domain;

import org.apache.jena.graph.Triple;

import java.util.HashSet;
import java.util.Set;

public class BasicGraphPattern {
    private Set<Triple> triples;
    private double informationGain;

    public BasicGraphPattern() {
        triples = new HashSet<>();
        informationGain = 0.0;
    }

    public Set<Triple> getTriples() {
        return triples;
    }

    public void setTriples(Set<Triple> triples) {
        this.triples = triples;
    }

    public double getInformationGain() {
        return informationGain;
    }

    public void setInformationGain(double informationGain) {
        this.informationGain = informationGain;
    }
}
