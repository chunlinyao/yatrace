package yatrace;

import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Symbol;

public class Client {

    private static IFn requireFn = RT.var("clojure.core", "require").fn();
    static {
        requireFn.invoke(Symbol.intern("yatrace.core.client"));
    }

    private static IFn mainFn = RT.var("yatrace.core.client", "javamain").fn();

    public static void main(String[] args) {
        mainFn.invoke(args);
    }

}
