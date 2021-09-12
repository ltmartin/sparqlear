package base.domain;

import java.util.List;

public class State {
    private double information;
    private double coverage;
    private BasicGraphPattern basicGraphPattern;
    private List<BindingWrapper> trainingSet;

    public State(double information, double coverage, BasicGraphPattern basicGraphPattern, List<BindingWrapper> trainingSet) {
        this.information = information;
        this.coverage = coverage;
        this.basicGraphPattern = basicGraphPattern;
        this.trainingSet = trainingSet;
    }

    public State(double information, BasicGraphPattern basicGraphPattern, List<BindingWrapper> trainingSet) {
        this.information = information;
        this.basicGraphPattern = basicGraphPattern;
        this.trainingSet = trainingSet;
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
