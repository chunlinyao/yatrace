package yatrace;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Agent {
	static public interface Advice {
		void onMethodBegin(Thread thread, String className, String methodName,
				String descriptor, Object thisObject, Object[] args);

		void onMethodEnd(Thread thread, Object resultOrException2);

	}

	static public interface AgentMain {
		void agentmain(final String args, final Instrumentation inst);
	}

	public static final Method ON_METHOD_BEGIN;
	public static final Method ON_METHOD_END;

	private static volatile Advice delegate = null;
	private static volatile ExecutorService executor = null;

	static {
		try {
			ON_METHOD_BEGIN = Agent.class.getMethod("onMethodBegin",
					String.class, String.class, String.class, Object.class,
					Object[].class);
			ON_METHOD_END = Agent.class.getMethod("onMethodEnd", Object.class);
		} catch (final NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}

	public static void agentmain(final String args, final Instrumentation inst)
			throws Exception {
		final String[] parts = args.split("\n", 2);
		final URL agentJar = new File(parts[0]).toURI().toURL();
		final ClassLoader classLoader = makeClassLoader(new URL[] { agentJar });
		reset();
		executor = Executors.newSingleThreadExecutor();

		AccessController.doPrivileged(new PrivilegedAction<Void>() {

			@Override
			public Void run() {
				final ClassLoader oldLoader = Thread.currentThread()
						.getContextClassLoader();
				try {
					Thread.currentThread().setContextClassLoader(classLoader);
					executor.submit(
							Executors
									.privilegedCallableUsingCurrentClassLoader(

									new Callable<Void>() {

										@Override
										public Void call() {
											try {
												final Class<?> mainClass = Class
														.forName(
																"yatrace.Main",
																false,
																classLoader);
												final AgentMain agentMain = (AgentMain) mainClass
														.newInstance();
												agentMain.agentmain(parts[1],
														inst);
												delegate = (Advice) agentMain;
												return null;
											} catch (final Exception e) {
												throw new RuntimeException(e);
											}

										}
									})).get();
					return null;
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
					return null;
				} catch (final ExecutionException e) {
					throw new RuntimeException(e.getCause());
				} finally {
					Thread.currentThread().setContextClassLoader(oldLoader);
				}
			}

		});

	}

	static public ClassLoader makeClassLoader(final URL[] urls) {
		return AccessController
				.doPrivileged(new PrivilegedAction<ClassLoader>() {
					@Override
					public ClassLoader run() {
						return new URLClassLoader(urls) {

							/*
							 * (non-Javadoc)
							 * 
							 * @see
							 * java.lang.ClassLoader#loadClass(java.lang.String,
							 * boolean)
							 */
							@Override
							protected synchronized Class<?> loadClass(
									final String name, final boolean resolve)
									throws ClassNotFoundException {
								final Class<?> loadedClass = findLoadedClass(name);
								if (loadedClass != null) {
									return loadedClass;
								}
								if (name.startsWith("yatrace.Agent")) {
									return getParent().loadClass(name);
								}
								try {
									final Class<?> aClass = findClass(name);
									if (resolve) {
										resolveClass(aClass);
									}
									return aClass;
								} catch (final Exception e) {
									return super.loadClass(name, resolve);
								}
							}

						};

					}
				});
	}

	public static void premain(final String args, final Instrumentation inst)
			throws Exception {
		agentmain(args, inst);
	}

	public static void onMethodBegin(final String className,
			final String methodName, final String descriptor,
			final Object thisObject, final Object[] args) {
		if (delegate != null) {
			final Thread t = Thread.currentThread();
			executor.execute(new Runnable() {

				@Override
				public void run() {
					delegate.onMethodBegin(t, className, methodName, descriptor,
							thisObject, args);
				}

			});

		}
	}

	public static void onMethodEnd(final Object resultOrException) {
		if (delegate != null) {
			final Thread t = Thread.currentThread();
			executor.execute(new Runnable() {

				@Override
				public void run() {
					delegate.onMethodEnd(t, resultOrException);
				}

			});

		}
	}

	public static void reset() {
		delegate = null;
		if (executor != null) {
			executor.shutdownNow();
		}
		executor = null;
	}
}