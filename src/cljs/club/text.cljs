(ns club.text)

(def how-to-match
"# Qu’est-ce qu’une bonne reconstitution ?

Le but est seulement de reconstituer « graphiquement » les expressions.

* Il ne faut pas simplifier les expressions.
* « a+b » doit être codée `(Somme a b)` et non `(Somme b a)`.
  Nous félicitons la maîtrise la commutativité mais le Club exige que les
  valeurs soient codées dans l’ordre.
* Une fraction ayant 1 pour numérateur peut être obtenue avec `(Quotient 1 x)`,
  mais aussi avec `(Inverse x)`.
* Dans l’expression « 1+2 », le symbole de l’addition est entre le 1 et le 2.
  Avec le Club, on parle du résultat de l’opération donc on utilise le mot
  « Somme » et on le met devant le x et le 1. Donc taper il faut taper
  `(Somme 1 2)` et non `(1 Somme 2)` !")
