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

(defn natureFromLisp
  [src]
  (try (-> src parseLisp first)
       (catch js/Object e "error")))

(def available-ops
  [:span (t ["Commandes disponibles :"])
    [:code "Somme"] ", "
    [:code "Diff"] ", "
    [:code "Produit"] ", "
    [:code "Quotient"] ", "
    [:code "Opposé"] ", "
    [:code "Inverse"] ", "
    [:code "Carré"] ", "
    [:code "Puissance"] " et "
    [:code "Racine"] "."])

(def all-exprs
  ["(Somme 1 2)"
   "(Somme a 1)"
   "(Somme 1 a)"
   "(Somme a b)"
   "(Diff 1 2)"
   "(Diff a 1)"
   "(Diff 1 a)"
   "(Diff a b)"
   "(Produit 1 2)"
   "(Produit 1 a)"
   "(Produit a 1)"
   "(Produit a b)"
   "(Produit a a)"
   "(Quotient 1 2)"
   "(Quotient a 1)"
   "(Quotient 1 a)"
   "(Quotient a b)"
   "(Carré 1)"
   "(Carré a)"
   "(Produituissance a 1)"
   "(I a)"
   "(Opposé a)"
   "(Produit 1 (Somme a 2))"
   "(Produit 1 (Diff a 2))"
   "(Somme 1 (Produit 2 a))"
   "(Somme 1 (Opposé 2))"
   "(Somme 1 (Opposé a))"
   "(Diff 1 (Opposé 2))"
   "(Diff 1 (Opposé a))"
   "(Diff (Produit 1 a) 2)"
   "(Somme 1 (Quotient a 2))"
   "(Diff (Opposé a) 1)"
   "(Opposé (Diff a 1))"
   "(Diff 1 (Somme 2 a))"
   "(Somme (Diff a 1) 2)"
   "(Quotient (Somme a b) 1)"
   "(I (Somme a 1))"
   "(Quotient (Produit 1 a) 2)"
   "(Opposé (Produit 1 a))"
   "(Opposé (I a))"
   "(Opposé (Quotient a b))"
   "(Opposé (Carré a))"
   "(Carré (Opposé a))"
   "(Carré (Somme 1 a))"
   "(Somme 1 (Carré a))"
   "(Produit 1 (Carré a))"
   "(Carré (Produit 1 a))"
   "(Quotient (Carré a) 1)"
   "(Carré (Quotient a 1))"
   "(Produit a (Produit 1 a))"
   "(Carré (I a))"
   "(I (Carré a))"
   "(Racine (Carré a))"
   "(Racine (Somme (Carré a) (Carré b)))"
   "(Produit a (Somme 1 (Produit 2 a)))"
   "(Diff (Produit 1 a) (Somme 2 a))"
   "(Diff (Produit 1 a) (Produit 2 a))"
   "(Diff (Produit 1 (Diff a 2)) 3)"
   "(Diff 1 (Produit 2 (Somme a 3)))"
   "(Somme (Diff (Produit 1 a) a) 2)"
   "(Somme (Diff a (Quotient a 1)) 2)"
   "(Somme (Diff (Carré a) a) 1)"
   "(Diff (Carré (Somme a 1)) 2)"
   "(Produit (Somme a 1) (Diff a 2))"
   "(Somme (Opposé (Carré a)) 1)"
   "(Somme (Produit 1 (Carré a)) a)"
   "(Somme (Diff (Opposé a) a) 1)"
   "(Somme (Diff a (Opposé a)) 1)"
   "(Somme (Diff (Carré a) a) 1)"
   "(Somme (Diff a (Produit 1 (Diff a 2))) 3)"
   "(Quotient (Somme a 1) (Diff a 2))"
   "(Somme 1 (Quotient 2 (Somme a 3)))"
   "(Racine (Somme (Carré (Diff 1 2)) (Carré (Somme 3 4))))"
   "(Produit 1 (Somme 2 3) (Somme 4 5))"
   "(Somme (Carré a) (Produit 1 a) 2)"
   "(Diff (Produit 1 (Carré (Somme a 2))) 3)"
   "(Produit 1 (Somme 2 3) (Diff 4 5))"
   "(Diff (Carré (Somme a 1)) (Carré a))"
   "(Somme (Produit 1 (Carré a)) (Produit 2 a) 3)"
   "(Quotient (Somme (Produit 1 a) 2) (Somme (Produit 3 a) 4))"
   "(Somme 1 (Quotient 2 (Somme (Produit 3 a) 4)))"
   "(Opposé (Quotient (Diff (Produit a (Racine b)) (Puissance (Inverse c) d)) (Carré (Somme x y z))))"])

(defn translate-error
  [err]
  (case err
    "Empty expr"         (t ["Expression vide"])
    "Missing starting (" (t ["Première « ( » manquante"])
    "Already closed"     (t ["Expression déjà fermée"])
    "Missing )"          (t ["Au moins une « ) » manquante"])
    "Double ("           (t ["Erreur : « (( »"])
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

(defn expr-error
  [src]
  (try (do (renderLispAsLaTeX src) "")
       (catch js/Object e
         (let [[_err _val] (str/split (.-message e) ":")
               msg (str (translate-error _err)
                        (if _val (str ": " (translate-val (str/trim _val)))))]
           msg))))

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

(defn correct
  [src1 src2]
  (= (renderLispAsLaTeX src1) (renderLispAsLaTeX src2)))
