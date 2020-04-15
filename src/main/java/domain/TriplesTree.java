package domain;

import org.apache.jena.graph.Triple;

import java.util.List;

/**
 * @author Leandro Tabares Martín
 *
 */
public class TriplesTree {
    private String example;
    private List<Triple> directTriples;
    private List<TriplesTree> children;

    public TriplesTree(String example) {
        this.example = example;
    }

    public TriplesTree(String example, List<Triple> directTriples) {
        this.example = example;
        this.directTriples = directTriples;
    }

    public TriplesTree(String example, List<Triple> directTriples, List<TriplesTree> children) {
        this.example = example;
        this.directTriples = directTriples;
        this.children = children;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    public List<Triple> getDirectTriples() {
        return directTriples;
    }

    public void setDirectTriples(List<Triple> directTriples) {
        this.directTriples = directTriples;
    }

    public List<TriplesTree> getChildren() {
        return children;
    }

    public void setChildren(List<TriplesTree> children) {
        this.children = children;
    }
}
