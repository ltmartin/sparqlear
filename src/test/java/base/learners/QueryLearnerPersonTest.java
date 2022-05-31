package base.learners;

import base.Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.text.ParseException;
import java.util.Optional;
import java.util.Set;

@SpringBootTest(classes = Application.class)
public class QueryLearnerPersonTest {

    @Autowired
    private QueryLearner queryLearner;

    @Test
    void peopleWhoKnowsSomeoneThatKnowsPerson2() {
        String examples = "+Person10 +Person11 +Person14 -Person16 -Person18 -Person17 -Person19 -Person20";
        try {
            Optional<Set<String>> learnedQueries = queryLearner.learn(examples);
            System.out.println("===================================================");
            System.out.println("Result: ");
            if (!learnedQueries.isPresent())
                System.out.println("Nothing learned.");
            else
                learnedQueries.stream().forEach(System.out::println);
            System.out.println("===================================================");
        } catch (ParseException | IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void peopleWhoKnowsSomeoneWhoKnowsSomeoneThatKnowsPerson1() {
        String examples = "+Person9 +Person10 +Person11 +Person12 +Person13 +Person14 +Person16 +Person18 -Person4 -Person2 -Person3";
        try {
            Optional<Set<String>> learnedQueries = queryLearner.learn(examples);
            System.out.println("===================================================");
            System.out.println("Result: ");
            if (!learnedQueries.isPresent())
                System.out.println("Nothing learned.");
            else
                learnedQueries.stream().forEach(System.out::println);
            System.out.println("===================================================");
        } catch (ParseException | IOException e) {
            e.printStackTrace();
        }
    }
}
