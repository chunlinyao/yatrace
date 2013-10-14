package yatrace;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;

public class Agent {

	public static void agentmain(final String args, final Instrumentation inst)
			throws Exception {
		final String[] parts = args.split("\n", 2);
		final URL agentJar = new File(parts[0]).toURI().toURL();
		final ClassLoader classLoader = makeClassLoader(new URL[] { agentJar });
		Class<?> mainClass = Class.forName("yatrace.Main", false, classLoader);
		final Method method = mainClass.getMethod("agentMain", new Class[] {
				String.class, Instrumentation.class });
		AccessController.doPrivileged(new PrivilegedAction<Void>() {

			@Override
			public Void run() {
				ClassLoader oldLoader = Thread.currentThread()
						.getContextClassLoader();
				try {
					Thread.currentThread().setContextClassLoader(classLoader);
					Thread thread = new Thread(new Runnable() {

						@Override
						public void run() {
							try {
								method.invoke(null, new Object[] { parts[1],
										inst });
							} catch (Exception e) {
								throw new RuntimeException(e);
							}

						}
					});
					thread.start();
					thread.join();
					return null;
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				} finally {
					Thread.currentThread().setContextClassLoader(oldLoader);
				}
			}

		});

	}

	static public ClassLoader makeClassLoader(final URL[] urls) {
		return AccessController
				.doPrivileged(new PrivilegedAction<ClassLoader>() {
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
							protected Class<?> loadClass(final String name,
									final boolean resolve)
									throws ClassNotFoundException {
								final Class<?> loadedClass = findLoadedClass(name);
								if (loadedClass != null) {
									return loadedClass;
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
}
