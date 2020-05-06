package base.domain;

public class Example {
    public static final Boolean CATEGORY_POSITIVE = true;
    public static final Boolean CATEGORY_NEGATIVE = false;

    private Integer group;
    private String example;
    private Boolean category;

    public Example(Integer group, String example, Boolean category) {
        this.group = group;
        this.example = example;
        this.category = category;
    }

    public Example(String example, Boolean category) {
        this.example = example;
        this.category = category;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Example example1 = (Example) o;

        if (group != null ? !group.equals(example1.group) : example1.group != null) return false;
        if (!example.equals(example1.example)) return false;
        return category.equals(example1.category);
    }

    @Override
    public int hashCode() {
        int result = group != null ? group.hashCode() : 0;
        result = 31 * result + example.hashCode();
        result = 31 * result + category.hashCode();
        return result;
    }
}
