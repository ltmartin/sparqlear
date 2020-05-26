package base.utils;

import base.domain.Example;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.*;

@Component
@Lazy
public class ExampleUtils {

    public Set<Example> parseExamples(String examples) throws ParseException {
        Set<Example> parsedExamples = new HashSet<>();
        if (checkStructure(examples)){
            boolean groupedExamples = examples.contains("<");
            Set<String> splittedStringExamples = new LinkedHashSet<>();
            Collections.addAll(splittedStringExamples, examples.split("\\s(?=\\+|-)"));

            int groupId = 0;
            if (!groupedExamples) {
                for (String e : splittedStringExamples) {
                    boolean positive = e.charAt(0) == '+';
                    if (positive)
                        parsedExamples.add(new Example(groupId++, e.substring(1), Example.CATEGORY_POSITIVE, 0));
                    else
                        parsedExamples.add(new Example(groupId++, e.substring(1), Example.CATEGORY_NEGATIVE, 0));
                }
            } else {
                for (String e : splittedStringExamples) {
                    boolean positive = e.charAt(0) == '+';
                    String aux = e.substring(2, e.length() - 1);

                    Set<String> variables = new LinkedHashSet<>();
                    Collections.addAll(variables, aux.split(",\\s{1}"));
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

    public List<String> getExamplesAsListOfString(Collection<Example> exampleCollection){
        List<String> result = new LinkedList<>();

        for (Example e: exampleCollection) {
            result.add(e.getExample());
        }

        return result;
    }
}
