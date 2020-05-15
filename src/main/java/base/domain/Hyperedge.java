package base.domain;

import org.apache.jena.graph.Triple;

public class Hyperedge {
    private Triple triple;
    private double informationGain;

    public Hyperedge(Triple triple, double informationGain) {
        this.triple = triple;
        this.informationGain = informationGain;
    }

    public Triple getTriple() {
        return triple;
    }

    public void setTriple(Triple triple) {
        this.triple = triple;
    }

    public double getInformationGain() {
        return informationGain;
    }

    public void setInformationGain(double informationGain) {
        this.informationGain = informationGain;
    }
}
