(ns select-om-all.utils
  (:require [cljs.core.async :refer [put!]]))

;;; Default search for local datasource: case-insensitive substring match

(defn subs?
  "Determines whether a string contains a substring."
  [s subs]
  (when-not (nil? s)
    (not= (.indexOf s subs) -1)))

(defn lower
  "Converts string to all lower-case."
  [s]
  (when-not (nil? s)
    (.toLowerCase s)))

(defn row-match [query row]
  (let [str-match #(if (string? %) (-> % lower (subs? query)))]
    (cond
      (map? row) (some str-match (vals row))
      (coll? row) (some str-match row)
      :else (str-match row))))

(defn default-local-search [rows query]
  (let [query (lower query)]
    (->> rows (filter (comp (partial row-match query) first)) (mapv second))))



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

