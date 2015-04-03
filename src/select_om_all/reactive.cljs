(ns select-om-all.reactive
  (:refer-clojure :exclude [concat])
  (:require [cljs.core.async
             :refer [>! <! alts! chan close! timeout pipe]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(defn concat [xs in]
  (let [out (chan)]
    (go-loop [xs (seq xs)]
      (if xs
        (do (>! out (first xs))
            (recur (next xs)))
        (if-let [x (<! in)]
          (do (>! out x)
              (recur xs))
          (close! out))))
    out))

(defn throttle*
  ([in msecs]
   (throttle* in msecs (chan)))
  ([in msecs out]
   (throttle* in msecs out (chan)))
  ([in msecs out control]
   (go-loop [state ::init last nil cs [in control]]
     (let [[_ _ sync] cs]
       (let [[v sc] (alts! cs)]
         (condp = sc
           in (condp = state
                ::init (do (>! out v)
                           (>! out [::throttle v])
                           (recur ::throttling last
                                  (conj cs (timeout msecs))))
                ::throttling (do (>! out v)
                                 (recur state v cs)))
           sync (if last
                  (do (>! out [::throttle last])
                      (recur state nil
                             (conj (pop cs) (timeout msecs))))
                  (recur ::init last (pop cs)))
           control (recur ::init nil
                          (if (= (count cs) 3)
                            (pop cs)
                            cs))))))
   out))

(defn throttle-msg? [x]
  (and (vector? x)
       (= (first x) ::throttle)))

(defn throttle
  ([in msecs] (throttle in msecs (chan)))
  ([in msecs out]
   (let [ch (chan 1 (comp (filter #(and (vector? %) (= (first %) ::throttle)))
                          (map second)))]
     (pipe (throttle* in msecs out) ch)
     ch)))