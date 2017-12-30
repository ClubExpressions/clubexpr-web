(ns club.text)

(def error-translations
  {"ReferenceError: 'fetch' is undefined"
   "'fetch' indisponible, mettez à jour votre navigateur ou essayez-en un autre !"})

(def status
"# Statut

Le Club des Expressions est en constante évolution.  N’hésitez pas à signaler
des bugs ou nous faire part de suggestions
[sur Github](https://github.com/ClubExpressions/clubexpr-web/issues/new)
ou [par email](mailto:profgraorg.org@gmail.com).")


(def contact
"# Contact

* Twitter : [@ClubExpr](http://twitter.com/clubexpr)
  (Publication d’une expression intéressante par semaine !)
* Email : [profgra.org@gmail.com](mailto:profgraorg.org@gmail.com)
* Github : [ClubExpressions/clubexpr](https://github.com/ClubExpressions/clubexpr-web/)")

(def thanks
"# Remerciements

Réalisé avec l’aide, aimable autant que redoutable, de :

* Jean-Philippe Rouquès (aide pédagogique)
* Damien Lecan (aide technique)
* tous les collègues et élèves sympathisants  
  (aide morale et premiers tests)
* [tous les logiciels sur lesquels est bâti le Club](https://github.com/ClubExpressions/clubexpr-web#technical-stack)  
  (épaules de géants)")

(def how-to-match
"# Qu’est-ce qu’une bonne reconstitution ?

Le but est seulement de reconstituer « graphiquement » les expressions.

* Il ne faut pas simplifier les expressions.
* « a+b » doit être codée `(Somme a b)` et non `(Somme b a)`.
  Nous félicitons la maîtrise la commutativité mais le Club exige que les
  valeurs soient codées dans l’ordre.
* Une fraction ayant 1 pour numérateur peut être obtenue avec `(Quotient 1 x)`,
  mais aussi avec `(Inverse x)`. Idem pour les carrés et la puissance 2 :
  `(Carré x)` et `(Puissance x 2)` sont équivalents.
* Dans l’expression « 1+2 », le symbole de l’addition est entre le 1 et le 2.
  Avec le Club, on parle du résultat de l’opération donc on utilise le mot
  « Somme » et on le met devant le x et le 1. Donc il faut taper
  `(Somme 1 2)` et non `(1 Somme 2)` !")
