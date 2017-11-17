# Club des Expressions v2

See the running app here:  
<http://futur.expressions.club>

## Technical stack

### Overview

* We build an [SPA](https://en.wikipedia.org/wiki/Single-page_application)
  (see [this article](https://johnpapa.net/pageinspa/) for a demythification)
* using [re-frame](https://github.com/Day8/re-frame/) which is a
  * [ClojureScript](https://clojurescript.org/), which is
    * [Clojure](https://clojure.org/) (a clever
      [Lisp](https://en.wikipedia.org/wiki/Lisp_(programming_language))
      on the [JVM](https://en.wikipedia.org/wiki/Java_virtual_machine))
    * that compiles to [JavaScript](https://en.wikipedia.org/wiki/JavaScript)
      (the language animating browsers)
  * [framework](https://en.wikipedia.org/wiki/Software_framework) built around
  * [Reagent](https://github.com/reagent-project/reagent) which is a
    ClojureScript interface to
    * [React](https://facebook.github.io/react/) using the
    * [Hiccup](https://github.com/weavejester/hiccup)-like syntax.
  * We have the powers of all the packages available at
    [npm](http://npmjs.com/), a huge software repository for JavaScript.
* Authentication thanks to [Auth0](http://auth0.com/).
* Persistency thanks to [Kinto](kinto.readthedocs.io/).

[Figwheel](https://github.com/bhauman/lein-figwheel) allows us to live code.

### Other libraries

* [cljs-time](https://github.com/andrewmcveigh/cljs-time)
* npm modules
  * Working integration of npm packages in ClojureScript (done
    [this way](http://blob.tomerweller.com/reagent-import-react-components-from-npm)
    but [there may be a cleaner way](https://clojurescript.org/news/2017-07-12-clojurescript-is-not-an-island-integrating-node-modules))
    * [react-katex](https://www.npmjs.com/package/react-katex)
      (math typesetting in the browser)
    * [clubexpr](https://www.npmjs.com/package/clubexpr) (math expressions)
    * [Kinto](https://www.npmjs.com/package/kinto) (persistency)
    * [Auth0](https://www.npmjs.com/package/auth0) (authentication)
    * [React-Bootstrap](https://www.npmjs.com/package/react-bootstrap)
      instead of just the `bootstrap` package (nice UI in the browser)
    * [react-select](https://www.npmjs.com/package/react-select)
    * [rc-slider](https://www.npmjs.com/package/rc-slider)
    * [react-checkbox-group](https://www.npmjs.com/package/react-checkbox-group)
    * [react-drag-sortable](https://www.npmjs.com/package/react-drag-sortable)
    * [react-datetime](https://www.npmjs.com/package/react-datetime)
    * [moment](https://www.npmjs.com/package/moment)
    * [CodeMirror (2)](https://www.npmjs.com/package/react-codemirror2) (text editor)
  * Modules that are planned to be used:
    * [react-table](https://www.npmjs.com/package/react-table) (tabular presentation)
    * [Tempura](https://github.com/ptaoussanis/tempura) (i18n)
    * [Timbre](https://github.com/ptaoussanis/timbre) (logging)
    * [re-learn](https://github.com/oliyh/re-learn) (quick interactive tuto for
      users)

## Dev setup

### Leiningen (the command is lein)

Install the Clojure/ClojureScript package manager, build frontend…

Install as told at [leiningen.org/](https://leiningen.org/). Yes, it a single
script which, when run, will install everything it needs.

It reads the `project.clj` lying at the root dir of the project.

### Get the source

`git clone git@github.com:ClubExpressions/clubexpr-web`

### Docker

Install Docker Community Edition with official guide: https://docs.docker.com/engine/installation/

Them install Docker Compose: https://docs.docker.com/compose/install/

### Host

Edit your `/etc/hosts` file and add:

  127.0.0.1   expressions.club.local

Save and quit.

### Dev build and source watch

1. `cd` to the root of this project (where this README exists)
2. Run Postgresql and Kinto at once: `docker-compose up`
3. In another terminal, run `lein do clean, figwheel`  to compile the app and start up
   figwheel hot-reloading,
4. Open `https://expressions.club.local/` to see the app (accept the self-signed certificate forever)

While step 3 is running, any changes you make to the ClojureScript
source files (in `src`) will be re-compiled and reflected in the running
page immediately.

### Kinto

Client side, we use the official npm package
[kinto](https://www.npmjs.com/package/kinto) (instead of the other official
[kinto-http](https://www.npmjs.com/package/kinto-http)).

The dev build points to `https://expressions.club.local/v1` and the prod build to
`http://localhost:8887/v1` (thanks to

    :closure-defines {goog.DEBUG false}

in `project.clj` whose value is in turn stored in the `debug?` var).

Our own instance is live [here](https://kinto.expressions.club/v1/).

#### General instructions to install the Kinto server without Docker

* `sudo apt-get install python3-dev`
* `sudo pip3 install kinto`
* for use with PG :
  * `sudo apt-get install postgresql`
  * `sudo pip3 install psycopg2 SQLAlchemy zope.sqlalchemy`

#### Local kinto

There are some `kinto.ini` files in the repo, just do  
`kinto start --ini kinto_XXX.ini`.

#### Official test instance

* Everything works ok with Firefox.
* /!\ Our Github page use https, the kinto test instance too, but Chrome
may complain about security issues.

#### Prod kinto

Attempts to use [kinto-alwaysdata](https://github.com/kinto/kinto-alwaysdata),
but encountered
[issues](https://github.com/Kinto/kinto-alwaysdata/issues/created_by/grahack).

Some Kinto author wrote
[very useful instructions](http://www.servicedenuages.fr/kinto-installation-alwaysdata-manuelle).

#### Kinto admin (like phpMyAdmin)

Go to one of these pages :

* <https://kinto.github.io/kinto-admin/>
* <https://kinto.expressions.club/v1/admin/>

then configure your Kinto instance.

## Dev notes

### Using an npm module in the ClojureScript build

The POC was done with
[react-mathjax](https://www.npmjs.com/package/react-mathjax).

From [this blog post](http://blob.tomerweller.com/reagent-import-react-components-from-npm),
where you'll find where I got the content of `package.json`,
`webpack.config.js` and `src/js/main.js`.

    $ vim package.json  # or use https://github.com/RyanMcG/lein-npm ?
    $ npm install
    $ vim webpack.config.js

I attempted to add `resources` before `public/js` but `lein clean` deleted
`bundle.js`! Running `npm run build` after `lein clean` was not good either
(can't remember why).

    $ mkdir src/js
    $ vim src/js/main.js  # remember to change player -> mathjax or whatever
    $ sudo npm install -g webpack
    $ npm run build
    $ vim project.clj  # to add exclusions of reagent
    $ vim project.clj  # and add the libs we are trying to use
                       # see https://clojurescript.org/reference/compiler-options
                       # for hints about the correct position of :foreign-libs
    $ lein clean && lein figwheel
    $ vim src/truc/core.cljs  # beware, there's a typo, use 'r' not 'reagent'

The commit of this addition in the README should be `POC mathjax` and also
contains all the relevant changes.

### Simple steps for adding another node package thereafter

    $ vim package.json  # just add one line
    $ npm install
    $ vim src/js/main.js  # add one line
    $ npm run build
    $ lein clean && lein figwheel
    $ vim src/truc/core.cljs  # require and use your package or component

### Simple steps for updating a node package

    $ vim package.json  # just add one line
    $ npm install
    $ npm run build
    $ git add package.json public/js/bundle.js
    $ git commit -m "Update name_of_the_package version_number"
    $ stop figwheel
    $ lein clean && lein figwheel  # maybe hard refresh to be sure
    $ use the new version

## Prod build

The steps :


	# kill lein with `Ctrl`+`C`
	git co master
    #  build a prod version (`:prod` in `project.clj`)
	lein do clean, cljsbuild once min
    # use the temporary `prod` branch
	git br -D prod
	git co -b prod
	git add resources/public/js/compiled/app.js -f
	git commit -m "Production tip"
	git push origin prod -f
	git co master
    # then on the server
	ssh server
	cd www/domains/expressions.club/repo
	git br prod_$(date +%Y%m%d-%H%M)
	git reset --hard HEAD\^
	git pull origin prod

We use [lein-git-version](https://github.com/cvillecsteele/lein-git-version)
to hardcode the precise commit used in prod..

# State and persistency structure

In a re-frame app, the state lies in the `app-db` atom, also referenced as
`db` when being a parameter to handlers.

Data is sent back and forth between the state and the persistency layer,
which is a Kinto instance with those collections :

* `users`
* `series`
* `groups`
* `works`
* `attempts`
* `progress`

**Warning** :  
Different records can have the same kinto id and in different collections.  
For this reason, beware of any `DELETE WHERE id=` from `psql`.

cohérence nettoyages data-from-js-obj: dans db, sub ou events

## First level keys

* `:current-page` keyword identifying the current page
* `:attempt-code` the Club Code for the landing page, as a string
* `:game-idx` the index (number) of the game expression to display in the
  landing page
* `:authenticated` boolean, is the visitor authenticated?
* [`:auth-data`](#auth-data) map related to authentication
* [`:profile-page`](#profile-page) map containing user data
* `:current-series-id` string identifying the current series
* [`:current-series`](#current-series) map containing the data of the current-series
* [`:series-page`](#series-page) vector containing the series of the user
* `:editing-series` boolean : are we editing the current series?
* [`:series-filtering`](#series-filtering) map with `:filters`, data related to filters and
  filtered `:expressions`
* [`:groups-page`](#groups-page) map containing data about the groups which the scholars of
   the user belong to
* [`:works-teacher-page`](#teacher-works) vector containing the works
  scheduled by the user (teacher)
* [`:works-scholar-page`](#scholar-works) vector containing the works
  scheduled for the user (scholar)
* `::scholar-working` boolean : is a scholar working?
* `::scholar-work-id` string : the id of the work on which a scholar is working
* [`::scholar-work`](#scholar-work) map containing data about the core of the
  Club: a scholar working on a series of exprs


Only nested data is explained below (links).

## Auth data

    Under the :auth-data key of the state:    | Records in the user collection:
                                              |
    {                                         | {
      :kinto-id "d6e...487",                  |   "auth0-id": "auth0|597...e79",
      :auth0-id "google-oauth2|104...035",    |   "lastname": "Debru",
      :access-token "vz5...j2f",  ; not used  |   "firstname": "Samantha"
      :expires-at "1509269446300" ; not used  |   "quality": "scholar",
    }                                         |   "school": "fake-id-0441993C",
                                              |   "teacher": "d6ee...e487",
                                              | }

## Profile page

    Under the :profile-page key:       |     Records in the user collection:
                                       |
    {                                  |     {
      :lastname "Debru",               |       "auth0-id": "auth0|597...e79",
      :firstname "Samantha"            |       "lastname": "Debru",
      :quality "teacher",              |       "firstname": "Samantha"
      :school "fake-id-0441993C",      |       "quality": "scholar",
      :teacher "d6ee...e487",          |       "school": "fake-id-0441993C",
      :teachers-list []  ; UI only     |       "teacher": "d6ee...e487",
    }                                  |     }

## Current series

Under the `:current-series` key of the state:

    {:title "A title", :desc "A desc", :exprs ["(Somme 1 2)", "(Somme a 1)"]}

Each series is stored in Kinto as:

    {
      "owner-id": "d6ee80e6-544b-425a-9d15-8d234174e487",
      "series": {
          "desc": "Preums pour tester le système",
          "exprs": ["(Somme 1 2)", "(Somme a 1)", "(Somme 1 a)"],
          "title": "Découverte"}
    }

## Series page

Under the `:series-page` key of the state, all the exprs owned by the user:

    [
      {
        :id "164399c5-65e5-43db-a020-c0fcc62228ae",
        :series
          {
           :title "A title",
           :desc "A nice description",
           :exprs ["(Produit a b)" "(Quotient 1 2)" "(Diff a b)"]
          }
      }
      ...
      {
        :id "e309b2e2-7929-4d61-9339-ed4e68176bef",
        :series
          {
            :title "Découverte",
            :desc "Une première pour tester le système",
            :exprs ["(Somme 1 2)" "(Somme a 1)" "(Somme 1 a)"]
          }
      }
    ]

## Series filtering

Under the `:series-filtering` key of the state (not stored in Kinto):

    {
      :expressions ["(Somme 1 2)" ... "(Quotient (Somme a 1) (Somme b 2))"],
      :filters
        {
          :identity <fn>
        },
      :nature "All",
      :depth [1 7],
      :nb-ops [1 7],
      :prevented-ops ["Somme" "Produit"]
    }}

## Groups page

Direct mapping here between the state and a record in Kinto.

    Under the `:groups-page` key:        |   Records in the groups collection:
                                         |
    {                                    |  {
      :31c69a95-81da-4e09-a2ae-ec2d98    |  "31c69a95-81da-4e09-a2ae-ec2d98":
        {                                |    {
          :lastname "Tartopil",          |      "lastname": "Tartopil",
          :firstname "Rachid",           |      "firstname": "Rachid",
          :groups #{"2nde1" "2nde1b"}    |      "groups": ["2nde1", "2nde1b"]
        },                               |    },
      :21c69a95-81da-4e09-a2ae-c2d98     |   "21c69a95-81da-4e09-a2ae-ec2d98":
        {                                |    {
          :lastname "Bérurien",          |      "lastname": "Bérurien",
          :firstname "Alix",             |      "firstname": "Alix",
          :groups #{"2nde1" "2nde1a"}    |      "groups": ["2nde1", "2nde1a"]
        }                                |    }
    }                                    |  }

The `id` of a record is the `id` of the owning teacher.

## Teacher works

    Under the `:works-teacher-page` key:     |  Records in the works collection:
                                             |
    [                                        |   {
      {                                      |     "teacher-id": "d6e...487",
        :id "4936f940-7f6c7f443652",         |     "to": "02/11/2017",
        :to "05/11/2017",                    |     "series-id": "16439228ae",
        :series-id "28fb58de-d78eab50de0e",  |     "group": "1S",
        :group "1S",                         |     "from": "23/09/2017"
        :from "28/10/2017",                  |   }
        :series-title "Seconde: démo"        |
        :scholars                            |
          {                                  |
            :id                              |
              {                              |
                :lastname "Tartopil"         |
                :firstname "Alix"            |
              }                              |
            ...                              |
          }                                  |
        :progress                            |
          {                                  |
            :id1 expr-code1                  |
            :id2 expr-code2                  |
            ...                              |
          }                                  |
        :show-progress bool                  |
      }                                      |
      ...                                    |
    ]                                        |

In the `:progress` map each `scholar-id` is associated with the index of the
last expression passed in non interactive mode, or maybe a specific code.
See the documentation of the [progress](#progress) collection.

## Scholar works

    Under the `:works-scholar-page` key:     |  Records in the works collection:
                                             |
    [                                        |   {
      {                                      |     "teacher-id": "d6e...487",
        :id "4936f940-7f6c7f443652",         |     "to": "02/11/2017",
        :to "05/11/2017",                    |     "series-id": "16439228ae",
        :series-id "28fb58de-d78eab50de0e",  |     "group": "1S",
        :group "1S",                         |     "from": "23/09/2017"
        :from "28/10/2017",                  |   }
        :series-title "Seconde: démo"        |
      }                                      |
      {                                      |
        :id "88417fb0-c8361f2c5d8d",         |
        :to "28/10/2017",                    |
        :series-id "28fb58de-d78eab50de0e",  |
        :group "2.1 gr1",                    |
        :from "26/10/2017",                  |
        :series-title "Seconde: démo"        |
      }                                      |
    ]                                        |

The `:group` item is not needed in the elements under the `:works-scholar-page`
key. We leave it for now.

## Scholar work

    Under the `:scholar-work` key:

    {
      :series
        {
          :title "A title",
           :desc "A description",
           :exprs ["(Somme 1 2)" ... "(Somme 1 a)"]
        }
      :current-expr-idx 0,
      :current-expr "",
      :interactive true,
      :shown-at
      :attempt "()",
      :error ""
    }

We can loosely consider that it is stored in the `attempts` collection.

A `:current-expr-idx` of `-666` means « the scholar finished the series
successfuly ».

## Attempts


    Records in the attempts collection

    {
      "scholar-id": "a1c69a95-81da-4e09-a2ae-e99a9eec2d98",
      "work-id": "training",
      "status": "aborted",
      "expr-idx": 4,
      "expr": "(Diff 1 2)",
      "expr-nature": "Diff",
      "shown-at": 1509790452423,
      "attempt": "(Diff 1 3)",
      "attempt-nature": "Diff"
      "attempted-at": 1509791480404,
      "duration": 1027981,
    }

`status` can be either

* `ok`
* `ok interactive`
* `mistake`
* `mistake interactive`
* `back to interactive`

## Progress

    Records in the progress collection

    {
      "81da...4e98": 0,
      "a1c6...7a09": -666,
      "9a95...a2ae": 3
    }

Their `id` is the relevant `work-id`.

With each `scholar-id` is associated the index of last expression passed in
non interactive mode.

 * `-666` means « the scholar finished the series successfuly ».
 * `-1` means « the scholar aborted trying the first expr ».
