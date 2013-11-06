(ns yatrace.core.decorate
  (:import [clojure.asm ClassReader ClassWriter ClassAdapter MethodVisitor
            Opcodes Type Label ]
           [clojure.asm.commons AdviceAdapter Method GeneratorAdapter]
           [yatrace Agent])
  )

(declare class-adapter method-adapter not-abstract?
         load-this-or-push-null-if-is-static load-arg-array prepare-result-by)

(defn decorate [^bytes bytecode-buffer name method-filter]
  (let [cr (ClassReader. bytecode-buffer)
        cw (ClassWriter. ClassWriter/COMPUTE_FRAMES)]
    (.accept cr (class-adapter cw name method-filter) ClassReader/EXPAND_FRAMES)
    (.toByteArray cw))
)

(defn- class-adapter [^ClassWriter cw ^String class-name method-filter]

  (proxy [ClassAdapter] [cw]
    (visitMethod [acc name desc sign exces]
      (let [^ClassAdapter this this
            mv (proxy-super visitMethod acc name desc sign exces)
            target? (delay (method-filter class-name name))]
        (if (and (not (nil? mv))
                 (not-abstract? acc)
                 @target?)
          (method-adapter class-name mv acc name desc)
          mv
          )
        ))
    ))

(defn- to-asm-method [^java.lang.reflect.Method m]
  (Method. (.getName m) (Type/getMethodDescriptor m)))

(defn method-adapter [^String class-name ^MethodVisitor mv acc ^String name ^String desc]
  (let [agent (Type/getType Agent)
        enter (to-asm-method Agent/ON_METHOD_BEGIN)
        exit  (to-asm-method Agent/ON_METHOD_END)
        start (Label.)
        end   (Label.)]
    (proxy [AdviceAdapter] [mv acc name desc]
      
      (visitMaxs [max-stack max-local]
        (let [^AdviceAdapter this this]
          (doto this
            (.mark end)
            (.catchException start end (Type/getType Throwable))
            .dup
            (.invokeStatic agent exit)
            .throwException)
          (proxy-super visitMaxs max-stack max-local)))
      
      (onMethodEnter []
        (let [^AdviceAdapter this this]
          (doto this
            (.push class-name)
            (.push name)
            (.push desc)
            (load-this-or-push-null-if-is-static acc)
            .loadArgArray
            (.invokeStatic agent enter)
            (.mark start))))

      (onMethodExit [opcode]
        (let [^AdviceAdapter this this]
          (if (not= opcode
                    Opcodes/ATHROW)
            (doto this
              (prepare-result-by opcode desc)
              (.invokeStatic agent exit)))))
      )))

(defn- not-abstract? [acc]
  (= 0
     (bit-and Opcodes/ACC_ABSTRACT acc)))

(defn- is-static-method [acc]
  (not= 0
        (bit-and Opcodes/ACC_STATIC acc)))

(defn- load-this-or-push-null-if-is-static [^AdviceAdapter this acc]
  (let [^Type null nil]
    (if (is-static-method acc)
      (.push this null)
      (.loadThis this))))

(defn- prepare-result-by [^AdviceAdapter this opcode ^String desc]
  (let [^Type null nil]
    (cond
     (= opcode Opcodes/RETURN)   (.push this null)
     (= opcode Opcodes/ARETURN)  (.dup this)
     (or (= opcode Opcodes/LRETURN)
         (= opcode Opcodes/DRETURN)) (doto this
                                       .dup2
                                       (.box (Type/getReturnType desc)))
         :else (doto this
                 .dup
                 (.box (Type/getReturnType desc)))
     ))
  )
