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
class QueryLearnerTest {
    @Autowired
    private QueryLearner queryLearner;


    @Test
    void allPositiveSingleVariableExamples() {
        String examples = "+\"Rafi Muhammad Chaudhry\"@en +\"Sergey Psakhie\"@en +\"Igniacio Matte Blanco\"@en";
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
        String examples = "+\"Rafi Muhammad Chaudhry\"@en +\"Sergey Psakhie\"@en +\"Igniacio Matte Blanco\"@en -Italian";
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
        String examples = "+(\"Igniacio Matte Blanco\"@en, 1908-10-03) +(\"Rafi Muhammad Chaudhry\"@en, 1903-07-01)";
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
        String examples = "+(\"Igniacio Matte Blanco\"@en, 1908-10-03) +(\"Rafi Muhammad Chaudhry\"@en, 1903-07-01) -(\"A. L. Narayana\"@en, 1887)";
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
    void positiveMultipleVariableExamples2() {
        String examples = "+(\"Frances Estelle Jones Bonner\"@en, \"United States\"@en, 2000-12-27) +(\"Peter Karter\"@en, \"United States\"@en, 2010-03-30) +(\"Lindsay Stuart Smith\"@en, \"Australian\"@en, 1970-09-12)";
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
    void join() {
        String examples = "+\"Frances Estelle Jones Bonner\"@en +\"Peter Karter\"@en +\"Lindsay Stuart Smith\"@en +1736-01-19 +1744-08-15 +1760-10-23";
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