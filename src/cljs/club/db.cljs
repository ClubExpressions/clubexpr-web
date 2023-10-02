(ns club.db
  (:require [cljs.spec.alpha :as s]
            [clojure.walk :refer [keywordize-keys]]
            [webpack.bundle]
            [goog.object :refer [getValueByKeys]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [club.utils :refer [error error-fn data-from-js-obj]]
            [club.expr :refer [all-exprs book-series make-book-series-record]]
            [club.config :as config]))

(s/def ::current-page keyword?)
(s/def ::attempt-code string?)
(s/def ::show-help boolean?)
(s/def ::game-idx number?)

(s/def ::profile-page
  (s/and map?
         (s/keys :req-un [::quality
                          ::school
                          ::teachers-list  ; UI only, not stored in profile
                          ::teacher
                          ::lastname
                          ::firstname])))
(s/def ::quality string?)
(s/def ::school string?)
(s/def ::teachers-list #(instance? PersistentVector %))
; TODO: empty or containing maps like {:id "val" :lastname "val"}
(s/def ::teacher string?)
(s/def ::lastname string?)
(s/def ::firstname string?)

(s/def ::authenticated boolean?)
(s/def ::auth-data
  (s/and map?
         (s/keys :req-un [::auth0-id
                          ::kinto-id
                          ::access-token
                          ::expires-at])))
(s/def ::auth0-id string?)
(s/def ::kinto-id string?)
(s/def ::access-token string?)
(s/def ::expires-at   string?)

(s/def ::groups-page
  (s/and map?
         ; TODO for each scholar id we have a map with
         ; :lastname (string) :firstname (string)
         ; :groups (set of strings)
         ))

(s/def ::series
  (s/and map?
         (s/keys :req-un [::title  ; TODO: specify those 3
                          ::desc
                          ::exprs])))
(s/def ::current-series-id string?)
(s/def ::current-series
  (s/and map?
         (s/or :empty empty?
               :series ::series)))
(s/def ::expr-mod-showing boolean?)
(s/def ::expr-mod-template string?)
(s/def ::expr-mod-map map?)
(s/def ::teacher-testing boolean?)
(s/def ::teacher-testing-interactive boolean?)
(s/def ::teacher-testing-idx number?)
(s/def ::teacher-testing-attempt string?)
(s/def ::teacher-testing-last-attempt string?)
(s/def ::editing-series boolean?)
(s/def ::series-page
  (s/and #(instance? PersistentVector %)
         ; TODO each elt is a map {:id string? :series ::series}
         ))
(s/def ::expressions #(instance? PersistentVector %))
(s/def ::filters map?)  ; TODO {:kw1 callable1 :kw2 callable2}
(s/def ::nature string?)
(s/def ::depth  #(instance? PersistentVector %))
(s/def ::nb-ops #(instance? PersistentVector %))
(s/def ::prevented-ops #(instance? PersistentVector %))
(s/def ::series-filtering
  (s/and map?
         (s/keys :req-un [::expressions
                          ::filters
                          ::nature
                          ::depth
                          ::nb-ops
                          ::prevented-ops])))
(s/def ::works-teacher-page
  (s/and #(instance? PersistentVector %)
         ; TODO each elt is a map
         ; {:id :teacher-id :from :to :series-id :series-title :group}
         ))
(s/def ::works-scholar-page
  (s/and #(instance? PersistentVector %)
         ; TODO each elt is a map
         ; {:id :from :to :series-id :series-title}
         ))
(s/def ::scholar-working boolean?)
(s/def ::scholar-work-id string?)
(s/def ::scholar-work
  (s/and map?
         (s/keys :req-un [::series
                          ::current-expr-idx
                          ::current-expr
                          ::interactive
                          ::shown-at
                          ::attempt])))

(s/def ::db
  (s/and map?
         (s/or :empty empty?
               :keys (s/keys :req-un
                             [::current-page
                              ::authenticated
                              ::auth-data
                              ::attempt-code
                              ::show-help
                              ::game-idx
                              ::profile-page
                              ::groups-page
                              ::series-page
                              ::works-teacher-page
                              ::works-scholar-page
                              ::current-series-id
                              ::current-series
                              ::expr-mod-showing
                              ::expr-mod-template
                              ::expr-mod-map
                              ::teacher-testing
                              ::teacher-testing-interactive
                              ::teacher-testing-idx
                              ::teacher-testing-attempt
                              ::teacher-testing-last-attempt
                              ::editing-series
                              ::series-filtering
                              ::scholar-working
                              ::scholar-work-id
                              ::scholar-work
                              ]))))

(def new-series
  {:title ""
   :desc ""
   :exprs []})

(def logout-db-fragment
  {:current-page :landing
   :authenticated false
   :auth-data {:kinto-id ""
               :auth0-id ""
               :access-token ""
               :expires-at   ""}
   :groups-page {}
   :series-page []
   :works-teacher-page []
   :works-scholar-page []
   :current-series-id ""
   :current-series new-series
   :expr-mod-showing false
   :expr-mod-template ""
   :expr-mod-map {}
   :teacher-testing false
   :teacher-testing-interactive true
   :teacher-testing-idx 0
   :teacher-testing-attempt ""
   :teacher-testing-last-attempt ""
   :editing-series false
   :series-filtering
     {:expressions []
      :filters {:identity identity}
      :nature "All"
      :depth  [1 7]
      :nb-ops [1 7]
      :prevented-ops []}
   :scholar-work-id ""
   :scholar-working false
   :scholar-work
     {:series new-series
      :current-expr-idx 0
      :current-expr ""
      :interactive true
      :shown-at ""
      :attempt ""}
   :profile-page {:quality "scholar"
                  :school "fake-id-no-school"
                  :teachers-list []
                  :teacher "no-teacher"
                  :lastname ""
                  :firstname ""}})

(def default-db
   (merge logout-db-fragment {:attempt-code "(Somme 1 2)"
                              :show-help false
                              :game-idx 0}))

(def k-client
  (let [b64 (js/window.btoa "user:pass")
        url (if false ;club.config/debug?
              "http://localhost:8887/v1"
              "https://kinto.expressions.club/v1")
        opts (clj->js {:remote url
                       :headers {:Authorization (str "Basic " b64)}})
        kinto (getValueByKeys js/window "deps" "kinto")
        kinto-instance (new kinto opts)]
    (.-api kinto-instance)))

(def error-404 "Error: HTTP 404; Error: HTTP 404 Not Found: Invalid Token / id")

;(def k-log (.. k-client
;               (bucket "default")
;               (collection "log")))
;
;(defn log!
;  [data]
;  (.. k-log (createRecord data)))

(def k-users (.. k-client
                 (bucket "default")
                 (collection "users")))

(def k-series (.. k-client
                  (bucket "default")
                  (collection "series")))

(def k-groups (.. k-client
                  (bucket "default")
                  (collection "groups")))

(def k-works (.. k-client
                 (bucket "default")
                 (collection "works")))

(def k-attempts (.. k-client
                    (bucket "default")
                    (collection "attempts")))

(def k-progress (.. k-client
                    (bucket "default")
                    (collection "progress")))

(defn base-user-record
  [auth0-id]
  {:auth0-id auth0-id
   :quality "scholar"
   :school "fake-id-no-school"
   :teacher "no-teacher"
   :lastname ""
   :firstname ""})

(defn set-auth-data!
  [reason
   {:keys [; from new-user-data
           auth0-id access-token expires-at
           ; from the new record
           id quality school teacher lastname firstname]}]
  (swap! app-db assoc-in [:authenticated] true)
  ; from new-user-data
  (swap! app-db assoc-in [:auth-data :auth0-id] auth0-id)
  (swap! app-db assoc-in [:auth-data :access-token] access-token)
  (swap! app-db assoc-in [:auth-data :expires-at] expires-at)
  ; from a record
  (swap! app-db assoc-in [:auth-data :kinto-id] id)
  (swap! app-db assoc-in [:profile-page] {:quality quality
                                          :school school
                                          :teacher teacher
                                          :teachers-list []
                                          :lastname lastname
                                          :firstname firstname})
  (rf/dispatch [:go-to-relevant-url reason])
  ; TODO circular dep if require events:
  ;(check-and-throw :club.db/db @app-db))
  )

(defn fetch-profile-data!
  [reason]
  (.. club.db/k-users
      (getRecord (clj->js (-> @app-db :auth-data :kinto-id)))
      (then #(set-auth-data!
               reason
               (merge {:access-token (-> @app-db :auth-data :access-token)
                       :expires-at (-> @app-db :auth-data :expires-at)}
                      (data-from-js-obj %))))
      (catch (error-fn "db/fetch-profile-data!"))))

(defn save-profile-data!
  []
  (.. club.db/k-users
      (updateRecord (clj->js
                      {:id       (-> @app-db :auth-data :kinto-id)
                       :auth0-id (-> @app-db :auth-data :auth0-id)
                       :quality   (-> @app-db :profile-page :quality)
                       :school    (-> @app-db :profile-page :school)
                       :teacher   (-> @app-db :profile-page :teacher)
                       :lastname  (-> @app-db :profile-page :lastname)
                       :firstname (-> @app-db :profile-page :firstname)}))
      (then #(rf/dispatch [:profile-save-ok]))
      (catch (error-fn "db/save-profile-data!"))))

(defn get-users!
  [{:keys [on-success] :or {on-success identity}}]
  (.. club.db/k-users
      (listRecords)
      (then on-success)
      (catch (error-fn "db/get-users!"))))

(defn groups-page-data-enhancer
  [scholar]
  (let [scholar-id (first scholar)
        scholar-data (second scholar)]
    [scholar-id {:lastname  (:lastname  scholar-data)
                 :firstname (:firstname scholar-data)
                 :groups    (set (:groups scholar-data))}]))

(defn groups-data->groups-page-data
  [data]
  (dissoc (into {} (map groups-page-data-enhancer data)) :id :last_modified))

(defn groups-reducer
  [m x]
  (into m
    (let [id (keyword (:id x))]
      {id {:lastname (:lastname x)
           :firstname (:firstname x)
           :groups #{}}})))

(defn ghost-scholars
  [scholars]
  (let [teacher-id (-> @app-db :auth-data :kinto-id)]
    (if (empty? scholars)
      [{:id (str "ghost-1-" teacher-id)
        :lastname "(fantôme)"
        :firstname "Casper"}
       {:id (str "ghost-2-" teacher-id)
        :lastname "(fantôme)"
        :firstname "Érika"}]
      scholars)))

(defn init-groups-data!
  []
  (let [teacher-id (-> @app-db :auth-data :kinto-id)]
    (get-users! {:on-success
                  #(rf/dispatch
                    [:init-groups
                      (->> % data-from-js-obj
                             (filter (fn [x] (= teacher-id (:teacher x))))
                             ghost-scholars
                             (reduce groups-reducer {}))])})))

(defn fetch-groups-data!
  []
  (.. club.db/k-groups
      (getRecord (clj->js (-> @app-db :auth-data :kinto-id)))
      (then
        #(rf/dispatch
          [:write-groups
            (-> % data-from-js-obj
                  groups-data->groups-page-data)]))
      (catch #(if (= error-404 (str %))  ; no such id in the groups coll?
                nil  ; could be that the teacher has no groups data yet.
                (error "db/fetch-groups-data!" %)))))

(defn groups-page-data-trimmer
  [scholar]
  (let [scholar-id (first scholar)
        scholar-data (second scholar)]
    [scholar-id {:lastname  (:lastname  scholar-data)
                 :firstname (:firstname scholar-data)
                 :groups    (:groups    scholar-data)}]))

(defn save-groups-data!
  []
  (let [groups-data (->> @app-db
                         :groups-page
                         (map groups-page-data-trimmer)
                         (filter (comp not empty? :groups second))
                         (into {}))
        record (merge {:id (-> @app-db :auth-data :kinto-id)} groups-data)]
    (.. club.db/k-groups
        (updateRecord (clj->js record))
        (then #(rf/dispatch [:groups-save-ok]))
        (catch (error-fn "db/save-groups-data!")))))

(defn fix-ranks
  [exprs]
  (vec (map #(identity {:content (:content %1) :rank %2}) exprs (range 666))))

(defn wrap-series
  [series]
  (let [wrap-w-content #(identity {:content %})]
    (-> series (update :exprs #(map wrap-w-content %))
               (update :exprs fix-ranks))))

(defn series-data->series-page-data
  [data]
  (-> data (dissoc :owner-id :last_modified)))

(defn fetch-series-data! ; lancé deux fois par page?
  []
  (let [kinto-id (-> @app-db :auth-data :kinto-id)]
    (.. club.db/k-series
        (listRecords)
        (then
          #(rf/dispatch
            [:write-series
              (->> % data-from-js-obj
                     (filter (fn [x] (= kinto-id (:owner-id x))))
                     (map series-data->series-page-data)
                     vec)]))
        (catch #(if (= error-404 (str %))  ; no such id in the series coll?
                  (swap! app-db assoc-in [:series-page] {})
                  (error "db/fetch-series-data!" %))))))

(defn series-trimmer
  [series]
  (update series :exprs #(map :content %)))

(defn save-series-data!
  []
  (let [current-series-id (-> @app-db :current-series-id)
        current-series (-> @app-db :current-series)
        record-fragment {:owner-id (-> @app-db :auth-data :kinto-id)
                         :series (series-trimmer current-series)}
        record (if (empty? current-series-id)
                 record-fragment
                 (merge record-fragment {:id current-series-id}))]
    (.. k-series
        (createRecord (clj->js record))
        (then #(rf/dispatch [:series-save-ok %]))
        (catch (error-fn "db/save-series-data!")))))

(defn save-one-book-series!
  [series-code]
  (let [user-id (-> @app-db :auth-data :kinto-id)
        series-data (get book-series series-code)
        record (make-book-series-record user-id series-code)]
    (.. k-series
        (createRecord (clj->js record))
        (catch (error-fn "db/save-book-series!")))))

(defn save-book-series!
  []
  (doall (map save-one-book-series! (keys book-series))))

(defn delete-series!
  []
  (let [current-series-id (-> @app-db :current-series-id)]
    (.. club.db/k-series
        (deleteRecord current-series-id)
        (then #(rf/dispatch [:series-delete-ok %]))
        (catch (error-fn "db/delete-series!")))))

(defn label-feeder
  [series-clj]
  (fn [work]
    (let [title (->> series-clj
                     (filter #(= (:series-id work) (-> % :id)))
                     first
                     :series
                     :title)]
      (merge work {:series-title title}))))

(defn scholars-feeder
  [groups-record]
  (fn [work]
    (let [groups-clj (data-from-js-obj groups-record)
          group (:group work)
          scholars (->> groups-clj
                       (filter #(some #{group} (:groups (second %))))
                       (map #(identity [(first %)
                                        (dissoc (second %) :groups)]))
                       (into {}))]
      (assoc work :scholars scholars))))

(defn fetch-works-teacher!
  []
  (let [teacher-id (-> @app-db :auth-data :kinto-id)]
    (.. club.db/k-groups
      (getRecord teacher-id)
      (then
        (fn [groups-record]
          (.. club.db/k-series
            (listRecords)
            (then
              (fn [series-list]
                (.. club.db/k-works
                  (listRecords)
                  (then
                    (fn [works-list]
                      (let [groups-clj (data-from-js-obj groups-record)
                            series-clj (data-from-js-obj series-list)
                            works (->> works-list
                                       data-from-js-obj
                                       (filter #(= teacher-id
                                                   (:teacher-id %)))
                                       (map #(dissoc % :last_modified
                                                       :teacher-id))
                                       (map (label-feeder series-clj))
                                       (map (scholars-feeder groups-record))
                                       vec)]
                        (rf/dispatch [:write-works-teacher works]))))
                  (catch (error-fn "db/fetch-works-teacher! works step")))))
            (catch (error-fn "db/fetch-works-teacher! series step")))))
      (catch #(if (= error-404 (str %))  ; no such id in the groups coll?
                  (rf/dispatch [:write-works-teacher []])
                  (error "db/fetch-works-teacher! groups step" %))))))

(defn fetch-progress!
  [work]
  (let [work-id (:id work)]
    (.. club.db/k-progress
        (getRecord work-id)
        (then
          (fn [progress-record]
            (let [progress-clj (-> progress-record
                                   data-from-js-obj
                                   (dissoc :id :last_modified))]
              (rf/dispatch [:progress-write work-id progress-clj]))))
        (catch #(if (= error-404 (str %))  ; no such id in the works coll?
                    (rf/dispatch [:progress-write work-id {}])
                    (error "db/fetch-progress!" %))))))

(defn save-work!
  [{:keys [teacher-id work-state]}]
  (let [record (-> work-state
                   (merge {:teacher-id teacher-id
                           :series-id (:series-id work-state)})
                   (dissoc (if (empty? (:id work-state)) :id)
                           :editing
                           :series-title
                           :scholars
                           :progress
                           :show-progress))]
    (.. club.db/k-works
        (createRecord (clj->js record))
        (then #(rf/dispatch [:work-save-ok (data-from-js-obj %)]))
        (catch (error-fn "event :work-save")))))

(defn delete-work!
  [work-id]
  (.. club.db/k-works
      (deleteRecord work-id)
      (then #(rf/dispatch [:work-delete-ok %]))
      (catch (error-fn "db/delete-work!"))))

(defn for-the-groups
  [groups]
  ; returns a filter which keeps a work if its group belongs to `groups`
  (fn [work]
    (some #{(:group work)} groups)))

(defn fetch-works-scholar!
  []
  (let [scholar-id (-> @app-db :auth-data :kinto-id)
        teacher-id (-> @app-db :profile-page :teacher)]
    (.. club.db/k-groups
        (getRecord teacher-id)
        (then
          (fn [groups-data]
            (let [groups-data-clj (data-from-js-obj groups-data)
                  scholar-id-kw (keyword scholar-id)
                  scholar-groups (:groups (scholar-id-kw groups-data-clj))]
              (.. club.db/k-series
                  (listRecords)
                  (then
                    (fn [series-list]
                      (.. club.db/k-works
                          (listRecords)
                          (then
                            (fn [works-list]
                              (let [series-clj (data-from-js-obj series-list)
                                    works (->> works-list
                                               data-from-js-obj
                                               (filter #(= teacher-id (:teacher-id %)))
                                               (filter (for-the-groups scholar-groups))
                                               (map (label-feeder series-clj))
                                               (map #(dissoc % :last_modified :teacher-id))
                                               vec)]
                                (rf/dispatch [:write-works-scholar works]))))
                          (catch (error-fn "db/fetch-works-scholar! works step")))))
                  (catch (error-fn "db/fetch-works-scholar! series step"))))))
        (catch #(if (= error-404 (str %))  ; no such id in the groups coll?
                    (rf/dispatch [:write-works-scholar []])
                    (error "db/fetch-works-scholar! groups step" %))))))

(defn with-progress-and-work-id
  [progress work-id]
  (if (= "training" work-id)
    (let [series {:title "Entraînement"
                  :desc  "Série proposée à tous les élèves"
                  :exprs all-exprs}]
      (rf/dispatch [:write-scholar-work series progress]))
    (.. club.db/k-works
        (getRecord work-id)
        (then
          (fn [work-record]
            (.. club.db/k-series
                (getRecord (-> work-record
                               data-from-js-obj
                               :series-id))
                (then
                  (fn [series-record]
                    (let [series (-> series-record
                                     data-from-js-obj
                                     :series)]
                      (rf/dispatch [:write-scholar-work series progress]))))
                (catch (error-fn "db/fetch-scholar-works! works step")))))
        (catch (error-fn "db/fetch-scholar-works! series step")))))

(defn fetch-scholar-work!
  [work-id]
  (.. club.db/k-progress
      (getRecord work-id)
      (then #(-> %
                 data-from-js-obj
                 (with-progress-and-work-id work-id)))
      (catch #(if (= error-404 (str %))  ; no such id in the progress coll?
                  (with-progress-and-work-id {} work-id)
                  (error "db/fetch-scholar-works! progress step" %)))))

(defn save-attempt!
  [attempt]
  (.. club.db/k-attempts
      (createRecord (clj->js attempt))
      (catch (error-fn "db/save-attempt!"))))

(defn save-progress!
  [progress]
  ; We pass data and an options
  ; `:patch true` allows us to merge into existing entries
  (.. club.db/k-progress
      (updateRecord (clj->js progress)       ; data
                    (clj->js {:patch true})) ; options in a map
      (catch #(if (= error-404 (str %))  ; no such id in the progress coll?
                  (.. club.db/k-progress          ; errors if id not found
                      (createRecord (clj->js progress))  ; so we create it
                      (catch (error-fn "db/save-progress! create"))
                  (error "db/save-progress! update no 404" %))))))

(defn get-schools!
  []
  [
   ; https://www.data.gouv.fr/fr/datasets/adresse-et-geolocalisation-des-etablissements-denseignement-du-premier-et-second-degres-1/
   {:id "fake-id-no-school" :name "Aucun établissement"}
   {:id "fake-id-0010013J" :name "Lycée LALANDE (01)"}
   {:id "fake-id-0030025L" :name "Lycée MADAME DE STAEL (03)"}
   {:id "fake-id-0030036Y" :name "Lycée THEODORE DE BANVILLE (03)"}
   {:id "fake-id-0030044G" :name "Lycée BLAISE DE VIGENERE (03)"}
   {:id "fake-id-0040543U" :name "Lycée ECOLE INTERNATIONALE PACA (04)"}
   {:id "fake-id-0060011E" :name "Lycée CARNOT (06)"}
   {:id "fake-id-0060030A" :name "Lycée MASSENA (06)"}
   {:id "fake-id-0080006N" :name "Lycée CHANZY (08)"}
   {:id "fake-id-0080018B" :name "Lycée VAUBAN (08)"}
   {:id "fake-id-0080053P" :name "Lycée THOMAS MASARYK (08)"}
   {:id "fake-id-0090018W" :name "Lycée DU COUSERANS (09)"}
   {:id "fake-id-0101028N" :name "Lycée CAMILLE CLAUDEL (10)"}
   {:id "fake-id-0120022J" :name "Lycée FERDINAND FOCH (12)"}
   {:id "fake-id-0130010R" :name "Lycée MONTMAJOUR (13)"}
   {:id "fake-id-0140004D" :name "Lycée ALAIN CHARTIER (14)"}
   {:id "fake-id-0140043W" :name "Lycée ANDRE MAUROIS (14)"}
   {:id "fake-id-0140061R" :name "Lycée MARCEL GAMBIER (14)"}
   {:id "fake-id-0142059M" :name "Lycée VICTOR HUGO (14)"}
   {:id "fake-id-0142107P" :name "Lycée CHARLES DE GAULLE (14)"}
   {:id "fake-id-0150646W" :name "Lycée EMILE DUCLAUX (15)"}
   {:id "fake-id-0180005H" :name "Lycée ALAIN FOURNIER (18)"}
   {:id "fake-id-0190011J" :name "Lycée D'ARSONVAL (19)"}
   {:id "fake-id-0211928G" :name "Lycée INTERNATIONAL CHARLES DE GAULL (21)"}
   {:id "fake-id-0240013J" :name "Lycée GIRAUT DE BORNEIL (24)"}
   {:id "fake-id-0240024W" :name "Lycée BERTRAN DE BORN (24)"}
   {:id "fake-id-0260008T" :name "Lycée DU DIOIS (26)"}
   {:id "fake-id-0260022H" :name "Lycée ALBERT TRIBOULET (26)"}
   {:id "fake-id-0260034W" :name "Lycée EMILE LOUBET (26)"}
   {:id "fake-id-0260035X" :name "Lycée CAMILLE VERNET (26)"}
   {:id "fake-id-0280007F" :name "Lycée MARCEAU (28)"}
   {:id "fake-id-0290007A" :name "Lycée LA PEROUSE-KERICHEN (29)"}
   {:id "fake-id-0290009C" :name "Lycée DE L'IROISE (29)"}
   {:id "fake-id-0290010D" :name "Lycée DE L'HARTELOIRE (29)"}
   {:id "fake-id-0290069T" :name "Lycée AUGUSTE BRIZEUX (29)"}
   {:id "fake-id-0310036W" :name "Lycée PIERRE DE FERMAT (31)"}
   {:id "fake-id-0310041B" :name "Lycée SAINT-SERNIN (31)"}
   {:id "fake-id-0312696M" :name "Lycée PIERRE BOURDIEU (31)"}
   {:id "fake-id-0312754A" :name "Lycée CLÉMENCE ROYER (31)"}
   {:id "fake-id-0320036R" :name "Lycée JOSEPH SAVERNE (32)"}
   {:id "fake-id-0330021U" :name "Lycée MICHEL MONTAIGNE (33)"}
   {:id "fake-id-0330022V" :name "Lycée MONTESQUIEU (33)"}
   {:id "fake-id-0330026Z" :name "Lycée FRANCOIS MAGENDIE (33)"}
   {:id "fake-id-0330115W" :name "Lycée RECLUS (33)"}
   {:id "fake-id-0331636Z" :name "Lycée JEAN MOULIN (33)"}
   {:id "fake-id-0332724G" :name "Lycée NORD BASSIN (33)"}
   {:id "fake-id-0332745E" :name "Lycée JEAN MONNET (33)"}
   {:id "fake-id-0332831Y" :name "Lycée SUD MEDOC LA BOETIE (33)"}
   {:id "fake-id-0332846P" :name "Lycée DES GRAVES (33)"}
   {:id "fake-id-0340005W" :name "Lycée FERDINAND FABRE (34)"}
   {:id "fake-id-0340075X" :name "Lycée PAUL VALERY (34)"}
   {:id "fake-id-0342266D" :name "Lycée INTERNAT D'EXCELLENCE (34)"}
   {:id "fake-id-0350024L" :name "Lycée EMILE ZOLA (35)"}
   {:id "fake-id-0350710G" :name "Lycée FRANCOIS RENE DE CHATEAUBRIAND (35)"}
   {:id "fake-id-0360002G" :name "Lycée ROLLINAT (36)"}
   {:id "fake-id-0360008N" :name "Lycée JEAN GIRAUDOUX (36)"}
   {:id "fake-id-0370035M" :name "Lycée DESCARTES (37)"}
   {:id "fake-id-0380027Y" :name "Lycée CHAMPOLLION (38)"}
   {:id "fake-id-0380028Z" :name "Lycée STENDHAL (38)"}
   {:id "fake-id-0383242T" :name "Lycée INTERNATIONAL EUROPOLE (38)"}
   {:id "fake-id-0390012B" :name "Lycée CHARLES NODIER (39)"}
   {:id "fake-id-0400046H" :name "Lycée SAINT-EXUPERY (40)"}
   {:id "fake-id-0400933X" :name "Lycée SUD DES LANDES (40)"}
   {:id "fake-id-0411071S" :name "Lycée  (41)"} ; ???
   {:id "fake-id-0420031F" :name "Lycée JEAN PUY (42)"}
   {:id "fake-id-0420041S" :name "Lycée CLAUDE FAURIEL (42)"}
   {:id "fake-id-0422132P" :name "Lycée L'ASTREE (42)"}
   {:id "fake-id-0440022K" :name "Lycée JULES VERNE (44)"}
   {:id "fake-id-0440024M" :name "Lycée GABRIEL GUISTHAU (44)"}
   {:id "fake-id-0441936R" :name "Collège LA PERVERIE (44)"}
   {:id "fake-id-0440172Y" :name "Lycée LA PERVERIE (44)"}
   {:id "fake-id-0442112G" :name "Lycée GALILEE (44)"}
   {:id "fake-id-0460026D" :name "Lycée JEAN LURCAT (46)"}
   {:id "fake-id-0470009E" :name "Lycée STENDHAL (47)"}
   {:id "fake-id-0470028A" :name "Lycée GEORGE SAND (47)"}
   {:id "fake-id-0490001K" :name "Lycée DAVID D'ANGERS (49)"}
   {:id "fake-id-0490040C" :name "Lycée DUPLESSIS MORNAY (49)"}
   {:id "fake-id-0500065Z" :name "Lycée LE VERRIER (50)"}
   {:id "fake-id-0510006E" :name "Lycée PIERRE BAYEN (51)"}
   {:id "fake-id-0510031G" :name "Lycée GEORGES CLEMENCEAU (51)"}
   {:id "fake-id-0511901P" :name "Lycée COLBERT (51)"}
   {:id "fake-id-0570106F" :name "Lycée CHARLEMAGNE (57)"}
   {:id "fake-id-0570107G" :name "Lycée HELENE BOUCHER (57)"}
   {:id "fake-id-0590024F" :name "Lycée DES NERVIENS (59)"}
   {:id "fake-id-0590063Y" :name "Lycée ALBERT CHATELET (59)"}
   {:id "fake-id-0590116F" :name "Lycée FENELON (59)"}
   {:id "fake-id-0590143K" :name "Lycée YVES KERNANEC (59)"}
   {:id "fake-id-0590181B" :name "Lycée MAXENCE VAN DER MEERSCH (59)"}
   {:id "fake-id-0590182C" :name "Lycée BAUDELAIRE (59)"}
   {:id "fake-id-0590212K" :name "Lycée GAMBETTA (59)"} ; fixed
   {:id "fake-id-0596892W" :name "Lycée D'EXCELLENCE EDGAR MORIN (59)"}
   {:id "fake-id-0620017G" :name "Lycée LAVOISIER (62)"}
   {:id "fake-id-0630018C" :name "Lycée BLAISE PASCAL (63)"}
   {:id "fake-id-0630019D" :name "Lycée JEANNE D'ARC (63)"}
   {:id "fake-id-0640010N" :name "Lycée RENE CASSIN (64)"}
   {:id "fake-id-0640017W" :name "Lycée EXPERIMENTAL A.MALRAUX (64)"}
   {:id "fake-id-0640046C" :name "Lycée PAUL REY (64)"}
   {:id "fake-id-0640047D" :name "Lycée JULES SUPERVIELLE (64)"}
   {:id "fake-id-0640055M" :name "Lycée LOUIS BARTHOU (64)"}
   {:id "fake-id-0640065Y" :name "Lycée MAURICE RAVEL (64)"}
   {:id "fake-id-0650012K" :name "Lycée MICHELET (65)"}
   {:id "fake-id-0650025Z" :name "Lycée THEOPHILE GAUTIER (65)"}
   {:id "fake-id-0650038N" :name "Lycée PIERRE MENDES FRANCE (65)"}
   {:id "fake-id-0670049P" :name "Lycée FREPPEL (67)"}
   {:id "fake-id-0670079X" :name "Lycée FUSTEL DE COULANGES (67)"}
   {:id "fake-id-0670080Y" :name "Lycée KLEBER (67)"}
   {:id "fake-id-0670081Z" :name "Lycée SECTIONS INTERNATIONALES (67)"}
   {:id "fake-id-0670083B" :name "Lycée MARIE CURIE (67)"}
   {:id "fake-id-0673050B" :name "Lycée ECOLE EUROPEENNNE DE STRASBOUR (67)"}
   {:id "fake-id-0680051L" :name "Lycée FREDERIC KIRSCHLEGER (68)"}
   {:id "fake-id-0681761V" :name "Lycée LAMBERT (68)"}
   {:id "fake-id-0690026D" :name "Lycée DU PARC (69)"}
   {:id "fake-id-0693446W" :name "Lycée CITE SCOLAIRE INTERNATIONALE (69)"}
   {:id "fake-id-0694069Y" :name "Lycée GERMAINE TILLION (69)"}
   {:id "fake-id-0710011B" :name "Lycée PONTUS DE TYARD (71)"}
   {:id "fake-id-0720029R" :name "Lycée MONTESQUIEU (72)"}
   {:id "fake-id-0720030S" :name "Lycée BELLEVUE (72)"}
   {:id "fake-id-0730013T" :name "Lycée VAUGELAS (73)"}
   {:id "fake-id-0731507S" :name "Lycée SAINT EXUPERY (73)"}
   {:id "fake-id-0740003B" :name "Lycée CLAUDE LOUIS BERTHOLLET (74)"}
   {:id "fake-id-0750648X" :name "Lycée VICTOR HUGO (75)"}
   {:id "fake-id-0750652B" :name "Lycée CHARLEMAGNE (75)"}
   {:id "fake-id-0750654D" :name "Lycée HENRI IV (75)"}
   {:id "fake-id-0750655E" :name "Lycée LOUIS LE GRAND (75)"}
   {:id "fake-id-0750656F" :name "Lycée LAVOISIER (75)"}
   {:id "fake-id-0750657G" :name "Lycée MONTAIGNE (75)"}
   {:id "fake-id-0750658H" :name "Lycée SAINT-LOUIS (75)"}
   {:id "fake-id-0750660K" :name "Lycée FENELON (75)"}
   {:id "fake-id-0750662M" :name "Lycée VICTOR DURUY (75)"}
   {:id "fake-id-0750667T" :name "Lycée CONDORCET (75)"}
   {:id "fake-id-0750668U" :name "Lycée JACQUES DECOUR (75)"}
   {:id "fake-id-0750669V" :name "Lycée JULES FERRY (75)"}
   {:id "fake-id-0750670W" :name "Lycée LAMARTINE (75)"}
   {:id "fake-id-0750673Z" :name "Lycée COLBERT (75)"}
   {:id "fake-id-0750679F" :name "Lycée PAUL VALERY (75)"}
   {:id "fake-id-0750682J" :name "Lycée RODIN (75)"}
   {:id "fake-id-0750683K" :name "Lycée CLAUDE MONET (75)"}
   {:id "fake-id-0750684L" :name "Lycée GABRIEL FAURE (75)"}
   {:id "fake-id-0750689S" :name "Lycée PAUL BERT (75)"}
   {:id "fake-id-0750693W" :name "Lycée BUFFON (75)"}
   {:id "fake-id-0750694X" :name "Lycée CAMILLE SEE (75)"}
   {:id "fake-id-0750699C" :name "Lycée JANSON DE SAILLY (75)"}
   {:id "fake-id-0750702F" :name "Lycée JEAN DE LA FONTAINE (75)"}
   {:id "fake-id-0750703G" :name "Lycée MOLIERE (75)"}
   {:id "fake-id-0750704H" :name "Lycée CARNOT (75)"}
   {:id "fake-id-0750714U" :name "Lycée HELENE BOUCHER (75)"}
   {:id "fake-id-0753844W" :name "Lycée SAINTE-GENEVIEVE (75)"}
   {:id "fake-id-0754684J" :name "Lycée GEORGES BRASSENS (75)"}
   {:id "fake-id-0760052U" :name "Lycée FRANCOIS 1ER (76)"}
   {:id "fake-id-0760090K" :name "Lycée PIERRE CORNEILLE (76)"}
   {:id "fake-id-0760093N" :name "Lycée CAMILLE SAINT-SAENS (76)"}
   {:id "fake-id-0770927P" :name "Lycée FRANCOIS 1ER (77)"}
   {:id "fake-id-0772737G" :name "Lycée INTERNAT D'EXCELLENCE SOURDUN (77)"}
   {:id "fake-id-0782562L" :name "Lycée HOCHE (78)"}
   {:id "fake-id-0783548H" :name "Lycée FRANCO ALLEMAND (78)"}
   {:id "fake-id-0783549J" :name "Lycée INTERNATIONAL (78)"}
   {:id "fake-id-0800009A" :name "Lycée LOUIS THUILLIER (80)"}
   {:id "fake-id-0800010B" :name "Lycée MADELEINE MICHELIS (80)"}
   {:id "fake-id-0810005R" :name "Lycée BELLEVUE (81)"}
   {:id "fake-id-0810006S" :name "Lycée LAPEROUSE (81)"}
   {:id "fake-id-0810030T" :name "Lycée LAS CASES (81)"}
   {:id "fake-id-0820016X" :name "Lycée FRANCOIS MITTERRAND (82)"}
   {:id "fake-id-0820020B" :name "Lycée JULES MICHELET (82)"}
   {:id "fake-id-0840003X" :name "Lycée FREDERIC MISTRAL (84)"}
   {:id "fake-id-0860035W" :name "Lycée CAMILLE GUERIN (86)"}
   {:id "fake-id-0870015U" :name "Lycée GAY LUSSAC (87)"}
   {:id "fake-id-0870016V" :name "Lycée LEONARD LIMOSIN (87)"}
   {:id "fake-id-0870045B" :name "Lycée BERNARD PALISSY (87)"}
   {:id "fake-id-0880020U" :name "Lycée CLAUDE GELLEE (88)"}
   {:id "fake-id-0880030E" :name "Lycée LA HAIE GRISELLE (88)"}
   {:id "fake-id-0890003V" :name "Lycée JACQUES AMYOT (89)"}
   {:id "fake-id-0920142E" :name "Lycée LOUIS PASTEUR (92)"}
   {:id "fake-id-0920143F" :name "Lycée LA FOLIE SAINT JAMES (92)"}
   {:id "fake-id-0920146J" :name "Lycée MARIE CURIE (92)"}
   {:id "fake-id-0922615T" :name "Lycée LUCIE AUBRAC (92)"}
   {:id "fake-id-0940117S" :name "Lycée EDOUARD BRANLY (94)"}
   {:id "fake-id-9840002E" :name "Lycée DE  PAUL GAUGUIN (984)"}
   {:id "fake-id-0010010F" :name "Lycée G.T. DU BUGEY (01)"}
   {:id "fake-id-0010014K" :name "Lycée G.T. EDGAR QUINET (01)"}
   {:id "fake-id-0010016M" :name "Lycée G.T. JOSEPH-MARIE CARRIAT (01)"}
   {:id "fake-id-0010072Y" :name "Lycée G.T. INTERNATIONAL (01)"}
   {:id "fake-id-0011194T" :name "Lycée G.T. DE LA PLAINE DE L'AIN (01)"}
   {:id "fake-id-0011276G" :name "Lycée G.T. DU VAL DE SAONE (01)"}
   {:id "fake-id-0011326L" :name "Lycée G.T. DE LA COTIERE (01)"}
   {:id "fake-id-0020032Z" :name "Lycée G.T. PAUL CLAUDEL (02)"}
   {:id "fake-id-0020048S" :name "Lycée G.T. HENRI MARTIN (02)"}
   {:id "fake-id-0020049T" :name "Lycée G.T. PIERRE DE LA RAMEE (02)"}
   {:id "fake-id-0020059D" :name "Lycée G.T. GERARD DE NERVAL (02)"}
   {:id "fake-id-0021946E" :name "Lycée G.T. Lycée EUROPEEN (02)"}
   {:id "fake-id-0040010P" :name "Lycée G.T. FELIX ESCLANGON (04)"}
   {:id "fake-id-0040027H" :name "Lycée G.T. ALEXANDRA DAVID NEEL (04)"}
   {:id "fake-id-0040490L" :name "Lycée G.T. PIERRE-GILLES DE GENNES (04)"}
   {:id "fake-id-0050006E" :name "Lycée G.T. DOMINIQUE VILLARS (05)"}
   {:id "fake-id-0050007F" :name "Lycée G.T. ARISTIDE BRIAND (05)"}
   {:id "fake-id-0060001U" :name "Lycée G.T. JACQUES AUDIBERTI (06)"}
   {:id "fake-id-0060009C" :name "Lycée G.T. AUGUSTE RENOIR (06)"}
   {:id "fake-id-0060013G" :name "Lycée G.T. BRISTOL (06)"}
   {:id "fake-id-0060014H" :name "Lycée G.T. JULES FERRY (06)"}
   {:id "fake-id-0060020P" :name "Lycée G.T. AMIRAL DE GRASSE (06)"}
   {:id "fake-id-0060026W" :name "Lycée G.T. PIERRE ET MARIE CURIE (06)"}
   {:id "fake-id-0060029Z" :name "Lycée G.T. PARC IMPERIAL (06)"}
   {:id "fake-id-0060031B" :name "Lycée G.T. ALBERT CALMETTE (06)"}
   {:id "fake-id-0060075Z" :name "Lycée G.T. LES EUCALYPTUS (06)"}
   {:id "fake-id-0061642C" :name "Lycée G.T. INTERNATIONAL (06)"}
   {:id "fake-id-0061760F" :name "Lycée G.T. ALEXIS DE TOCQUEVILLE (06)"}
   {:id "fake-id-0061763J" :name "Lycée G.T. GUILLAUME APOLLINAIRE (06)"}
   {:id "fake-id-0061884R" :name "Lycée G.T. HENRI MATISSE (06)"}
   {:id "fake-id-0061991G" :name "Lycée G.T. ALLIANCE/APEDA (06)"}
   {:id "fake-id-0062015H" :name "Lycée G.T. SIMONE VEIL (06)"}
   {:id "fake-id-0062089N" :name "Lycée G.T. RENE GOSCINNY (06)"}
   {:id "fake-id-0080007P" :name "Lycée G.T. SEVIGNE (08)"}
   {:id "fake-id-0080027L" :name "Lycée G.T. MONGE (08)"}
   {:id "fake-id-0080045F" :name "Lycée G.T. PIERRE BAYLE (08)"}
   {:id "fake-id-0090002D" :name "Lycée G.T. GABRIEL FAURE (09)"}
   {:id "fake-id-0090013R" :name "Lycée polyv. de MIREPOIX (09)"} ; fixed
   {:id "fake-id-0100015M" :name "Lycée G.T. F. ET I. JOLIOT CURIE (10)"}
   {:id "fake-id-0100022V" :name "Lycée G.T. CHRESTIEN DE TROYES (10)"}
   {:id "fake-id-0101016A" :name "Lycée G.T. EDOUARD HERRIOT (10)"}
   {:id "fake-id-0110004V" :name "Lycée G.T. PAUL SABATIER (11)"}
   {:id "fake-id-0110022P" :name "Lycée G.T. DOCTEUR LACROIX (11)"}
   {:id "fake-id-0120006S" :name "Lycée G.T. LA DECOUVERTE (12)"}
   {:id "fake-id-0120012Y" :name "Lycée G.T. JEAN VIGO (12)"}
   {:id "fake-id-0120031U" :name "Lycée G.T. RAYMOND SAVIGNAC (12)"}
   {:id "fake-id-0130001F" :name "Lycée G.T. EMILE ZOLA (13)"}
   {:id "fake-id-0130002G" :name "Lycée G.T. PAUL CEZANNE (13)"}
   {:id "fake-id-0130003H" :name "Lycée G.T. VAUVENARGUES (13)"}
   {:id "fake-id-0130036U" :name "Lycée G.T. PERIER (13)"}
   {:id "fake-id-0130037V" :name "Lycée G.T. MARCEL PAGNOL (13)"}
   {:id "fake-id-0130038W" :name "Lycée G.T. MARSEILLEVEYRE (13)"}
   {:id "fake-id-0130039X" :name "Lycée G.T. SAINT CHARLES (13)"}
   {:id "fake-id-0130040Y" :name "Lycée G.T. THIERS (13)"}
   {:id "fake-id-0130042A" :name "Lycée G.T. MONTGRAND (13)"}
   {:id "fake-id-0130043B" :name "Lycée G.T. VICTOR HUGO (13)"}
   {:id "fake-id-0130048G" :name "Lycée G.T. SAINT EXUPERY (13)"}
   {:id "fake-id-0130049H" :name "Lycée G.T. REMPART (RUE DU) (13)"}
   {:id "fake-id-0130160D" :name "Lycée G.T. EMPERI (L') (13)"}
   {:id "fake-id-0130161E" :name "Lycée G.T. ADAM DE CRAPONNE (13)"}
   {:id "fake-id-0130164H" :name "Lycée G.T. ALPHONSE DAUDET (13)"}
   {:id "fake-id-0130175V" :name "Lycée G.T. HONORE DAUMIER (13)"}
   {:id "fake-id-0131549N" :name "Lycée G.T. FREDERIC JOLIOT-CURIE (13)"}
   {:id "fake-id-0132210G" :name "Lycée G.T. JEAN LURCAT (13)"}
   {:id "fake-id-0132410Z" :name "Lycée G.T. MAURICE GENEVOIX (13)"}
   {:id "fake-id-0132495S" :name "Lycée G.T. ARTHUR RIMBAUD (13)"}
   {:id "fake-id-0132733A" :name "Lycée G.T. ANTONIN ARTAUD (13)"}
   {:id "fake-id-0133195C" :name "Lycée G.T. JEAN COCTEAU (13)"}
   {:id "fake-id-0133244F" :name "Lycée G.T. MARIE MADELEINE FOURCADE (13)"}
   {:id "fake-id-0133525L" :name "Lycée G.T. GEORGES DUBY (13)"}
   {:id "fake-id-0140013N" :name "Lycée G.T. MALHERBE (14)"}
   {:id "fake-id-0140014P" :name "Lycée G.T. AUGUSTIN FRESNEL (14)"}
   {:id "fake-id-0140017T" :name "Lycée G.T. JEAN ROSTAND (14)"}
   {:id "fake-id-0141555P" :name "Lycée G.T. MARIE CURIE (14)"}
   {:id "fake-id-0141796B" :name "Lycée G.T. SALVADOR ALLENDE (14)"}
   {:id "fake-id-0160002R" :name "Lycée G.T. GUEZ DE BALZAC (16)"}
   {:id "fake-id-0160003S" :name "Lycée G.T. MARGUERITE DE VALOIS (16)"}
   {:id "fake-id-0160004T" :name "Lycée G.T. CHARLES A COULOMB (16)"}
   {:id "fake-id-0160010Z" :name "Lycée G.T. ELIE VINET (16)"}
   {:id "fake-id-0170022G" :name "Lycée G.T. MAURICE MERLEAU-PONTY (17)"}
   {:id "fake-id-0170027M" :name "Lycée G.T. RENE JOSUE VALIN (17)"}
   {:id "fake-id-0170028N" :name "Lycée G.T. JEAN DAUTET (17)"}
   {:id "fake-id-0170029P" :name "Lycée G.T. LEONCE VIELJEUX (17)"}
   {:id "fake-id-0170058W" :name "Lycée G.T. BELLEVUE (17)"}
   {:id "fake-id-0170060Y" :name "Lycée G.T. BERNARD PALISSY (17)"}
   {:id "fake-id-0171418Z" :name "Lycée G.T. SAINT-EXUPERY (17)"}
   {:id "fake-id-0180006J" :name "Lycée G.T. MARGUERITE DE NAVARRE (18)"}
   {:id "fake-id-0180007K" :name "Lycée G.T. JACQUES COEUR (18)"}
   {:id "fake-id-0180024D" :name "Lycée G.T. JEAN MOULIN (18)"}
   {:id "fake-id-0190010H" :name "Lycée G.T. GEORGES CABANIS (19)"}
   {:id "fake-id-0190032G" :name "Lycée G.T. EDMOND PERRIER (19)"}
   {:id "fake-id-0190038N" :name "Lycée G.T. BERNART DE VENTADOUR (19)"}
   {:id "fake-id-0210012Z" :name "Lycée G.T. STEPHEN LIEGEARD (21)"}
   {:id "fake-id-0210015C" :name "Lycée G.T. CARNOT (21)"}
   {:id "fake-id-0210017E" :name "Lycée G.T. MONTCHAPET (21)"}
   {:id "fake-id-0211909L" :name "Lycée G.T. JEAN-MARC BOIVIN (21)"}
   {:id "fake-id-0220018A" :name "Lycée G.T. AUGUSTE PAVIE (22)"}
   {:id "fake-id-0220023F" :name "Lycée G.T. FELIX LE DANTEC (22)"}
   {:id "fake-id-0220056S" :name "Lycée G.T. FRANCOIS RABELAIS (22)"}
   {:id "fake-id-0220057T" :name "Lycée G.T. ERNEST RENAN (22)"}
   {:id "fake-id-0220058U" :name "Lycée G.T. CHAPTAL (22)"}
   {:id "fake-id-0220060W" :name "Lycée G.T. EUGENE FREYSSINET (22)"}
   {:id "fake-id-0220065B" :name "Lycée G.T. JOSEPH SAVINA (22)"}
   {:id "fake-id-0230002C" :name "Lycée G.T. EUGENE JAMOT (23)"}
   {:id "fake-id-0230020X" :name "Lycée G.T. PIERRE BOURDAN (23)"}
   {:id "fake-id-0240005A" :name "Lycée G.T. MAINE DE BIRAN (24)"}
   {:id "fake-id-0240025X" :name "Lycée G.T. LAURE GATET (24)"}
   {:id "fake-id-0240032E" :name "Lycée G.T. ARNAUT DANIEL (24)"}
   {:id "fake-id-0240035H" :name "Lycée G.T. PRE DE CORDY (24)"}
   {:id "fake-id-0241137F" :name "Lycée G.T. JAY DE BEAUFORT (24)"}
   {:id "fake-id-0250007X" :name "Lycée G.T. VICTOR HUGO (25)"}
   {:id "fake-id-0250008Y" :name "Lycée G.T. LOUIS PASTEUR (25)"}
   {:id "fake-id-0250010A" :name "Lycée G.T. LOUIS PERGAUD (25)"}
   {:id "fake-id-0250030X" :name "Lycée G.T. GEORGES CUVIER (25)"}
   {:id "fake-id-0250033A" :name "Lycée G.T. LE GRAND CHENOIS (25)"}
   {:id "fake-id-0250058C" :name "Lycée G.T. ARMAND PEUGEOT (25)"}
   {:id "fake-id-0260015A" :name "Lycée G.T. ALAIN BORNE (26)"}
   {:id "fake-id-0260017C" :name "Lycée G.T. ROUMANILLE (26)"}
   {:id "fake-id-0261277X" :name "Lycée G.T. LES TROIS SOURCES (26)"}
   {:id "fake-id-0270003G" :name "Lycée G.T. AUGUSTIN FRESNEL (27)"}
   {:id "fake-id-0270016W" :name "Lycée G.T. ARISTIDE BRIAND (27)"}
   {:id "fake-id-0270017X" :name "Lycée G.T. MODESTE LEROY (27)"}
   {:id "fake-id-0270042Z" :name "Lycée G.T. PORTE DE NORMANDIE (27)"}
   {:id "fake-id-0270044B" :name "Lycée G.T. GEORGES DUMEZIL (27)"}
   {:id "fake-id-0271431J" :name "Lycée G.T. JACQUES PREVERT (27)"}
   {:id "fake-id-0271579V" :name "Lycée G.T. LEOPOLD SEDAR SENGHOR (27)"}
   {:id "fake-id-0271580W" :name "Lycée G.T. ANDRE MALRAUX (27)"}
   {:id "fake-id-0271582Y" :name "Lycée G.T. MARC BLOCH (27)"}
   {:id "fake-id-0280015P" :name "Lycée G.T. EMILE ZOLA (28)"}
   {:id "fake-id-0280019U" :name "Lycée G.T. ROTROU (28)"}
   {:id "fake-id-0281047L" :name "Lycée G.T. FULBERT (28)"}
   {:id "fake-id-0290008B" :name "Lycée G.T. AMIRAL RONARC'H (29)"}
   {:id "fake-id-0290013G" :name "Lycée G.T. JULES LESVEN (29)"}
   {:id "fake-id-0290023T" :name "Lycée G.T. JEAN MOULIN (29)"}
   {:id "fake-id-0290034E" :name "Lycée G.T. JEAN-MARIE LE BRIS (29)"}
   {:id "fake-id-0290051Y" :name "Lycée G.T. TRISTAN CORBIERE (29)"}
   {:id "fake-id-0290062K" :name "Lycée G.T. RENE LAENNEC (29)"}
   {:id "fake-id-0290076A" :name "Lycée G.T. DE KERNEUZEC (29)"}
   {:id "fake-id-0290098Z" :name "Lycée G.T. DE CORNOUAILLE (29)"}
   {:id "fake-id-0292047T" :name "Lycée G.T. DU LEON (29)"}
   {:id "fake-id-0300021K" :name "Lycée G.T. ALPHONSE DAUDET (30)"}
   {:id "fake-id-0300023M" :name "Lycée G.T. ALBERT CAMUS (30)"}
   {:id "fake-id-0300046M" :name "Lycée G.T. CHARLES GIDE (30)"}
   {:id "fake-id-0301552Z" :name "Lycée G.T. PHILIPPE LAMOUR (30)"}
   {:id "fake-id-0301722J" :name "Lycée G.T. JEAN VILAR (30)"}
   {:id "fake-id-0310024H" :name "Lycée G.T. PIERRE D'ARAGON (31)"}
   {:id "fake-id-0310032S" :name "Lycée G.T. DE BAGATELLE (31)"}
   {:id "fake-id-0310039Z" :name "Lycée G.T. MARCELIN BERTHELOT (31)"}
   {:id "fake-id-0310044E" :name "Lycée G.T. DEODAT DE SEVERAC (31)"}
   {:id "fake-id-0310047H" :name "Lycée G.T. OZENNE (31)"}
   {:id "fake-id-0311323V" :name "Lycée G.T. RIVE GAUCHE (31)"}
   {:id "fake-id-0311334G" :name "Lycée G.T. EDMOND ROSTAND (31)"}
   {:id "fake-id-0311586F" :name "Lycée G.T. TOULOUSE-LAUTREC (31)"}
   {:id "fake-id-0311902Z" :name "Lycée G.T. STEPHANE HESSEL (31)"}
   {:id "fake-id-0312093G" :name "Lycée G.T. INTERNATIONAL VICTOR HUGO (31)"}
   {:id "fake-id-0312267W" :name "Lycée G.T. DES ARENES (31)"}
   {:id "fake-id-0312289V" :name "Lycée G.T. PIERRE-PAUL RIQUET (31)"}
   {:id "fake-id-0312290W" :name "Lycée G.T. HENRI MATISSE (31)"}
   {:id "fake-id-0312744P" :name "Lycée G.T. JEAN-PIERRE VERNANT (31)"}
   {:id "fake-id-0320002D" :name "Lycée G.T. PARDAILHAN (32)"}
   {:id "fake-id-0320009L" :name "Lycée G.T. BOSSUET (32)"}
   {:id "fake-id-0330003Z" :name "Lycée G.T. GRAND AIR (33)"}
   {:id "fake-id-0330010G" :name "Lycée G.T. ANATOLE DE MONZIE (33)"}
   {:id "fake-id-0330020T" :name "Lycée G.T. JAUFRE RUDEL (33)"}
   {:id "fake-id-0330023W" :name "Lycée G.T. CAMILLE JULLIAN (33)"}
   {:id "fake-id-0330027A" :name "Lycée G.T. FRANCOIS MAURIAC (33)"}
   {:id "fake-id-0330029C" :name "Lycée G.T. NICOLAS BREMONTIER (33)"}
   {:id "fake-id-0330088S" :name "Lycée G.T. MAX LINDER (33)"}
   {:id "fake-id-0331760J" :name "Lycée G.T. FERNAND DAGUIN (33)"}
   {:id "fake-id-0332081H" :name "Lycée G.T. ODILON REDON (33)"}
   {:id "fake-id-0332722E" :name "Lycée G.T. PAPE CLEMENT (33)"}
   {:id "fake-id-0332744D" :name "Lycée G.T. ELIE FAURE (33)"}
   {:id "fake-id-0332747G" :name "Lycée G.T. JEAN CONDORCET (33)"}
   {:id "fake-id-0332835C" :name "Lycée G.T. PHILIPPE COUSTEAU (33)"}
   {:id "fake-id-0340009A" :name "Lycée G.T. HENRI IV (34)"}
   {:id "fake-id-0340023R" :name "Lycée G.T. RENE GOSSE (34)"}
   {:id "fake-id-0340038G" :name "Lycée G.T. JOFFRE (34)"}
   {:id "fake-id-0340039H" :name "Lycée G.T. GEORGES CLEMENCEAU (34)"}
   {:id "fake-id-0340059E" :name "Lycée G.T. JEAN MOULIN (34)"}
   {:id "fake-id-0340076Y" :name "Lycée G.T. IRENE ET FREDERIC JOLIOT CURIE (34)"}
   {:id "fake-id-0341736C" :name "Lycée G.T. JEAN MONNET (34)"}
   {:id "fake-id-0350011X" :name "Lycée G.T. JEAN GUEHENNO (35)"}
   {:id "fake-id-0350022J" :name "Lycée G.T. BEAUMONT (35)"}
   {:id "fake-id-0350026N" :name "Lycée G.T. JEAN MACE (35)"}
   {:id "fake-id-0350028R" :name "Lycée G.T. BREQUIGNY (35)"}
   {:id "fake-id-0350029S" :name "Lycée G.T. JOLIOT-CURIE (35)"}
   {:id "fake-id-0350042F" :name "Lycée G.T. MAUPERTUIS (35)"}
   {:id "fake-id-0350053T" :name "Lycée G.T. BERTRAND D'ARGENTRE (35)"}
   {:id "fake-id-0351907H" :name "Lycée G.T. RENE DESCARTES (35)"}
   {:id "fake-id-0352009U" :name "Lycée G.T. VICTOR ET HELENE BASCH (35)"}
   {:id "fake-id-0352235P" :name "Lycée G.T. RENE CASSIN (35)"}
   {:id "fake-id-0352304P" :name "Lycée G.T. SEVIGNE (35)"}
   {:id "fake-id-0352318E" :name "Lycée G.T. JEAN BRITO (35)"}
   {:id "fake-id-0352341E" :name "Lycée G.T. ST JOSEPH (35)"}
   {:id "fake-id-0352533N" :name "Lycée G.T. FRANCOIS RENE DE CHATEAUBRIAND (35)"}
   {:id "fake-id-0352686E" :name "Lycée G.T. ANITA CONTI (35)"}
   {:id "fake-id-0360009P" :name "Lycée G.T. PIERRE ET MARIE CURIE (36)"}
   {:id "fake-id-0360024F" :name "Lycée G.T. HONORE DE BALZAC (36)"}
   {:id "fake-id-0370001A" :name "Lycée G.T. LEONARD DE VINCI (37)"}
   {:id "fake-id-0370016S" :name "Lycée G.T. ALFRED DE VIGNY (37)"}
   {:id "fake-id-0370036N" :name "Lycée G.T. BALZAC (37)"}
   {:id "fake-id-0370037P" :name "Lycée G.T. CHOISEUL (37)"}
   {:id "fake-id-0370039S" :name "Lycée G.T. PAUL-LOUIS COURIER (37)"}
   {:id "fake-id-0371417P" :name "Lycée G.T. JEAN MONNET (37)"}
   {:id "fake-id-0371418R" :name "Lycée G.T. JACQUES DE VAUCANSON (37)"}
   {:id "fake-id-0380008C" :name "Lycée G.T. L'OISELET (38)"}
   {:id "fake-id-0380029A" :name "Lycée G.T. LES EAUX CLAIRES (38)"}
   {:id "fake-id-0380032D" :name "Lycée G.T. EMMANUEL  MOUNIER (38)"}
   {:id "fake-id-0381599G" :name "Lycée G.T. DE L'EDIT (38)"}
   {:id "fake-id-0382270L" :name "Lycée G.T. PIERRE DU TERRAIL (38)"}
   {:id "fake-id-0382780R" :name "Lycée G.T. ARISTIDE BERGES (38)"}
   {:id "fake-id-0382838D" :name "Lycée G.T. LA PLEIADE (38)"}
   {:id "fake-id-0382920T" :name "Lycée G.T. MARIE CURIE (38)"}
   {:id "fake-id-0383069E" :name "Lycée G.T. CAMILLE COROT (38)"}
   {:id "fake-id-0383119J" :name "Lycée G.T. PIERRE BEGHIN (38)"}
   {:id "fake-id-0383263R" :name "Lycée G.T. MARIE REYNOARD (38)"}
   {:id "fake-id-0390019J" :name "Lycée G.T. JEAN MICHEL (39)"}
   {:id "fake-id-0400017B" :name "Lycée G.T. VICTOR DURUY (40)"}
   {:id "fake-id-0400018C" :name "Lycée G.T. CHARLES DESPIAU (40)"}
   {:id "fake-id-0400067F" :name "Lycée G.T. JEAN CASSAIGNE (40)"}
   {:id "fake-id-0410002E" :name "Lycée G.T. FRANCOIS PHILIBERT DESSAIGNES (41)"}
   {:id "fake-id-0410017W" :name "Lycée G.T. CLAUDE DE FRANCE (41)"}
   {:id "fake-id-0410030K" :name "Lycée G.T. RONSARD (41)"}
   {:id "fake-id-0410959V" :name "Lycée G.T. CAMILLE CLAUDEL (41)"}
   {:id "fake-id-0420013L" :name "Lycée G.T. ALBERT CAMUS (42)"}
   {:id "fake-id-0420014M" :name "Lycée G.T. JACOB HOLTZER (42)"}
   {:id "fake-id-0420018S" :name "Lycée G.T. BEAUREGARD (42)"}
   {:id "fake-id-0420033H" :name "Lycée G.T. ALBERT THOMAS (42)"}
   {:id "fake-id-0420034J" :name "Lycée G.T. CARNOT (42)"}
   {:id "fake-id-0420042T" :name "Lycée G.T. HONORE D'URFE (42)"}
   {:id "fake-id-0420043U" :name "Lycée G.T. JEAN MONNET (42)"}
   {:id "fake-id-0420044V" :name "Lycée G.T. SIMONE WEIL (42)"}
   {:id "fake-id-0420046X" :name "Lycée G.T. ETIENNE MIMARD (42)"}
   {:id "fake-id-0421788R" :name "Lycée G.T. DU FOREZ (42)"}
   {:id "fake-id-0421976V" :name "Lycée G.T. FRANCOIS MAURIAC-FOREZ (42)"}
   {:id "fake-id-0422284E" :name "Lycée G.T. DES HORIZONS (42)"}
   {:id "fake-id-0430003V" :name "Lycée G.T. LA FAYETTE (43)"}
   {:id "fake-id-0430021P" :name "Lycée G.T. SIMONE WEIL (43)"}
   {:id "fake-id-0430947W" :name "Lycée G.T. LEONARD DE VINCI (43)"}
   {:id "fake-id-0440012Z" :name "Lycée G.T. GRAND AIR (44)"}
   {:id "fake-id-0440021J" :name "Lycée G.T. CLEMENCEAU (44)"}
   {:id "fake-id-0440029T" :name "Lycée G.T. LIVET (44)"}
   {:id "fake-id-0440069L" :name "Lycée G.T. ARISTIDE BRIAND (44)"}
   {:id "fake-id-0440077V" :name "Lycée G.T. JACQUES PREVERT (44)"}
   {:id "fake-id-0440086E" :name "Lycée G.T. LA COLINIERE (44)"}
   {:id "fake-id-0440288Z" :name "Lycée G.T. ALBERT CAMUS (44)"}
   {:id "fake-id-0441992B" :name "Lycée G.T. PAYS DE RETZ (44)"}
   {:id "fake-id-0441993C" :name "Lycée G.T. CARCOUET (44)"}
   {:id "fake-id-0442095N" :name "Lycée G.T. LA HERDRIE (44)"}
   {:id "fake-id-0442207K" :name "Lycée G.T. CAMILLE CLAUDEL (44)"}
   {:id "fake-id-0442309W" :name "Lycée G.T. ALCIDE D'ORBIGNY (44)"}
   {:id "fake-id-0442834S" :name "Lycée G.T. HONORÉ D'ESTIENNE D'ORVES (44)"}
   {:id "fake-id-0440176C" :name "Collège et lycée SAINT-DOMINIQUE (44)"}
   {:id "fake-id-0442083A" :name "Centre Éducatif Nantais pour Sportifs ou CENS (44)"}
   {:id "fake-id-0450029M" :name "Lycée G.T. BERNARD PALISSY (45)"}
   {:id "fake-id-0450040Z" :name "Lycée G.T. EN FORET (45)"}
   {:id "fake-id-0450042B" :name "Lycée G.T. DURZY (45)"}
   {:id "fake-id-0450049J" :name "Lycée G.T. POTHIER (45)"}
   {:id "fake-id-0450062Y" :name "Lycée G.T. DUHAMEL DU MONCEAU (45)"}
   {:id "fake-id-0450782F" :name "Lycée G.T. VOLTAIRE (45)"}
   {:id "fake-id-0451462V" :name "Lycée G.T. JACQUES MONOD (45)"}
   {:id "fake-id-0451484U" :name "Lycée G.T. FRANCOIS VILLON (45)"}
   {:id "fake-id-0451526P" :name "Lycée G.T. CHARLES PEGUY (45)"}
   {:id "fake-id-0470001W" :name "Lycée G.T. BERNARD PALISSY (47)"}
   {:id "fake-id-0470003Y" :name "Lycée G.T. JEAN BAPTISTE DE BAUDRE (47)"}
   {:id "fake-id-0470018P" :name "Lycée G.T. MARGUERITE FILHOL (47)"}
   {:id "fake-id-0480007X" :name "Lycée G.T. CHAPTAL (48)"}
   {:id "fake-id-0480009Z" :name "Lycée G.T. EMILE PEYTAVIN (48)"}
   {:id "fake-id-0490002L" :name "Lycée G.T. JOACHIM DU BELLAY (49)"}
   {:id "fake-id-0491966W" :name "Lycée G.T. HENRI BERGSON (49)"}
   {:id "fake-id-0492061Z" :name "Lycée G.T. AUGUSTE ET JEAN RENOIR (49)"}
   {:id "fake-id-0492089E" :name "Lycée G.T. EMMANUEL MOUNIER (49)"}
   {:id "fake-id-0492148U" :name "Lycée G.T. JEAN BODIN (49)"}
   {:id "fake-id-0500016W" :name "Lycée G.T. JEAN FRANCOIS MILLET (50)"}
   {:id "fake-id-0500017X" :name "Lycée G.T. ALEXIS DE TOCQUEVILLE (50)"}
   {:id "fake-id-0500026G" :name "Lycée G.T. CHARLES FRANCOIS LEBRUN (50)"}
   {:id "fake-id-0500082T" :name "Lycée G.T. HENRI CORNAT (50)"}
   {:id "fake-id-0501828R" :name "Lycée G.T. VICTOR GRIGNARD (50)"}
   {:id "fake-id-0501839C" :name "Lycée G.T. SIVARD DE BEAULIEU (50)"}
   {:id "fake-id-0510032H" :name "Lycée G.T. JEAN JAURES (51)"}
   {:id "fake-id-0510034K" :name "Lycée G.T. FRANKLIN ROOSEVELT (51)"}
   {:id "fake-id-0511926S" :name "Lycée G.T. MARC CHAGALL (51)"}
   {:id "fake-id-0520019N" :name "Lycée G.T. PHILIPPE LEBON (52)"}
   {:id "fake-id-0520027X" :name "Lycée G.T. ST EXUPERY (52)"}
   {:id "fake-id-0520028Y" :name "Lycée G.T. BLAISE PASCAL (52)"}
   {:id "fake-id-0520844K" :name "Lycée G.T. EDME BOUCHARDON (52)"}
   {:id "fake-id-0530004S" :name "Lycée G.T. VICTOR HUGO (53)"}
   {:id "fake-id-0530010Y" :name "Lycée G.T. AMBROISE PARE (53)"}
   {:id "fake-id-0530011Z" :name "Lycée G.T. DOUANIER ROUSSEAU (53)"}
   {:id "fake-id-0530016E" :name "Lycée G.T. LAVOISIER (53)"}
   {:id "fake-id-0540034U" :name "Lycée G.T. ERNEST BICHAT (54)"}
   {:id "fake-id-0540038Y" :name "Lycée G.T. HENRI POINCARE (54)"}
   {:id "fake-id-0540039Z" :name "Lycée G.T. JEANNE D'ARC (54)"}
   {:id "fake-id-0540040A" :name "Lycée G.T. FREDERIC CHOPIN (54)"}
   {:id "fake-id-0540041B" :name "Lycée G.T. GEORGES DE LA TOUR (54)"}
   {:id "fake-id-0540042C" :name "Lycée G.T. HENRI LORITZ (54)"}
   {:id "fake-id-0540044E" :name "Lycée G.T. ARTHUR VAROQUAUX (54)"}
   {:id "fake-id-0540058V" :name "Lycée G.T. JACQUES MARQUETTE (54)"}
   {:id "fake-id-0540066D" :name "Lycée G.T. LOUIS MAJORELLE (54)"}
   {:id "fake-id-0540070H" :name "Lycée G.T. JACQUES CALLOT (54)"}
   {:id "fake-id-0541286E" :name "Lycée G.T. LOUIS BERTRAND (54)"}
   {:id "fake-id-0550002D" :name "Lycée G.T. RAYMOND POINCARE (55)"}
   {:id "fake-id-0560025Y" :name "Lycée G.T. DUPUY DE LOME (56)"}
   {:id "fake-id-0560038M" :name "Lycée G.T. JOSEPH LOTH (56)"}
   {:id "fake-id-0560051B" :name "Lycée G.T. ALAIN RENE LESAGE (56)"}
   {:id "fake-id-0560101F" :name "Lycée G.T. SAINT-LOUIS-LA PAIX (56)"}
   {:id "fake-id-0561534N" :name "Lycée G.T. BENJAMIN FRANKLIN (56)"}
   {:id "fake-id-0561607T" :name "Lycée G.T. VICTOR HUGO (56)"}
   {:id "fake-id-0561627P" :name "Lycée G.T. CHARLES DE GAULLE (56)"}
   {:id "fake-id-0570023R" :name "Lycée G.T. ANTOINE DE SAINT-EXUPERY (57)"}
   {:id "fake-id-0570029X" :name "Lycée G.T. JEAN MOULIN (57)"}
   {:id "fake-id-0570054Z" :name "Lycée G.T. FABERT (57)"}
   {:id "fake-id-0570057C" :name "Lycée G.T. ROBERT SCHUMAN (57)"}
   {:id "fake-id-0570058D" :name "Lycée G.T. LOUIS VINCENT (57)"}
   {:id "fake-id-0570081D" :name "Lycée G.T. ERCKMANN CHATRIAN (57)"}
   {:id "fake-id-0570085H" :name "Lycée G.T. JEAN-VICTOR PONCELET (57)"}
   {:id "fake-id-0570098X" :name "Lycée G.T. JEAN DE PANGE (57)"}
   {:id "fake-id-0572027U" :name "Lycée G.T. JEAN-BAPTISTE COLBERT (57)"}
   {:id "fake-id-0572757M" :name "Lycée G.T. GEORGES DE LA TOUR (57)"}
   {:id "fake-id-0573281G" :name "Lycée G.T. DE LA COMMUNICATION (57)"}
   {:id "fake-id-0580031U" :name "Lycée G.T. JULES RENARD (58)"}
   {:id "fake-id-0580032V" :name "Lycée G.T. RAOUL FOLLEREAU (58)"}
   {:id "fake-id-0580753D" :name "Lycée G.T. ALAIN COLAS (58)"}
   {:id "fake-id-0590010R" :name "Lycée G.T. PAUL HAZARD (59)"}
   {:id "fake-id-0590034S" :name "Lycée G.T. PAUL DUEZ (59)"}
   {:id "fake-id-0590035T" :name "Lycée G.T. FENELON (59)"}
   {:id "fake-id-0590060V" :name "Lycée G.T. ALFRED KASTLER (59)"}
   {:id "fake-id-0590064Z" :name "Lycée G.T. JEAN-BAPTISTE COROT (59)"}
   {:id "fake-id-0590071G" :name "Lycée G.T. JEAN BART (59)"}
   {:id "fake-id-0590073J" :name "Lycée G.T. AUGUSTE ANGELLIER (59)"}
   {:id "fake-id-0590086Y" :name "Lycée G.T. MARGUERITE DE FLANDRE (59)"}
   {:id "fake-id-0590101P" :name "Lycée G.T. DES FLANDRES (59)"}
   {:id "fake-id-0590110Z" :name "Lycée G.T. JEAN PERRIN (59)"}
   {:id "fake-id-0590112B" :name "Lycée G.T. DUPLEIX (59)"}
   {:id "fake-id-0590117G" :name "Lycée G.T. LOUIS PASTEUR (59)"}
   {:id "fake-id-0590119J" :name "Lycée G.T. FAIDHERBE (59)"}
   {:id "fake-id-0590184E" :name "Lycée G.T. JEAN ROSTAND (59)"}
   {:id "fake-id-0590207E" :name "Lycée G.T. LOUIS PASTEUR (59)"}
   {:id "fake-id-0590221V" :name "Lycée G.T. HENRI WALLON (59)"}
   {:id "fake-id-0590222W" :name "Lycée G.T. WATTEAU (59)"}
   {:id "fake-id-0590258K" :name "Lycée G.T. GASTON BERGER (59)"}
   {:id "fake-id-0594424N" :name "Lycée G.T. RAYMOND QUENEAU (59)"}
   {:id "fake-id-0595616J" :name "Lycée G.T. DU NOORDOVER (59)"}
   {:id "fake-id-0595786U" :name "Lycée G.T. JEAN PROUVE (59)"}
   {:id "fake-id-0595809U" :name "Lycée G.T. DE L'ESCAUT (59)"}
   {:id "fake-id-0595867G" :name "Lycée G.T. INTERNATIONAL MONTEBELLO (59)"}
   {:id "fake-id-0595885B" :name "Lycée G.T. ARTHUR RIMBAUD (59)"}
   {:id "fake-id-0600001A" :name "Lycée G.T. FELIX FAURE (60)"}
   {:id "fake-id-0600009J" :name "Lycée G.T. JEAN ROSTAND (60)"}
   {:id "fake-id-0600013N" :name "Lycée G.T. CASSINI (60)"}
   {:id "fake-id-0600014P" :name "Lycée G.T. PIERRE D AILLY (60)"}
   {:id "fake-id-0600021X" :name "Lycée G.T. JULES UHRY (60)"}
   {:id "fake-id-0601824G" :name "Lycée G.T. JEANNE HACHETTE (60)"}
   {:id "fake-id-0601826J" :name "Lycée G.T. HUGUES CAPET (60)"}
   {:id "fake-id-0601832R" :name "Lycée G.T. JEAN MONNET (60)"}
   {:id "fake-id-0601865B" :name "Lycée G.T. CONDORCET (60)"}
   {:id "fake-id-0610001V" :name "Lycée G.T. ALAIN (61)"}
   {:id "fake-id-0610002W" :name "Lycée G.T. MARGUERITE DE NAVARRE (61)"}
   {:id "fake-id-0610014J" :name "Lycée G.T. AUGUSTE CHEVALIER (61)"}
   {:id "fake-id-0610026X" :name "Lycée G.T. NAPOLEON (61)"}
   {:id "fake-id-0620006V" :name "Lycée G.T. ROBESPIERRE (62)"}
   {:id "fake-id-0620040G" :name "Lycée G.T. LOUIS BLARINGHEM (62)"}
   {:id "fake-id-0620042J" :name "Lycée G.T. ANDRE MALRAUX (62)"}
   {:id "fake-id-0620062F" :name "Lycée G.T. PIERRE DE COUBERTIN (62)"}
   {:id "fake-id-0620070P" :name "Lycée G.T. DIDEROT (62)"}
   {:id "fake-id-0620108F" :name "Lycée G.T. CONDORCET (62)"}
   {:id "fake-id-0620120U" :name "Lycée G.T. ANATOLE FRANCE (62)"}
   {:id "fake-id-0620166U" :name "Lycée G.T. ALBERT CHATELET (62)"}
   {:id "fake-id-0622803K" :name "Lycée G.T. BLAISE PASCAL (62)"}
   {:id "fake-id-0622949U" :name "Lycée G.T. MARIETTE (62)"}
   {:id "fake-id-0623915U" :name "Lycée G.T. VOLTAIRE (62)"}
   {:id "fake-id-0624430D" :name "Lycée G.T. GAMBETTA CARNOT (62)"}
   {:id "fake-id-0630034V" :name "Lycée G.T. MURAT (63)"}
   {:id "fake-id-0630052P" :name "Lycée G.T. C. ET P. VIRLOGEUX (63)"}
   {:id "fake-id-0630068G" :name "Lycée G.T. MONTDORY (63)"}
   {:id "fake-id-0630069H" :name "Lycée G.T. JEAN ZAY (63)"}
   {:id "fake-id-0630077S" :name "Lycée G.T. AMBROISE BRUGIERE (63)"}
   {:id "fake-id-0631861F" :name "Lycée G.T. RENE DESCARTES (63)"}
   {:id "fake-id-0640011P" :name "Lycée G.T. LOUIS DE FOIX (64)"}
   {:id "fake-id-0640052J" :name "Lycée G.T. GASTON FEBUS (64)"}
   {:id "fake-id-0641732K" :name "Lycée G.T. SAINT-JOHN PERSE (64)"}
   {:id "fake-id-0641839B" :name "Lycée G.T. JACQUES MONOD (64)"}
   {:id "fake-id-0650026A" :name "Lycée G.T. MARIE CURIE (65)"}
   {:id "fake-id-0650040R" :name "Lycée G.T. LA SERRE DE SARSAN (65)"}
   {:id "fake-id-0660010C" :name "Lycée G.T. FRANCOIS ARAGO (66)"}
   {:id "fake-id-0660907C" :name "Lycée G.T. ANNEXE LGT PABLO PICASSO (66)"}
   {:id "fake-id-0670002N" :name "Lycée G.T. SCHURE (67)"}
   {:id "fake-id-0670005S" :name "Lycée G.T. ANDRE MAUROIS (67)"}
   {:id "fake-id-0670007U" :name "Lycée G.T. ADRIEN ZELLER (67)"}
   {:id "fake-id-0670020H" :name "Lycée G.T. ROBERT SCHUMAN (67)"}
   {:id "fake-id-0670041F" :name "Lycée G.T. HENRI MECK (67)"}
   {:id "fake-id-0670057Y" :name "Lycée G.T. GENERAL LECLERC (67)"}
   {:id "fake-id-0670071N" :name "Lycée G.T. DOCTEUR KOEBERLE (67)"}
   {:id "fake-id-0670078W" :name "Lycée G.T. JEAN MONNET (67)"}
   {:id "fake-id-0670082A" :name "Lycée G.T. LOUIS PASTEUR (67)"}
   {:id "fake-id-0672604S" :name "Lycée G.T. MARC BLOCH (67)"}
   {:id "fake-id-0680007N" :name "Lycée G.T. BARTHOLDI (68)"}
   {:id "fake-id-0680008P" :name "Lycée G.T. CAMILLE SEE (68)"}
   {:id "fake-id-0680015X" :name "Lycée G.T. ALFRED KASTLER (68)"}
   {:id "fake-id-0680031P" :name "Lycée G.T. ALBERT SCHWEITZER (68)"}
   {:id "fake-id-0680032R" :name "Lycée G.T. MICHEL DE MONTAIGNE (68)"}
   {:id "fake-id-0680034T" :name "Lycée G.T. LOUIS ARMAND (68)"}
   {:id "fake-id-0680060W" :name "Lycée G.T. RIBEAUPIERRE (68)"}
   {:id "fake-id-0680073K" :name "Lycée G.T. SCHEURER KESTNER (68)"}
   {:id "fake-id-0690023A" :name "Lycée G.T. AMPERE (69)"}
   {:id "fake-id-0690027E" :name "Lycée G.T. EDOUARD HERRIOT (69)"}
   {:id "fake-id-0690028F" :name "Lycée G.T. SAINT JUST (69)"}
   {:id "fake-id-0690029G" :name "Lycée G.T. LACASSAGNE (69)"}
   {:id "fake-id-0690031J" :name "Lycée G.T. ANTOINE DE SAINT-EXUPERY (69)"}
   {:id "fake-id-0690032K" :name "Lycée G.T. JULIETTE RÉCAMIER (69)"}
   {:id "fake-id-0690035N" :name "Lycée G.T. AUGUSTE ET LOUIS LUMIERE (69)"}
   {:id "fake-id-0690037R" :name "Lycée G.T. LA MARTINIERE DIDEROT (69)"}
   {:id "fake-id-0690038S" :name "Lycée G.T. LA MARTINIERE  DUCHERE (69)"}
   {:id "fake-id-0690042W" :name "Lycée G.T. COLBERT (69)"}
   {:id "fake-id-0690074F" :name "Lycée G.T. PARC CHABRIERES (69)"}
   {:id "fake-id-0690082P" :name "Lycée G.T. JEAN PERRIN (69)"}
   {:id "fake-id-0690085T" :name "Lycée G.T. RENE CASSIN (69)"}
   {:id "fake-id-0690103M" :name "Lycée G.T. FREDERIC FAYS (69)"}
   {:id "fake-id-0690104N" :name "Lycée G.T. MARCEL SEMBAT (69)"}
   {:id "fake-id-0690132U" :name "Lycée G.T. PIERRE BROSSOLETTE (69)"}
   {:id "fake-id-0692517L" :name "Lycée G.T. ALBERT CAMUS (69)"}
   {:id "fake-id-0693044J" :name "Lycée G.T. JEAN-PAUL SARTRE (69)"}
   {:id "fake-id-0693330V" :name "Lycée G.T. LOUIS ARAGON (69)"}
   {:id "fake-id-0693478F" :name "Lycée G.T. CONDORCET (69)"}
   {:id "fake-id-0693518Z" :name "Lycée G.T. BLAISE PASCAL (69)"}
   {:id "fake-id-0693619J" :name "Lycée G.T. ROBERT DOISNEAU (69)"}
   {:id "fake-id-0693654X" :name "Lycée G.T. RENE DESCARTES (69)"}
   {:id "fake-id-0694026B" :name "Lycée G.T. ROSA PARKS (69)"}
   {:id "fake-id-0701052N" :name "Lycée G.T. LES HABERGES (70)"}
   {:id "fake-id-0710023P" :name "Lycée G.T. LA PRAT'S (71)"}
   {:id "fake-id-0710054Y" :name "Lycée G.T. HENRI PARRIAT (71)"}
   {:id "fake-id-0710071S" :name "Lycée G.T. GABRIEL VOISIN (71)"}
   {:id "fake-id-0711137A" :name "Lycée G.T. CAMILLE CLAUDEL (71)"}
   {:id "fake-id-0720012X" :name "Lycée G.T. RACAN (72)"}
   {:id "fake-id-0720055U" :name "Lycée G.T. PAUL SCARRON (72)"}
   {:id "fake-id-0721493G" :name "Lycée G.T. MARGUERITE YOURCENAR (72)"}
   {:id "fake-id-0721548S" :name "Lycée G.T. ANDRE MALRAUX (72)"}
   {:id "fake-id-0730005J" :name "Lycée G.T. JEAN MOULIN (73)"}
   {:id "fake-id-0730037U" :name "Lycée G.T. PAUL HEROULT (73)"}
   {:id "fake-id-0731248K" :name "Lycée G.T. LOUIS ARMAND (73)"}
   {:id "fake-id-0731392S" :name "Lycée G.T. DU GRANIER (73)"}
   {:id "fake-id-0740005D" :name "Lycée G.T. GABRIEL FAURE (74)"}
   {:id "fake-id-0740037N" :name "Lycée G.T. MME DE STAEL (74)"}
   {:id "fake-id-0740046Y" :name "Lycée G.T. LA VERSOIE (74)"}
   {:id "fake-id-0741418P" :name "Lycée G.T. CHARLES BAUDELAIRE (74)"}
   {:id "fake-id-0741476C" :name "Lycée G.T. JEAN MONNET (74)"}
   {:id "fake-id-0741532N" :name "Lycée G.T. DE L'ALBANAIS (74)"}
   {:id "fake-id-0750647W" :name "Lycée G.T. TURGOT (75)"}
   {:id "fake-id-0750651A" :name "Lycée G.T. SIMONE WEIL (75)"}
   {:id "fake-id-0750653C" :name "Lycée G.T. SOPHIE GERMAIN (75)"}
   {:id "fake-id-0750663N" :name "Lycée G.T. CHAPTAL (75)"}
   {:id "fake-id-0750664P" :name "Lycée G.T. RACINE (75)"}
   {:id "fake-id-0750675B" :name "Lycée G.T. VOLTAIRE (75)"}
   {:id "fake-id-0750680G" :name "Lycée G.T. ARAGO (75)"}
   {:id "fake-id-0750685M" :name "Lycée G.T. PIERRE-GILLES DE GENNES-ENCPB (75)"}
   {:id "fake-id-0750690T" :name "Lycée G.T. FRANCOIS VILLON (75)"}
   {:id "fake-id-0750692V" :name "Lycée G.T. EMILE DUBOIS (75)"}
   {:id "fake-id-0750698B" :name "Lycée G.T. CLAUDE BERNARD (75)"}
   {:id "fake-id-0750700D" :name "Lycée G.T. JEAN-BAPTISTE SAY (75)"}
   {:id "fake-id-0750705J" :name "Lycée G.T. HONORE DE BALZAC (75)"}
   {:id "fake-id-0750711R" :name "Lycée G.T. HENRI BERGSON (75)"}
   {:id "fake-id-0750715V" :name "Lycée G.T. MAURICE RAVEL (75)"}
   {:id "fake-id-0760005T" :name "Lycée G.T. THOMAS CORNEILLE (76)"}
   {:id "fake-id-0760023M" :name "Lycée G.T. JEHAN ANGO (76)"}
   {:id "fake-id-0760029U" :name "Lycée G.T. ANDRE MAUROIS (76)"}
   {:id "fake-id-0760035A" :name "Lycée G.T. GUY DE MAUPASSANT (76)"}
   {:id "fake-id-0760058A" :name "Lycée G.T. SCHUMAN-PERRET (76)"}
   {:id "fake-id-0760072R" :name "Lycée G.T. GUILLAUME LE CONQUERANT (76)"}
   {:id "fake-id-0760076V" :name "Lycée G.T. JEAN PREVOST (76)"}
   {:id "fake-id-0760091L" :name "Lycée G.T. JEANNE D'ARC (76)"}
   {:id "fake-id-0760096S" :name "Lycée G.T. GUSTAVE FLAUBERT (76)"}
   {:id "fake-id-0760109F" :name "Lycée G.T. LES BRUYERES (76)"}
   {:id "fake-id-0760110G" :name "Lycée G.T. MARCEL SEMBAT (76)"}
   {:id "fake-id-0760174B" :name "Lycée G.T. CLAUDE MONET (76)"}
   {:id "fake-id-0761742F" :name "Lycée G.T. VAL DE SEINE (76)"}
   {:id "fake-id-0762169V" :name "Lycée G.T. PABLO NERUDA (76)"}
   {:id "fake-id-0762879S" :name "Lycée G.T. VALLEE DU CAILLY (76)"}
   {:id "fake-id-0762920L" :name "Lycée G.T. PIERRE DE COUBERTIN (76)"}
   {:id "fake-id-0762953X" :name "Lycée G.T. DE LA COTE D'ALBATRE (76)"}
   {:id "fake-id-0770918E" :name "Lycée G.T. URUGUAY FRANCE (77)"}
   {:id "fake-id-0770922J" :name "Lycée G.T. GASTON BACHELARD (77)"}
   {:id "fake-id-0770926N" :name "Lycée G.T. FRANCOIS COUPERIN (77)"}
   {:id "fake-id-0770930T" :name "Lycée G.T. HENRI MOISSAN (77)"}
   {:id "fake-id-0770931U" :name "Lycée G.T. PIERRE DE COUBERTIN (77)"}
   {:id "fake-id-0770933W" :name "Lycée G.T. JACQUES AMYOT (77)"}
   {:id "fake-id-0771512A" :name "Lycée G.T. VAN DONGEN (77)"}
   {:id "fake-id-0771663P" :name "Lycée G.T. GEORGE SAND (77)"}
   {:id "fake-id-0771763Y" :name "Lycée G.T. CHARLES LE CHAUVE (77)"}
   {:id "fake-id-0772120L" :name "Lycée G.T. JEAN MOULIN (77)"}
   {:id "fake-id-0772127U" :name "Lycée G.T. GALILEE (77)"}
   {:id "fake-id-0772188K" :name "Lycée G.T. PIERRE MENDES-FRANCE (77)"}
   {:id "fake-id-0772229E" :name "Lycée G.T. JEAN VILAR (77)"}
   {:id "fake-id-0772243V" :name "Lycée G.T. CAMILLE CLAUDEL (77)"}
   {:id "fake-id-0772292Y" :name "Lycée G.T. MARTIN LUTHER KING (77)"}
   {:id "fake-id-0772294A" :name "Lycée G.T. EMILY BRONTE (77)"}
   {:id "fake-id-0772685A" :name "Lycée G.T. SAMUEL BECKETT (77)"}
   {:id "fake-id-0780422K" :name "Lycée G.T. FRANCOIS VILLON (78)"}
   {:id "fake-id-0780515L" :name "Lycée G.T. LES SEPT MARES (78)"}
   {:id "fake-id-0780582J" :name "Lycée G.T. JEAN VILAR (78)"}
   {:id "fake-id-0781297L" :name "Lycée G.T. PLAINE DE NEAUPHLE (78)"}
   {:id "fake-id-0781512V" :name "Lycée G.T. DESCARTES (78)"}
   {:id "fake-id-0781845G" :name "Lycée G.T. JULES FERRY (78)"}
   {:id "fake-id-0781861Z" :name "Lycée G.T. LOUIS DE BROGLIE (78)"}
   {:id "fake-id-0781898P" :name "Lycée G.T. CHARLES DE GAULLE (78)"}
   {:id "fake-id-0781949V" :name "Lycée G.T. DE VILLAROY (78)"}
   {:id "fake-id-0782132U" :name "Lycée G.T. JEANNE D'ALBRET (78)"}
   {:id "fake-id-0782539L" :name "Lycée G.T. ST EXUPERY (78)"}
   {:id "fake-id-0782546U" :name "Lycée G.T. LE CORBUSIER (78)"}
   {:id "fake-id-0782563M" :name "Lycée G.T. LA BRUYERE (78)"}
   {:id "fake-id-0782567S" :name "Lycée G.T. MARIE CURIE (78)"}
   {:id "fake-id-0782568T" :name "Lycée G.T. ALAIN (78)"}
   {:id "fake-id-0782822U" :name "Lycée G.T. PIERRE CORNEILLE (78)"}
   {:id "fake-id-0782924E" :name "Lycée G.T. EVARISTE GALOIS (78)"}
   {:id "fake-id-0783140P" :name "Lycée G.T. MANSART (78)"}
   {:id "fake-id-0790019S" :name "Lycée G.T. JOSEPH DESFONTAINES (79)"}
   {:id "fake-id-0790023W" :name "Lycée G.T. JEAN MACE (79)"}
   {:id "fake-id-0790024X" :name "Lycée G.T. PAUL GUERIN (79)"}
   {:id "fake-id-0790029C" :name "Lycée G.T. ERNEST PEROCHON (79)"}
   {:id "fake-id-0790036K" :name "Lycée G.T. CITE SCOLAIRE JEAN MOULIN (79)"}
   {:id "fake-id-0791062A" :name "Lycée G.T. VENISE VERTE (79)"}
   {:id "fake-id-0801841S" :name "Lycée G.T. ROBERT DE LUZARCHES (80)"}
   {:id "fake-id-0810023K" :name "Lycée G.T. VICTOR HUGO (81)"}
   {:id "fake-id-0810033W" :name "Lycée G.T. MARECHAL SOULT (81)"}
   {:id "fake-id-0810959C" :name "Lycée G.T. BORDE BASSE (81)"}
   {:id "fake-id-0820004J" :name "Lycée G.T. JEAN DE PRADES (82)"}
   {:id "fake-id-0820021C" :name "Lycée G.T. BOURDELLE (82)"}
   {:id "fake-id-0820883P" :name "Lycée G.T. CLAUDE NOUGARO (82)"}
   {:id "fake-id-0830015R" :name "Lycée G.T. JEAN MOULIN (83)"}
   {:id "fake-id-0830025B" :name "Lycée G.T. JEAN AICARD (83)"}
   {:id "fake-id-0830050D" :name "Lycée G.T. BEAUSSIER (83)"}
   {:id "fake-id-0830053G" :name "Lycée G.T. DUMONT D'URVILLE (83)"}
   {:id "fake-id-0831243A" :name "Lycée G.T. BONAPARTE (83)"}
   {:id "fake-id-0831407D" :name "Lycée G.T. DU COUDON (83)"}
   {:id "fake-id-0840004Y" :name "Lycée G.T. THEODORE AUBANEL (84)"}
   {:id "fake-id-0840005Z" :name "Lycée G.T. PHILIPPE DE GIRARD (84)"}
   {:id "fake-id-0840016L" :name "Lycée G.T. VICTOR HUGO (84)"}
   {:id "fake-id-0840017M" :name "Lycée G.T. ISMAEL DAUPHIN (84)"}
   {:id "fake-id-0840026X" :name "Lycée G.T. ARC (DE L') (84)"}
   {:id "fake-id-0840935K" :name "Lycée G.T. RENE CHAR (84)"}
   {:id "fake-id-0841093G" :name "Lycée G.T. LUCIE AUBRAC (84)"}
   {:id "fake-id-0841117H" :name "Lycée G.T. STEPHANE HESSEL (84)"}
   {:id "fake-id-0850006V" :name "Lycée G.T. GEORGES CLEMENCEAU (85)"}
   {:id "fake-id-0850025R" :name "Lycée G.T. PIERRE MENDES-FRANCE (85)"}
   {:id "fake-id-0850032Y" :name "Lycée G.T. SAVARY DE MAULEON (85)"}
   {:id "fake-id-0851346B" :name "Lycée G.T. FRANCOIS TRUFFAUT (85)"}
   {:id "fake-id-0851401L" :name "Lycée G.T. J.DE LATTRE DE TASSIGNY (85)"}
   {:id "fake-id-0860003L" :name "Lycée G.T. MARCELIN BERTHELOT (86)"}
   {:id "fake-id-0860005N" :name "Lycée G.T. CITE TECHNIQUE EDOUARD BRANLY (86)"}
   {:id "fake-id-0860009T" :name "Lycée G.T. ANDRE THEURIET (86)"}
   {:id "fake-id-0860021F" :name "Lycée G.T. GUY CHAUVET (86)"}
   {:id "fake-id-0860028N" :name "Lycée G.T. JEAN MOULIN (86)"}
   {:id "fake-id-0860034V" :name "Lycée G.T. VICTOR HUGO (86)"}
   {:id "fake-id-0860038Z" :name "Lycée G.T. ALIENOR D'AQUITAINE (86)"}
   {:id "fake-id-0861223M" :name "Lycée G.T. PILOTE INNOVANT INTERNATIONAL (86)"}
   {:id "fake-id-0861228T" :name "Lycée G.T. DU BOIS D'AMOUR (86)"}
   {:id "fake-id-0870003F" :name "Lycée G.T. JEAN GIRAUDOUX (87)"}
   {:id "fake-id-0870017W" :name "Lycée G.T. AUGUSTE RENOIR (87)"}
   {:id "fake-id-0870040W" :name "Lycée G.T. PAUL ELUARD (87)"}
   {:id "fake-id-0870050G" :name "Lycée G.T. JEAN BAPTISTE DARNET (87)"}
   {:id "fake-id-0870118F" :name "Lycée G.T. RAOUL DAUTRY (87)"}
   {:id "fake-id-0880004B" :name "Lycée G.T. JEAN LURCAT (88)"}
   {:id "fake-id-0880019T" :name "Lycée G.T. LOUIS LAPICQUE (88)"}
   {:id "fake-id-0880036L" :name "Lycée G.T. JEAN-BAPTISTE VUILLAUME (88)"}
   {:id "fake-id-0880055G" :name "Lycée G.T. JULES FERRY (88)"}
   {:id "fake-id-0891168L" :name "Lycée G.T. PIERRE LAROUSSE (89)"}
   {:id "fake-id-0891200W" :name "Lycée G.T. CATHERINE ET RAYMOND JANOT (89)"}
   {:id "fake-id-0900002N" :name "Lycée G.T. CONDORCET (90)"}
   {:id "fake-id-0900003P" :name "Lycée G.T. GUSTAVE COURBET (90)"}
   {:id "fake-id-0900004R" :name "Lycée G.T. RAOUL FOLLEREAU (90)"}
   {:id "fake-id-0910623H" :name "Lycée G.T. MARCEL PAGNOL (91)"}
   {:id "fake-id-0910625K" :name "Lycée G.T. ROSA PARKS (91)"}
   {:id "fake-id-0910626L" :name "Lycée G.T. BLAISE PASCAL (91)"}
   {:id "fake-id-0910627M" :name "Lycée G.T. JEAN BAPTISTE COROT (91)"}
   {:id "fake-id-0910687C" :name "Lycée G.T. FUSTEL DE COULANGES (91)"}
   {:id "fake-id-0911021R" :name "Lycée G.T. FRANCOIS-JOSEPH TALMA (91)"}
   {:id "fake-id-0911251R" :name "Lycée G.T. PARC DES LOGES (91)"}
   {:id "fake-id-0911346U" :name "Lycée G.T. ALBERT EINSTEIN (91)"}
   {:id "fake-id-0911577V" :name "Lycée G.T. JACQUES PREVERT (91)"}
   {:id "fake-id-0911632E" :name "Lycée G.T. RENE CASSIN (91)"}
   {:id "fake-id-0911913K" :name "Lycée G.T. VALLEE DE CHEVREUSE (91)"}
   {:id "fake-id-0911927A" :name "Lycée G.T. MAURICE ELIOT (91)"}
   {:id "fake-id-0911938M" :name "Lycée G.T. CAMILLE CLAUDEL (91)"}
   {:id "fake-id-0911961M" :name "Lycée G.T. EDMOND MICHELET (91)"}
   {:id "fake-id-0911983L" :name "Lycée G.T. JULES VERNE (91)"}
   {:id "fake-id-0920130S" :name "Lycée G.T. DESCARTES (92)"}
   {:id "fake-id-0920131T" :name "Lycée G.T. AUGUSTE RENOIR (92)"}
   {:id "fake-id-0920132U" :name "Lycée G.T. ALBERT CAMUS (92)"}
   {:id "fake-id-0920134W" :name "Lycée G.T. JACQUES PREVERT (92)"}
   {:id "fake-id-0920135X" :name "Lycée G.T. EMMANUEL MOUNIER (92)"}
   {:id "fake-id-0920137Z" :name "Lycée G.T. GUY DE MAUPASSANT (92)"}
   {:id "fake-id-0920138A" :name "Lycée G.T. PAUL LAPIE (92)"}
   {:id "fake-id-0920141D" :name "Lycée G.T. JOLIOT-CURIE (92)"}
   {:id "fake-id-0920144G" :name "Lycée G.T. L'AGORA (92)"}
   {:id "fake-id-0920145H" :name "Lycée G.T. LAKANAL (92)"}
   {:id "fake-id-0920147K" :name "Lycée G.T. PAUL LANGEVIN (92)"}
   {:id "fake-id-0920149M" :name "Lycée G.T. MICHELET (92)"}
   {:id "fake-id-0920798T" :name "Lycée G.T. RABELAIS (92)"}
   {:id "fake-id-0920799U" :name "Lycée G.T. RICHELIEU (92)"}
   {:id "fake-id-0920801W" :name "Lycée G.T. ALEXANDRE DUMAS (92)"}
   {:id "fake-id-0920802X" :name "Lycée G.T. JEAN-PIERRE VERNANT (92)"}
   {:id "fake-id-0921399W" :name "Lycée G.T. MAURICE GENEVOIX (92)"}
   {:id "fake-id-0921555R" :name "Lycée G.T. JACQUES MONOD (92)"}
   {:id "fake-id-0921594H" :name "Lycée G.T. MICHEL ANGE (92)"}
   {:id "fake-id-0930116W" :name "Lycée G.T. HENRI WALLON (93)"}
   {:id "fake-id-0930117X" :name "Lycée G.T. LE CORBUSIER (93)"}
   {:id "fake-id-0930118Y" :name "Lycée G.T. JEAN RENOIR (93)"}
   {:id "fake-id-0930120A" :name "Lycée G.T. JACQUES FEYDER (93)"}
   {:id "fake-id-0930121B" :name "Lycée G.T. JEAN JAURES (93)"}
   {:id "fake-id-0930123D" :name "Lycée G.T. OLYMPE DE GOUGES (93)"}
   {:id "fake-id-0930124E" :name "Lycée G.T. MARCELIN BERTHELOT (93)"}
   {:id "fake-id-0930125F" :name "Lycée G.T. PAUL ELUARD (93)"}
   {:id "fake-id-0930127H" :name "Lycée G.T. GEORGES CLEMENCEAU (93)"}
   {:id "fake-id-0930830X" :name "Lycée G.T. ALBERT SCHWEITZER (93)"}
   {:id "fake-id-0930833A" :name "Lycée G.T. JEAN ZAY (93)"}
   {:id "fake-id-0930834B" :name "Lycée G.T. VOILLAUME (93)"}
   {:id "fake-id-0931272C" :name "Lycée G.T. GUSTAVE EIFFEL (93)"}
   {:id "fake-id-0931430Z" :name "Lycée G.T. JACQUES BREL (93)"}
   {:id "fake-id-0931565W" :name "Lycée G.T. FLORA TRISTAN (93)"}
   {:id "fake-id-0931585T" :name "Lycée G.T. ANDRE BOULLOCHE (93)"}
   {:id "fake-id-0931613Y" :name "Lycée G.T. LOUISE MICHEL (93)"}
   {:id "fake-id-0932031C" :name "Lycée G.T. CHARLES DE GAULLE (93)"}
   {:id "fake-id-0932034F" :name "Lycée G.T. WOLFGANG AMADEUS MOZART (93)"}
   {:id "fake-id-0932577W" :name "Lycée G.T. GERMAINE TILLION (93)"}
   {:id "fake-id-0940115P" :name "Lycée G.T. ROMAIN ROLLAND (94)"}
   {:id "fake-id-0940116R" :name "Lycée G.T. EUGENE DELACROIX (94)"}
   {:id "fake-id-0940120V" :name "Lycée G.T. MARCELIN BERTHELOT (94)"}
   {:id "fake-id-0940121W" :name "Lycée G.T. D'ARSONVAL (94)"}
   {:id "fake-id-0940123Y" :name "Lycée G.T. GUILLAUME APOLLINAIRE (94)"}
   {:id "fake-id-0940124Z" :name "Lycée G.T. HECTOR BERLIOZ (94)"}
   {:id "fake-id-0940580V" :name "Lycée G.T. MAXIMILIEN SORRE (94)"}
   {:id "fake-id-0941347D" :name "Lycée G.T. PABLO PICASSO (94)"}
   {:id "fake-id-0950640E" :name "Lycée G.T. JULIE-VICTOIRE DAUBIE (95)"}
   {:id "fake-id-0950645K" :name "Lycée G.T. VAN GOGH (95)"}
   {:id "fake-id-0950646L" :name "Lycée G.T. RENE CASSIN (95)"}
   {:id "fake-id-0950647M" :name "Lycée G.T. GERARD DE NERVAL (95)"}
   {:id "fake-id-0950648N" :name "Lycée G.T. JEAN-JACQUES ROUSSEAU (95)"}
   {:id "fake-id-0950651S" :name "Lycée G.T. JACQUES PREVERT (95)"}
   {:id "fake-id-0951147F" :name "Lycée G.T. FRAGONARD (95)"}
   {:id "fake-id-0951399E" :name "Lycée G.T. ALFRED KASTLER (95)"}
   {:id "fake-id-0951637N" :name "Lycée G.T. GALILEE (95)"}
   {:id "fake-id-0951723G" :name "Lycée G.T. MONTESQUIEU (95)"}
   {:id "fake-id-0951753P" :name "Lycée G.T. LEONARD DE VINCI (95)"}
   {:id "fake-id-0951766D" :name "Lycée G.T. SIMONE DE BEAUVOIR (95)"}
   {:id "fake-id-0951922Y" :name "Lycée G.T. CAMILLE SAINT-SAENS (95)"}
   {:id "fake-id-6200001G" :name "Lycée G.T. FESCH (620)"}
   {:id "fake-id-6200002H" :name "Lycée G.T. LAETITIA BONAPARTE (620)"}
   {:id "fake-id-7200009X" :name "Lycée G.T. GIOCANTE DE CASABIANCA (720)"}
   {:id "fake-id-7200021K" :name "Lycée G.T. PASCAL PAOLI (720)"}
   {:id "fake-id-9710002A" :name "Lycée G.T. GERVILLE REACHE (971)"}
   {:id "fake-id-9710003B" :name "Lycée G.T. BAIMBRIDGE (971)"}
   {:id "fake-id-9710774P" :name "Lycée G.T. FAUSTIN FLERET (971)"}
   {:id "fake-id-9710882G" :name "Lycée G.T. DES DROITS DE L'HOMME (971)"}
   {:id "fake-id-9710921Z" :name "Lycée G.T. FÉLIX PROTO (971)"}
   {:id "fake-id-9710922A" :name "Lycée G.T. YVES LEBORGNE (971)"}
   {:id "fake-id-9710923B" :name "Lycée G.T. JARDIN D'ESSAI (971)"}
   {:id "fake-id-9710940V" :name "Lycée G.T. SONNY RUPAIRE (971)"}
   {:id "fake-id-9711252J" :name "Lycée G.T. ROBERT WEINUM (971)"}
   {:id "fake-id-9720002V" :name "Lycée G.T. VICTOR SCHOELCHER (972)"}
   {:id "fake-id-9720003W" :name "Lycée G.T. BELLEVUE (972)"}
   {:id "fake-id-9720004X" :name "Lycée G.T. JOSEPH GAILLARD (972)"}
   {:id "fake-id-9720350Y" :name "Lycée G.T. FRANTZ FANON (972)"}
   {:id "fake-id-9720694X" :name "Lycée G.T. ACAJOU 1 (972)"}
   {:id "fake-id-9720727H" :name "Lycée G.T. MONTGERALD (972)"}
   {:id "fake-id-9720825P" :name "Lycée G.T. CENTRE SUD (972)"}
   {:id "fake-id-9720900W" :name "Lycée G.T. CENTRE EM'PEHEL FORMECOL (972)"}
   {:id "fake-id-9730001N" :name "Lycée G.T. FELIX EBOUE (973)"}
   {:id "fake-id-9730108E" :name "Lycée G.T. GASTON MONNERVILLE (973)"}
   {:id "fake-id-9730196A" :name "Lycée G.T. LEON-GONTRAN DAMAS (973)"}
   {:id "fake-id-9740001H" :name "Lycée G.T. LECONTE DE LISLE (974)"}
   {:id "fake-id-9740019C" :name "Lycée G.T. AMBROISE VOLLARD (974)"}
   {:id "fake-id-9740043D" :name "Lycée G.T. SARDA GARRIGA (974)"}
   {:id "fake-id-9740054R" :name "Lycée G.T. LISLET GEOFFROY (974)"}
   {:id "fake-id-9740471U" :name "Lycée G.T. AMIRAL PIERRE BOUVET (974)"}
   {:id "fake-id-9740597F" :name "Lycée G.T. EVARISTE DE PARNY (974)"}
   {:id "fake-id-9740787M" :name "Lycée G.T. ANTOINE ROUSSIN (974)"}
   {:id "fake-id-9740952S" :name "Lycée G.T. PIERRE POIVRE (974)"}
   {:id "fake-id-9741046U" :name "Lycée G.T. BELLEPIERRE (974)"}
   {:id "fake-id-9741050Y" :name "Lycée G.T. LOUIS PAYEN (974)"}
   {:id "fake-id-9741185V" :name "Lycée G.T. LE VERGER (974)"}
   {:id "fake-id-9741324W" :name "Lycée G.T. MAHATMA GANDHI (974)"}
   {:id "fake-id-9750001C" :name "Lycée G.T. ÉMILE LETOURNEL (975)"} ; fixed
   {:id "fake-id-9760127J" :name "Lycée G.T. YOUNOUSSA BAMANA (976)"}
   {:id "fake-id-9830002K" :name "Lycée G.T. LA PEROUSE (983)"}
   {:id "fake-id-9830507J" :name "Lycée G.T. ANTOINE KELA (983)"}
   {:id "fake-id-9830557N" :name "Lycée G.T. DU GRAND NOUMEA (983)"}
   {:id "fake-id-9870026P" :name "Lycée G.T. DE WALLIS (987)"}
   {:id "fake-id-0010032E" :name "Lycée polyv. XAVIER BICHAT (01)"}
   {:id "fake-id-0010034G" :name "Lycée polyv. PAUL PAINLEVE (01)"}
   {:id "fake-id-0020034B" :name "Lycée polyv. PIERRE MECHAIN (02)"}
   {:id "fake-id-0021939X" :name "Lycée polyv. JULES VERNE (02)"}
   {:id "fake-id-0031044U" :name "Lycée polyv. GENEVIEVE VINCENT (03)"}
   {:id "fake-id-0031082K" :name "Lycée polyv. VALERY LARBAUD (03)"}
   {:id "fake-id-0040003G" :name "Lycée polyv. ANDRE HONNORAT (04)"}
   {:id "fake-id-0040533H" :name "Lycée polyv. LES ISCLES (04)"}
   {:id "fake-id-0050003B" :name "Lycée polyv. D ALTITUDE (05)"}
   {:id "fake-id-0060033D" :name "Lycée polyv. HONORE D'ESTIENNE D'ORVES (06)"}
   {:id "fake-id-0061987C" :name "Lycée polyv. DE LA MONTAGNE (06)"}
   {:id "fake-id-0070003R" :name "Lycée polyv. MARCEL GIMOND (07)"}
   {:id "fake-id-0070004S" :name "Lycée polyv. ASTIER (07)"}
   {:id "fake-id-0070021K" :name "Lycée polyv. VINCENT D'INDY (07)"}
   {:id "fake-id-0071351F" :name "Lycée polyv. XAVIER MALLET (07)"}
   {:id "fake-id-0071397F" :name "Lycée polyv. LE CHEYLARD (07)"}
   {:id "fake-id-0080039Z" :name "Lycée polyv. PAUL VERLAINE (08)"}
   {:id "fake-id-0080040A" :name "Lycée polyv. JEAN MOULIN (08)"}
   {:id "fake-id-0090015T" :name "Lycée polyv. PYRENE (09)"}
   {:id "fake-id-0100003Z" :name "Lycée polyv. GASTON BACHELARD (10)"}
   {:id "fake-id-0100023W" :name "Lycée polyv. MARIE DE CHAMPAGNE (10)"}
   {:id "fake-id-0100025Y" :name "Lycée polyv. LES LOMBARDS (10)"}
   {:id "fake-id-0110012D" :name "Lycée polyv. JEAN DURAND (11)"}
   {:id "fake-id-0110019L" :name "Lycée polyv. JACQUES RUFFIE (11)"}
   {:id "fake-id-0110023R" :name "Lycée polyv. LOUISE MICHEL (11)"}
   {:id "fake-id-0111048E" :name "Lycée polyv. ERNEST FERROUL (11)"}
   {:id "fake-id-0130050J" :name "Lycée polyv. DENIS DIDEROT (13)"}
   {:id "fake-id-0130053M" :name "Lycée polyv. JEAN PERRIN (13)"}
   {:id "fake-id-0130143K" :name "Lycée polyv. PAUL LANGEVIN (13)"}
   {:id "fake-id-0131747D" :name "Lycée polyv. AUGUSTE ET LOUIS LUMIERE (13)"}
   {:id "fake-id-0133015G" :name "Lycée polyv. PIERRE MENDES-FRANCE (13)"}
   {:id "fake-id-0133288D" :name "Lycée polyv. JEAN MONNET (13)"}
   {:id "fake-id-0133406G" :name "Lycée polyv. MEDITERRANEE (DE LA ) (13)"}
   {:id "fake-id-0134003F" :name "Lycée polyv. LA FOURRAGERE (13)"}
   {:id "fake-id-0140052F" :name "Lycée polyv. LOUIS LIARD (14)"}
   {:id "fake-id-0140056K" :name "Lycée polyv. ALBERT SOREL (14)"}
   {:id "fake-id-0142131R" :name "Lycée polyv. JULES DUMONT D'URVILLE (14)"}
   {:id "fake-id-0150030B" :name "Lycée polyv. DE HAUTE AUVERGNE (15)"}
   {:id "fake-id-0160020K" :name "Lycée polyv. JEAN MONNET (16)"}
   {:id "fake-id-0160022M" :name "Lycée polyv. EMILE ROUX (16)"}
   {:id "fake-id-0170051N" :name "Lycée polyv. LOUIS AUDOUIN DUBREUIL (17)"}
   {:id "fake-id-0180035R" :name "Lycée polyv. EDOUARD VAILLANT (18)"}
   {:id "fake-id-0190012K" :name "Lycée polyv. DANTON (19)"}
   {:id "fake-id-0210003P" :name "Lycée polyv. PRIEUR DE LA COTE D'OR (21)"}
   {:id "fake-id-0210006T" :name "Lycée polyv. CLOS MAIRE (21)"}
   {:id "fake-id-0210013A" :name "Lycée polyv. DESIRE NISARD (21)"}
   {:id "fake-id-0210018F" :name "Lycée polyv. HIPPOLYTE FONTAINE (21)"}
   {:id "fake-id-0210019G" :name "Lycée polyv. LE CASTEL (21)"}
   {:id "fake-id-0210047M" :name "Lycée polyv. ANNA JUDIC (21)"}
   {:id "fake-id-0212015B" :name "Lycée polyv. SIMONE WEIL (21)"}
   {:id "fake-id-0212045J" :name "Lycée polyv. E.J. MAREY (21)"}
   {:id "fake-id-0220027K" :name "Lycée polyv. FULGENCE BIENVENUE (22)"}
   {:id "fake-id-0230051F" :name "Lycée polyv. JEAN FAVARD (23)"}
   {:id "fake-id-0240021T" :name "Lycée polyv. ALCIDE DUSOLIER (24)"}
   {:id "fake-id-0241125T" :name "Lycée polyv. ANTOINE DE ST EXUPERY (24)"}
   {:id "fake-id-0250032Z" :name "Lycée polyv. VIETTE (25)"}
   {:id "fake-id-0250043L" :name "Lycée polyv. XAVIER MARMIER (25)"}
   {:id "fake-id-0251711Z" :name "Lycée polyv. CLAUDE NICOLAS LEDOUX (25)"}
   {:id "fake-id-0251994G" :name "Lycée polyv. GERMAINE TILLION (25)"} ; fixed
   {:id "fake-id-0260006R" :name "Lycée polyv. FRANCOIS JEAN ARMORIN (26)"}
   {:id "fake-id-0260019E" :name "Lycée polyv. DR. GUSTAVE JAUME (26)"}
   {:id "fake-id-0270026G" :name "Lycée polyv. LOUISE MICHEL (27)"}
   {:id "fake-id-0271581X" :name "Lycée polyv. CLEMENT ADER (27)"}
   {:id "fake-id-0271585B" :name "Lycée polyv. JEAN MOULIN (27)"}
   {:id "fake-id-0290012F" :name "Lycée polyv. VAUBAN (29)"}
   {:id "fake-id-0290022S" :name "Lycée polyv. PAUL SERUSIER (29)"}
   {:id "fake-id-0290108K" :name "Lycée polyv. DUPUY DE LOME (29)"}
   {:id "fake-id-0300027S" :name "Lycée polyv. ERNEST HEMINGWAY (30)"}
   {:id "fake-id-0300052U" :name "Lycée polyv. ANDRE CHAMSON (30)"}
   {:id "fake-id-0300950V" :name "Lycée polyv. ALBERT EINSTEIN (30)"}
   {:id "fake-id-0310038Y" :name "Lycée polyv. BELLEVUE (31)"}
   {:id "fake-id-0310040A" :name "Lycée polyv. RAYMOND NAVES (31)"}
   {:id "fake-id-0312746S" :name "Lycée polyv. MARIE LOUISE DISSARD FRANCOISE (31)"}
   {:id "fake-id-0312915A" :name "Lycée polyv. LÉON BLUM (31)"} ; fixed
   {:id "fake-id-0320015T" :name "Lycée polyv. MARECHAL LANNES (32)"}
   {:id "fake-id-0320023B" :name "Lycée polyv. ALAIN-FOURNIER (32)"}
   {:id "fake-id-0330028B" :name "Lycée polyv. GUSTAVE EIFFEL (33)"}
   {:id "fake-id-0330089T" :name "Lycée polyv. JEAN MONNET (33)"}
   {:id "fake-id-0330109P" :name "Lycée polyv. JEAN RENOU (33)"}
   {:id "fake-id-0330126H" :name "Lycée polyv. VICTOR LOUIS (33)"}
   {:id "fake-id-0330135T" :name "Lycée polyv. ALFRED KASTLER (33)"}
   {:id "fake-id-0332832Z" :name "Lycée polyv. LES IRIS (33)"}
   {:id "fake-id-0333273D" :name "Lycée polyv. VACLAV HAVEL (33)"}
   {:id "fake-id-0340002T" :name "Lycée polyv. AUGUSTE LOUBATIERES (34)"}
   {:id "fake-id-0340011C" :name "Lycée polyv. JEAN MOULIN (34)"}
   {:id "fake-id-0340028W" :name "Lycée polyv. JOSEPH VALLOT (34)"}
   {:id "fake-id-0340030Y" :name "Lycée polyv. LOUIS FEUILLADE (34)"}
   {:id "fake-id-0340040J" :name "Lycée polyv. JULES GUESDE (34)"}
   {:id "fake-id-0340042L" :name "Lycée polyv. JEAN MERMOZ (34)"}
   {:id "fake-id-0341794R" :name "Lycée polyv. JEAN-FRANCOIS CHAMPOLLION (34)"}
   {:id "fake-id-0341921D" :name "Lycée polyv. GEORGES POMPIDOU (34)"}
   {:id "fake-id-0342090M" :name "Lycée polyv. VICTOR HUGO (34)"}
   {:id "fake-id-0342091N" :name "Lycée polyv. MARC BLOCH (34)"}
   {:id "fake-id-0350005R" :name "Lycée polyv. YVON BOURGES (35)"}
   {:id "fake-id-0350048M" :name "Lycée polyv. JACQUES CARTIER (35)"}
   {:id "fake-id-0360005K" :name "Lycée polyv. PASTEUR (36)"}
   {:id "fake-id-0360019A" :name "Lycée polyv. GEORGE SAND (36)"}
   {:id "fake-id-0360043B" :name "Lycée polyv. BLAISE PASCAL (36)"}
   {:id "fake-id-0370009J" :name "Lycée polyv. FRANCOIS RABELAIS (37)"}
   {:id "fake-id-0380014J" :name "Lycée polyv. HECTOR BERLIOZ (38)"}
   {:id "fake-id-0380033E" :name "Lycée polyv. VAUCANSON (38)"}
   {:id "fake-id-0380034F" :name "Lycée polyv. LOUISE MICHEL (38)"}
   {:id "fake-id-0380049X" :name "Lycée polyv. DE LA MATHEYSINE (38)"}
   {:id "fake-id-0380053B" :name "Lycée polyv. CHARLES GABRIEL PRAVAZ (38)"}
   {:id "fake-id-0380063M" :name "Lycée polyv. LA SAULAIE (38)"}
   {:id "fake-id-0380081G" :name "Lycée polyv. ELLA FITZGERALD (38)"}
   {:id "fake-id-0380091T" :name "Lycée polyv. EDOUARD HERRIOT (38)"}
   {:id "fake-id-0380092U" :name "Lycée polyv. FERDINAND BUISSON (38)"}
   {:id "fake-id-0381603L" :name "Lycée polyv. ANDRE  ARGOUGES (38)"}
   {:id "fake-id-0381630R" :name "Lycée polyv. UNITÉ SOINS ETUDES GRÉSIVAUDAN (38)"}
   {:id "fake-id-0382863F" :name "Lycée polyv. DU GRESIVAUDAN (38)"}
   {:id "fake-id-0382895R" :name "Lycée polyv. PHILIBERT DELORME (38)"}
   {:id "fake-id-0390013C" :name "Lycée polyv. JACQUES DUHAMEL (39)"}
   {:id "fake-id-0390786T" :name "Lycée polyv. PRE SAINT SAUVEUR (39)"}
   {:id "fake-id-0391092A" :name "Lycée polyv. PAUL EMILE VICTOR (39)"}
   {:id "fake-id-0400002K" :name "Lycée polyv. GASTON CRAMPE (40)"}
   {:id "fake-id-0400007R" :name "Lycée polyv. DE BORDA (40)"}
   {:id "fake-id-0410001D" :name "Lycée polyv. AUGUSTIN THIERRY (41)"}
   {:id "fake-id-0420008F" :name "Lycée polyv. JEREMIE DE LA RUE (42)"}
   {:id "fake-id-0420027B" :name "Lycée polyv. GEORGES BRASSENS (42)"}
   {:id "fake-id-0430953C" :name "Lycée polyv. EMMANUEL CHABRIER (43)"}
   {:id "fake-id-0440001M" :name "Lycée polyv. JOUBERT-EMILIEN MAILLARD (44)"}
   {:id "fake-id-0440005S" :name "Lycée polyv. GUY MOQUET - ETIENNE LENOIR (44)"}
   {:id "fake-id-0440030U" :name "Lycée polyv. GASPARD MONGE - LA CHAUVINIERE (44)"}
   {:id "fake-id-0441552Y" :name "Lycée polyv. LES BOURDONNIERES (44)"}
   {:id "fake-id-0442752C" :name "Lycée polyv. AIME CESAIRE (44)"}
   {:id "fake-id-0442765S" :name "Lycée polyv. NELSON MANDELA (44)"}
   {:id "fake-id-0450050K" :name "Lycée polyv. JEAN ZAY (45)"}
   {:id "fake-id-0450051L" :name "Lycée polyv. BENJAMIN FRANKLIN (45)"}
   {:id "fake-id-0451483T" :name "Lycée polyv. MAURICE GENEVOIX (45)"}
   {:id "fake-id-0460013P" :name "Lycée polyv. LEO FERRE (46)"}
   {:id "fake-id-0480688M" :name "Lycée polyv. THEOPHILE ROUSSEL (48)"}
   {:id "fake-id-0490782J" :name "Lycée polyv. BLAISE PASCAL (49)"}
   {:id "fake-id-0492224B" :name "Lycée polyv. DE L'HYROME (49)"}
   {:id "fake-id-0492430A" :name "Lycée polyv. JULIEN GRACQ (49)"} ; fixed
   {:id "fake-id-0500002F" :name "Lycée polyv. EMILE LITTRE (50)"}
   {:id "fake-id-0501219D" :name "Lycée polyv. PIERRE ET MARIE CURIE-CAMILLE (50)"}
   {:id "fake-id-0510007F" :name "Lycée polyv. ETIENNE OEHMICHEN (51)"}
   {:id "fake-id-0510053F" :name "Lycée polyv. LA FONTAINE DU VE (51)"}
   {:id "fake-id-0510062R" :name "Lycée polyv. FRANCOIS 1ER (51)"}
   {:id "fake-id-0520021R" :name "Lycée polyv. DIDEROT (52)"}
   {:id "fake-id-0530949U" :name "Lycée polyv. RAOUL VADEPIED (53)"}
   {:id "fake-id-0540030P" :name "Lycée polyv. ALFRED MEZIERES (54)"}
   {:id "fake-id-0540076P" :name "Lycée polyv. JEAN ZAY (54)"}
   {:id "fake-id-0542208G" :name "Lycée polyv. STANISLAS (54)"}
   {:id "fake-id-0550008K" :name "Lycée polyv. HENRI VOGT (55)"}
   {:id "fake-id-0550072E" :name "Lycée polyv. ALFRED KASTLER (55)"}
   {:id "fake-id-0560026Z" :name "Lycée polyv. COLBERT (56)"}
   {:id "fake-id-0561698S" :name "Lycée polyv. JEAN MACE (56)"}
   {:id "fake-id-0570021N" :name "Lycée polyv. CHARLES HERMITE (57)"}
   {:id "fake-id-0570094T" :name "Lycée polyv. MANGIN (57)"}
   {:id "fake-id-0570108H" :name "Lycée polyv. LA BRIQUERIE (57)"}
   {:id "fake-id-0570146Z" :name "Lycée polyv. JULIE DAUBIE (57)"}
   {:id "fake-id-0572022N" :name "Lycée polyv. FELIX MAYER (57)"}
   {:id "fake-id-0573227Y" :name "Lycée polyv. LOUIS DE CORMONTAIGNE (57)"}
   {:id "fake-id-0573326F" :name "Lycée polyv. LOUIS CASIMIR TEYSSIER (57)"}
   {:id "fake-id-0573491K" :name "Lycée polyv. ERNEST CUVELETTE (57)"}
   {:id "fake-id-0580008U" :name "Lycée polyv. ROMAIN ROLLAND (58)"}
   {:id "fake-id-0580014A" :name "Lycée polyv. PIERRE GILLES DE GENNES (58)"}
   {:id "fake-id-0580761M" :name "Lycée polyv. MAURICE GENEVOIX (58)"}
   {:id "fake-id-0590011S" :name "Lycée polyv. GUSTAVE EIFFEL (59)"}
   {:id "fake-id-0590018Z" :name "Lycée polyv. JESSE DE FOREST (59)"}
   {:id "fake-id-0590042A" :name "Lycée polyv. CAMILLE DESMOULINS (59)"}
   {:id "fake-id-0590044C" :name "Lycée polyv. JOSEPH MARIE JACQUARD (59)"}
   {:id "fake-id-0590083V" :name "Lycée polyv. CAMILLE CLAUDEL (59)"}
   {:id "fake-id-0590149S" :name "Lycée polyv. PIERRE FOREST (59)"}
   {:id "fake-id-0590168M" :name "Lycée polyv. EUGENE THOMAS (59)"}
   {:id "fake-id-0590233H" :name "Lycée polyv. EMILE ZOLA (59)"}
   {:id "fake-id-0595884A" :name "Lycée polyv. ANDRE LURCAT (59)"}
   {:id "fake-id-0596854E" :name "Lycée polyv. DU PAYS DE CONDE (59)"}
   {:id "fake-id-0596925G" :name "Lycée polyv. CHARLOTTE PERRIAND (59)"}
   {:id "fake-id-0597005U" :name "Lycée polyv. DU VAL DE LYS (59)"}
   {:id "fake-id-0610006A" :name "Lycée polyv. MEZERAY (61)"}
   {:id "fake-id-0611148S" :name "Lycée polyv. JEAN MONNET (61)"}
   {:id "fake-id-0620027T" :name "Lycée polyv. PABLO PICASSO (62)"}
   {:id "fake-id-0620140R" :name "Lycée polyv. EUGENE WOILLEZ (62)"}
   {:id "fake-id-0620256S" :name "Lycée polyv. GUY MOLLET (62)"}
   {:id "fake-id-0622276M" :name "Lycée polyv. JAN LAVEZZARI (62)"}
   {:id "fake-id-0623981R" :name "Lycée polyv. VAUBAN (62)"}
   {:id "fake-id-0624440P" :name "Lycée polyv. D'ARTOIS (62)"}
   {:id "fake-id-0630001J" :name "Lycée polyv. BLAISE PASCAL (63)"}
   {:id "fake-id-0640057P" :name "Lycée polyv. SAINT CRICQ (64)"}
   {:id "fake-id-0641844G" :name "Lycée polyv. DE NAVARRE (64)"}
   {:id "fake-id-0660004W" :name "Lycée polyv. DEODAT DE SEVERAC (66)"}
   {:id "fake-id-0660011D" :name "Lycée polyv. JEAN LURCAT (66)"}
   {:id "fake-id-0660014G" :name "Lycée polyv. PABLO PICASSO (66)"}
   {:id "fake-id-0660021P" :name "Lycée polyv. CHARLES RENOUVIER (66)"}
   {:id "fake-id-0660809W" :name "Lycée polyv. ARISTIDE MAILLOL (66)"}
   {:id "fake-id-0660924W" :name "Lycée polyv. CHRISTIAN BOURQUIN (66)"}
   {:id "fake-id-0670084C" :name "Lycée polyv. JEAN ROSTAND (67)"}
   {:id "fake-id-0671832C" :name "Lycée polyv. JEAN-BAPTISTE SCHWILGUE (67)"}
   {:id "fake-id-0672614C" :name "Lycée polyv. GEORGES IMBERT (67)"}
   {:id "fake-id-0672615D" :name "Lycée polyv. LOUIS MARCHAL (67)"}
   {:id "fake-id-0672677W" :name "Lycée polyv. MARGUERITE YOURCENAR (67)"}
   {:id "fake-id-0672806L" :name "Lycée polyv. MARCEL RUDLOFF (67)"}
   {:id "fake-id-0680001G" :name "Lycée polyv. JEAN JACQUES HENNER (68)"}
   {:id "fake-id-0680010S" :name "Lycée polyv. BLAISE PASCAL (68)"}
   {:id "fake-id-0680016Y" :name "Lycée polyv. THEODORE DECK (68)"}
   {:id "fake-id-0680066C" :name "Lycée polyv. JEAN MERMOZ (68)"}
   {:id "fake-id-0680068E" :name "Lycée polyv. LOUISE WEISS (68)"}
   {:id "fake-id-0681768C" :name "Lycée polyv. LAVOISIER (68)"}
   {:id "fake-id-0681882B" :name "Lycée polyv. MARTIN SCHONGAUER (68)"}
   {:id "fake-id-0681888H" :name "Lycée polyv. AMELIE ZURCHER (68)"}
   {:id "fake-id-0691644M" :name "Lycée polyv. LOUIS ARMAND (69)"}
   {:id "fake-id-0692717D" :name "Lycée polyv. JACQUES BREL (69)"}
   {:id "fake-id-0692800U" :name "Lycée polyv. CHARLIE CHAPLIN (69)"}
   {:id "fake-id-0693566B" :name "Lycée polyv. FRANCOIS MANSART (69)"}
   {:id "fake-id-0693734J" :name "Lycée polyv. AIGUERANDE (69)"}
   {:id "fake-id-0700009E" :name "Lycée polyv. AUGUSTIN COURNOT (70)"}
   {:id "fake-id-0700018P" :name "Lycée polyv. GEORGES COLOMB (70)"}
   {:id "fake-id-0700905D" :name "Lycée polyv. EDOUARD BELIN (70)"}
   {:id "fake-id-0701078S" :name "Lycée polyv. LUMIERE (70)"}
   {:id "fake-id-0710010A" :name "Lycée polyv. MATHIAS (71)"}
   {:id "fake-id-0710018J" :name "Lycée polyv. JULIEN WITTMER (71)"}
   {:id "fake-id-0710026T" :name "Lycée polyv. LÉON BLUM (71)"}
   {:id "fake-id-0710042K" :name "Lycée polyv. HENRI VINCENOT (71)"}
   {:id "fake-id-0711422K" :name "Lycée polyv. HILAIRE DE CHARDONNET (71)"}
   {:id "fake-id-0711729U" :name "Lycée polyv. EMILAND GAUTHEY (71)"}
   {:id "fake-id-0720017C" :name "Lycée polyv. ROBERT GARNIER (72)"}
   {:id "fake-id-0720021G" :name "Lycée polyv. D'ESTOURNELLES DE CONSTANT (72)"}
   {:id "fake-id-0720027N" :name "Lycée polyv. PERSEIGNE (72)"}
   {:id "fake-id-0720033V" :name "Lycée Polyv. TOUCHARD-WASHINGTON (72)"}
   {:id "fake-id-0721094Y" :name "Lycée polyv. LE MANS SUD (72)"}
   {:id "fake-id-0730003G" :name "Lycée polyv. MARLIOZ (73)"}
   {:id "fake-id-0740009H" :name "Lycée polyv. DES GLIERES (74)"}
   {:id "fake-id-0740013M" :name "Lycée polyv. GUILLAUME FICHET (74)"}
   {:id "fake-id-0740051D" :name "Lycée polyv. ANNA DE NOAILLES (74)"}
   {:id "fake-id-0741669M" :name "Lycée polyv. ROGER FRISON ROCHE (74)"}
   {:id "fake-id-0750502N" :name "Lycée polyv. MAXIMILIEN VOX-ART-DESSIN (75)"}
   {:id "fake-id-0750558Z" :name "Lycée polyv. PAUL POIRET (75)"}
   {:id "fake-id-0750671X" :name "Lycée polyv. EDGAR QUINET (75)"}
   {:id "fake-id-0750677D" :name "Lycée polyv. ELISA LEMONNIER (75)"}
   {:id "fake-id-0750712S" :name "Lycée polyv. DIDEROT (75)"}
   {:id "fake-id-0750786X" :name "Lycée polyv. LAZARE PONTICELLI (75)"}
   {:id "fake-id-0751708Z" :name "Lycée polyv. LOUIS ARMAND (75)"}
   {:id "fake-id-0752701D" :name "Lycée polyv. FRANCOIS TRUFFAUT (75)"}
   {:id "fake-id-0753268V" :name "Lycée polyv. JEAN LURCAT (75)"}
   {:id "fake-id-0760054W" :name "Lycée polyv. PORTE OCEANE (76)"}
   {:id "fake-id-0760095R" :name "Lycée polyv. BLAISE PASCAL (76)"}
   {:id "fake-id-0762600N" :name "Lycée polyv. EDOUARD DELAMARE DEBOUTTEVILLE (76)"}
   {:id "fake-id-0762601P" :name "Lycée polyv. GEORGES BRASSENS (76)"}
   {:id "fake-id-0762880T" :name "Lycée polyv. RAYMOND QUENEAU (76)"}
   {:id "fake-id-0763002A" :name "Lycée polyv. DU GOLF (76)"}
   {:id "fake-id-0770942F" :name "Lycée polyv. THIBAUT DE CHAMPAGNE (77)"}
   {:id "fake-id-0771940R" :name "Lycée polyv. GERARD DE NERVAL (77)"}
   {:id "fake-id-0771941S" :name "Lycée polyv. RENE CASSIN (77)"}
   {:id "fake-id-0771996B" :name "Lycée polyv. HONORE DE BALZAC (77)"}
   {:id "fake-id-0772223Y" :name "Lycée polyv. RENE DESCARTES (77)"}
   {:id "fake-id-0772230F" :name "Lycée polyv. BLAISE PASCAL (77)"}
   {:id "fake-id-0772277G" :name "Lycée polyv. HENRI BECQUEREL (77)"}
   {:id "fake-id-0772295B" :name "Lycée polyv. LA TOUR DES DAMES (77)"}
   {:id "fake-id-0772296C" :name "Lycée polyv. DE LA MARE CARREE (77)"}
   {:id "fake-id-0772310T" :name "Lycée polyv. SIMONE SIGNORET (77)"}
   {:id "fake-id-0772332S" :name "Lycée polyv. SONIA DELAUNAY (77)"}
   {:id "fake-id-0772342C" :name "Lycée polyv. CLEMENT ADER (77)"}
   {:id "fake-id-0772688D" :name "Lycée polyv. EMILIE DU CHATELET (77)"}
   {:id "fake-id-0772751X" :name "Lycée polyv. CHARLOTTE DELBO (77)"}
   {:id "fake-id-0781819D" :name "Lycée polyv. EMILIE DE BRETEUIL (78)"}
   {:id "fake-id-0781859X" :name "Lycée polyv. VINCENT VAN GOGH (78)"}
   {:id "fake-id-0781860Y" :name "Lycée polyv. LES PIERRES VIVES (78)"}
   {:id "fake-id-0781883Y" :name "Lycée polyv. DUMONT D'URVILLE (78)"}
   {:id "fake-id-0781884Z" :name "Lycée polyv. CONDORCET (78)"}
   {:id "fake-id-0781948U" :name "Lycée polyv. ANTOINE LAVOISIER (78)"}
   {:id "fake-id-0781950W" :name "Lycée polyv. LOUISE WEISS (78)"}
   {:id "fake-id-0781951X" :name "Lycée polyv. LEOPOLD SEDAR SENGHOR (78)"}
   {:id "fake-id-0781952Y" :name "Lycée polyv. SONIA DELAUNAY (78)"}
   {:id "fake-id-0782540M" :name "Lycée polyv. JEAN ROSTAND (78)"}
   {:id "fake-id-0782549X" :name "Lycée polyv. LOUIS BASCAN (78)"}
   {:id "fake-id-0782565P" :name "Lycée polyv. JULES FERRY (78)"}
   {:id "fake-id-0801882L" :name "Lycée polyv. LA HOTOIE (80)"}
   {:id "fake-id-0810012Y" :name "Lycée polyv. JEAN JAURES (81)"}
   {:id "fake-id-0820899G" :name "Lycée polyv. JEAN BAYLET (82)"}
   {:id "fake-id-0830007G" :name "Lycée polyv. RAYNOUARD (83)"}
   {:id "fake-id-0830032J" :name "Lycée polyv. DE LORGUES - THOMAS EDISON (83)"}
   {:id "fake-id-0830042V" :name "Lycée polyv. ANTOINE DE SAINT EXUPERY (83)"}
   {:id "fake-id-0831242Z" :name "Lycée polyv. DU GOLFE DE SAINT TROPEZ (83)"}
   {:id "fake-id-0831440P" :name "Lycée polyv. ALBERT CAMUS (83)"}
   {:id "fake-id-0831559U" :name "Lycée polyv. MAURICE JANETTI (83)"}
   {:id "fake-id-0831563Y" :name "Lycée polyv. COSTEBELLE (83)"}
   {:id "fake-id-0831616F" :name "Lycée polyv. ROUVIERE (83)"}
   {:id "fake-id-0831646N" :name "Lycée polyv. DU VAL D'ARGENS (83)"}
   {:id "fake-id-0840001V" :name "Lycée polyv. CHARLES DE GAULLE (PLACE) (84)"}
   {:id "fake-id-0840015K" :name "Lycée polyv. JEAN HENRI FABRE (84)"}
   {:id "fake-id-0840918S" :name "Lycée polyv. VAL DE DURANCE (84)"}
   {:id "fake-id-0850016F" :name "Lycée polyv. ATLANTIQUE (85)"}
   {:id "fake-id-0850068M" :name "Lycée polyv. FRANCOIS RABELAIS (85)"}
   {:id "fake-id-0851390Z" :name "Lycée polyv. LEONARD DE VINCI (85)"}
   {:id "fake-id-0851400K" :name "Lycée polyv. JEAN MONNET (85)"}
   {:id "fake-id-0860037Y" :name "Lycée polyv. LOUIS ARMAND (86)"}
   {:id "fake-id-0861408N" :name "Lycée polyv. LPO KYOTO (86)"}
   {:id "fake-id-0870019Y" :name "Lycée polyv. SUZANNE VALADON (87)"}
   {:id "fake-id-0875023M" :name "Lycée polyv. MARYSE BASTIE (87)"}
   {:id "fake-id-0880040R" :name "Lycée polyv. PIERRE ET MARIE CURIE (88)"}
   {:id "fake-id-0890005X" :name "Lycée polyv. JEAN-JOSEPH FOURIER (89)"}
   {:id "fake-id-0890008A" :name "Lycée polyv. DU PARC DES CHAUMES (89)"}
   {:id "fake-id-0890032B" :name "Lycée polyv. CHEVALIER D'EON (89)"}
   {:id "fake-id-0890070T" :name "Lycée polyv. SAINT JOSEPH (89)"}
   {:id "fake-id-0891199V" :name "Lycée polyv. LOUIS DAVIER (89)"}
   {:id "fake-id-0910621F" :name "Lycée polyv. FRANCISQUE SARCEY (91)"}
   {:id "fake-id-0910622G" :name "Lycée polyv. GEOFFROY-SAINT-HILAIRE (91)"}
   {:id "fake-id-0910676R" :name "Lycée polyv. CLEMENT ADER (91)"}
   {:id "fake-id-0910975R" :name "Lycée polyv. JEAN PIERRE TIMBAUD (91)"}
   {:id "fake-id-0911492C" :name "Lycée polyv. L'ESSOURIAU (91)"}
   {:id "fake-id-0911937L" :name "Lycée polyv. FRANCOIS TRUFFAUT (91)"}
   {:id "fake-id-0911945V" :name "Lycée polyv. MARGUERITE YOURCENAR (91)"}
   {:id "fake-id-0911946W" :name "Lycée polyv. LEONARD DE VINCI (91)"}
   {:id "fake-id-0911962N" :name "Lycée polyv. MARIE LAURENCIN (91)"}
   {:id "fake-id-0911985N" :name "Lycée polyv. ALFRED KASTLER (91)"}
   {:id "fake-id-0912163G" :name "Lycée polyv. PAUL LANGEVIN (91)"}
   {:id "fake-id-0920136Y" :name "Lycée polyv. NEWTON-ENREA (92)"}
   {:id "fake-id-0922249V" :name "Lycée polyv. MONTESQUIEU (92)"}
   {:id "fake-id-0922277A" :name "Lycée polyv. CHARLES PETIET (92)"}
   {:id "fake-id-0922397F" :name "Lycée polyv. EUGENE IONESCO (92)"}
   {:id "fake-id-0922464D" :name "Lycée polyv. LOUISE MICHEL (92)"}
   {:id "fake-id-0930119Z" :name "Lycée polyv. EUGENE DELACROIX (93)"}
   {:id "fake-id-0930126G" :name "Lycée polyv. AUGUSTE BLANQUI (93)"}
   {:id "fake-id-0931584S" :name "Lycée polyv. JEAN ROSTAND (93)"}
   {:id "fake-id-0932026X" :name "Lycée polyv. ALFRED NOBEL (93)"}
   {:id "fake-id-0932030B" :name "Lycée polyv. MAURICE UTRILLO (93)"}
   {:id "fake-id-0932048W" :name "Lycée polyv. BLAISE CENDRARS (93)"}
   {:id "fake-id-0932073Y" :name "Lycée polyv. PAUL ROBERT (93)"}
   {:id "fake-id-0932117W" :name "Lycée polyv. LUCIE AUBRAC (93)"}
   {:id "fake-id-0932118X" :name "Lycée polyv. JEAN MOULIN (93)"}
   {:id "fake-id-0932122B" :name "Lycée polyv. D'ALEMBERT (93)"}
   {:id "fake-id-0932123C" :name "Lycée polyv. ANDRE SABATIER (93)"}
   {:id "fake-id-0932221J" :name "Lycée polyv. BLAISE PASCAL (93)"}
   {:id "fake-id-0932282A" :name "Lycée polyv. LEO LAGRANGE (93)"}
   {:id "fake-id-0932638M" :name "Lycée polyv. INTERNATIONAL (93)"}
   {:id "fake-id-0940111K" :name "Lycée polyv. GUSTAVE EIFFEL (94)"}
   {:id "fake-id-0940113M" :name "Lycée polyv. LANGEVIN-WALLON (94)"}
   {:id "fake-id-0940114N" :name "Lycée polyv. ANTOINE DE SAINT EXUPERY (94)"}
   {:id "fake-id-0940119U" :name "Lycée polyv. PAUL DOUMER (94)"}
   {:id "fake-id-0940122X" :name "Lycée polyv. CONDORCET (94)"}
   {:id "fake-id-0940742W" :name "Lycée polyv. GUILLAUME BUDE (94)"}
   {:id "fake-id-0940743X" :name "Lycée polyv. GEORGES BRASSENS (94)"}
   {:id "fake-id-0941018W" :name "Lycée polyv. EDOUARD BRANLY (94)"}
   {:id "fake-id-0941301D" :name "Lycée polyv. FREDERIC MISTRAL (94)"}
   {:id "fake-id-0941413A" :name "Lycée polyv. LEON BLUM (94)"}
   {:id "fake-id-0941474S" :name "Lycée polyv. DARIUS MILHAUD (94)"}
   {:id "fake-id-0941918Z" :name "Lycée polyv. CHRISTOPHE COLOMB (94)"}
   {:id "fake-id-0941930M" :name "Lycée polyv. JOHANNES GUTENBERG (94)"}
   {:id "fake-id-0941952L" :name "Lycée polyv. FRANCOIS ARAGO (94)"}
   {:id "fake-id-0941974K" :name "Lycée polyv. ROBERT SCHUMAN (94)"}
   {:id "fake-id-0941975L" :name "Lycée polyv. PIERRE BROSSOLETTE (94)"}
   {:id "fake-id-0942269F" :name "Lycée polyv. PAULINE ROLAND (94)"}
   {:id "fake-id-0950641F" :name "Lycée polyv. JEAN JAURES (95)"}
   {:id "fake-id-0950650R" :name "Lycée polyv. JEAN-JACQUES ROUSSEAU (95)"}
   {:id "fake-id-0950666H" :name "Lycée polyv. GEORGES BRAQUE (95)"}
   {:id "fake-id-0950947N" :name "Lycée polyv. DE LA TOURELLE (95)"}
   {:id "fake-id-0951104J" :name "Lycée polyv. JEAN PERRIN (95)"}
   {:id "fake-id-0951722F" :name "Lycée polyv. JEAN MONNET (95)"}
   {:id "fake-id-0951727L" :name "Lycée polyv. CHARLES BAUDELAIRE (95)"}
   {:id "fake-id-0951748J" :name "Lycée polyv. EVARISTE GALOIS (95)"}
   {:id "fake-id-0951756T" :name "Lycée polyv. JULES VERNE (95)"}
   {:id "fake-id-0951763A" :name "Lycée polyv. LOUIS JOUVET (95)"}
   {:id "fake-id-0951788C" :name "Lycée polyv. GEORGE SAND (95)"}
   {:id "fake-id-0951824S" :name "Lycée polyv. DE L HAUTIL (95)"}
   {:id "fake-id-0951937P" :name "Lycée polyv. PAUL-EMILE VICTOR (95)"}
   {:id "fake-id-0951974E" :name "Lycée polyv. LOUIS ARMAND (95)"}
   {:id "fake-id-0952173W" :name "Lycée polyv. EUGENE RONCERAY (95)"}
   {:id "fake-id-0952196W" :name "Lycée polyv. GUSTAVE MONOD (95)"}
   {:id "fake-id-6200043C" :name "Lycée polyv. GEORGES CLEMENCEAU SARTENE (620)"}
   {:id "fake-id-6200063Z" :name "Lycée polyv. JEAN PAUL DE ROCCA SERRA (620)"}
   {:id "fake-id-7200123W" :name "Lycée polyv. DE BALAGNE (720)"}
   {:id "fake-id-7200719U" :name "Lycée polyv. DU FIUM'ORBU (720)"}
   {:id "fake-id-9710722H" :name "Lycée polyv. CARNOT (971)"}
   {:id "fake-id-9710981P" :name "Lycée polyv. ILES DU NORD (971)"}
   {:id "fake-id-9711012Y" :name "Lycée polyv. HYACINTHE BASTARAUD (971)"}
   {:id "fake-id-9711032V" :name "Lycée polyv. CHARLES COEFFIN (971)"}
   {:id "fake-id-9711033W" :name "Lycée polyv. POINTE NOIRE (971)"}
   {:id "fake-id-9711046K" :name "Lycée polyv. LPO CHEVALIER DE SAINT-GEORGES (971)"}
   {:id "fake-id-9711066G" :name "Lycée polyv. HOTELIER DU GOSIER (971)"}
   {:id "fake-id-9711082Z" :name "Lycée polyv. NORD GRANDE TERRE (971)"}
   {:id "fake-id-9720692V" :name "Lycée polyv. NORD ATLANTIQUE (972)"}
   {:id "fake-id-9720695Y" :name "Lycée polyv. ACAJOU 2 (972)"}
   {:id "fake-id-9720725F" :name "Lycée polyv. JOSEPH ZOBEL (972)"}
   {:id "fake-id-9720726G" :name "Lycée polyv. JOSEPH PERNOCK (972)"}
   {:id "fake-id-9720771F" :name "Lycée polyv. LA JETEE (972)"}
   {:id "fake-id-9720823M" :name "Lycée polyv. DU NORD CARAIBE (972)"}
   {:id "fake-id-9720888H" :name "Lycée polyv. SAINT-JAMES (972)"}
   {:id "fake-id-9730235T" :name "Lycée polyv. BERTENE JUMINER (973)"}
   {:id "fake-id-9730371R" :name "Lycée polyv. LUMINA SOPHIE (973)"}
   {:id "fake-id-9730421V" :name "Lycée polyv. LEOPOLD ELFORT (973)"}
   {:id "fake-id-9730423X" :name "Lycée polyv. LAMA PREVOT (973)"}
   {:id "fake-id-9730476E" :name "Lycée polyv. DE LA NOUVELLE CHANCE (973)"}
   {:id "fake-id-9740002J" :name "Lycée polyv. ROLAND GARROS (974)"}
   {:id "fake-id-9740045F" :name "Lycée polyv. ANTOINE DE SAINT-EXUPERY (974)"}
   {:id "fake-id-9741051Z" :name "Lycée polyv. PAUL MOREAU (974)"}
   {:id "fake-id-9741052A" :name "Lycée polyv. STELLA (974)"}
   {:id "fake-id-9741087N" :name "Lycée polyv. BOISJOLY POTIER (974)"}
   {:id "fake-id-9741173G" :name "Lycée polyv. LA POSSESSION (974)"}
   {:id "fake-id-9741182S" :name "Lycée polyv. JEAN JOLY (974)"}
   {:id "fake-id-9741186W" :name "Lycée polyv. DE TROIS BASSINS (974)"}
   {:id "fake-id-9741206T" :name "Lycée polyv. DE BOIS D'OLIVE (974)"}
   {:id "fake-id-9741230U" :name "Lycée polyv. DE VINCENDO (974)"}
   {:id "fake-id-9741231V" :name "Lycée polyv. MARIE CURIE (974)"}
   {:id "fake-id-9741233X" :name "Lycée polyv. DE BRAS FUSIL (974)"}
   {:id "fake-id-9741263E" :name "Lycée polyv. PIERRE LAGOURGUE (974)"}
   {:id "fake-id-9741380G" :name "Lycée polyv. DE ST PAUL 4 (974)"}
   {:id "fake-id-9760182U" :name "Lycée polyv. DE SADA (976)"}
   {:id "fake-id-9760229V" :name "Lycée polyv. DE PETITE-TERRE (976)"}
   {:id "fake-id-9760270P" :name "Lycée polyv. DU NORD (976)"}
   {:id "fake-id-9760316P" :name "Lycée polyv. TANI MALANDI (976)"}
   {:id "fake-id-9760338N" :name "Lycée polyv. DE DEMBENI (976)"}
   {:id "fake-id-9760370Y" :name "Lycée polyv. DE MAMOUDZOU NORD (976)"}
   {:id "fake-id-9760380J" :name "Lycée polyv. DE MAMOUDZOU SUD (976)"}
   {:id "fake-id-9830003L" :name "Lycée polyv. JULES GARNIER (983)"}
   {:id "fake-id-9830483H" :name "Lycée polyv. WILLIAMA HAUDRA (LIFOU) (983)"}
   {:id "fake-id-9830635Y" :name "Lycée polyv. DE POUEMBOUT (983)"}
   {:id "fake-id-9840023C" :name "Lycée polyv. DE TAAONE (984)"}
   {:id "fake-id-9840339W" :name "Lycée polyv. DE TARAVAO (TAIARAPU) (984)"}
   {:id "fake-id-9840386X" :name "Lycée polyv. TUIANU LE GAYIC (984)"}
   {:id "fake-id-9840407V" :name "Lycée polyv. DE AORAI (984)"}
   {:id "fake-id-0010017N" :name "Lycée pro JOSEPH-MARIE CARRIAT (01)"}
   {:id "fake-id-0011010T" :name "Lycée pro DU BUGEY (01)"}
   {:id "fake-id-0020022N" :name "Lycée pro JEAN MONNET (02)"}
   {:id "fake-id-0020078Z" :name "Lycée pro JULIE DAUBIE (02)"}
   {:id "fake-id-0021479X" :name "Lycée pro FRANCOISE DOLTO (02)"}
   {:id "fake-id-0030924N" :name "Lycée pro VAL D'ALLIER (03)"}
   {:id "fake-id-0050008G" :name "Lycée pro PAUL HERAUD (05)"}
   {:id "fake-id-0050027C" :name "Lycée pro PIERRE MENDES FRANCE (05)"}
   {:id "fake-id-0060002V" :name "Lycée pro JACQUES DOLLE (06)"}
   {:id "fake-id-0060022S" :name "Lycée pro LEON CHIRIS (06)"}
   {:id "fake-id-0060023T" :name "Lycée pro FRANCIS DE CROISSET (06)"}
   {:id "fake-id-0060038J" :name "Lycée pro VAUBAN (06)"}
   {:id "fake-id-0060040L" :name "Lycée pro PASTEUR (06)"}
   {:id "fake-id-0060042N" :name "Lycée pro LES PALMIERS (06)"}
   {:id "fake-id-0061635V" :name "Lycée pro AUGUSTE ESCOFFIER (06)"}
   {:id "fake-id-0070002P" :name "Lycée pro JOSEPH ET ETIENNE MONTGOLFIER (07)"}
   {:id "fake-id-0070009X" :name "Lycée pro LEON PAVIN (07)"}
   {:id "fake-id-0070016E" :name "Lycée pro HOTELIER (07)"}
   {:id "fake-id-0090003E" :name "Lycée pro JEAN DURROUX (09)"}
   {:id "fake-id-0100016N" :name "Lycée pro DENIS DIDEROT (10)"}
   {:id "fake-id-0100945Y" :name "Lycée pro GABRIEL VOISIN (10)"}
   {:id "fake-id-0110027V" :name "Lycée pro ÉDOUARD HERRIOT (11)"} ; fixed
   {:id "fake-id-0120014A" :name "Lycée pro JEAN VIGO (12)"}
   {:id "fake-id-0120036Z" :name "Lycée pro LA DECOUVERTE (12)"}
   {:id "fake-id-0121157T" :name "Lycée pro RAYMOND SAVIGNAC (12)"}
   {:id "fake-id-0130012T" :name "Lycée pro PERDIGUIER (13)"}
   {:id "fake-id-0130013U" :name "Lycée pro GUSTAVE EIFFEL (13)"}
   {:id "fake-id-0130025G" :name "Lycée pro ETOILE (DE L') (13)"}
   {:id "fake-id-0130054N" :name "Lycée pro POINSO-CHAPUIS (13)"}
   {:id "fake-id-0130055P" :name "Lycée pro CHATELIER (LE) (13)"}
   {:id "fake-id-0130058T" :name "Lycée pro ESTAQUE (L') (13)"}
   {:id "fake-id-0130059U" :name "Lycée pro BLAISE PASCAL (13)"}
   {:id "fake-id-0130068D" :name "Lycée pro CAMILLE JULLIAN (13)"}
   {:id "fake-id-0130072H" :name "Lycée pro AMPERE (13)"}
   {:id "fake-id-0130157A" :name "Lycée pro FERRAGES (QUARTIER LES) (13)"}
   {:id "fake-id-0130172S" :name "Lycée pro LEONARD DE VINCI (13)"}
   {:id "fake-id-0131709M" :name "Lycée pro ADAM DE CRAPONNE (13)"}
   {:id "fake-id-0132319A" :name "Lycée pro MAURICE GENEVOIX (13)"}
   {:id "fake-id-0132569X" :name "Lycée pro EMILE ZOLA (13)"}
   {:id "fake-id-0140018U" :name "Lycée pro VICTOR LEPINE (14)"}
   {:id "fake-id-0141599M" :name "Lycée pro JEAN JOORIS (14)"}
   {:id "fake-id-0141640G" :name "Lycée pro GUIBRAY (14)"}
   {:id "fake-id-0142178S" :name "Lycée pro JEAN MERMOZ (14)"}
   {:id "fake-id-0150022T" :name "Lycée pro JOSEPH CONSTANT (15)"}
   {:id "fake-id-0160047P" :name "Lycée pro CHARLES A COULOMB (16)"}
   {:id "fake-id-0160119T" :name "Lycée pro LOUIS DELAGE (16)"}
   {:id "fake-id-0170025K" :name "Lycée pro GILLES JAMAIN (17)"}
   {:id "fake-id-0170030R" :name "Lycée pro LEONCE VIELJEUX (17)"}
   {:id "fake-id-0170078T" :name "Lycée pro BERNARD PALISSY (17)"}
   {:id "fake-id-0171238D" :name "Lycée pro ROMPSAY (17)"}
   {:id "fake-id-0180026F" :name "Lycée pro JEAN MOULIN (18)"}
   {:id "fake-id-0180042Y" :name "Lycée pro JACQUES COEUR (18)"}
   {:id "fake-id-0190008F" :name "Lycée pro BORT-ARTENSE (19)"}
   {:id "fake-id-0190034J" :name "Lycée pro RENE CASSIN (19)"}
   {:id "fake-id-0190045W" :name "Lycée pro GEORGES CABANIS (19)"}
   {:id "fake-id-0190674E" :name "Lycée pro BERNART DE VENTADOUR (19)"}
   {:id "fake-id-0190701J" :name "Lycée pro LAVOISIER (19)"}
   {:id "fake-id-0210056X" :name "Lycée pro EUGENE GUILLAUME (21)"}
   {:id "fake-id-0211356K" :name "Lycée pro ANTOINE (21)"}
   {:id "fake-id-0220064A" :name "Lycée pro LA CLOSERIE (22)"}
   {:id "fake-id-0220070G" :name "Lycée pro CHAPTAL (22)"}
   {:id "fake-id-0220072J" :name "Lycée pro JOSEPH SAVINA (22)"}
   {:id "fake-id-0220083W" :name "Lycée pro FELIX LE DANTEC (22)"}
   {:id "fake-id-0221595P" :name "Lycée pro LA FONTAINE DES EAUX (22)"}
   {:id "fake-id-0230003D" :name "Lycée pro JEAN JAURES (23)"}
   {:id "fake-id-0230008J" :name "Lycée pro DELPHINE GAY (23)"}
   {:id "fake-id-0230027E" :name "Lycée pro LOUIS GASTON ROUSSILLAT (23)"}
   {:id "fake-id-0240012H" :name "Lycée pro CHARDEUIL METIERS DU BATIMENT (24)"}
   {:id "fake-id-0240028A" :name "Lycée pro PABLO PICASSO (24)"}
   {:id "fake-id-0240039M" :name "Lycée pro PORTE D AQUITAINE (24)"}
   {:id "fake-id-0240048X" :name "Lycée pro PRE DE CORDY (24)"}
   {:id "fake-id-0240050Z" :name "Lycée pro ARNAUT DANIEL (24)"}
   {:id "fake-id-0250001R" :name "Lycée pro NELSON MANDELA (25)"}
   {:id "fake-id-0250013D" :name "Lycée pro PIERRE-ADRIEN PARIS (25)"}
   {:id "fake-id-0250014E" :name "Lycée pro CONDE (25)"}
   {:id "fake-id-0250063H" :name "Lycée pro JOUFFROY D'ABBANS (25)"}
   {:id "fake-id-0250064J" :name "Lycée pro MONTJOUX (25)"}
   {:id "fake-id-0250067M" :name "Lycée pro LES HUISSELETS (25)"}
   {:id "fake-id-0251079M" :name "Lycée pro TRISTAN BERNARD (25)"}
   {:id "fake-id-0251349F" :name "Lycée pro TOUSSAINT LOUVERTURE (25)"}
   {:id "fake-id-0260037Z" :name "Lycée pro VICTOR HUGO (26)"}
   {:id "fake-id-0260044G" :name "Lycée pro AUGUSTE BOUVET (26)"}
   {:id "fake-id-0260116K" :name "Lycée pro MONTESQUIEU (26)"}
   {:id "fake-id-0270052K" :name "Lycée pro GEORGES DUMEZIL (27)"}
   {:id "fake-id-0271268G" :name "Lycée pro ARISTIDE BRIAND (27)"}
   {:id "fake-id-0271319M" :name "Lycée pro PORTE DE NORMANDIE (27)"}
   {:id "fake-id-0290001U" :name "Lycée pro JEAN MOULIN (29)"}
   {:id "fake-id-0290072W" :name "Lycée pro JEAN CHAPTAL (29)"}
   {:id "fake-id-0290078C" :name "Lycée pro ROZ GLAS (29)"}
   {:id "fake-id-0290102D" :name "Lycée pro JULES LESVEN (29)"}
   {:id "fake-id-0300011Z" :name "Lycée pro PAUL LANGEVIN (30)"}
   {:id "fake-id-0300057Z" :name "Lycée pro GASTON DARBOUX (30)"}
   {:id "fake-id-0301210C" :name "Lycée pro VOLTAIRE (30)"}
   {:id "fake-id-0310033T" :name "Lycée pro ELISABETH ET NORBERT CASTERET (31)"}
   {:id "fake-id-0310051M" :name "Lycée pro GUYNEMER (31)"}
   {:id "fake-id-0310054R" :name "Lycée pro RENEE BONNET (31)"}
   {:id "fake-id-0310090E" :name "Lycée pro DEODAT DE SEVERAC (31)"}
   {:id "fake-id-0310091F" :name "Lycée pro STEPHANE HESSEL (31)"}
   {:id "fake-id-0311324W" :name "Lycée pro DU MIRAIL (31)"}
   {:id "fake-id-0320040V" :name "Lycée pro PARDAILHAN (32)"}
   {:id "fake-id-0330011H" :name "Lycée pro ANATOLE DE MONZIE (33)"}
   {:id "fake-id-0330032F" :name "Lycée pro NICOLAS BREMONTIER (33)"}
   {:id "fake-id-0330033G" :name "Lycée pro DES MENUTS (33)"}
   {:id "fake-id-0330102G" :name "Lycée pro ODILON REDON (33)"}
   {:id "fake-id-0330114V" :name "Lycée pro PAUL BROCA (33)"}
   {:id "fake-id-0330119A" :name "Lycée pro JEHAN DUPERIER (33)"}
   {:id "fake-id-0330142A" :name "Lycée pro TREGEY RIVE DE GARONNE (33)"}
   {:id "fake-id-0331460H" :name "Lycée pro LES CHARTRONS (33)"}
   {:id "fake-id-0331668J" :name "Lycée pro MARCEL DASSAULT (33)"}
   {:id "fake-id-0331882S" :name "Lycée pro EMILE COMBES (33)"}
   {:id "fake-id-0332344U" :name "Lycée pro HENRI BRULLE (33)"}
   {:id "fake-id-0332345V" :name "Lycée pro PHILADELPHE DE GERDE (33)"}
   {:id "fake-id-0332346W" :name "Lycée pro PHILIPPE COUSTEAU (33)"}
   {:id "fake-id-0332781U" :name "Lycée pro DE L ESTUAIRE (33)"}
   {:id "fake-id-0340012D" :name "Lycée pro JEAN MERMOZ (34)"}
   {:id "fake-id-0340045P" :name "Lycée pro JULES FERRY (34)"}
   {:id "fake-id-0340088L" :name "Lycée pro IRENE ET FREDERIC JOLIOT CURIE (34)"}
   {:id "fake-id-0350009V" :name "Lycée pro ALPHONSE PELLE (35)"}
   {:id "fake-id-0350031U" :name "Lycée pro JEAN JAURES (35)"}
   {:id "fake-id-0350059Z" :name "Lycée pro BREQUIGNY (35)"}
   {:id "fake-id-0350062C" :name "Lycée pro MAUPERTUIS (35)"}
   {:id "fake-id-0350761M" :name "Lycée pro JEAN GUEHENNO (35)"}
   {:id "fake-id-0351054F" :name "Lycée pro CHARLES TILLON (35)"}
   {:id "fake-id-0351878B" :name "Lycée pro COETLOGON (35)"}
   {:id "fake-id-0360003H" :name "Lycée pro CHATEAUNEUF (36)"}
   {:id "fake-id-0371123V" :name "Lycée pro JEAN CHAPTAL (37)"}
   {:id "fake-id-0380010E" :name "Lycée pro GAMBETTA (38)"}
   {:id "fake-id-0380023U" :name "Lycée pro JACQUES PREVERT (38)"}
   {:id "fake-id-0380037J" :name "Lycée pro JEAN  JAURES (38)"}
   {:id "fake-id-0381602K" :name "Lycée pro L'ODYSSEE (38)"}
   {:id "fake-id-0381606P" :name "Lycée pro DE L'EDIT (38)"}
   {:id "fake-id-0382031B" :name "Lycée pro THOMAS EDISON (38)"}
   {:id "fake-id-0390015E" :name "Lycée pro DES METIERS DE LA MODE ET DE L (39)"}
   {:id "fake-id-0390020K" :name "Lycée pro LE CORBUSIER - BATIMENT (39)"}
   {:id "fake-id-0390021L" :name "Lycée pro MONTCIEL (39)"}
   {:id "fake-id-0390024P" :name "Lycée pro PIERRE VERNOTTE (39)"}
   {:id "fake-id-0390914G" :name "Lycée pro FERDINAND FILLOD (39)"}
   {:id "fake-id-0400047J" :name "Lycée pro JEAN D ARCET (40)"}
   {:id "fake-id-0400049L" :name "Lycée pro AMBROISE CROIZAT (40)"}
   {:id "fake-id-0400057V" :name "Lycée pro SAINT-EXUPERY (40)"}
   {:id "fake-id-0410031L" :name "Lycée pro ANDRE AMPERE (41)"}
   {:id "fake-id-0410036S" :name "Lycée pro DENIS PAPIN (41)"}
   {:id "fake-id-0420073B" :name "Lycée pro JACOB HOLTZER (42)"}
   {:id "fake-id-0420074C" :name "Lycée pro ALBERT CAMUS (42)"}
   {:id "fake-id-0420075D" :name "Lycée pro BEAUREGARD (42)"}
   {:id "fake-id-0420076E" :name "Lycée pro CARNOT (42)"}
   {:id "fake-id-0420077F" :name "Lycée pro ALBERT THOMAS (42)"}
   {:id "fake-id-0420079H" :name "Lycée pro ETIENNE MIMARD (42)"}
   {:id "fake-id-0430023S" :name "Lycée pro AUGUSTE AYMARD (43)"}
   {:id "fake-id-0440035Z" :name "Lycée pro LEONARD DE VINCI (44)"}
   {:id "fake-id-0440056X" :name "Lycée pro ALBERT CHASSAGNE (44)"}
   {:id "fake-id-0440063E" :name "Lycée pro LOUIS-JACQUES GOUSSIER (44)"}
   {:id "fake-id-0440074S" :name "Lycée pro BROSSAUD-BLANCHO (44)"}
   {:id "fake-id-0440310Y" :name "Lycée pro JEAN JACQUES AUDUBON (44)"}
   {:id "fake-id-0440315D" :name "Lycée pro ANDRE BOULLOCHE (44)"}
   {:id "fake-id-0440537V" :name "Lycée pro LES SAVARIERES (44)"}
   {:id "fake-id-0440541Z" :name "Lycée pro DES TROIS RIVIERES (44)"}
   {:id "fake-id-0441550W" :name "Lycée pro OLIVIER GUICHARD (44)"}
   {:id "fake-id-0441656L" :name "Lycée pro PABLO NERUDA (44)"}
   {:id "fake-id-0450043C" :name "Lycée pro JEANNETTE VERDIER (45)"}
   {:id "fake-id-0451442Y" :name "Lycée pro CHATEAU BLANC (45)"}
   {:id "fake-id-0470004Z" :name "Lycée pro ANTOINE LOMET (47)"}
   {:id "fake-id-0470641S" :name "Lycée pro BENOIT D AZY (47)"}
   {:id "fake-id-0470782V" :name "Lycée pro JEAN MONNET (47)"}
   {:id "fake-id-0490005P" :name "Lycée pro SIMONE VEIL (49)"}
   {:id "fake-id-0490784L" :name "Lycée pro HENRI DUNANT (49)"}
   {:id "fake-id-0490801E" :name "Lycée pro PAUL EMILE VICTOR (49)"}
   {:id "fake-id-0491646Y" :name "Lycée pro LUDOVIC MENARD (49)"}
   {:id "fake-id-0500089A" :name "Lycée pro ALEXIS DE TOCQUEVILLE (50)"}
   {:id "fake-id-0510050C" :name "Lycée pro DE L'ARGONNE (51)"}
   {:id "fake-id-0520008B" :name "Lycée pro EUGENE DECOMBLE (52)"}
   {:id "fake-id-0520029Z" :name "Lycée pro BLAISE PASCAL (52)"}
   {:id "fake-id-0520795G" :name "Lycée pro EDME BOUCHARDON (52)"}
   {:id "fake-id-0520923W" :name "Lycée pro ST EXUPERY (52)"}
   {:id "fake-id-0530013B" :name "Lycée pro ROBERT BURON (53)"}
   {:id "fake-id-0530040F" :name "Lycée pro PIERRE ET MARIE CURIE (53)"}
   {:id "fake-id-0540032S" :name "Lycée pro DARCHE (54)"}
   {:id "fake-id-0540037X" :name "Lycée pro PAUL LAPIE (54)"}
   {:id "fake-id-0540059W" :name "Lycée pro HELENE BARDOT (54)"}
   {:id "fake-id-0540060X" :name "Lycée pro LA TOURNELLE (54)"}
   {:id "fake-id-0540067E" :name "Lycée pro REGIONAL DU TOULOIS (54)"}
   {:id "fake-id-0540082W" :name "Lycée pro PAUL LOUIS CYFFLE (54)"}
   {:id "fake-id-0540085Z" :name "Lycée pro BERTRAND SCHWARTZ (54)"}
   {:id "fake-id-0540086A" :name "Lycée pro JEAN MORETTE (54)"}
   {:id "fake-id-0541605B" :name "Lycée pro JEAN MARC REISER (54)"}
   {:id "fake-id-0550003E" :name "Lycée pro EMILE ZOLA (55)"}
   {:id "fake-id-0550004F" :name "Lycée pro LIGIER RICHIER (55)"}
   {:id "fake-id-0550026E" :name "Lycée pro ALAIN-FOURNIER (55)"}
   {:id "fake-id-0550891V" :name "Lycée pro EUGENE FREYSSINET (55)"}
   {:id "fake-id-0560019S" :name "Lycée pro AMPERE (56)"}
   {:id "fake-id-0560042S" :name "Lycée pro JULIEN CROZET (56)"}
   {:id "fake-id-0560053D" :name "Lycée pro JEAN GUEHENNO (56)"}
   {:id "fake-id-0561507J" :name "Lycée pro LOUIS ARMAND (56)"}
   {:id "fake-id-0570051W" :name "Lycée pro PIERRE ET MARIE CURIE (57)"}
   {:id "fake-id-0570061G" :name "Lycée pro ALAIN FOURNIER (57)"}
   {:id "fake-id-0570077Z" :name "Lycée pro MARYSE BASTIE (57)"}
   {:id "fake-id-0570095U" :name "Lycée pro DOMINIQUE LABROISE (57)"}
   {:id "fake-id-0570100Z" :name "Lycée pro SIMON LAZARD (57)"}
   {:id "fake-id-0570124A" :name "Lycée pro RENE CASSIN (57)"}
   {:id "fake-id-0570144X" :name "Lycée pro METIERS DU BATIMENT ET T.P (57)"}
   {:id "fake-id-0572028V" :name "Lycée pro SOPHIE GERMAIN (57)"}
   {:id "fake-id-0572075W" :name "Lycée pro JEAN MACE (57)"}
   {:id "fake-id-0573080N" :name "Lycée pro HURLEVENT (57)"}
   {:id "fake-id-0580020G" :name "Lycée pro PIERRE BEREGOVOY (58)"}
   {:id "fake-id-0580042F" :name "Lycée pro LE MONT-CHATELET (58)"}
   {:id "fake-id-0580050P" :name "Lycée pro JEAN ROSTAND (58)"}
   {:id "fake-id-0590005K" :name "Lycée pro PIERRE-JOSEPH FONTAINE (59)"}
   {:id "fake-id-0590015W" :name "Lycée pro PIERRE ET MARIE CURIE (59)"}
   {:id "fake-id-0590037V" :name "Lycée pro LOUISE DE BETTIGNIES (59)"}
   {:id "fake-id-0590098L" :name "Lycée pro PLACIDE COURTOY (59)"}
   {:id "fake-id-0590125R" :name "Lycée pro MICHEL SERVET (59)"}
   {:id "fake-id-0590133Z" :name "Lycée pro MAURICE DUHAMEL (59)"}
   {:id "fake-id-0590144L" :name "Lycée pro AUTOMOBILE ALFRED MONGY (59)"}
   {:id "fake-id-0590187H" :name "Lycée pro LOUIS LOUCHEUR (59)"}
   {:id "fake-id-0590189K" :name "Lycée pro LAVOISIER (59)"}
   {:id "fake-id-0590198V" :name "Lycée pro GEORGES GUYNEMER (59)"}
   {:id "fake-id-0590216P" :name "Lycée pro LE CORBUSIER (59)"}
   {:id "fake-id-0590249A" :name "Lycée pro JACQUES-YVES COUSTEAU (59)"}
   {:id "fake-id-0590252D" :name "Lycée pro LOUIS-LEOPOLD BOILLY (59)"}
   {:id "fake-id-0590255G" :name "Lycée pro GUY DEBEYRE (59)"}
   {:id "fake-id-0590257J" :name "Lycée pro ILE DE FLANDRE (59)"}
   {:id "fake-id-0592611T" :name "Lycée pro LOUIS BLERIOT (59)"}
   {:id "fake-id-0592712C" :name "Lycée pro LOUIS ARMAND (59)"}
   {:id "fake-id-0592833J" :name "Lycée pro LES HAUTS DE FLANDRE (59)"}
   {:id "fake-id-0592850C" :name "Lycée pro ANTOINE DE SAINT EXUPERY (59)"}
   {:id "fake-id-0594375K" :name "Lycée pro DINAH DERYCKE (59)"}
   {:id "fake-id-0594400M" :name "Lycée pro AUTOMOBILE ET TRANSPORTS (59)"}
   {:id "fake-id-0594652L" :name "Lycée pro DES PLAINES DU NORD (59)"}
   {:id "fake-id-0595480L" :name "Lycée pro ILE JEANTY (59)"}
   {:id "fake-id-0595678B" :name "Lycée pro LEONARD DE VINCI (59)"}
   {:id "fake-id-0595787V" :name "Lycée pro ALAIN SAVARY (59)"}
   {:id "fake-id-0595856V" :name "Lycée pro DE L'YSER (59)"}
   {:id "fake-id-0595894L" :name "Lycée pro FRANCOIS RABELAIS (59)"}
   {:id "fake-id-0596957S" :name "Lycée pro AIME CESAIRE (59)"}
   {:id "fake-id-0600004D" :name "Lycée pro LES JACOBINS (60)"}
   {:id "fake-id-0600017T" :name "Lycée pro M.GRENET (MIXTE) (60)"}
   {:id "fake-id-0600048B" :name "Lycée pro DONATION DE ROTHSCHILD (60)"}
   {:id "fake-id-0600063T" :name "Lycée pro JULES UHRY (60)"}
   {:id "fake-id-0601363F" :name "Lycée pro ROBERT DESNOS (60)"}
   {:id "fake-id-0610019P" :name "Lycée pro FLORA TRISTAN (61)"}
   {:id "fake-id-0610027Y" :name "Lycée pro NAPOLEON (61)"}
   {:id "fake-id-0611157B" :name "Lycée pro MARECHAL LECLERC (61)"}
   {:id "fake-id-0611182D" :name "Lycée pro GABRIEL (61)"}
   {:id "fake-id-0620030W" :name "Lycée pro PHILIPPE AUGUSTE (62)"}
   {:id "fake-id-0620059C" :name "Lycée pro LEO LAGRANGE (62)"}
   {:id "fake-id-0620121V" :name "Lycée pro FLORA TRISTAN (62)"}
   {:id "fake-id-0620124Y" :name "Lycée pro BERNARD CHOCHOY (62)"}
   {:id "fake-id-0620163R" :name "Lycée pro DE L'AA (62)"}
   {:id "fake-id-0620167V" :name "Lycée pro PIERRE MENDES-FRANCE (62)"}
   {:id "fake-id-0620189U" :name "Lycée pro PIERRE DE COUBERTIN (62)"}
   {:id "fake-id-0620257T" :name "Lycée pro ALAIN SAVARY - JULES FERRY (62)"}
   {:id "fake-id-0620258U" :name "Lycée pro VOLTAIRE (62)"}
   {:id "fake-id-0623377J" :name "Lycée pro FRANCOIS HENNEBIQUE (62)"}
   {:id "fake-id-0630012W" :name "Lycée pro FRANCOIS RABELAIS (63)"}
   {:id "fake-id-0630061Z" :name "Lycée pro DESAIX (63)"}
   {:id "fake-id-0630078T" :name "Lycée pro GERMAINE TILLION (63)"}
   {:id "fake-id-0640012R" :name "Lycée pro LOUIS DE FOIX (64)"}
   {:id "fake-id-0640028H" :name "Lycée pro AIZPURDI (64)"}
   {:id "fake-id-0640031L" :name "Lycée pro ANDRE CAMPA (64)"}
   {:id "fake-id-0640053K" :name "Lycée pro FRANCIS JAMMES (64)"}
   {:id "fake-id-0640058R" :name "Lycée pro HONORE BARADAT (64)"}
   {:id "fake-id-0640079N" :name "Lycée pro PIERRE ET MARIE CURIE (64)"}
   {:id "fake-id-0640080P" :name "Lycée pro MOLIERE (64)"}
   {:id "fake-id-0650014M" :name "Lycée pro DE L'ARROUZA (65)"}
   {:id "fake-id-0650029D" :name "Lycée pro REFFYE (65)"}
   {:id "fake-id-0650035K" :name "Lycée pro PIERRE MENDES FRANCE (65)"}
   {:id "fake-id-0670043H" :name "Lycée pro CAMILLE SCHNEIDER (67)"}
   {:id "fake-id-0670062D" :name "Lycée pro ARISTIDE BRIAND (67)"}
   {:id "fake-id-0670067J" :name "Lycée pro HAUTE-BRUCHE (67)"}
   {:id "fake-id-0670129B" :name "Lycée pro JEAN GEILER (67)"}
   {:id "fake-id-0680027K" :name "Lycée pro JOSEPH VOGT (68)"}
   {:id "fake-id-0690008J" :name "Lycée pro GUSTAVE EIFFEL (69)"}
   {:id "fake-id-0690092A" :name "Lycée pro DU 1ER FILM (69)"}
   {:id "fake-id-0690106R" :name "Lycée pro FREDERIC FAYS (69)"}
   {:id "fake-id-0690130S" :name "Lycée pro JULES VERNE (69)"}
   {:id "fake-id-0691676X" :name "Lycée pro MARTIN LUTHER KING (69)"}
   {:id "fake-id-0692418D" :name "Lycée pro MARC SEGUIN (69)"}
   {:id "fake-id-0692968B" :name "Lycée pro ANDRE CUZIN (69)"}
   {:id "fake-id-0693094N" :name "Lycée pro FERNAND FOREST (69)"}
   {:id "fake-id-0693095P" :name "Lycée pro FRANCOIS CEVERT (69)"}
   {:id "fake-id-0693200D" :name "Lycée pro PABLO PICASSO (69)"}
   {:id "fake-id-0700011G" :name "Lycée pro HENRI FERTET (70)"}
   {:id "fake-id-0700038L" :name "Lycée pro LUXEMBOURG (70)"}
   {:id "fake-id-0700882D" :name "Lycée pro PONTARCHER (70)"}
   {:id "fake-id-0710014E" :name "Lycée pro THOMAS DUMOREY (71)"}
   {:id "fake-id-0710077Y" :name "Lycée pro JULIEN DE BALLEURE (71)"}
   {:id "fake-id-0710079A" :name "Lycée pro FRANCOISE DOLTO (71)"}
   {:id "fake-id-0710087J" :name "Lycée pro ASTIER (71)"}
   {:id "fake-id-0711384U" :name "Lycée pro THEODORE MONOD (71)"}
   {:id "fake-id-0720013Y" :name "Lycée pro MAL LECLERC HAUTECLOCQUE (72)"}
   {:id "fake-id-0730006K" :name "Lycée pro LE GRAND ARC (73)"}
   {:id "fake-id-0730012S" :name "Lycée pro HOTELIER (73)"}
   {:id "fake-id-0730900G" :name "Lycée pro PAUL HEROULT (73)"}
   {:id "fake-id-0731043M" :name "Lycée pro LA CARDINIERE (73)"}
   {:id "fake-id-0731249L" :name "Lycée pro LOUIS ARMAND (73)"}
   {:id "fake-id-0740010J" :name "Lycée pro LE SALEVE (74)"}
   {:id "fake-id-0740014N" :name "Lycée pro HOTELIER FRANCOIS BISE (74)"}
   {:id "fake-id-0740054G" :name "Lycée pro GERMAIN SOMMEILLER (74)"}
   {:id "fake-id-0740059M" :name "Lycée pro DU CHABLAIS (74)"}
   {:id "fake-id-0740062R" :name "Lycée pro AMEDEE GORDINI (74)"}
   {:id "fake-id-0750419Y" :name "Lycée pro CAMILLE JENATZY (75)"}
   {:id "fake-id-0750508V" :name "Lycée pro CHARLES DE GAULLE (75)"}
   {:id "fake-id-0750553U" :name "Lycée pro GASTON BACHELARD (75)"}
   {:id "fake-id-0750588G" :name "Lycée pro RENE CASSIN (75)"}
   {:id "fake-id-0750770E" :name "Lycée pro ABBE GREGOIRE (75)"}
   {:id "fake-id-0750775K" :name "Lycée pro GUSTAVE FERRIE (75)"}
   {:id "fake-id-0750783U" :name "Lycée pro CHENNEVIERE MALEZIEUX (75)"}
   {:id "fake-id-0750784V" :name "Lycée pro METIERS DE L'AMEUBLEMENT (75)"}
   {:id "fake-id-0750785W" :name "Lycée pro GALILEE (75)"}
   {:id "fake-id-0750787Y" :name "Lycée pro CORVISART-TOLBIAC (75)"}
   {:id "fake-id-0750788Z" :name "Lycée pro MARCEL DEPREZ (75)"}
   {:id "fake-id-0750800M" :name "Lycée pro EDMOND ROSTAND (75)"}
   {:id "fake-id-0750808W" :name "Lycée pro ETIENNE DOLET (75)"}
   {:id "fake-id-0752109K" :name "Lycée pro SUZANNE VALADON (75)"}
   {:id "fake-id-0752388N" :name "Lycée pro PIERRE LESCOT (75)"}
   {:id "fake-id-0752700C" :name "Lycée pro ARMAND CARREL (75)"}
   {:id "fake-id-0752845K" :name "Lycée pro THEOPHILE GAUTIER (75)"}
   {:id "fake-id-0752961L" :name "Lycée pro GUSTAVE EIFFEL (75)"}
   {:id "fake-id-0753350J" :name "Lycée pro MARIA DERAISMES (75)"}
   {:id "fake-id-0760007V" :name "Lycée pro AUGUSTE BARTHOLDI (76)"}
   {:id "fake-id-0760013B" :name "Lycée pro PIERRE ET MARIE CURIE (76)"}
   {:id "fake-id-0760024N" :name "Lycée pro EMULATION DIEPPOISE (76)"}
   {:id "fake-id-0760036B" :name "Lycée pro DESCARTES (76)"}
   {:id "fake-id-0760088H" :name "Lycée pro ELISA LEMONNIER (76)"}
   {:id "fake-id-0760114L" :name "Lycée pro LE HURLE-VENT (76)"}
   {:id "fake-id-0760142S" :name "Lycée pro GUSTAVE FLAUBERT (76)"}
   {:id "fake-id-0760145V" :name "Lycée pro VAL DE SEINE (76)"}
   {:id "fake-id-0761322Z" :name "Lycée pro CLAUDE MONET (76)"}
   {:id "fake-id-0762836V" :name "Lycée pro FERNAND LEGER (76)"}
   {:id "fake-id-0770919F" :name "Lycée pro URUGUAY FRANCE (77)"}
   {:id "fake-id-0770932V" :name "Lycée pro PIERRE DE COUBERTIN (77)"}
   {:id "fake-id-0770944H" :name "Lycée pro AUGUSTE PERDONNET (77)"}
   {:id "fake-id-0771880A" :name "Lycée pro CHARLES BAUDELAIRE (77)"}
   {:id "fake-id-0782593V" :name "Lycée pro JEAN PERRIN (78)"}
   {:id "fake-id-0782603F" :name "Lycée pro JACQUES PREVERT (78)"}
   {:id "fake-id-0790043T" :name "Lycée pro PAUL GUERIN (79)"}
   {:id "fake-id-0790090U" :name "Lycée pro LES GRIPPEAUX (79)"}
   {:id "fake-id-0790964U" :name "Lycée pro THOMAS JEAN MAIN (79)"}
   {:id "fake-id-0800013E" :name "Lycée pro DE L ACHEULEEN (80)"}
   {:id "fake-id-0801628K" :name "Lycée pro ROMAIN ROLLAND (80)"}
   {:id "fake-id-0801704T" :name "Lycée pro ALFRED MANESSIER (80)"}
   {:id "fake-id-0810016C" :name "Lycée pro LE SIDOBRE (81)"}
   {:id "fake-id-0810962F" :name "Lycée pro BORDE BASSE (81)"}
   {:id "fake-id-0810995S" :name "Lycée pro DOCTEUR CLEMENT DE PEMILLE (81)"}
   {:id "fake-id-0811324Z" :name "Lycée pro MARIE-ANTOINETTE RIESS (81)"}
   {:id "fake-id-0820006L" :name "Lycée pro JEAN-LOUIS ETIENNE (82)"}
   {:id "fake-id-0820032P" :name "Lycée pro BOURDELLE (82)"}
   {:id "fake-id-0820700R" :name "Lycée pro JEAN DE PRADES (82)"}
   {:id "fake-id-0830016S" :name "Lycée pro LEON BLUM (83)"}
   {:id "fake-id-0831354W" :name "Lycée pro LA COUDOULIERE (83)"}
   {:id "fake-id-0840042P" :name "Lycée pro ROBERT SCHUMAN (84)"}
   {:id "fake-id-0840046U" :name "Lycée pro ARISTIDE BRIAND (COURS) (84)"}
   {:id "fake-id-0840113S" :name "Lycée pro ALEXANDRE DUMAS (84)"}
   {:id "fake-id-0840763Y" :name "Lycée pro ARGENSOL (QUARTIER DE L') (84)"}
   {:id "fake-id-0840939P" :name "Lycée pro RENE CHAR (84)"}
   {:id "fake-id-0841078R" :name "Lycée pro REGIONAL MONTESQUIEU (84)"}
   {:id "fake-id-0850028U" :name "Lycée pro EDOUARD BRANLY (85)"}
   {:id "fake-id-0850033Z" :name "Lycée pro ERIC TABARLY (85)"}
   {:id "fake-id-0850043K" :name "Lycée pro VALERE MATHE (85)"}
   {:id "fake-id-0850146X" :name "Lycée pro RENE COUZINET (85)"}
   {:id "fake-id-0860006P" :name "Lycée pro EDOUARD BRANLY (86)"}
   {:id "fake-id-0860010U" :name "Lycée pro LES TERRES ROUGES (86)"}
   {:id "fake-id-0870013S" :name "Lycée pro GEORGE SAND (87)"}
   {:id "fake-id-0870041X" :name "Lycée pro EDOUARD VAILLANT (87)"}
   {:id "fake-id-0870051H" :name "Lycée pro JEAN BAPTISTE DARNET (87)"}
   {:id "fake-id-0870119G" :name "Lycée pro RAOUL DAUTRY (87)"}
   {:id "fake-id-0870730W" :name "Lycée pro MARCEL PAGNOL (87)"}
   {:id "fake-id-0875071P" :name "Lycée pro ECOLE BEAUTÉ FORMATION (87)"}
   {:id "fake-id-0880013L" :name "Lycée pro PIERRE MENDES FRANCE (88)"}
   {:id "fake-id-0880023X" :name "Lycée pro ISABELLE VIVIANI (88)"}
   {:id "fake-id-0880057J" :name "Lycée pro JACQUES AUGUSTIN (88)"}
   {:id "fake-id-0880064S" :name "Lycée pro EMILE GALLE (88)"}
   {:id "fake-id-0881140L" :name "Lycée pro CAMILLE CLAUDEL (88)"}
   {:id "fake-id-0881370L" :name "Lycée pro LOUIS GEISLER (88)"}
   {:id "fake-id-0890057D" :name "Lycée pro BLAISE PASCAL (89)"}
   {:id "fake-id-0891159B" :name "Lycée pro SAINT GERMAIN (89)"}
   {:id "fake-id-0900019G" :name "Lycée pro JULES FERRY (90)"}
   {:id "fake-id-0900236T" :name "Lycée pro RAOUL FOLLEREAU (90)"}
   {:id "fake-id-0910628N" :name "Lycée pro PAUL BELMONDO (91)"}
   {:id "fake-id-0911037H" :name "Lycée pro ANDRE-MARIE AMPERE (91)"}
   {:id "fake-id-0911343R" :name "Lycée pro AUGUSTE PERRET (91)"}
   {:id "fake-id-0911401D" :name "Lycée pro NELSON-MANDELA (91)"}
   {:id "fake-id-0911493D" :name "Lycée pro LES FRERES MOREAU (91)"}
   {:id "fake-id-0911578W" :name "Lycée pro PIERRE MENDES FRANCE (91)"}
   {:id "fake-id-0920163C" :name "Lycée pro LOUIS GIRARD (92)"}
   {:id "fake-id-0920680P" :name "Lycée pro LEONARD DE VINCI (92)"}
   {:id "fake-id-0921505L" :name "Lycée pro LOUIS DARDENNE (92)"}
   {:id "fake-id-0921595J" :name "Lycée pro DANIEL BALAVOINE (92)"}
   {:id "fake-id-0921626T" :name "Lycée pro CLAUDE CHAPPE (92)"}
   {:id "fake-id-0921677Y" :name "Lycée pro PAUL LANGEVIN (92)"}
   {:id "fake-id-0930128J" :name "Lycée pro DENIS PAPIN (93)"}
   {:id "fake-id-0930129K" :name "Lycée pro MADELEINE VIONNET (93)"}
   {:id "fake-id-0930130L" :name "Lycée pro CONDORCET (93)"}
   {:id "fake-id-0930135S" :name "Lycée pro SIMONE WEIL (93)"}
   {:id "fake-id-0930138V" :name "Lycée pro FREDERIC BARTHOLDI (93)"}
   {:id "fake-id-0931024H" :name "Lycée pro JEAN-PIERRE TIMBAUD (93)"}
   {:id "fake-id-0931738J" :name "Lycée pro ARTHUR RIMBAUD (93)"}
   {:id "fake-id-0931739K" :name "Lycée pro JEAN MOULIN (93)"}
   {:id "fake-id-0940134K" :name "Lycée pro VAL DE BIEVRE (94)"}
   {:id "fake-id-0940143V" :name "Lycée pro JEAN MOULIN (94)"}
   {:id "fake-id-0940145X" :name "Lycée pro CAMILLE CLAUDEL (94)"}
   {:id "fake-id-0941232D" :name "Lycée pro JEAN MACE (94)"}
   {:id "fake-id-0941298A" :name "Lycée pro MICHELET (94)"}
   {:id "fake-id-0941604H" :name "Lycée pro SAMUEL DE CHAMPLAIN (94)"}
   {:id "fake-id-0950657Y" :name "Lycée pro FERDINAND BUISSON (95)"}
   {:id "fake-id-6200003J" :name "Lycée pro JULES ANTONINI (620)"}
   {:id "fake-id-6200004K" :name "Lycée pro FINOSELLO (620)"}
   {:id "fake-id-7200011Z" :name "Lycée pro SCAMARONI (720)"}
   {:id "fake-id-7200093N" :name "Lycée pro JEAN NICOLI (720)"}
   {:id "fake-id-9710052E" :name "Lycée pro LOUIS DELGRES (971)"}
   {:id "fake-id-9710709U" :name "Lycée pro GERTY ARCHIMEDE (971)"}
   {:id "fake-id-9710746J" :name "Lycée pro AUGUSTIN ARRON (971)"}
   {:id "fake-id-9720005Y" :name "Lycée pro DUMAS JEAN-JOSEPH (972)"}
   {:id "fake-id-9720424D" :name "Lycée pro LUMINA SOPHIE (EX BATELIERE) (972)"}
   {:id "fake-id-9720429J" :name "Lycée pro DILLON (972)"}
   {:id "fake-id-9720430K" :name "Lycée pro LA TRINITE (972)"}
   {:id "fake-id-9720468B" :name "Lycée pro RAYMOND NERIS (972)"}
   {:id "fake-id-9720501M" :name "Lycée pro CHATEAUBOEUF (972)"}
   {:id "fake-id-9720515C" :name "Lycée pro ANDRE ALIKER (972)"}
   {:id "fake-id-9720844K" :name "Lycée pro PLACE D ARMES (972)"}
   {:id "fake-id-9730003R" :name "Lycée pro MAX JOSEPHINE (973)"}
   {:id "fake-id-9730308X" :name "Lycée pro ELIE CASTOR (973)"}
   {:id "fake-id-9730372S" :name "Lycée pro BALATA METIERS DU BTP (973)"}
   {:id "fake-id-9730425Z" :name "Lycée pro RAYMOND TARCY (973)"}
   {:id "fake-id-9740004L" :name "Lycée pro ROCHES MAIGRES (974)"}
   {:id "fake-id-9740015Y" :name "Lycée pro VUE BELLE (974)"}
   {:id "fake-id-9740020D" :name "Lycée pro VICTOR SCHOELCHER (974)"}
   {:id "fake-id-9740082W" :name "Lycée pro JULIEN DE RONTAUNAY (974)"}
   {:id "fake-id-9740472V" :name "Lycée pro PATU DE ROSEMONT (974)"}
   {:id "fake-id-9740479C" :name "Lycée pro AMIRAL LACAZE (974)"}
   {:id "fake-id-9740552G" :name "Lycée pro LEON DE LEPERVANCHE (974)"}
   {:id "fake-id-9740575G" :name "Lycée pro FRANCOIS DE MAHY (974)"}
   {:id "fake-id-9740737H" :name "Lycée pro DE L'HORIZON (974)"}
   {:id "fake-id-9740910W" :name "Lycée pro JEAN PERRIN (974)"}
   {:id "fake-id-9740921H" :name "Lycée pro ISNELLE AMELIN (974)"}
   {:id "fake-id-9740934X" :name "Lycée pro PAUL LANGEVIN (974)"}
   {:id "fake-id-9741308D" :name "Lycée pro SAINT-FRANÇOIS-XAVIER (974)"}
   {:id "fake-id-9750003E" :name "Lycée pro SAINT PIERRE (975)"}
   {:id "fake-id-9760125G" :name "Lycée pro DE KAHANI (976)"}
   {:id "fake-id-9760163Y" :name "Lycée pro DE KAWENI (976)"}
   {:id "fake-id-9760220K" :name "Lycée pro DE DZOUMOGNE (976)"}
   {:id "fake-id-9760363R" :name "Lycée pro DE BANDRELE (976)"}
   {:id "fake-id-9830006P" :name "Lycée pro COMMERCIAL ET HOTELIER (983)"}
   {:id "fake-id-9830306R" :name "Lycée pro PETRO ATTITI (983)"}
   {:id "fake-id-9830460H" :name "Lycée pro AUGUSTIN TY (983)"}
   {:id "fake-id-9830508K" :name "Lycée pro DE LA VALLEE DU TIR (ALP) (983)"}
   {:id "fake-id-9830509L" :name "Lycée pro DE LA FOA (ALP) (983)"}
   {:id "fake-id-9830511N" :name "Lycée pro DE HOUAILOU (ALP) (983)"}
   {:id "fake-id-9830512P" :name "Lycée pro DE POINDIMIE (ALP) (983)"}
   {:id "fake-id-9830514S" :name "Lycée pro DE KONE (ALP) (983)"}
   {:id "fake-id-9830515T" :name "Lycée pro DE KOUMAC (ALP) (983)"}
   {:id "fake-id-9830516U" :name "Lycée pro DE LA ROCHE (ALP) (983)"}
   {:id "fake-id-9830517V" :name "Lycée pro D'OUVEA (ALP) (983)"}
   {:id "fake-id-9840166H" :name "Lycée pro DE UTUROA (984)"}
   {:id "fake-id-9840267T" :name "Lycée pro DE FAAA (984)"}
   {:id "fake-id-9840341Y" :name "Lycée pro DE MAHINA (984)"}
   {:id "fake-id-0351799R" :name "Collège DES FONTAINES (35)"}
   {:id "fake-id-0440013A" :name "Collège BELLEVUE (44)"}
   {:id "fake-id-0440016D" :name "Collège ANNE DE BRETAGNE (44)"}
   {:id "fake-id-0440283U" :name "Collège LIBERTAIRE RUTIGLIANO (44)"}
   {:id "fake-id-0440284V" :name "Collège STENDHAL (44)"}
   {:id "fake-id-0440285W" :name "Collège GASTON SERPETTE (44)"}
   {:id "fake-id-0440287Y" :name "Collège LE HERAULT (44)"}
   {:id "fake-id-0440309X" :name "Collège ROSA PARKS (44)"}
   {:id "fake-id-0440311Z" :name "Collège ERNEST RENAN (44)"}
   {:id "fake-id-0441545R" :name "Collège LA NOE LAMBERT (44)"}
   {:id "fake-id-0441608J" :name "Collège LA DURANTIERE (44)"}
   {:id "fake-id-0440536U" :name "Collège SOPHIE GERMAIN (44)"}
  ])

(defn fetch-teachers-list!
  [school-id]
  (get-users!
    {:on-success
      #(rf/dispatch
        [:write-teachers-list
          (->> % data-from-js-obj
                 (filter (fn [x] (= "teacher" (:quality x))))
                 (filter (fn [x] (= school-id (:school x))))
                 (map (fn [x] {:id (:id x) :lastname (:lastname x)}))
                 vec)])}))
