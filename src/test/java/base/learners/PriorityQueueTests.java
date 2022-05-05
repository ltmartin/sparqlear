package base.learners;

import base.Application;
import base.domain.State;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.PriorityQueue;


public class PriorityQueueTests {

    @Test
    public void test(){
        State state1 = new State(0, null, null);
        state1.setCoverage(0);
        State state2 = new State(1, null, null);
        state2.setCoverage(1);
        State state3 = new State(0.5, null, null);
        state3.setCoverage(0);

        PriorityQueue<State> states = new PriorityQueue<>();
        states.add(state1);
        states.add(state2);
        states.add(state3);
        while (!states.isEmpty()){
            State s = states.remove();
            System.out.println(s.getInformation() + " " + s.getCoverage());
        }
    }
}
