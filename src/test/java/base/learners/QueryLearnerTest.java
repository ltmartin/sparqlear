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
        //String examples = "+Cuba +Venezuela +Colombia";
        String examples = "+Alan +Albert +Joseph";
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
        String examples = "+Cuba +Venezuela +Colombia -Malta -Love";
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
        //String examples = "+<Fidel, Cuba, male> +<Leonardo, Italy, male>";
        String examples = "+<Newton, England> +<Faraday, England>";
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
        String examples = "+<Fidel, Cuba, male> +<Leonardo, Italy, male> -<blue, plane, tree>";
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