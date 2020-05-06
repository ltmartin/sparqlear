package base.learners;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Set;

@Component
@Lazy
public class QueryLearner {
    @Resource
    private TripleFinder tripleFinder;

    public Set<String> learn(String examples){
        Set<String> derivedQueries = new HashSet<>();

        return derivedQueries;
    }
}
