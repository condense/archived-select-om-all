(ns select-om-all.input
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [<! put!] :as a]
            [cljs.core.match :refer-macros [match]]
            [clojure.string :refer [blank?]]
            [om.core :as om]
            [sablono.core :refer-macros [html]]
            [select-om-all.utils :refer [handle-key-down ESC]]))

;;; INPUT COMPONENT must deal with:
;;; PROPS
;;; placeholder — placeholder text
;;; STATE
;;; text — input value
;;; refocus — control channel, when to focus back on itself (if necessary for succesful input)
;;; focus — put! :focus when ready to read user input for autocomplete
;;; blur — put! :blur when suggestions list should be dismissed
;;; input — put! query content here
;;; keycodes — put! raw user input
;;; selecting? — atom of selecting state, to specially handle keys if necessary
;;; hold? — atom, set to true when blur of all other AutoComplete components must be ignored

;;; Default input component implementation

(defn Input [{:keys [placeholder editable? default display-fn edit-fn
                     initial-loading?]
              :or   {display-fn identity
                     edit-fn identity}} owner]
  (reify
    om/IDisplayName (display-name [_] "AutoComplete Input")
    om/IDidMount
    (did-mount [_]
      (go-loop []
        (when-let [_ (<! (om/get-state owner :refocus))]
          (.focus (om/get-node owner "input"))
          (recur))))
    om/IRenderState
    (render-state [_ {:keys [focus blur input keycodes hold? selecting?
                             refocus open? value typing
                             autocompleter]}]
      (let [id (str (gensym))]
        (html
         [:div.has-feedback
          [:label.control-label.sr-only {:for id}]
          [:input.form-control
           {:ref            "input"
            :id             id
            :style          {:width "100%"}
            :type           "text"
            :placeholder    (if initial-loading? "Loading..." placeholder)
            :disabled       initial-loading?
            :default-value  (display-fn default)
            :value          (let [v (if (= value :select-om-all.logic/none)
                                      "" (display-fn value))]
                              (if open? (or typing v) v))
            :on-focus       (fn [_]
                              (when-not open?
                                (put! focus :focus)
                                (when-not editable? (put! input "")))
                              true)
            :on-mouse-up    (fn [e]
                              (when-not editable?
                                (let [t (.-target e)]
                                  (.setSelectionRange
                                   t 0 (.. t -value -length)))))
            :on-mouse-down  (when-not editable?
                              (fn [_]
                                (if open?
                                  (put! keycodes ESC)
                                  (put! input ""))
                                true))
            :on-blur        #(do
                               (when editable?
                                 (put! autocompleter (edit-fn (.. % -target -value))))
                               (put! blur :blur) true)
            :on-input       #(let [v (.. % -target -value)]
                               (put! input v)
                               (om/set-state! owner :typing v)
                               true)
            :on-key-down    (partial handle-key-down keycodes selecting? hold?)
            :on-mouse-enter #(reset! hold? true)
            :on-mouse-leave #(do (reset! hold? false) true)}]
          (when-not (or open? (blank? value) (= :select-om-all.logic/none value))
            [:span.glyphicon.glyphicon-remove.form-control-feedback
             {:style {:right          34
                      :pointer-events "inherit"
                      :cursor         "pointer"}
              :on-mouse-down
              #(put! autocompleter :select-om-all.logic/none)}])
          [:span.glyphicon.form-control-feedback
           {:class (str "glyphicon-chevron-" (if open? "up" "down"))
            :style {:pointer-events "inherit"
                    :cursor         "pointer"}
            :on-mouse-down
            (fn []
              (if open?
                (reset! hold? false)
                (do
                  (if editable?
                    (put! input
                          (if (= value :select-om-all.logic/none)
                            "" (display-fn value)))
                    (let [t (om/get-node owner "input")]
                      (.setSelectionRange t 0 (.. t -value -length))))
                  (put! refocus true)))
              true)}]])))))