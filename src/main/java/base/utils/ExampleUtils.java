package base.utils;

import base.domain.ExampleWrapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.*;

@Component
@Lazy
public class ExampleUtils {

    public Set<ExampleWrapper> parseExamples(String examples) throws ParseException {
        Set<ExampleWrapper> parsedExampleWrappers = new LinkedHashSet<>();
        if (checkStructure(examples)){
            boolean groupedExamples = examples.contains("(");
            Set<String> splittedStringExamples = new LinkedHashSet<>();
            Collections.addAll(splittedStringExamples, examples.split("\\s(?=\\+|-)"));

            int groupId = 0;
            if (!groupedExamples) {
                for (String e : splittedStringExamples) {
                    boolean positive = e.charAt(0) == '+';
                    if (positive)
                        parsedExampleWrappers.add(new ExampleWrapper(groupId++, e.substring(1), ExampleWrapper.CATEGORY_POSITIVE, 0));
                    else
                        parsedExampleWrappers.add(new ExampleWrapper(groupId++, e.substring(1), ExampleWrapper.CATEGORY_NEGATIVE, 0));
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
                            parsedExampleWrappers.add(new ExampleWrapper(groupId, v, ExampleWrapper.CATEGORY_POSITIVE, position++));
                        else
                            parsedExampleWrappers.add(new ExampleWrapper(groupId, v, ExampleWrapper.CATEGORY_NEGATIVE, position++));
                    groupId++;
                }
            }

        } else
            throw new ParseException("Invalid example format.", 0);

        return parsedExampleWrappers;
    }

    private boolean checkStructure(String examples) {
//        String pattern = "^((\\+|-){1}(([A-Za-z0-9\\-\"@\\s.]+)|(\\([A-Za-z0-9\\-\"@\\s.]+(,\\s[A-Za-z0-9\\-\"@\\s.]+)+\\)))\\s?)+$";
//        return examples.matches(pattern);
        return true;
    }

    public List<String> getExamplesAsListOfString(Collection<ExampleWrapper> exampleWrapperCollection){
        List<String> result = new LinkedList<>();

        for (ExampleWrapper e: exampleWrapperCollection) {
            result.add(e.getExample());
        }

        return result;
    }

    public static Set<String> cleanExamples(Set<ExampleWrapper> exampleWrappers){
        Set<String> cleanExamples = new HashSet<>();

            for (ExampleWrapper exampleWrapper : exampleWrappers) {
                String example = exampleWrapper.getExample();
                cleanExamples.add(cleanString(example));
            }

       return cleanExamples;
    }

    public static String cleanString(String _string){
        _string = UtilsJena.removeLanguageAnnotation(_string);
        _string = _string.replaceAll("\"", "");

        return _string;
    }

    public static List<String> removeDoubleQuotes(List<String> list){
        List<String> _list = new LinkedList<>();
        for (String s : list) {
            _list.add(s.replaceAll("\"", ""));
        }
        return _list;
    }

    public static List<String> removeDuplicates(List<String> list){
        Set<String> noDuplicates = new HashSet<>(list);
        list.clear();
        list.addAll(noDuplicates);
        return list;
    }
}
