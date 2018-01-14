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

(def several-schools
"## Plusieurs établissements

Les collègues sur plusieurs établissements doivent pour l’instant créer
plusieurs comptes, un pour chaque établissement.")

(def multi-accounts
"## Comptes élèves multiples

Il peut arriver que des élèves perdent leurs informations de connexion et
doivent créer un autre compte. Il y a plusieurs moyens de reconnaître le compte
à garder parmi les autres. Les comptes obsolètes peuvent être rangés dans un
groupe qui ne sera pas utilisé, que vous pouvez appeler par exemple
« poubelle », ou « zzz_poubelle » pour qu’il soit classé à la fin.

Dans la partie « Groupes », les élèves sans groupe sont répertoriés dans un
groupe qui apparaît tout en haut de la liste, appelé « Sans groupe ». Si un
élève apparaît dans ce groupe, c’est qu’il faut sans doute utiliser le compte
qui vient d’apparaître plutôt que l’ancien. Il faut donc passer l’ancien compte
dans le groupe « poubelle » puis le nouveau dans le bon groupe.

Si au moment des inscriptions vous avez passé plusieurs comptes d’un même élève
dans un groupe, le paragraphe précédent ne peut pas s’appliquer. Pour gérer
cette situation, des symboles ☺ ont été placés devant les noms des élèves. En
les survolant ou en cliquant dessus vous obtiendrez un identifiant technique
qui vous permettra de faire le lien entre un nom et l’avancement d’une série.  
Exemple : j’ai deux comptes pour un même élève, les deux comptes ayant les
identifiants `a1b2...` et `789f...` (survoler ou cliquer sur ☺). Dans la page
résultats, on observe que l’un des comptes semble être moins actif que l’autre.
On note son identifiant :  c’est ce compte-ci qu’il faudra passer dans le
groupe « poubelle ».")
