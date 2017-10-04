(ns club.expr
  (:require [goog.object :refer [getValueByKeys]]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.string :as str]
            [club.utils :refer [jsx->clj
                                js->clj-vals
                                groups-option
                                scholar-comparator
                                FormControlFixed]]))

(def clubexpr (getValueByKeys js/window "deps" "clubexpr"))
(def renderLispAsLaTeX (.-renderLispAsLaTeX clubexpr))

(def react-mathjax (getValueByKeys js/window "deps" "react-mathjax"))
(def ctx (getValueByKeys react-mathjax "Context"))
(def node (getValueByKeys react-mathjax "Node"))

(defn populate-properties
  [expr-obj]
  (let [properties (.-properties clubexpr)
        expr-obj-clj (-> expr-obj js->clj keywordize-keys)
        expr-properties (-> expr-obj
                            (getValueByKeys "expr")
                            properties
                            jsx->clj
                            js->clj-vals
                            )]
    (assoc expr-obj-clj :properties expr-properties)))

(def reified-expressions
  (map populate-properties (.-expressions clubexpr)))

(defn rendition-block
  [src]
  (try [:> ctx [:> node (renderLispAsLaTeX src)]]
       (catch js/Object e
         (let [[_err _val] (str/split (.-message e) ":")]
           [:div.text-center
             {:style {:color "red"}}  ; TODO CSS
             (str _err (if _val (str ": " _val)))]))))

(defn rendition
  [src]
  (let [react-mathjax (getValueByKeys js/window "deps" "react-mathjax")
        ctx (getValueByKeys react-mathjax "Context")
        node (getValueByKeys react-mathjax "Node")
        renderLispAsLaTeX (.-renderLispAsLaTeX clubexpr)]
    [:> ctx [:> node {:inline true} (renderLispAsLaTeX src)]]))
