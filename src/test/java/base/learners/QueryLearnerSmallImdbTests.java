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

    @Test
    void menInAgeRangeBornInCanada() {
        String examples = "+(Jim Carrey, 50-59, Canada) +(Christopher Lane, 50-59, Canada) +(Tyler Mane, 50-59, Canada) -(Farah Zeynep Abdullah, 20-29, Turkey) -(Haruka Abe, 30-39, Japan)";
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
    void moviesFromUSA() {
        String examples = "+(USA, 2 Minutes Later) +(USA, 101 Dalmatians) -(1024 FILM, Characters)";
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
    void moviesFromIsleOfMan() {
        String examples = "+(Isle of Man, Miss Potter) +(Isle of Man, The Mistress of Spices) -(USA, 101 Dalmatians) -(UK, Red Sky) -(Ireland, Miss Julie) -(Rwanda, Rising from Ashes) -(Mali, Macadam tribu)";
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
    void moviesFromItaly() {
        String examples = "+(Italy, 1000 dollari sul nero) +(Italy, A casa nostra) -(UK, Red Sky) -(Ireland, Miss Julie) -(Rwanda, Rising from Ashes) -(Mali, Macadam tribu)";
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
