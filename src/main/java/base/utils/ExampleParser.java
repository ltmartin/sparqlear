package base.utils;

import base.domain.Example;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
@Lazy
public class ExampleParser {

    public Set<Example> parse(String examples) throws ParseException {
        Set<Example> parsedExamples = new HashSet<>();
        if (checkStructure(examples)){
            boolean groupedExamples = examples.contains("<");
            Set<String> splittedStringExamples = new HashSet<>();
            splittedStringExamples.addAll(Arrays.asList(examples.split("\\s(?=\\+|-)")));

            if (!groupedExamples){
                splittedStringExamples.stream().forEach(e -> {
                    boolean positive = e.charAt(0) == '+';
                    if (positive)
                        parsedExamples.add(new Example(e.substring(1), Example.CATEGORY_POSITIVE));
                    else
                        parsedExamples.add(new Example(e.substring(1), Example.CATEGORY_NEGATIVE));
                });
            } else {
                int groupId = 0;
                for (String e : splittedStringExamples) {
                    boolean positive = e.charAt(0) == '+';
                    String aux = e.substring(2, e.length() - 1);

                    Set<String> variables = Set.of(aux.split(",\\s{1}"));
                    int position = 0;
                    for (String v : variables)
                        if (positive)
                            parsedExamples.add(new Example(groupId, v, Example.CATEGORY_POSITIVE, position++));
                        else
                            parsedExamples.add(new Example(groupId, v, Example.CATEGORY_NEGATIVE, position++));
                    groupId++;
                }
            }

        } else
            throw new ParseException("Invalid example format.", 0);

        return parsedExamples;
    }

    private boolean checkStructure(String examples) {
        String pattern = "^((\\+|-){1}((\\w+)|(<\\w+(,\\s\\w+)+>))\\s?)+$";
        return examples.matches(pattern);
    }
}
