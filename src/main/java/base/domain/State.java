package base.domain;

import java.util.LinkedList;
import java.util.List;

public class State {
    private double information;
    private double coverage;
    private BasicGraphPattern basicGraphPattern;
    private List<BindingWrapper> trainingSet;
    private Motif motifInstance;

    public State(double information, BasicGraphPattern basicGraphPattern, List<BindingWrapper> trainingSet) {
        this.information = information;
        this.basicGraphPattern = basicGraphPattern;
        this.trainingSet = trainingSet;

    }

    public Motif getMotifInstance() {
        return motifInstance;
    }

    public void setMotifInstance(Motif motifInstance) {
        this.motifInstance = motifInstance;
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

    public BasicGraphPattern getBasicGraphPattern() {
        return basicGraphPattern;
    }

    public void setBasicGraphPattern(BasicGraphPattern basicGraphPattern) {
        this.basicGraphPattern = basicGraphPattern;
    }

    public List<BindingWrapper> getTrainingSet() {
        return trainingSet;
    }

    public void setTrainingSet(List<BindingWrapper> trainingSet) {
        this.trainingSet = trainingSet;
    }

}
