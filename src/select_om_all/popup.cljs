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
  (.setVisible popup false)
  (when show?
    (.setPinnedCorner popup corner/TOP_LEFT)
    (.setPosition popup (goog.positioning.AnchoredViewportPosition.
                         anchor corner/BOTTOM_LEFT))
    (.setVisible popup true)))

(defn component->owner
  "DANGER: get owner of Om component."
  [c]
  (.. c -props -children -owner))

(defn component->node
  "DANGER: get node of Om component."
  [c]
  (om/get-node (component->owner c)))

(defn Popup [{:keys [anchor popup show resize]} owner]
  (reify
    om/IDisplayName (display-name [_] "Popup")
    om/IDidMount
    (did-mount [_]
      (->> anchor component->node s/getSize .-width
           (om/set-state! (component->owner popup) :width))
      (let [anchor (component->node anchor)
            popup (goog.ui.Popup. (component->node popup))
            reposition #(when (.isVisible popup) (.reposition popup))]
        (doto popup
          (.setVisible false)
          (.setAutoHide false)
          (.setHideOnEscape false))
        (om/set-state! owner {:resize reposition})
        (e/listen js/window et/RESIZE reposition)
        (go-loop []
          (let [[e c] (alts! [show resize])]
            (when (or (= c show) (.isVisible popup))
              (show-popup popup anchor e))
            (recur)))))
    om/IWillUnmount
    (will-unmount [_]
      (e/unlisten js/window et/RESIZE (om/get-state owner :resize)))
    om/IRender
    (render [_] (html [:div anchor popup]))))