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
 * @param <T>
 * @param <R>
 * @author <a href="mailto:me@nayuki.io">Nayuki</a>
 */
final class Memoizer<T,R> {
	
	private final Function<T,R> function;
	Map<T,SoftReference<R>> cache = new ConcurrentHashMap<>();
	private Set<T> pending = new HashSet<>();
	
	
	// Creates a memoizer based on the given function that takes one input to compute an output.
	public Memoizer(Function<T,R> func) {
		function = func;
	}
	
	
	// Computes function.apply(arg) or returns a cached copy of a previous call.
	public R get(T arg) {
		// Non-blocking fast path
		{
			SoftReference<R> ref = cache.get(arg);
			if (ref != null) {
				R result = ref.get();
				if (result != null)
					return result;
			}
		}
		
		// Sequential slow path
		while (true) {
			synchronized(this) {
				SoftReference<R> ref = cache.get(arg);
				if (ref != null) {
					R result = ref.get();
					if (result != null)
						return result;
					cache.remove(arg);
				}
				assert !cache.containsKey(arg);
				
				if (pending.add(arg))
					break;
				
				try {
					this.wait();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}
		
		try {
			R result = function.apply(arg);
			cache.put(arg, new SoftReference<>(result));
			return result;
		} finally {
			synchronized(this) {
				pending.remove(arg);
				this.notifyAll();
			}
		}
	}
	
}
