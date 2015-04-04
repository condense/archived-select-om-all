(ns select-om-all.popup
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [goog.positioning.Corner :as corner]
            [goog.ui.Popup]
            [goog.positioning.AnchoredViewportPosition]
            [goog.events :as e]
            [goog.events.EventType :as et]
            [goog.style :as s]
            [cljs.core.async :refer [alts! <!]]
            [om.core :as om]
            [sablono.core :refer-macros [html]]))

(defn show-popup [popup anchor show?]
  (if show?
    (do
      (.setPinnedCorner popup corner/TOP_LEFT)
      (.setPosition popup (goog.positioning.AnchoredViewportPosition.
                           anchor corner/BOTTOM_LEFT))
      (.setVisible popup true))
    (.setVisible popup false)))

(defn Popup [{:keys [show resize parent] :as props} owner]
  (reify
    om/IDisplayName (display-name [_] "Popup")
    om/IDidMount
    (did-mount [_]
      (let [anchor (om/get-node owner "anchor")
            popup (goog.ui.Popup. (om/get-node owner "popup"))
            reposition #(when (.isVisible popup) (.reposition popup))]
        (->> anchor s/getSize .-width (om/set-state! parent :width))
        (doto popup
          (.setVisible false)
          (.setAutoHide false)
          (.setHideOnEscape false))
        (om/set-state! owner {:reposition reposition})
        (e/listen js/window et/RESIZE reposition)
        (go-loop []
          (let [[e c] (alts! [show resize])]
            (when (or (= c show) (.isVisible popup))
              (show-popup popup anchor e))
            (recur)))))
    om/IWillUnmount
    (will-unmount [_]
      (e/unlisten js/window et/RESIZE (om/get-state owner :reposition)))
    om/IRender
    (render [_]
      (html
       [:div
        [:div {:ref "anchor"} (:anchor props)]
        [:div {:ref   "popup"
               :style {:visibility "hidden"
                       :position   "absolute"
                       :z-index    9000}}
         (:popup props)]]))))