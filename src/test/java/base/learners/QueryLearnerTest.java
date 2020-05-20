package base.learners;

import base.Application;
import base.exceptions.ExampleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.text.ParseException;
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
    void learn() {
        String examples = "+Cuba +Venezuela +Colombia";
        try {
            Set<String> learnedQueries = queryLearner.learn(examples);
            learnedQueries.stream().forEach(query -> System.out.println(query));
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (ExampleException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}