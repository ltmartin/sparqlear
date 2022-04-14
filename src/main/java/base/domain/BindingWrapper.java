package base.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class BindingWrapper {
    // Positive or Negative
    private Boolean category;
    // Pairs <variable, binding>
    private Map<String, String> bindings;

    public BindingWrapper() {
        bindings = new HashMap<>();
    }

    public BindingWrapper(Boolean category, Map<String, String> bindings) {
        this.category = category;
        this.bindings = bindings;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BindingWrapper that = (BindingWrapper) o;
        return category.equals(that.category) && bindings.equals(that.bindings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, bindings);
    }

    @Override
    public String toString() {
        return "BindingWrapper{" +
                "category=" + ((category == false)? "Negative" : "Positive") +
                ", bindings=" + bindings +
                '}';
    }
}
