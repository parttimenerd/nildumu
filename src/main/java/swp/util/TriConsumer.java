package swp.util;

@FunctionalInterface
public interface TriConsumer<U, V, W> {
	void accept(U u, V v, W w);
}
