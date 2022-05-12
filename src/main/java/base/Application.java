package base;

import base.learners.QueryLearner;
import base.services.MotifsService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;

/**
 * @author Leandro Tabares Martín
 *
 **/
@SpringBootApplication
public class Application implements CommandLineRunner {
    @Resource
    private QueryLearner learner;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

//        clearConsole();
//
//        System.out.println("Welcome to SPARQLEAR demo!!!");
//        System.out.println("The format for the examples is: ");
//        System.out.println("+positiveExampleN -negativeExampleX");
//        System.out.println("If you want to type compound examples the format is: ");
//        System.out.println("+(component1, component2, componentN) -(component1, component2, componentN)");
//        System.out.println("Please, enter the examples:");
//
//        Scanner scanner = new Scanner(System.in, "utf-8");
//        while (scanner.hasNextLine()) {
//            String examples = scanner.nextLine();
//            System.out.println(examples);
//            // Piece of code to get a clean instance of the Spring bean
//            learner = learner.getLearner();
//
//            Optional<Set<String>> learnedQueries = learner.learn(examples);
//
//            System.out.println("===================================================");
//            System.out.println("Result: ");
//            if (!learnedQueries.isPresent())
//                System.out.println("Nothing learned.");
//            else
//                learnedQueries.stream().forEach(System.out::println);
//
//            System.out.println("===================================================");
//        }
    }

    private void clearConsole(){
        try {
            Runtime.getRuntime().exec("clear");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
