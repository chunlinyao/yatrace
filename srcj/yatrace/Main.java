package yatrace;

import java.lang.instrument.Instrumentation;

import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Symbol;

public class Main {

    private static IFn requireFn = RT.var("clojure.core", "require").fn();
    static {
        requireFn.invoke(Symbol.intern("yatrace.core"));
    }

    private static IFn mainFn = RT.var("yatrace.core", "javamain").fn();
    private static IFn agentMainFn = RT.var("yatrace.core", "agentmain").fn();

    public static void main(String[] args) {
    
        mainFn.invoke(args);
    }

    public static void agentMain(String args, Instrumentation inst) {
        agentMainFn.invoke(args, inst);
    }
    
}
