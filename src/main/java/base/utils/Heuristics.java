package base.utils;

import base.domain.BasicGraphPattern;
import base.domain.State;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;

import java.util.Set;

public class Heuristics {
    public static Triple chooseTriplePattern(Set<Triple> bestTriplePatterns, Triple triple, Triple bestTriple) {
        for (Triple t : bestTriplePatterns) {
            if (t.getSubject().equals(bestTriple.getSubject())){
                return bestTriple;
            }
        }

        for (Triple t : bestTriplePatterns) {
            if (t.getSubject().equals(triple.getSubject()))
                return triple;
        }
        return bestTriple;
    }
}
