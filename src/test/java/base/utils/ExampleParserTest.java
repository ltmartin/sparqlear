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
class ExampleParserTest {
    @Resource
    private ExampleParser exampleParser;

    @Test
    void parse() {
        String example1 = "+Belgium";
        String example2 = "+Belgium -Cuba";
        String example3 = "+<Rubens, Belgium>";
        String example4 = "+<Rubens, Belgium, Painter> +<Jose, Cuba, Writer>";

        test1(example1);
        test2(example2);
        test3(example3);
        test4(example4);
    }

    boolean test1(String example){
        Set<Example> expectedOutput = Set.of(new Example("Belgium", Example.CATEGORY_POSITIVE));
        try {
            Set<Example> output = exampleParser.parse(example);
            Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return true;
    }

    boolean test2(String example){
        Set<Example> expectedOutput = Set.of(new Example("Belgium", Example.CATEGORY_POSITIVE), new Example("Cuba", Example.CATEGORY_NEGATIVE));
        try {
            Set<Example> output = exampleParser.parse(example);
            Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return true;
    }

    boolean test3(String example){
        Set<Example> expectedOutput = Set.of(new Example(0,"Rubens", Example.CATEGORY_POSITIVE), new Example(0,"Belgium", Example.CATEGORY_POSITIVE));
        try {
            Set<Example> output = exampleParser.parse(example);
            Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return true;
    }

    boolean test4(String example){
        Set<Example> expectedOutput = Set.of(
                new Example(0,"Rubens", Example.CATEGORY_POSITIVE),
                new Example(0,"Belgium", Example.CATEGORY_POSITIVE),
                new Example(0,"Painter", Example.CATEGORY_POSITIVE),
                new Example(1,"Jose", Example.CATEGORY_POSITIVE),
                new Example(1,"Cuba", Example.CATEGORY_POSITIVE),
                new Example(1,"Writer", Example.CATEGORY_POSITIVE)
        );
        try {
            Set<Example> output = exampleParser.parse(example);
            Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return true;
    }
}