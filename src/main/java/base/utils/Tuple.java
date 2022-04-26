package base.utils;

import com.github.jsonldjava.utils.Obj;

import java.util.LinkedList;
import java.util.List;

public class Tuple<T> {
    private List<T> elements;

    public Tuple(){
        elements = new LinkedList<>();
    }

    public Tuple(T item){
        elements = new LinkedList<>();
        elements.add(item);
    }

    public void addItem(T item){
        elements.add(item);
    }

    public String toString(){
        StringBuilder builder = new StringBuilder();

        builder.append("(");
        for (T item : elements) {
            builder.append("\"" + item.toString() + "\" ");
        }
        builder.append(")");

        return builder.toString();
    }
}
