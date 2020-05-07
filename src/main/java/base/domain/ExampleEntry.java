package base.domain;

import java.util.Map;

public class ExampleEntry<K,V> implements Map.Entry<K,V> {
    private K key;
    private V value;

    public ExampleEntry(K key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        return value;
    }

    @Override
    public V setValue(V value) {
        this.value = value;
        return value;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        K key = getKey();
        V value = getValue();
        sb.append(key.toString());
        sb.append('=');
        sb.append(value.toString());
        return sb.toString();
    }
}
