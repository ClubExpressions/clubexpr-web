(ns club.expr
  (:require [goog.object :refer [getValueByKeys]]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.string :as str]
            [club.utils :refer [t
                                jsx->clj
                                js->clj-vals
                                groups-option
                                FormControlFixed]]))

(def clubexpr (getValueByKeys js/window "deps" "clubexpr"))
(def available-ops (js->clj (.-operations clubexpr)))
(def natureFromLisp (.-natureFromLisp clubexpr))
(def renderExprAsLisp (.-renderExprAsLisp clubexpr))
(def renderLispAsLaTeX-js (.-renderLispAsLaTeX clubexpr))
(defn renderLispAsLaTeX
  [src]
  (try (-> src renderLispAsLaTeX-js js->clj keywordize-keys)
       (catch js/Object e {:error (.-message e)})))
(def parseLisp (.-parse clubexpr))
(def replaceValuesWith (.-replaceValuesWith clubexpr))
(def replaceValues #(replaceValuesWith %1 (clj->js %2)))

(def tex-inline (getValueByKeys js/window "deps" "react-katex" "InlineMath"))
(def tex-block (getValueByKeys js/window "deps" "react-katex" "BlockMath"))

(defn parseLispNoErrorWhenEmpty
  [src]
  (try (getValueByKeys (parseLisp src) "tree")
       (catch js/Object e
         (if (= "Error: Empty expr" (str e))
                 []
                 (throw e)))))

(defn get-val-in-lisp
  [src path]
  (let [expr (parseLispNoErrorWhenEmpty src)]
      (get-in expr path)))

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
  (let [expr-wrapper #(clj->js {"expr" (getValueByKeys (parseLisp %) "tree")})
        series-wrapper #(map expr-wrapper %)
        demo
          ["(Produit 5 (Somme x 2))"
           "(Somme 1 (Produit 2 x))"
           "(Diff x (Somme y 1))"
           "(Produit 2 (Somme (Produit 3 a) 4))"
           "(Produit pi (Quotient R 2))"]
        seconde-serie-1
          ["(Produit 3 x)"
           "(Quotient R 4)"
           "(Produit 10 (Diff x 2))"
           "(Somme (Produit 10 a) b)"
           "(Diff 1 (Opposé x))"
           "(Quotient (Produit a b) c)"
           "(Produit a (Quotient b c))"
           "(Diff (Somme (Produit 10 a) b) a)"
           "(Diff (Diff (Somme (Produit 10 a) b) a) b)"]
        algebre
          ["(Somme x (Produit 2 x))"
           "(Somme 1 (Produit 4 (Somme x 3)))"]
        seconde-second-degre
          ["(Carré x)"
           "(Carré (Somme a b))"
           "(Somme (Carré a) (Carré b))"
           "(Carré (Diff a b))"
           "(Produit (Somme 5 m) (Somme 5 n))"
           "(Produit 3 (Carré x))"
           "(Carré (Produit 3 x))"
           "(Produit (Diff x 2) (Somme x 3))"
           "(Produit (Somme a b) (Diff a b))"
           "(Somme (Produit 10 (Somme m n))
                   (Produit (Diff 5 m) (Diff 5 n)))"]
        ]
  (map populate-properties (concat
                             (series-wrapper demo)
                             (series-wrapper algebre)
                             (series-wrapper seconde-serie-1)
                             (series-wrapper seconde-second-degre)
                             (.-expressions clubexpr)))))

(def all-exprs
  ["(Somme 1 2)"

   "(Produit 5 (Somme x 2))"
   "(Somme 1 (Produit 2 x))"
   "(Diff x (Somme y 1))"
   "(Produit 2 (Somme (Produit 3 a) 4))"
   "(Produit pi (Quotient R 2))"

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
   "(Puissance a 1)"
   "(Inverse a)"
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
   "(Inverse (Somme a 1))"
   "(Quotient (Produit 1 a) 2)"
   "(Opposé (Produit 1 a))"
   "(Opposé (Inverse a))"
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
   "(Carré (Inverse a))"
   "(Inverse (Carré a))"
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

(defn translate-msg
  [err]
  (case err
    "Empty expr"         (t ["Expression vide"])
    "Missing starting (" (t ["Première « ( » manquante"])
    "Already closed"     (t ["Expression déjà fermée"])
    "Missing )"          (t ["Au moins une « ) » manquante"])
    "Double ("           (t ["Erreur : « (( »"])
    "Missing cmd"        (t ["Une commande est manquante"])
    "Unknown cmd"        (t ["Commande inconnue"])
    "Invalid char"       (t ["Caractère non autorisé"])
    "Bad leaf"           (t ["Opérande non autorisée"])
    "nb args < 1"
      (t ["besoin d’exactement une opérande, vous n’en avez pas fourni"])
    "nb args > 1"
      (t ["besoin d’exactement une opérande, vous en avez fourni trop"])
    "nb args < 2"
      (t ["besoin d’au moins deux opérandes, vous n’en avez pas fourni assez"])
    "nb args > 2"
      (t ["besoin d’au plus deux opérandes, vous en avez fourni trop"])
    (t [err])))  ; beware: some msg are like "Somme: nb args < 2"

(defn split-and-translate
  [msg]
  (let [[_err _val] (str/split msg ":")]
    (str (translate-msg _err)
         (if _val (str " : " (translate-msg (str/trim _val)))))))

(defn warning-li
  [warning]
  [:li (split-and-translate warning)])

(defn format-warnings
  [warnings]
  [:ul {:id "club-warning"} (doall (map warning-li warnings))])

(defn infix-rendition
  [src inline]
  (let [{:keys [latex warnings error]} (renderLispAsLaTeX src)]
    (if error
      (let [error-style {:style {:min-height "3.5em"
                                 :padding-top "1em"
                                 :color "red"}}  ; TODO CSS
            msg (split-and-translate error)]
        (if inline
          [:span error-style msg]
          [:div.text-center error-style msg]))
      [:div
        [:> (if inline tex-inline tex-block) {:math latex}]
        (if (not (empty? warnings)) (format-warnings warnings))]
      )))

(defn expr-error
  [src]
  (try (do (renderLispAsLaTeX src) "")
       (catch js/Object e
         (let [[_err _val] (str/split (.-message e) ":")
               msg (str (translate-msg _err)
                        (if _val (str ": " (translate-msg (str/trim _val)))))]
           msg))))

(defn vec->list-as-hiccup
  [expr]
  (if (instance? PersistentVector expr)
    [:li [:span (first expr)]
         (apply vector (cons :ul (map vec->list-as-hiccup (rest expr))))]
    [:li expr]))

(defn tree-size
  [tree]
  (if (string? (second tree))
    1
    (let [sub-trees (-> tree rest rest first rest)]
    (+ 1 (reduce + (map tree-size sub-trees))))))

(defn tree-rendition
  [src]
  (let [expr (try (-> src parseLisp js->clj keywordize-keys :tree)
                  (catch js/Object e (t ["Erreur"])))
        tree (vec->list-as-hiccup expr)
        size (tree-size tree)
        class-suffix (if (> size 15) "big" "small")]
    [:ul.tree {:class (str "tree-" class-suffix)} tree]))

(defn correct
  [src1 src2]
  (let [latex1 (-> src1 renderLispAsLaTeX :latex)
        latex2 (-> src2 renderLispAsLaTeX :latex)]
    (= latex1 latex2)))

(defn correct-nature
  [target-src attempt-src]
  (let [target (natureFromLisp target-src)
        attempt (natureFromLisp attempt-src)]
    (or
      (empty? attempt)
      (and (= "Inverse"  target)
           (= "Quotient" attempt))
      (and (= "Quotient" target)
           (= "Inverse"  attempt))
      (and (= "Carré"     target)
           (= "Puissance" attempt))
      (and (= "Puissance" target)
           (= "Carré" attempt))
      (= target attempt))))
