(ns examples.basics.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async
             :refer [>! <! alts! chan put! timeout]]
            [goog.net.Jsonp]
            [goog.Uri]
            [om.core :as om]
            [sablono.core :refer-macros [html]]
            [select-om-all.core :refer [AutoComplete]]
            [figwheel.client :as fw]
            [om-i.core]
            [om-i.hacks]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

(def base-url
  "http://en.wikipedia.org/w/api.php?action=opensearch&format=json&search=")

(defn jsonp
  ([uri] (jsonp (chan) uri))
  ([c uri]
   (let [gjsonp (goog.net.Jsonp. (goog.Uri. uri))]
     (.send gjsonp nil #(put! c %))
     c)))

(defn wikipedia-search [query]
  (go (let [;; simulate slow network
            ;_ (<! (timeout 2000))
            resp (<! (jsonp (str base-url query)))]
        (apply mapv vector (rest resp)))))

(defn App [{:keys [choice1 choice2 hl1 hl2 datasource] :as props} owner]
  (reify
    om/IDisplayName (display-name [_] "App")
    om/IRender
    (render [_]
      (html
       [:div.container
        [:h2.text-center "SelectOmAll Demo"]
        [:hr]
        [:p "Select mode, local datasource, value is string.
         On load, 10th value from dataset is preselected.
         Press button to change value programmatically."]
        [:div {:style {:width  400
                       :display "inline-block"}}
         (om/build AutoComplete {:datasource  datasource
                                 :default     (datasource 10)
                                 :value       (:value props)
                                 :get-cols     vector
                                 :placeholder "Placeholder ;-)"})]
        [:button.btn
         {:style {:margin-left 10}
          :on-click #(om/update! props :value (rand-nth datasource))}
         "Pick random item"]
        [:hr]
        [:p "Select mode, remote datasource, value is vector of 3 elements, but rendered only 2 using get-cols.
         In input you see only first column because value is transformed by display-fn."]
        [:div {:style {:width 800
                       :display "inline-block"}}
         (om/build AutoComplete {:completions  wikipedia-search
                                 :flex         [1 2]
                                 :get-cols     (fn [v] [(v 0) (v 2)])
                                 :throttle     750
                                 :placeholder  "Search Wikipedia!"
                                 :height       250
                                 :display-fn   first
                                 :on-change    #(om/update! props :choice1 (first %))
                                 :on-highlight #(om/update! props :hl1 (first %))})]
        [:span " Choice:" choice1]
        [:span " | Highlight:" hl1]
        [:hr]
        [:p "Disabled field."]
        [:div {:style {:width  400
                       :display "inline-block"}}
         (om/build AutoComplete {:datasource   datasource
                                 :editable?    true
                                 :disabled?    true
                                 :placeholder  "Disabled."
                                 :get-cols     vector
                                 :on-change    #(om/update! props :choice2 %)
                                 :on-highlight #(om/update! props :hl2 %)})]

        [:hr]
        [:div {:style {:height 200}}]
        [:p "Edit mode, local datasource, value is string.
        On the bottom of viewport, popup should pop... up ;-)"]
        [:div {:style {:width  400
                       :display "inline-block"}}
         (om/build AutoComplete {:datasource   datasource
                                 :editable?    true
                                 :placeholder  "Type arbitrary value here."
                                 :get-cols     vector
                                 :value        "Clear me!"
                                 :on-change    #(om/update! props :choice2 %)
                                 :on-highlight #(om/update! props :hl2 %)})]
        [:span " Choice:" choice2]
        [:span " | Highlight:" hl2]]))))

(def AZ (mapv js/String.fromCharCode (range 65 91)))

(defn rand-str [n]
  (apply str (repeatedly n #(rand-nth AZ))))

(def app-state
  (atom {:datasource (vec (repeatedly 1000 #(rand-str 20)))}))

(om/root App app-state {:target (js/document.getElementById "app")
                        :instrument (fn [f cursor m]
                                        (om/build* f cursor
                                                   (assoc m
                                                          :descriptor om-i.core/instrumentation-methods)))})
(defonce ___
         (do
           (om-i.hacks/insert-styles)
           (om-i.core/setup-component-stats!)))
;;; aux

(fw/start {:websocket-url "ws://localhost:3449/figwheel-ws"})
