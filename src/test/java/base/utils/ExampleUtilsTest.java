package base.utils;

import base.Application;
import base.domain.Example;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.text.ParseException;
import java.util.Set;

@SpringBootTest(classes = Application.class)
class ExampleUtilsTest {
    @Resource
    private ExampleUtils exampleUtils;

    @Test
    void parse() {
        String example1 = "+Belgium";
        String example2 = "+Belgium -Cuba";
        String example3 = "+<Rubens, Belgium>";
        String example4 = "+<Rubens, Belgium, Painter> +<Jose, Cuba, Writer>";
        String example5 = "+Cuba +Venezuela +Colombia -Gris -Pan";

        test1(example1);
        test2(example2);
        test3(example3);
        test4(example4);
        test5(example5);
    }

    boolean test1(String example){
        Set<Example> expectedOutput = Set.of(new Example(0, "Belgium", Example.CATEGORY_POSITIVE, 0));
        try {
            Set<Example> output = exampleUtils.parseExamples(example);
            Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return true;
    }

    boolean test2(String example){
        Set<Example> expectedOutput = Set.of(new Example(0,"Belgium", Example.CATEGORY_POSITIVE, 0), new Example(1, "Cuba", Example.CATEGORY_NEGATIVE, 0));
        try {
            Set<Example> output = exampleUtils.parseExamples(example);
            Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return true;
    }

    boolean test3(String example){
        Set<Example> expectedOutput = Set.of(new Example(0,"Rubens", Example.CATEGORY_POSITIVE, 0), new Example(0,"Belgium", Example.CATEGORY_POSITIVE, 1));
        try {
            Set<Example> output = exampleUtils.parseExamples(example);
            Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return true;
    }

    boolean test4(String example){
        Set<Example> expectedOutput = Set.of(
                new Example(0,"Rubens", Example.CATEGORY_POSITIVE, 0),
                new Example(0,"Belgium", Example.CATEGORY_POSITIVE, 1),
                new Example(0, "Painter", Example.CATEGORY_POSITIVE, 2),
                new Example(1, "Jose", Example.CATEGORY_POSITIVE, 0),
                new Example(1, "Cuba", Example.CATEGORY_POSITIVE, 1),
                new Example(1, "Writer", Example.CATEGORY_POSITIVE, 2)
        );
        try {
            Set<Example> output = exampleUtils.parseExamples(example);
            Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return true;
    }

    boolean test5(String example) {
        Set<Example> expectedOutput = Set.of(new Example(0, "Cuba", Example.CATEGORY_POSITIVE, 0),
                new Example(1, "Venezuela", Example.CATEGORY_POSITIVE, 0),
                new Example(2, "Colombia", Example.CATEGORY_POSITIVE, 0),
                new Example(3, "Gris", Example.CATEGORY_NEGATIVE, 0),
                new Example(4, "Pan", Example.CATEGORY_NEGATIVE, 0));
        try {
            Set<Example> output = exampleUtils.parseExamples(example);
            Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return true;
    }
}