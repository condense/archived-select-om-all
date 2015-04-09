(ns select-om-all.input
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [<! put!] :as a]
            [cljs.core.match :refer-macros [match]]
            [clojure.string :refer [blank?]]
            [om.core :as om]
            [sablono.core :refer-macros [html]]
            [select-om-all.utils :refer [handle-key-down relevant-keys KEYS
                                         ESC BKSP UP_ARROW DOWN_ARROW]]))

;;; INPUT COMPONENT must deal with:
;;; PROPS
;;; placeholder — placeholder text
;;; editable? — allow input to contain non-selected items
;;; default — initial item
;;; display-fn — function to convert item (incl. default) to its representation
;;; undisplay-fn — when `editable?` convert user input to item
;;; initial-loading? — lock input while waiting for remote data outside of component
;;; simple? — mimic traditional select without search input (but probably listening for keys to filter options)
;;; STATE
;;; text — input value
;;; refocus — control channel, when to focus back on itself (if necessary for succesful input)
;;; focus — put! :focus when ready to read user input for autocomplete
;;; blur — put! :blur when suggestions list should be dismissed
;;; input — put! query content here
;;; keycodes — put! raw user input
;;; selecting? — atom of selecting state, to specially handle keys if necessary
;;; hold? — atom, set to true when blur of all other AutoComplete components must be ignored
;;; open? — is completions list open?
;;; value — current item
;;; autocompleter — channel with completion result, put! (undisplay-fn text) here in Edit mode blur

;;; Default input component implementation

(defn display [display-fn value]
  (or (display-fn value) ""))

(defn Input [{:keys [placeholder editable? default display-fn undisplay-fn
                     initial-loading? disabled? simple? simple-timeout]
              :or   {display-fn identity
                     undisplay-fn identity
                     simple-timeout 1000}} owner]
  (reify
    om/IDisplayName (display-name [_] "AutoComplete Input")
    om/IDidMount
    (did-mount [_]
      (go-loop []
        (when-let [_ (<! (om/get-state owner :refocus))]
          (.focus (om/get-node owner "input"))
          (recur))))
    om/IDidUpdate
    (did-update [_ _ prev-state]
      (when (and editable? (om/get-state owner :typing)
                 (= (:value prev-state) (om/get-state owner :value)))
        (put! (om/get-state owner :autocompleter)
              (undisplay-fn (om/get-state owner :typing))))
      (when (and (:open? prev-state) (not (om/get-state owner :open?)))
        (om/set-state-nr! owner :typing nil)))
    om/IRenderState
    (render-state [_ {:keys [focus refocus blur input keycodes autocompleter
                             open? hold? selecting? value typing]}]
      (let [id (str (gensym))
            display-fn (partial display display-fn)
            open! #(put! input (if (om/get-props owner :simple?)
                                 (display-fn (om/get-state owner :value)) ""))
            close! #(put! keycodes ESC)]
        (html
         [:div.has-feedback
          [:label.control-label.sr-only {:for id}]
          [:input.form-control
           {:ref            "input"
            :id             id
            :style          {:width         "100%"
                             :padding-right 42
                             :text-overflow "ellipsis"}
            :type           "text"
            :placeholder    (if initial-loading? "Loading..." placeholder)
            :disabled       (or disabled? initial-loading?)
            :read-only      (when simple? "readonly")
            :default-value  (display-fn default)
            :value          (if (and open? typing (not simple?))
                              typing
                              (display-fn value))
            :on-focus       (fn [_]
                              (when-not open?
                                (put! focus :focus)
                                (when-not editable? (open!)))
                              true)
            :on-mouse-up    (fn [e]
                              (when-not editable?
                                (let [t (.-target e)]
                                  (.setSelectionRange
                                   t 0 (.. t -value -length)))))
            :on-mouse-down  (when-not editable?
                              (fn [_] ((if open? close! open!)) true))
            :on-blur        #(do (put! blur :blur) true)
            :on-input       (when-not simple?
                              #(let [v (.. % -target -value)]
                                 (om/set-state! owner :typing v)
                                 (put! input v)
                                 true))
            :on-key-down    (fn [e]
                              (let [kc (.-keyCode e)]
                                (when (and (not open?) (#{UP_ARROW DOWN_ARROW} kc))
                                  (open!))
                                (if simple?
                                  (do
                                    (when (#{UP_ARROW DOWN_ARROW} kc)
                                      (.preventDefault e))
                                    (if (relevant-keys kc)
                                      (let [v (or (om/get-state owner :typing) "")
                                            v (if (= kc BKSP)
                                                (subs v 0 (dec (count v)))
                                                (str v (js/String.fromCharCode kc)))]
                                        (when-let [id (om/get-state owner :simple-timeout-id)]
                                          (js/clearTimeout id))
                                        (om/set-state! owner :typing v)
                                        (put! input v)
                                        (om/set-state-nr!
                                         owner :simple-timeout-id
                                         (js/setTimeout
                                          #(om/set-state-nr! owner :typing nil)
                                          simple-timeout)))
                                      (handle-key-down keycodes selecting? hold? e)))
                                  (handle-key-down keycodes selecting? hold? e))
                                true))
            :on-mouse-enter #(reset! hold? true)
            :on-mouse-leave #(do (reset! hold? false) true)}]
          (when-not (or open? disabled? simple? (blank? value))
            [:span.glyphicon.glyphicon-remove.form-control-feedback
             {:style {:right          17
                      :width          23
                      :margin-right   5
                      :color          "#aaa"
                      :pointer-events "inherit"
                      :cursor         "pointer"}
              :on-mouse-down
              #(do
                 (put! refocus true)
                 (put! autocompleter "")
                 true)}])
          [:span.glyphicon.form-control-feedback
           {:class (str "glyphicon-chevron-" (if open? "up" "down"))
            :style {:pointer-events "inherit"
                    :cursor         "pointer"
                    :width          23}
            :on-mouse-down
            (fn []
              (if open?
                (reset! hold? false)
                (do
                  (when-not simple?
                    (let [t (om/get-node owner "input")]
                      (.setSelectionRange t 0 (.. t -value -length))))
                  (open!)
                  (put! refocus true)))
              true)}]])))))