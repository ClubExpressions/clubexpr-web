# Suite à l’entrevue avec le CTO du Club

## Étapes à suivre

1. Archiver les données
1. Configurer le serveur pour autoriser des connexions via oauth:
   une section par service (google, facebook…) avec openid connect multiauth
1. Configurer auth0 en ajoutant une autre app (regular web app, respons-type:
   code), authorization code grant flow
1. du côté de mon app JS: récupérer l’access token

## Diagramme de l’authentification

```
user          navig         web app         kinto          auth0
 |------1------>|              |              |              |
 |              |------2------>|              |              |
 |              |              |--+           |              |
 |              |              |  |3          |              |
 |              |              |<-+           |              |
 |              |              |------4------>|              |
 |              |              |              |--+           |
 |              |              |              |  |5          |
 |              |              |              |<-+           |
 |              |              |              |------6------>|
 |              |              |              |              |--+
 |              |              |              |              |  |7
 |              |              |              |              |<-+
 |              |              |              |<-----8-------|
 |              |              |              |--+           |
 |              |              |              |  |9          |
 |              |              |              |<-+           |
 |              |<--------------10------------|              |
 |              |------11----->|              |              |
 |              |              |--+           |              |
 |              |              |  |12         |              |
 |              |              |<-+           |              |
 |              |<-----13------|              |              |
 |<-----14------|              |              |              |

  1: appui sur le bouton de connexion
  2: le navigateur déclenche un evt de clic dans mon appli
  3: calcul de la requête
  4: redirection
  5: kinto réagit en fonction de la conf
  6: redirection
  7: auth0 réagit
  8: redirection
  9: ?
 10: redirection
 11: le navigateur déclenche un evt de navigation dans mon appli
 12: récupération de l’access token pour l’utiliser dans les prochaines requêtes
 13: affichage « Vous êtes connecté(e) »
 14: radiations electro-magnétiques (lumière)
```

## Diagramme pour les autres requêtes

```
user          navig         web app         kinto          auth0
 |------1------>|              |              |              |
 |              |------2------>|              |              |
 |              |              |--+           |              |
 |              |              |  |3          |              |
 |              |              |<-+           |              |
 |              |              |------4------>|              |
 |              |              |              |--+           |
 |              |              |              |  |5          |
 |              |              |              |<-+           |
 |              |              |<-----6-------|              |
 |              |<-----7-------|              |              |
 |<-----8-------|              |              |              |

 1: manip de l’UI de l’appli web
 2: le navigateur déclenche un evt de clic dans mon appli
 3: réaction de l’appli + interaction éventuelle avec kinto
    en lui passant l’access token
 4: requête si besoin
 5: kinto calcule la réponse
 6: réponse
 7: l’appli wew réagit et construit une vue
 8: radiations electro-magnétiques (lumière)
```

## Derniers points:

* si expiration (401), afficher un message et relancer l’authentification
* relire la doc des permissions de Kinto (granularité à la donnée?)
* que faire des données existantes?
