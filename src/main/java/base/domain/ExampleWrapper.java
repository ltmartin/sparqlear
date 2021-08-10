package base.domain;

import java.util.Objects;

public class ExampleWrapper implements Comparable<ExampleWrapper> {
    public static final Boolean CATEGORY_POSITIVE = true;
    public static final Boolean CATEGORY_NEGATIVE = false;

    private Integer group;
    private String example;
    private Boolean category;
    private Integer position;

    public ExampleWrapper(Integer group, String example, Boolean category, Integer position) {
        this.group = group;
        this.example = example;
        this.category = category;
        this.position = position;
    }

    public Integer getGroup() {
        return group;
    }

    public String getExample() {
        return example;
    }

    public Boolean getCategory() {
        return category;
    }

    public Integer getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExampleWrapper exampleWrapper1 = (ExampleWrapper) o;

        if (!Objects.equals(group, exampleWrapper1.group)) return false;
        if (!example.equals(exampleWrapper1.example)) return false;
        return category.equals(exampleWrapper1.category);
    }

    @Override
    public int hashCode() {
        int result = group != null ? group.hashCode() : 0;
        result = 31 * result + example.hashCode();
        result = 31 * result + category.hashCode();
        return result;
    }


    @Override
    public int compareTo(ExampleWrapper o) {
        if (!this.getGroup().equals(o.getGroup()))
            return this.getGroup().compareTo(o.getGroup());
        else if (!this.getExample().equals(o.getExample()))
            return this.getExample().compareTo(o.getExample());
        else
            return this.getPosition().compareTo(o.getPosition());
    }

    @Override
    public String toString() {
        return example;
    }
}
