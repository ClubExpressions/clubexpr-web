(ns club.subs
  (:require [re-frame.core :as rf]
            [reagent.core :refer [as-element]]
            [reagent.ratom :refer [make-reaction]]
            [clojure.walk :refer [keywordize-keys]]
            [club.utils :refer [groups-option
                                series-option
                                data-from-js-obj
                                past-work?
                                future-work?
                                works-comparator-rev]]
            [club.expr :refer [infix-rendition reified-expressions]]
            [club.db :refer [get-users!
                             fetch-teachers-list!
                             init-groups-data!
                             fetch-groups-data!
                             fetch-series-data!
                             fetch-works-teacher!
                             fetch-scholar-work!
                             fetch-works-scholar!]]))

; Placeholder for future translation mechanism
(defn t [[txt]] txt)

; Layer 1

(rf/reg-sub
 :current-page
 (fn [db]
   (:current-page db)))

(rf/reg-sub
 :authenticated
 (fn [db]
   (:authenticated db)))

(rf/reg-sub
 :attempt-code
 (fn [db]
   (:attempt-code db)))

(rf/reg-sub
 :landing-game-code
 (fn [db]
   "(Opposé (Quotient (Diff (Produit a (Racine b)) (Puissance (Inverse c) d)) (Carré (Somme x y z))))"
   ))

(rf/reg-sub
 :profile-quality
 (fn [db]
   (-> db :profile-page :quality)))

(rf/reg-sub
 :profile-school
 (fn [db]
   (-> db :profile-page :school)))

(rf/reg-sub
 :profile-teacher
 (fn [db]
   (-> db :profile-page :teacher)))

(rf/reg-sub
 :profile-lastname
 (fn [db]
   (-> db :profile-page :lastname)))

(rf/reg-sub
 :profile-firstname
 (fn [db]
   (-> db :profile-page :firstname)))

(rf/reg-sub
 :groups-groups
 (fn [db [_ scholar-id]]
   (-> db :groups-page (get scholar-id) :groups)))

(rf/reg-sub
 :current-series-id
 (fn [db]
   (-> db :current-series-id)))

(rf/reg-sub
 :current-series
 (fn [db]
   (-> db :current-series)))

(rf/reg-sub
 :editing-series
 (fn [db]
   (-> db :editing-series)))

(rf/reg-sub
 :series-title
 (fn [db]
   (-> db :current-series :title)))

(rf/reg-sub
 :series-desc
 (fn [db]
   (-> db :current-series :desc)))

(rf/reg-sub
 :series-exprs
 (fn [db]
   (-> db :current-series :exprs)))

(rf/reg-sub
 :series-filtering-filters
 (fn [db]
   (-> db :series-filtering :filters)))

(rf/reg-sub
 :series-filtering-nature
 (fn [db]
   (-> db :series-filtering :nature)))

(rf/reg-sub
 :series-filtering-depth
 (fn [db]
   (-> db :series-filtering :depth)))

(rf/reg-sub
 :series-filtering-nb-ops
 (fn [db]
   (-> db :series-filtering :nb-ops)))

(rf/reg-sub
 :series-filtering-prevented-ops
 (fn [db]
   (-> db :series-filtering :prevented-ops)))

(rf/reg-sub
 :scholar-working
 (fn [db]
   (-> db :scholar-working)))

(rf/reg-sub
 :scholar-work-id
 (fn [db]
   (-> db :scholar-work-id)))

(rf/reg-sub
 :scholar-work-user-attempt
 (fn [db]
   (-> db :scholar-work :attempt)))

; Layer 2

(rf/reg-sub
  :profile-school-pretty
  (fn [query-v _]
     (rf/subscribe [:profile-school]))
  (fn [profile-school query-v _]
    (case profile-school
      "fake-id-no-school" (t ["Aucun établissement"])
      (->> (club.db/get-schools!)
           (filter #(= profile-school (:id %)))
           first
           :name))))

(rf/reg-sub-raw
  :profile-teachers-list
  (fn [app-db _]
    (let [school-id (get-in @app-db [:profile-page :school])
          _ (fetch-teachers-list! school-id)]
      (make-reaction
        (fn [] (get-in @app-db [:profile-page :teachers-list] []))
        :on-dispose #(do)))))

(rf/reg-sub
  :profile-teacher-pretty
  (fn [query-v _]
    [(rf/subscribe [:profile-teacher])
     (rf/subscribe [:profile-teachers-list])])
  (fn [[profile-teacher profile-teachers-list] query-v _]
    (case profile-teacher
      "no-teacher" (t ["Pas de professeur"])
      (->> profile-teachers-list
           (filter #(= profile-teacher (:id %)))
           first
           :lastname))))

(rf/reg-sub-raw
  :groups-page
  (fn [app-db _]
    (let [_ (init-groups-data!)
          _ (fetch-groups-data!)]
      (make-reaction
        (fn [] (get-in @app-db [:groups-page] []))
        :on-dispose #(do)))))

(rf/reg-sub
  :groups
  (fn [query-v _]
    (rf/subscribe [:groups-page]))
  (fn [groups-page query-v _]
    (sort (reduce #(into %1 (-> %2 second :groups)) #{} groups-page))))

(rf/reg-sub
  :groups-value
  (fn [[_ scholar-id] _]
    (rf/subscribe [:groups-groups scholar-id]))
  (fn [groups query-v _]
    (vec (map groups-option (sort groups)))))

(rf/reg-sub
  :groups-for-select
  (fn [query-v _]
    (rf/subscribe [:groups]))
  (fn [groups query-v _]
    (vec (map groups-option (sort groups)))))

(rf/reg-sub-raw
  :series-page
  (fn [app-db _]
    (let [_ (fetch-series-data!)]
      (make-reaction
        (fn [] (:series-page @app-db))
        :on-dispose #(do)))))

(rf/reg-sub
  :series-for-select
  (fn [query-v _]
    (rf/subscribe [:series-page]))
  (fn [series-page query-v _]
    (vec (map series-option series-page))))

(defn wrap-expr
  [sorted-expr]
  (let [lisp (:content sorted-expr)
        rank (:rank sorted-expr)]
    {:content (as-element
                [:span
                  ; src is needed for the lisp src to be fetched back by the
                  ; :series-exprs-sort event handler
                  {:src lisp
                   :on-double-click #(rf/dispatch [:series-exprs-delete rank])}
                  (infix-rendition lisp true)])}))

(rf/reg-sub
  :series-exprs-with-content-key
  (fn [query-v _]
    (rf/subscribe [:series-exprs]))
  (fn [exprs query-v _]
    (map wrap-expr exprs)))

(rf/reg-sub
  :series-filtered-exprs
  (fn [query-v _]
    (rf/subscribe [:series-filtering-filters]))
  (fn [filters query-v _]
    (let [f (apply every-pred (vals filters))]
      (filter f reified-expressions))))

(rf/reg-sub-raw
  :works-data-teacher
  (fn [app-db _]
    (let [_ (fetch-works-teacher!)]
      (make-reaction
        (fn [] (:works-teacher-page @app-db))
        :on-dispose #(do)))))

(rf/reg-sub
  :works-data-teacher-sorted
  (fn [query-v _]
    (rf/subscribe [:works-data-teacher]))
  (fn [works-data-teacher query-v _]
    (let [past-works   (filter past-work? works-data-teacher)
          future-works (filter future-work? works-data-teacher)]
      [(sort works-comparator-rev past-works)
       (sort works-comparator-rev future-works)]
      )))

(rf/reg-sub-raw
  :works-data-scholar
  (fn [app-db _]
    (let [_ (fetch-works-scholar!)]
      (make-reaction
        (fn [] (:works-scholar-page @app-db))
        :on-dispose #(do)))))

(rf/reg-sub
  :works-data-scholar-sorted
  (fn [query-v _]
    (rf/subscribe [:works-data-scholar]))
  (fn [works-data-scholar query-v _]
    (let [past-works   (filter past-work? works-data-scholar)
          future-works (filter future-work? works-data-scholar)]
      [(sort works-comparator-rev past-works)
       (sort works-comparator-rev future-works)]
      )))

(rf/reg-sub-raw
  :scholar-work
  (fn [app-db [_ work-id]]
    (let [_ (fetch-scholar-work! work-id)]
      (make-reaction
        (fn [] (:scholar-work @app-db))
        :on-dispose #(do)))))
