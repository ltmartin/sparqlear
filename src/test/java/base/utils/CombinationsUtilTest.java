package base.utils;

import base.Application;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest(classes = Application.class)
class CombinationsUtilTest {

    @Test
    void generateCombinations() {
        List<List<String>> results = CombinationsUtil.generateCombinations(5);
        List<List<String>> expected = new ArrayList<>();
        expected.add(List.of("0", "0 1", "0 2", "0 1 2", "0 3", "0 1 3", "0 2 3", "0 1 2 3", "0 4", "0 1 4", "0 2 4", "0 1 2 4", "0 3 4", "0 1 3 4", "0 2 3 4", "0 1 2 3 4"));
        expected.add(List.of("1", "1 2", "1 3", "1 2 3", "1 4", "1 2 4", "1 3 4", "1 2 3 4"));
        expected.add(List.of("2", "2 3", "2 4", "2 3 4"));
        expected.add(List.of("3", "3 4"));
        expected.add(List.of("4"));

        Assertions.assertEquals(results, expected);
    }
}