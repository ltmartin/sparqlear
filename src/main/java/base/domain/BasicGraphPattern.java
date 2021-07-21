package base.domain;

import org.apache.jena.graph.Triple;

import java.util.HashSet;
import java.util.Set;

public class BasicGraphPattern {
    private Set<Triple> triplePatterns;
    private double information;
    private double coverage;

    public BasicGraphPattern() {
        triplePatterns = new HashSet<>();
        information = 0.0;
        coverage = 0.0;
    }

    public Set<Triple> getTriplePatterns() {
        return triplePatterns;
    }

    public void setTriplePatterns(Set<Triple> triplePatterns) {
        this.triplePatterns = triplePatterns;
    }

    public double getInformation() {
        return information;
    }

    public void setInformation(double information) {
        this.information = information;
    }

    public double getCoverage() {
        return coverage;
    }

    public void setCoverage(double coverage) {
        this.coverage = coverage;
    }
}
