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
public class QueryLearnerSmallImdbTests {
    @Autowired
    private QueryLearner queryLearner;

    @Test
    void allPositiveSingleVariableExamples() {
        String examples = "+Robin Williams +Jim Carrey +Eddie Murphy";
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
