package net.logicsquad.qr4j;

import java.lang.ref.SoftReference;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A thread-safe cache based on soft references.
 * 
 * @param <T> index type
 * @param <R> return type
 * @author <a href="mailto:me@nayuki.io">Nayuki</a>
 */
final class Memoizer<T, R> {
	/**
	 * {@link Function} from index type to return type
	 */
	private final Function<T, R> function;

	/**
	 * Cache of return values
	 */
	private final Map<T, SoftReference<R>> cache = new ConcurrentHashMap<>();

	/**
	 * Set of index values for which a result is being computed
	 */
	private final Set<T> pending = new HashSet<>();	

	/**
	 * Constructor taking a {@link Function} that takes one input to compute an output
	 * 
	 * @param function {@link Function} from index type to return type
	 */
	public Memoizer(Function<T, R> function) {
		this.function = function;
		return;
	}

	/**
	 * Returns an object of the return type for the supplied index value. This either computes
	 * {@code function.apply(index)} or returns a cached copy of a previous call.
	 * 
	 * @param index value of index type
	 * @return computed result
	 */
	public R get(T index) {
		// Non-blocking fast path
		{
			SoftReference<R> ref = cache.get(index);
			if (ref != null) {
				R result = ref.get();
				if (result != null)
					return result;
			}
		}

		// Sequential slow path
		while (true) {
			synchronized (this) {
				SoftReference<R> ref = cache.get(index);
				if (ref != null) {
					R result = ref.get();
					if (result != null)
						return result;
					cache.remove(index);
				}
				assert !cache.containsKey(index);

				if (pending.add(index))
					break;

				try {
					this.wait();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}

		try {
			R result = function.apply(index);
			cache.put(index, new SoftReference<>(result));
			return result;
		} finally {
			synchronized (this) {
				pending.remove(index);
				this.notifyAll();
			}
		}
	}
}
