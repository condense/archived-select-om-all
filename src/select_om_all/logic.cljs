(ns select-om-all.logic
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.match :refer-macros [match]]
            [cljs.core.async :refer [>! <! alts! chan] :as a]
            [select-om-all.reactive :as r]
            [select-om-all.utils :refer [index-of]]))

;;; Inspiration and some code are from
;;; David Nolen's “Comparative Literate Programming”,
;;; http://swannodette.github.io/2013/08/17/comparative/

(defn now [] (js/Date.))

;;; Fancy “types” to make reading code easier (or harder)
;;;
;;; Index = Number | ::none
;;; Direction = :next | :previous
;;; Highlight = Number | Direction | :clear
;;; Item = Any
;;; Items = Vector of Item
;;; ListCommand = [(:highlight | :unhighlight | :select | :unselect) Number]
;;;               | [:show Boolean] | [:set-items Items] | [:loading Boolean]

(defn move-cursor
  "Cycle over items.
  `items`     :: Items
  `current`   :: Index
  `direction` :: Direction
  -> Number"
  [items current direction]
  (let [cnt (count items)]
    (match [current direction]
      [::none :next] 0
      [::none :previous] (dec cnt)
      [_      :next] (mod (inc current) cnt)
      [_      :previous] (mod (dec current) cnt))))

(defn highlight
  "Update index of highlighted item.
  `highlight` :: Highlight
  `current`   :: Index
  `items`     :: Items
  -> Index"
  [highlight current items]
  (if (= highlight :clear)
    ::none
    (if (number? highlight) highlight (move-cursor items current highlight))))

(defn highlighter
  "Listen `in` for highlight commands and put index of current highlighted
  item to `out`. Also notify `list-ctrl` about change by putting
  `[:unhighlight idx]` or `[:highlight idx]`.
   Optionally stop by receiving `control` event.
  `in`        :: Channel of (Highlight | Any)
  `list-ctrl` :: Channel of ListCommand to control ui list
  `items`     :: Items
  `control`   :: Channel of Any
  -> Channel of Index"
  ([in list-ctrl items] (highlighter in list-ctrl items (chan)))
  ([in list-ctrl items control]
   (let [out (chan)]
     (go-loop [highlighted ::none]
       (let [[e c] (alts! [in control])]
         (condp = c
           control :done
           in (if (or (#{:next :previous :clear} e) (number? e))
                (do (when (number? highlighted)
                      (>! list-ctrl [:unhighlight highlighted]))
                    (let [highlighted (highlight e highlighted items)]
                      (>! out highlighted)
                      (when (number? highlighted)
                        (>! list-ctrl [:highlight highlighted]))
                      (recur highlighted)))
                (do (>! out e)
                    (recur highlighted))))))
     out)))

(defn selector
  "Listen `in` for select command and put selected item or index to `out`.
  Also notify `list-ctrl` about change by putting
  `[:select idx]` or `[:unselect idx]`.
  `in`        :: Channel of (Highlight | :select)
  `items`     :: Items
  -> Channel of (Highlight | [:select Index] | [:select Item])"
  [in items]
  (let [out (chan)]
    (go-loop [highlighted ::none selected ::none]
      (let [e (<! in)]
        (if (= e :select)
          (do
            (if (number? highlighted)
              (>! out [:select (and highlighted (nth items highlighted))])
              (>! out [:select highlighted]))
            (recur highlighted highlighted))
          (do
            (>! out e)
            (if (or (= e ::none) (number? e))
              (recur e selected)
              (recur highlighted selected))))))
    out))

(defn menu-proc
  "Process menu logic: highlight & select appropriate items in list
   analyzing events from `select` and doing control via list-ctrl.
   Processing is stopped by sending control event to `cancel`.
   `select`    :: Channel of (Highlight | :select)
   `cancel`    :: Channel of Any
   `list-ctrl` :: Channel of ListCommand to control ui list
   `items`     :: Items
   -> Channel of ([:select Item] | ::cancel)"
  [select cancel list-ctrl items]
  (let [ctrl (chan)
        sel (chan 1 (comp (filter vector?) (map second)))]
    (a/pipe (selector (highlighter select list-ctrl items ctrl) items) sel)
    (go (let [[v sc] (alts! [cancel sel])]
          (do (>! ctrl :exit)
              (if (or (= sc cancel)
                      (= v ::none))
                ::cancel
                v))))))

(defn autocompleter
  "Autocompleter. Opt keys:
  `:focus`       :: Channel of :focus
  `:query`       :: Channel of String
  `:query-ctrl`  :: Channel of Any to control query throttle
  `:select`      :: Channel of (Number | Direction | :select)
  `:cancel`      :: Channel of (:blur | Any)
  `:selecting?`  :: Atom of Boolean
  `:simple?`     :: Boolean
  `:completions` :: String -> Channel of Items
  -> Channel of [:select (Item | ::none)]"
  [{:keys [focus query select cancel list-ctrl simple? completions] :as opts}]
  (let [out (chan)
        [query raw] (a/split r/throttle-msg? query)]
    (go-loop [items nil focused false hl nil]
      (let [[v sc] (alts! [raw cancel focus query select])]
        (cond

          (= sc focus)
          (recur items true hl)

          (= sc cancel)
          (do (>! list-ctrl [:show false])
              (>! (:query-ctrl opts) (now))
              (recur items (not= v :blur) hl))

          (and focused (= sc query))
          (let [_ (when-not items (>! list-ctrl [:loading true]))
                _ (>! list-ctrl [:show true])
                [v c] (alts! [cancel (completions (second v))])]
            (>! list-ctrl [:loading false])
            (if (= c cancel)
              (do (>! list-ctrl [:show false])
                  (recur nil (not= v :blur) hl))
              (if simple?
                (let [items (<! (completions ""))]
                  (>! list-ctrl [:set-items items])
                  (let [v (first v)
                        v (and v (index-of items v))]
                    (when v (>! list-ctrl [:highlight v]))
                    (recur items focused v)))
                (do (>! list-ctrl [:set-items v])
                    (recur v focused hl)))))

          (and (pos? (count items)) (= sc select))
          (let [_ (reset! (:selecting? opts) true)
                _ (>! (:query-ctrl opts) (now))
                choice (<! (menu-proc (r/concat [(or hl ::pass) v] select)
                                      (a/merge [raw cancel])
                                      list-ctrl items))]
            (reset! (:selecting? opts) false)
            (>! list-ctrl [:show false])
            (if (= choice ::cancel)
              (recur nil (not= v :blur) hl)
              (do (>! out choice)
                  (recur nil focused hl))))

          :else
          (recur items focused hl))))
    out))

