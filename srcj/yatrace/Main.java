package yatrace;

import java.lang.instrument.Instrumentation;

import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Symbol;

public class Main implements Agent.Advice, Agent.AgentMain {

	private IFn requireFn;
	private IFn agentMainFn;
	{
		requireFn = RT.var("clojure.core", "require").fn();
		requireFn.invoke(Symbol.intern("yatrace.core"));
		agentMainFn = RT.var("yatrace.core", "agentmain").fn();
	}

	public void onMethodBegin(String className, String methodName,
			String descriptor, Object thisObject, Object[] args) {
	}

	public void onMethodEnd(Object resultOrException) {
	}

	@Override
	public void agentmain(String args, Instrumentation inst) {
		agentMainFn.invoke(args, inst);
	}

}
