(ns club.expr
  (:require [goog.object :refer [getValueByKeys]]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.string :as str]
            [club.utils :refer [t
                                jsx->clj
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

(defn translate-error
  [err]
  (case err
    "Empty expr"         (t ["Expression vide"])
    "Missing starting (" (t ["Première « ( » manquante"])
    "Trailing )"         (t ["Trop de « ) »"])
    "Missing )"          (t ["Au moins une « ) » manquante"])
    "Double ("           (t ["Erreur : « (( »"])
    "More than one root" (t ["Expression déjà fermée"])
    "Missing cmd"        (t ["Une commande est manquante"])
    "Unknown cmd"        (t ["Commande inconnue"])
    (t [err])))  ; beware: some msg are like "Somme: nb args < 2"

(defn translate-val
  [value]
  (case value
    "nb args < 1"  (t ["nbre d’opérandes < 1"])
    "nb args > 1"  (t ["nbre d’opérandes > 1"])
    "nb args < 2"  (t ["nbre d’opérandes < 2"])
    "nb args > 2"  (t ["nbre d’opérandes > 2"])
    (t [value])))

(defn rendition-block
  [src]
  (try [:> ctx [:> node (renderLispAsLaTeX src)]]
       (catch js/Object e
         (let [[_err _val] (str/split (.-message e) ":")]
           [:div.text-center
             {:style {:color "red"}}  ; TODO CSS
             (str (translate-error _err)
                  (if _val (str ": " (translate-val (str/trim _val)))))]))))

(defn rendition
  [src]
  (let [react-mathjax (getValueByKeys js/window "deps" "react-mathjax")
        ctx (getValueByKeys react-mathjax "Context")
        node (getValueByKeys react-mathjax "Node")
        renderLispAsLaTeX (.-renderLispAsLaTeX clubexpr)]
    [:> ctx [:> node {:inline true} (renderLispAsLaTeX src)]]))
