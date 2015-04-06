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

(defn make-completions
  "Make completions functions which takes query and returns channel which will
  receive suggested items. User can provide custom `completions` by himself,
  or `search-fn` which filter given dataset based on query, or rely on default
  search function. Also, `index-fn` is function to prepare data for search
  (e.g. `first` to search first column in case of array data)
  Dataset is to be supplied with `:datasource` option of component."
  [{:keys [completions search-fn index-fn]
    :or   {search-fn default-local-search index-fn identity}} owner]
  (or completions
      (fn [query]
        (go
          (search-fn (map index-fn (om/get-props owner :datasource)) query)))))

(defn make-autocomplete-state [{:keys [throttle default value]
                                :or   {throttle 100} :as props} owner]
  (let [completions (make-completions props owner)
        selecting? (atom false)
        hold? (atom false)
        input (chan)
        query-ctrl (chan)
        blur (chan 1 (remove (fn [_] @hold?)))
        ;; index of item where mouse down/up
        ;; to select by mouse only if it clicks in item bounds
        mousedown (chan (sliding-buffer 1))
        mouseup (chan (sliding-buffer 1))
        mouseselect (chan 1 (comp (filter (fn [[d u]] (= d u)))
                                  (map (constantly :select))))
        ;; index of item to highlight
        hover (chan)
        keycodes (chan 1 (comp (filter
                                (fn [kc]
                                  (and (KEYS kc)
                                       (or (not= kc TAB) @selecting?))))
                               (map key->keyword)))
        keycodes* (a/mult keycodes)
        ;; cancel on blur and :exit key (typically ESC)
        cancel (a/merge [blur (a/tap keycodes* (chan 1 (filter #{:exit})))])
        state {:focus       (chan)
               :refocus     (chan)
               :blur        blur
               :input       input
               :keycodes    keycodes
               :mousedown   mousedown
               :mouseup     mouseup
               :hover       hover
               :query-ctrl  query-ctrl
               :query       (r/throttle* input throttle (chan) query-ctrl)
               :select      (a/merge [(a/tap keycodes* (chan)) hover mouseselect])
               :cancel      cancel
               :list-ctrl   (chan)
               :completions completions
               :selecting?  selecting?
               :hold?       hold?
               :resize!     (chan)
               :value       (or default value :select-om-all.logic/none)}
        autocompleter (autocompleter state)]
    (a/pipe (a/map vector [mousedown mouseup]) mouseselect)
    (assoc state :autocompleter autocompleter)))

(defn AutoComplete [{:keys [input-component list-component
                            editable? default
                            on-highlight on-change]
                     :or   {input-component Input
                            list-component  FDTList
                            on-highlight    identity
                            on-change       identity}
                     :as   props} owner]
  (reify
    om/IDisplayName (display-name [_] "AutoComplete")
    om/IInitState
    (init-state [_]
      (let [{:keys [autocompleter list-ctrl] :as state}
            (make-autocomplete-state props owner)]
        (go-loop []
          (when-let [e (<! list-ctrl)]
            (match e
              [:show x] (om/update-state! owner
                                          #(merge % {:highlighted nil
                                                     :open?       x}))
              [:set-items v] (om/set-state! owner :items v)
              [:highlight n] (om/set-state! owner :highlighted n)
              [:unhighlight n] (om/set-state! owner :highlighted nil)
              [:loading x] (om/set-state! owner :loading? x)
              :else nil)
            (recur)))
        (go-loop []
          (when-let [choice (<! autocompleter)]
            (om/set-state! owner :value choice)
            (recur)))
        state))
    om/IDidMount
    (did-mount [_]
      (when-not (or editable? default (:value props))
        (go
          (let [_ (om/set-state! owner :initial-loading? true)
                [choice] (<! ((om/get-state owner :completions) ""))
                _ (om/set-state! owner :initial-loading? false)]
            (when choice
              (om/set-state! owner :value choice))))))
    om/IDidUpdate
    (did-update [_ prev-props prev-state]
      (let [{:keys [value highlighted items]} (om/get-state owner)]
        (when (not= (:value prev-state) value)
          (on-change (if (= :select-om-all.logic/none value) nil value)))
        (when (not= (:highlighted prev-state) highlighted)
          (on-highlight (get items highlighted)))
        (when (not= (:value prev-props) (:value props))
          (om/set-state! owner :value (:value props)))))
    om/IRenderState
    (render-state [_ state]
      (html
       [:div
        (om/build
         Popup
         {:anchor       (om/build input-component props {:state state})
          :popup        (om/build list-component props {:state state})
          :open?        (:open? state)
          :resize-ch    (:resize! state)
          :set-width-fn #(om/set-state! owner :width %)})]))))