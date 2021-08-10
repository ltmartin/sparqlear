package base.utils;

import base.Application;
import base.domain.ExampleWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.text.ParseException;
import java.util.Set;

@SpringBootTest(classes = Application.class)
class ExampleWrapperUtilsTest {
    @Resource
    private ExampleUtils exampleUtils;

    @Test
    void parse() {
        String example1 = "+Belgium";
        String example2 = "+Belgium -Cuba";
        String example3 = "+(Rubens, Belgium)";
        String example4 = "+(Rubens, Belgium, Painter) +(Jose, Cuba, Writer)";
        String example5 = "+Cuba +Venezuela +Colombia -Gris -Pan";

        test1(example1);
        test2(example2);
        test3(example3);
        test4(example4);
        test5(example5);
    }

    boolean test1(String example){
        Set<ExampleWrapper> expectedOutput = Set.of(new ExampleWrapper(0, "Belgium", ExampleWrapper.CATEGORY_POSITIVE, 0));
        try {
            Set<ExampleWrapper> output = exampleUtils.parseExamples(example);
            Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return true;
    }

    boolean test2(String example){
        Set<ExampleWrapper> expectedOutput = Set.of(new ExampleWrapper(0,"Belgium", ExampleWrapper.CATEGORY_POSITIVE, 0), new ExampleWrapper(1, "Cuba", ExampleWrapper.CATEGORY_NEGATIVE, 0));
        try {
            Set<ExampleWrapper> output = exampleUtils.parseExamples(example);
            Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return true;
    }

    boolean test3(String example){
        Set<ExampleWrapper> expectedOutput = Set.of(new ExampleWrapper(0,"Rubens", ExampleWrapper.CATEGORY_POSITIVE, 0), new ExampleWrapper(0,"Belgium", ExampleWrapper.CATEGORY_POSITIVE, 1));
        try {
            Set<ExampleWrapper> output = exampleUtils.parseExamples(example);
            Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return true;
    }

    boolean test4(String example){
        Set<ExampleWrapper> expectedOutput = Set.of(
                new ExampleWrapper(0,"Rubens", ExampleWrapper.CATEGORY_POSITIVE, 0),
                new ExampleWrapper(0,"Belgium", ExampleWrapper.CATEGORY_POSITIVE, 1),
                new ExampleWrapper(0, "Painter", ExampleWrapper.CATEGORY_POSITIVE, 2),
                new ExampleWrapper(1, "Jose", ExampleWrapper.CATEGORY_POSITIVE, 0),
                new ExampleWrapper(1, "Cuba", ExampleWrapper.CATEGORY_POSITIVE, 1),
                new ExampleWrapper(1, "Writer", ExampleWrapper.CATEGORY_POSITIVE, 2)
        );
        try {
            Set<ExampleWrapper> output = exampleUtils.parseExamples(example);
            Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return true;
    }

    boolean test5(String example) {
        Set<ExampleWrapper> expectedOutput = Set.of(new ExampleWrapper(0, "Cuba", ExampleWrapper.CATEGORY_POSITIVE, 0),
                new ExampleWrapper(1, "Venezuela", ExampleWrapper.CATEGORY_POSITIVE, 0),
                new ExampleWrapper(2, "Colombia", ExampleWrapper.CATEGORY_POSITIVE, 0),
                new ExampleWrapper(3, "Gris", ExampleWrapper.CATEGORY_NEGATIVE, 0),
                new ExampleWrapper(4, "Pan", ExampleWrapper.CATEGORY_NEGATIVE, 0));
        try {
            Set<ExampleWrapper> output = exampleUtils.parseExamples(example);
            Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return true;
    }
}