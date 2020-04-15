package domain;

import java.util.*;

public class QueueSet<T> implements Set<T>, Queue<T> {
    Queue<T> elements;

    public QueueSet() {
        elements = new LinkedList<>();
    }

    public T poll(){
        return elements.poll();
    }

    @Override
    public T element() {
        return elements.element();
    }

    @Override
    public T peek() {
        return elements.peek();
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return elements.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return elements.iterator();
    }

    @Override
    public Object[] toArray() {
        return elements.toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return elements.toArray(a);
    }

    @Override
    public boolean add(T t) {
        if (!contains(t)) {
            elements.add(t);
            return true;
        }
        return false;
    }

    @Override
    public boolean offer(T t) {
        return false;
    }

    @Override
    public T remove() {
        return elements.remove();
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return elements.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        for (T item : c)
            if (!this.contains(item))
                this.add(item);
        return true;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return elements.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return elements.retainAll(c);
    }

    @Override
    public void clear() {
        elements.clear();
    }
}
