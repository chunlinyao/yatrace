package yatrace;

import java.lang.instrument.Instrumentation;

import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Symbol;

public class Main implements Agent.Advice, Agent.AgentMain {

	private IFn requireFn;
	private IFn agentMainFn;
	private IFn methodBeginFn;
	private IFn methodEndFn;
	{
		requireFn = RT.var("clojure.core", "require").fn();
		requireFn.invoke(Symbol.intern("yatrace.core"));
		agentMainFn = RT.var("yatrace.core", "agentmain").fn();
		methodBeginFn = RT.var("yatrace.core", "method-enter").fn();
		methodEndFn = RT.var("yatrace.core", "method-exit").fn();
	}

	public void onMethodBegin(Thread t, String className, String methodName,
			String descriptor, Object thisObject, Object[] args) {
		methodBeginFn.invoke(t, className, methodName, descriptor, thisObject, args);
	}

	public void onMethodEnd(Thread t, Object resultOrException) {
		methodEndFn.invoke(t, resultOrException);
	}

	@Override
	public void agentmain(String args, Instrumentation inst) {
		agentMainFn.invoke(args, inst);
	}

}
