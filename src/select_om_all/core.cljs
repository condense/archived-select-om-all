(ns select-om-all.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.match :refer-macros [match]]
            [cljs.core.async
             :refer [>! <! alts! chan sliding-buffer put!] :as a]
            [om.core :as om]
            [sablono.core :refer-macros [html]]
            [cljsjs.fixed-data-table]
            [select-om-all.reactive :as r]
            [select-om-all.logic :refer [autocompleter]]
            [select-om-all.input :refer [Input]]
            [select-om-all.list :refer [FDTList]]
            [select-om-all.popup :refer [Popup]]
            [select-om-all.utils :refer [key->keyword TAB KEYS
                                        default-local-search]]))

(enable-console-print!)

(defn make-autocomplete-state [completions throttle default]
  (let [selecting? (atom false)
        hold? (atom false)
        input (chan)
        query-ctrl (chan)
        blur (chan 1 (remove (fn [_] @hold?)))
        ;; index of item where mouse down/up
        mousedown (chan (sliding-buffer 1))
        mouseup (chan (sliding-buffer 1))
        list-ctrl (chan)
        hover (chan)
        keycodes (chan 1 (comp (filter
                                (fn [kc]
                                  (and (KEYS kc)
                                       (or (not= kc TAB) @selecting?))))
                               (map key->keyword)))
        keycodes* (a/mult keycodes)
        mouseselect (chan 1 (comp (filter (fn [[d u]] (= d u)))
                                  (map (constantly :select))))
        choice (chan)
        cancel* (a/mult (a/merge [blur (a/tap keycodes* (chan 1 (filter #{:exit})))]))
        state {:focus          (chan)
               :refocus        (chan)
               :blur           blur
               :input          input
               :keycodes       keycodes
               :mousedown      mousedown
               :mouseup        mouseup
               :hover          hover
               :query-ctrl     query-ctrl
               :query          (r/throttle* input throttle (chan) query-ctrl)
               :select         (a/merge [(a/tap keycodes* (chan)) hover mouseselect])
               :cancel         (a/tap cancel* (chan))
               :list-ctrl      list-ctrl
               :list-ctrl*     (a/mult list-ctrl)
               :completions    completions
               :selecting?     selecting?
               :hold?          hold?
               :choice         choice
               :choice*        (a/mult choice)
               :cancel*        cancel*
               :current-choice (atom (or default ""))}
        autocompleter (autocompleter state)]
    (a/pipe (a/map vector [mousedown mouseup]) mouseselect)
    (assoc state :autocompleter autocompleter)))

(defn make-completions [owner completions search-fn array?]
  (or completions
      (fn [query]
        (go ((or search-fn
                 default-local-search)
             (map (if array? identity vector)
                  (om/get-props owner :cursor))
             query)))))

(defn AutoComplete [{:keys [search-fn completions throttle array? editable?
                            input-component list-component default]
                     :or   {throttle 100
                            input-component Input
                            list-component FDTList}
                     :as props} owner]
  (reify
    om/IDisplayName (display-name [_] "AutoComplete")
    om/IInitState
    (init-state [_]
      (let [completions (make-completions owner completions search-fn array?)
            {:keys [autocompleter current-choice list-ctrl*] :as state}
            (make-autocomplete-state completions throttle default)]
        (when-not editable?
          (a/pipe (a/tap (:cancel* state)
                         (chan 1 (comp
                                  (filter #{:blur})
                                  (map #(deref current-choice)))))
                  (:choice state)))
        (go-loop []
          (when-let [choice (<! autocompleter)]
            (when-let [on-change (om/get-props owner :on-change)]
              (on-change choice))
            (>! (:choice state)
                (reset! current-choice
                        (if (= :select-om-all.logic/none choice)
                          ""
                          (first choice))))
            (recur)))
        (assoc state
               :pop (a/tap list-ctrl*
                           (chan 1 (comp (filter (comp #{:show} first))
                                         (map second))))
               :resize (chan))))
    om/IDidMount
    (did-mount [_]
      (when-not (or editable? default)
        (go
          (let [ctrl (om/get-state owner :list-ctrl)
                _ (>! ctrl [:initial-loading true])
                [[choice]] (<! ((om/get-state owner :completions) ""))
                _ (>! ctrl [:initial-loading false])]
            (when choice
              (when-let [on-change (om/get-props owner :on-change)]
                (on-change choice))
              (>! (om/get-state owner :choice)
                  (reset! (om/get-state owner :current-choice) choice)))))))
    om/IRender
    (render [_]
      (let [state (om/get-state owner)]
        (html [:div
               (om/build Popup
                         {:anchor (om/build input-component props {:state state})
                          :popup  (om/build list-component props {:state state})
                          :show   (:pop state)
                          :resize (:resize state)})])))))