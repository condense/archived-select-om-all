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

(defn toggle-popup [popup anchor show?]
  (if show?
    (do
      (.setPinnedCorner popup corner/TOP_LEFT)
      (.setPosition popup (goog.positioning.AnchoredViewportPosition.
                           anchor corner/BOTTOM_LEFT))
      (.setVisible popup true))
    (.setVisible popup false)))

(defn Popup [{:keys [open? resize-ch set-width-fn]
              :or {set-width-fn identity}
              :as props} owner]
  (reify
    om/IDisplayName (display-name [_] "Popup")
    om/IDidMount
    (did-mount [_]
      (let [anchor (om/get-node owner "anchor")
            popup (goog.ui.Popup. (om/get-node owner "popup"))
            reposition #(when (.isVisible popup) (.reposition popup))]
        (-> anchor s/getSize .-width set-width-fn)
        (doto popup
          (.setVisible false)
          (.setAutoHide false)
          (.setHideOnEscape false))
        (om/set-state! owner {:reposition reposition
                              :instance popup})
        (e/listen js/window et/RESIZE reposition)
        (go-loop []
          (when-let [_ (<! resize-ch)]
            (when (.isVisible popup)
              (toggle-popup popup anchor true))
            (recur)))))
    om/IDidUpdate
    (did-update [_ _ _]
      (toggle-popup (om/get-state owner :instance)
                    (om/get-node owner "anchor") open?))
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