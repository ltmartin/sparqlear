package base.domain;

import org.apache.jena.graph.Triple;

public class Hyperedge {
    private Triple triple;
    private float informationGain;

    public Hyperedge(Triple triple, float informationGain) {
        this.triple = triple;
        this.informationGain = informationGain;
    }

    public Triple getTriple() {
        return triple;
    }

    public void setTriple(Triple triple) {
        this.triple = triple;
    }

    public float getInformationGain() {
        return informationGain;
    }

    public void setInformationGain(float informationGain) {
        this.informationGain = informationGain;
    }
}
