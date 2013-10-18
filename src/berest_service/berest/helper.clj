(ns berest-service.berest.helper)

(defn thrush [& args]
  (reduce #(%2 %1) args))

(defn rcomp
  "reverse compose function, composes arguments from left to right
  (pipeline order)"
  [& args]
  (apply comp (reverse args)))

(def |-> "symbol/shortcut for rcomp (reverse compose) function"
  rcomp)

(def |<- "symbol/shortcut for comp (compose) function"
  comp)

(def --< "symbol/shortcut for juxt function"
  juxt)

(def |* "symbol/shortcut for partial function"
  partial)

(defn partial-kw
  "partial function which works with keyword (optional) arguments"
  [f & kw-args]
  (fn [& args]
    (apply f (concat args kw-args))))

(def |*kw "symbol/shortcut for partial-kw function"
  partial-kw)

(defn juxt*
  "symbol for juxt and directly useable as a function with variable arguments
  (at least be two)"
  [& rest]
  ((apply juxt (butlast rest)) (last rest)))

(def --<* "symbol/shortcut for juxt*"
  juxt*)

(defn args-21->12
  "swap the two arguments before applying f to them"
  [f arg2 arg1]
  (f arg1 arg2))

(def swap
  "swap the two arguments before applying them to f"
  args-21->12)

(defn args-231->123
  "rotate arguments in the order described by the function's name"
  [f arg2 arg3 arg1]
  (f arg1 arg2 arg3))



; name-with-attributes by Konrad Hinsen:
(defn name-with-attributes
  "To be used in macro definitions.
  Handles optional docstrings and attribute maps for a name to be defined
  in a list of macro arguments. If the first macro argument is a string,
  it is added as a docstring to name and removed from the macro argument
  list. If afterwards the first macro argument is a map, its entries are
  added to the name's metadata map and the map is removed from the
  macro argument list. The return value is a vector containing the name
  with its extended metadata map and the list of unprocessed macro
  arguments."
  [name macro-args]
  (let [[docstring macro-args] (if (string? (first macro-args))
                                 [(first macro-args) (next macro-args)]
                                 [nil macro-args])
        [attr macro-args] (if (map? (first macro-args))
                            [(first macro-args) (next macro-args)]
                            [{} macro-args])
        attr (if docstring
               (assoc attr :doc docstring)
               attr)
        attr (if (meta name)
               (conj (meta name) attr)
               attr)]
    [(with-meta name attr) macro-args]))

(defmacro defn|
  "Based on Meikel Brandmeyers code.
  Takes a normal ist of parameters, the first one which is a keyword
  is taken as the start of a sequence of pairs of keywords with
  default values. Nevertheless all parameters are treated as
  keyword parameters, but only the keyword ones as some with default values
  (:or form) of a map destructuring.
  The function name will get an | appended unless its a keyword, string or symbol;
  in that case its converted to a symbol and left untouched."
  [fn-name & fn-tail]
  (let [[fn-name [args & body]] (name-with-attributes fn-name fn-tail)
        fn-name* (if (some true? (--<* keyword? string? fn-name))
                   fn-name
                   (symbol (str fn-name \|)))
        [kws-no-default kws-vals] (split-with symbol? args)
        syms (map #(-> % name symbol) (take-nth 2 kws-vals))
        values (take-nth 2 (rest kws-vals))
        syms-vals (apply hash-map (interleave syms values))
        de-map {:keys (vec (concat kws-no-default syms))
                :or syms-vals}]
    `(defn ~fn-name* [& ~de-map]
       ~@body)))

(defmacro defnk*
  "Based on Meikel Brandmeyers code.
  Like defn| but additionally defines the function named 'fn-name'
  with all parameters without default values as positional parameters.
  Takes a normal ist of parameters, the first one which is a keyword
  is taken as the start of a sequence of pairs of keywords with
  default values. Nevertheless all parameters are treated as
  keyword parameters, but only the keyword ones as some with default values
  (:or form) of a map destructuring.
  The function name will get an | appended unless its a keyword, string or symbol;
  in that case its converted to a symbol and left untouched."
  [public+or-private fn-name & fn-tail]
  (let [defn* ({:+ 'defn :- 'defn-} public+or-private)
        [fn-name [args & body]] (name-with-attributes fn-name fn-tail)
        fn-name| (if (some true? (--<* keyword? string? fn-name))
                   fn-name
                   (symbol (str fn-name \|)))
        [kws-no-default kws-vals] (split-with symbol? args)
        syms (map #(-> % name symbol) (take-nth 2 kws-vals))
        values (take-nth 2 (rest kws-vals))
        syms-vals (apply hash-map (interleave syms values))

        remove-if-empty (fn [m sym] (if (empty? (sym m)) (dissoc m sym) m))
        create-params #(if (empty? %) [] `[& ~%])
        kw-params| (-> {:keys (vec (concat kws-no-default syms))
                        :or syms-vals}
                       (remove-if-empty ,,, :or)
                       (remove-if-empty ,,, :keys)
                       create-params)
        kw-params (-> {:keys (vec syms)
                       :or syms-vals}
                      (remove-if-empty ,,, :or)
                      (remove-if-empty ,,, :keys)
                      create-params)]
    `(do
       (~defn* ~fn-name| [~@kw-params|]
               ~@body)
       (~defn* ~fn-name [~@kws-no-default ~@kw-params]
               ~@body))))

(defmacro defnk
  "Same as defnk*, but shortcut to create two public functions with name fn-name(|)."
  [fn-name & fn-tail]
  `(defnk* :+ ~fn-name ~@fn-tail))

(defmacro defnk-
  "Same as defnk*, but shortcut to create two private functions with name fn-name(|)."
  [fn-name & fn-tail]
  `(defnk* :- ~fn-name ~@fn-tail))
