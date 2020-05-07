package base.utils;

import base.Application;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.text.ParseException;
import java.util.Set;

@SpringBootTest(classes = Application.class)
class DatasetsParserTest {
    @Resource
    private DatasetsParser datasetsParser;

    @Test
    void test1() {
       String datasets = "<http://dbpedia.org>";
       Set<String> expectedOutput = Set.of(datasets);
        Set<String> output = null;
        try {
            output = datasetsParser.parse(datasets);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
    }

    @Test
    void test2() {
        String datasets = "<http://dbpedia.org> <http://query.wikidata.org>";
        Set<String> expectedOutput = Set.of("<http://dbpedia.org>", "<http://query.wikidata.org>");
        Set<String> output = null;
        try {
            output = datasetsParser.parse(datasets);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        Assertions.assertTrue(expectedOutput.size() == output.size() && expectedOutput.containsAll(output) && output.containsAll(expectedOutput));
    }
}