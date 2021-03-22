package swp.util;

@FunctionalInterface
public interface TriFunction<U, V, W, X> {
	X accept(U u, V v, W w);
}
