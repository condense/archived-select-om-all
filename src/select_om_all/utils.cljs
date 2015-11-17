(ns select-om-all.utils
  (:require [cljs.core.async :refer [put!]]
            clojure.string))

;;; Default search for local datasource: case-insensitive substring match

(defn subs?
  "Determines whether a string contains a substring."
  [s subs]
  (when-not (nil? s)
    (not= (.indexOf s subs) -1)))

(defn start-with? [s subs]
  (when-not (nil? s)
    (zero? (.indexOf s subs))))

(defn lower
  "Converts string to all lower-case."
  [s]
  (when-not (nil? s)
    (clojure.string/lower-case s)))

(defn str-matcher [simple?]
  (if simple? start-with? subs?))

(defn row-match [simple? query row]
  (let [str-match (str-matcher simple?)
        str-match #(if (string? %) (-> % lower (str-match query)))]
    (cond
      (map? row) (some str-match (vals row))
      (coll? row) (some str-match row)
      :else (str-match row))))

(defn default-local-search [simple? rows query]
  (let [query (lower query)]
    (->> rows (filter (comp (partial row-match simple? query) first)) (map second))))



;;; Special control key codes and their semantics

(def ENTER 13)
(def UP_ARROW 38)
(def DOWN_ARROW 40)
(def TAB 9)
(def ESC 27)
(def BKSP 8)

(def KEYS #{UP_ARROW DOWN_ARROW ENTER TAB ESC})

(defn key->keyword
  "Convert control key to selector/highlighter command."
  [code]
  (condp = code
    UP_ARROW :previous
    DOWN_ARROW :next
    ENTER :select
    TAB :select
    ESC :exit))

(defn handle-key-down
  "Put event's keyCode into channel, prevent focus jump while selecting."
  [keycodes selecting? hold? e]
  (when (= (.-keyCode e) TAB)
    (reset! hold? false)
    (when @selecting?
      (.preventDefault e)))
  (put! keycodes (.-keyCode e))
  true)

(defn relevant-keys [kc]
  (or (= kc 8)
      (and (> kc 46)
           (not (#{91 92 93} kc)))))

(defn index-of [xs x]
  (let [len (count xs)]
    (loop [i 0]
      (if (< i len)
        (if (= (nth xs i) x)
          i
          (recur (inc i)))
        nil))))
