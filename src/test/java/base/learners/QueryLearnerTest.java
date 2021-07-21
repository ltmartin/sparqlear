package base.learners;

import base.Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.text.ParseException;
import java.util.Optional;
import java.util.Set;

@SpringBootTest(classes = Application.class)
class QueryLearnerTest {
    @Value("${sparqlear.sparql.endpoint}")
    private String endpoint;
    @Value("${sparqlear.sparql.candidateTriples.limit}")
    private int limit;
    @Autowired
    private TripleFinder tripleFinder;
    @Autowired
    private QueryLearner queryLearner;


    @Test
    void allPositiveSingleVariableExamples() {
        String examples = "+Rafi +Sergey +Igniacio";
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
    void positiveAndNegativeSingleVariableExamples() {
        String examples = "+Rafi +Sergey +Igniacio -Italian";
        try {
            Optional<Set<String>> learnedQueries = queryLearner.learn(examples);
            System.out.println("===================================================");
            System.out.println("Result: ");
            if (!learnedQueries.isPresent())
                System.out.println("Nothing learned");
            else
                learnedQueries.stream().forEach(System.out::println);
            System.out.println("===================================================");
        } catch (ParseException | IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void positiveMultipleVariableExamples() {
        String examples = "+(Igniacio, 1908-10-03) +(Rafi, 1903-07-01)";
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
    void positiveAndNegativeMultipleVariableExamples() {
        String examples = "+(Igniacio, 1908-10-03) +(Rafi, 1903-07-01) -(plane, albert)";
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