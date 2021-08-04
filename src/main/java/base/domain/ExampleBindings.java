package base.domain;

import java.util.HashMap;
import java.util.Map;

public class ExampleBindings {
    // Positive or Negative
    private Boolean category;
    // Pairs <variable, binding>
    private Map<String, String> bindings;

    public ExampleBindings() {
        bindings = new HashMap<>();
    }

    public Boolean getCategory() {
        return category;
    }

    public void setCategory(Boolean category) {
        this.category = category;
    }

    public Map<String, String> getBindings() {
        return bindings;
    }

    public void setBindings(Map<String, String> bindings) {
        this.bindings = bindings;
    }
}
