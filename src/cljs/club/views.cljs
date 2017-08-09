(ns club.views
  (:require [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [goog.object :refer [getValueByKeys]]
            [webpack.bundle]
            [club.config :as config]
            [cljs.pprint :refer [pprint]]))

; Placeholder for future translation mechanism
(defn t [[txt]] txt)

(def react-bootstrap (getValueByKeys js/window "deps" "react-bootstrap"))
(def bs-grid (getValueByKeys react-bootstrap "Grid"))
(def bs-row  (getValueByKeys react-bootstrap "Row"))
(def bs-col  (getValueByKeys react-bootstrap "Col"))

(defn src-input
  []
  [:div
   (t ["Code Club: "])
   [:input {:type "text"
            :value @(rf/subscribe [:attempt-code])
            :on-change #(rf/dispatch [:user-code-club-src-change
                                      (-> % .-target .-value)])}]])

(defn rendition
  [src]
  (let [react-mathjax (getValueByKeys js/window "deps" "react-mathjax")
        ctx (getValueByKeys react-mathjax "Context")
        node (getValueByKeys react-mathjax "Node")
        clubexpr (getValueByKeys js/window "deps" "clubexpr")
        renderLispAsLaTeX (.-renderLispAsLaTeX clubexpr)]
    [:> ctx [:> node (renderLispAsLaTeX src)]]))
 
(defn nav-bar
  []
  (let [page @(rf/subscribe [:current-page])
        active #(if (= %1 %2) "active" "")]
    [:nav.navbar.navbar-default.navbar-fixed-top
      [:div.container
        [:div.navbar-header
          [:a.navbar-brand {:href "#/"} (t ["Club des Expressions"])]]
        [:div {:class "navbar-collapse collapse"}
          [:ul {:class "nav navbar-nav"}
           [:li {:class (active page :landing)}
             [:a {:href "#/"} (t ["Accueil"])]]
           [:li {:class (active page :profile)}
             [:a {:href "#/profile"} (t ["Profil"])]]
          ]]]]
     ))

(defn page-landing
  []
  [:div.container
    [:div.jumbotron
      [:h2 (t ["Nouveau venu ?"])]
      [:p (t ["Bonjour, tapez du Code Club ci-dessous pour former une expression mathématique."])]
      [:p (t ["Parmi les commandes disponibles, il y a :"])
          [:code "Somme"] ", "
          [:code "Diff"] ", "
          [:code "Produit"] ", "
          [:code "Produit"] ", "
          [:code "Carre"] ", "
          [:code "Racine"] "."]
      [src-input]
      [rendition @(rf/subscribe [:attempt-code])]]
    [:> bs-grid
      [:> bs-row
        [:h2 (t ["Qu’est-ce que le Club des Expressions ?"])]
        [:> bs-col {:xs 6 :md 6}
          [:h3 (t ["Pour les enseignants"])]
          [:p (t ["Le Club des Expressions vous permet de faire travailler vos élèves sur le sens et la structure des expressions mathématiques."])]
          [:p (t ["Vous vous créez un compte, vous faites créer un compte à vos élèves, et vous pourrez leur attribuer des séries d’expressions à reconstituer."])]]
        [:> bs-col {:xs 6 :md 6}
          [:h3 (t ["Pour les élèves"])]
          [:p (t ["Le Club des Expressions vous permet de travailler sur le sens et la structure des expressions mathématiques."])]
          [:p (t ["Si votre professeur n’utilise pas le Club, vous pourrez quand même obtenir des séries d’expressions à reconstituer. Il est préférable bien sûr que votre professeur vous guide, mettez cette personne au courant !"])]]
      ]]])

(defn main-panel []
  (fn []
    [:div.container-fluid
      [nav-bar]
      [page-landing]
      (when (and false config/debug?) [:pre {:style {:bottom "0px"}}
                                           (with-out-str (pprint @app-db))])
    ]
    ))
