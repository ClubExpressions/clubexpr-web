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
(def parseLisp (.-parse clubexpr))

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

(defn infix-rendition
  [src inline]
  (try [:> ctx [:> node {:inline inline} (renderLispAsLaTeX src)]]
       (catch js/Object e
         (let [[_err _val] (str/split (.-message e) ":")
               error-style {:style {:color "red"}}  ; TODO CSS
               msg (str (translate-error _err)
                        (if _val (str ": " (translate-val (str/trim _val)))))]
           (if inline
             [:span error-style msg]
             [:div.text-center error-style msg])))))

(defn vec->hiccup
  [expr]
  (if (instance? PersistentVector expr)
    [:li [:span (first expr)]
         [:ul (map vec->hiccup (rest expr))]]
    [:li expr]))

(defn tree-rendition
  [src]
  (let [expr (try (js->clj (parseLisp src))
                  (catch js/Object e (t ["Erreur"])))]
    [:ul.tree (vec->hiccup expr)]))
