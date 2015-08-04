(ns select-om-all.list
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.match :refer-macros [match]]
            [cljs.core.async
             :refer [>! <! alts! chan put!] :as a]
            [clojure.string :refer [blank?]]
            [om.core :as om]
            [sablono.core :refer-macros [html]]
            [cljsjs.fixed-data-table]
            [select-om-all.utils :refer [handle-key-down]]))

;;; LIST COMPONENT must deal with:
;;; PROPS
;;; flex — proportions of columns, e.g. [1 3 2]
;;; get-cols — function to convert item to vector of columns
;;; height — height of list
;;; STATE
;;; refocus — put! when interaction like scroll has ended and input should regain focus
;;; blur — put! :blur when suggestions list should be dismissed
;;; keycodes — put! raw user input
;;; selecting? — atom of selecting state, to specially handle keys if necessary
;;; hold? — atom, set to true when blur of all other AutoComplete components must be ignored
;;; mousedown/mouseup — put! index of item where event corresponding happened, used to determine if select item or not
;;; hover — put! index of hovered item
;;; highlighted — index of item to highlight
;;; items — suggestions to render
;;; value — selected item
;;; loading? — true when query is processed
;;; width — width of list

;;; Default list component implementation, based on FixedDataTable

(def Table (js/React.createFactory js/FixedDataTable.Table))
(def Column (js/React.createFactory js/FixedDataTable.Column))

(defn cell-getter [k row] (nth row k))

(defn cell-renderer [hover highlighted value mousedown mouseup
                     cell-data cell-data-key row-data row-index]
  (html [:div {:on-mouse-enter #(put! hover row-index)
               :on-mouse-down  #(do
                                  (put! hover row-index)
                                  (put! mousedown row-index)
                                  true)
               :on-mouse-up    #(do
                                  (put! hover row-index)
                                  (put! mouseup row-index)
                                  true)
               :style          {:cursor "pointer"}
               :class          (cond
                                 (= row-data value) "bg-primary"
                                 (= row-index highlighted) "bg-info"
                                 :else nil)}
         ;; when blank put nbsp to prevent cell collapse and bad bg coloring
         (if (blank? cell-data) " " cell-data)]))

(defn FDTList [{:keys [fixed flex get-cols height rowHeight]
                :or {height  200
                     rowHeight 32
                     get-cols identity}} owner]
  (reify
    om/IDisplayName (display-name [_] "AutoComplete Table")
    om/IWillUpdate
    (will-update [_ _ _]
      (a/put! (om/get-state owner :resize!) true))
    om/IDidUpdate
    (did-update [_ _ _]
      (a/put! (om/get-state owner :resize!) true))
    om/IRenderState
    (render-state [_ {:keys [keycodes mousedown mouseup hover highlighted
                             hold? blur refocus items loading? selecting?
                             value width]}]
      (html
       (if (or loading? (zero? (count items)))
         [:div {:style {:border           "solid 1px #d3d3d3"
                        :width            width
                        :background-color "white"}}
          (if loading? "Loading..." "No results")]
         [:div
          {:on-mouse-enter #(reset! hold? true)
           :on-mouse-leave #(do (reset! hold? false) true)
           :on-key-down    (partial handle-key-down keycodes selecting? hold?)
           :on-blur        #(do (put! blur :blur) true)
           :on-mouse-up    #(do (put! refocus true) true)}
          (apply Table #js {:width       width
                            :maxHeight   (if (= height :full)
                                           (-> items count inc (* rowHeight))
                                           height)
                            :rowGetter   #(get-cols (nth items %))
                            :rowsCount   (count items)
                            :scrollToRow highlighted
                            :rowHeight   rowHeight}
                 (let [r (partial cell-renderer hover highlighted
                                  (and value (get-cols value))
                                  mousedown mouseup)]
                   (map #(Column #js {:dataKey        %
                                      :cellRenderer   r
                                      :cellDataGetter cell-getter
                                      :flexGrow       (if (get fixed %) 0 (get flex % 1))
                                      :width          (get fixed % 0)})
                        (-> items first get-cols count range))))])))))

