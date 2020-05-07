package base.utils;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Set;

@Component
@Lazy
public class DatasetsParser {

    public Set<String> parse(String datasets) throws ParseException {
        if (verifyStructure(datasets))
            return Set.of(datasets.split("\\s"));
        else
            throw new ParseException("Incorrect datasets parameter's structure.",0);
    }

    private boolean verifyStructure(String datasets){
        String pattern = "<(?:(?:https?|ftp|file):\\/\\/|www\\.|ftp\\.)(?:\\([-\\w+&@#\\/%=~_|$?!:,.]*\\)|[-\\w+&@#\\/%=~_|$?!:,.])*(?:\\([-\\w+&@#\\/%=~_|$?!:,.]*\\)|[\\w+&@#\\/%=~_|$])>(\\s<(?:(?:https?|ftp|file):\\/\\/|www\\.|ftp\\.)(?:\\([-\\w+&@#\\/%=~_|$?!:,.]*\\)|[-\\w+&@#\\/%=~_|$?!:,.])*(?:\\([-\\w+&@#\\/%=~_|$?!:,.]*\\)|[\\w+&@#\\/%=~_|$])>)*";
        return datasets.matches(pattern);
    }
}
