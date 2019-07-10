package nildumu.util;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * For fields that are evaluated lazy
 */
public class Lazy<E> {

    private Optional<E> element;

    private final Supplier<E> supplier;

    public Lazy(Supplier<E> supplier) {
        this.supplier = supplier;
        this.element = Optional.empty();
    }

    public Lazy(E element){
        this.supplier = null;
        this.element = Optional.of(element);
    }

    public E get(){
        if (!element.isPresent()){
            element = Optional.of(supplier.get());
        }
        return element.get();
    }

    public static <E> Lazy<E> l(E element){
        return new Lazy<>(element);
    }

    public static <E> Lazy<E> l(Supplier<E> supplier){
        return new Lazy<>(supplier);
    }
}
