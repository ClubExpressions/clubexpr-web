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
    * [react-mathjax](https://www.npmjs.com/package/react-mathjax)
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
  * Modules that caused problems (there is a branch for the attempt)
    * [CodeMirror](https://www.npmjs.com/package/react-codemirror) (text editor)
  * Modules that are planned to be used:
    * [Tempura](https://github.com/ptaoussanis/tempura) (i18n)
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

Different records can have the same kinto id and in different collections.

For this reason, beware of any `DELETE WHERE id=` from `psql`.

