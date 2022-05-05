package base.domain;

import java.util.List;

public class State implements Comparable<State> {
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

    @Override
    public int compareTo(State state) {
        if (((this.getInformation() != 0) || (this.getCoverage() != 0)) && ((state.getInformation() != 0) || (state.getCoverage() != 0))){
            double f1_state1 = computeF1(this);
            double f1_state2 = computeF1(state);
            return Double.compare(f1_state1, f1_state2) * -1;
        } else if ((this.getInformation() != 0) || (state.getInformation() != 0)){
            return Double.compare(this.getInformation(), state.getInformation()) * -1;
        } else
            return Double.compare(this.getCoverage(), state.getCoverage()) * -1;
    }

    private double computeF1(State state) {
        return 2 * ((state.getInformation() * state.getCoverage()) / (state.getInformation() + state.getCoverage()));
    }
}
