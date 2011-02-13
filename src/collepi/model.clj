(ns collepi.model
  (:use
     digest.core
     [collepi util]
     clj-gravatar.core
     )
  (:require
     [appengine-magic.services.datastore :as ds]
     )

  (:require
     key
     [clojure.contrib.json :as json]
     [clojure.contrib.io :as io])
  )

(declare get-book)


; rakuten {{{
(def *rakuten-developer-id* nil)
(def *rakuten-affiliate-id* nil)
(def *api-urls*
  {"2009-03-26" "http://api.rakuten.co.jp/rws/2.0/json?"
   "2009-04-15" "http://api.rakuten.co.jp/rws/2.0/json?"
   "2010-03-18" "http://api.rakuten.co.jp/rws/3.0/json?"})
(def *version* "2010-03-18")
(def *operation* "BooksBookSearch")

(defmacro with-rakuten-developer-id [dev-id & body]
  `(binding [*rakuten-developer-id* ~dev-id] ~@body))
(defmacro with-version [version & body]
  `(binding [*version* ~version] ~@body))
(defmacro with-affiliate-id [aff-id & body]
  `(binding [*rakuten-affiliate-id* ~aff-id] ~@body))

(defn- map->url-parameters [m]
  (apply str (interpose "&" (map (fn [[k v]] (str (name k) "=" v)) m)))
  )

(defn- make-url [m]
  (let [data (assoc m :developerId *rakuten-developer-id* :operation *operation* :version *version*)
        data2 (if (nil? *rakuten-affiliate-id*) data (assoc data :affiliateId *rakuten-affiliate-id*))
        ]
    (str (get *api-urls* *version*) (map->url-parameters data2))
    )
  )

(defn rakuten-book-search [& {:as m}]
  (json/read-json (apply str (io/read-lines (make-url m))))
  )
; }}}

; entity
(ds/defentity User [^:key email nickname avatar date])
(ds/defentity Book [^:key isbn title author thumbnail])
(ds/defentity Collection [^:key id book user point read? date])
(ds/defentity History [book user point read? comment date])

(defn collection-id [book user]
  (str1str (str (:isbn book) (:email user)))
  )

;; User
(defn create-user
  ([user]
   (create-user (.getEmail user) (.getNickname user)))
  ([email nickname & {:keys [date] :or {date (now)}}]
   (aif (ds/retrieve User email) it
        (do (ds/save! (User. email nickname (gravatar-image email) date))
          (ds/retrieve User email)))))
(defn get-user [key-or-email] (ds/retrieve User key-or-email))


;; Book
(defn get-book [key-or-isbn] (ds/retrieve Book key-or-isbn))
(defn create-book [isbn & {:keys [static? title author thumbnail] :or {static? false}}]
  (aif (ds/retrieve Book isbn) it
       (if static?
         (do
           (ds/save! (Book. isbn title author thumbnail))
           (ds/retrieve Book isbn)
           )
         (with-rakuten-developer-id
           key/*rakuten-developer-id*
           (let [bookdata (rakuten-book-search :isbn isbn)]
             (when-not (= "NotFound" (-> bookdata :Header :Status))
               (let [item (-> bookdata :Body :BooksBookSearch :Items :Item first)
                     thumbnail* [(:smallImageUrl item)
                                 (:mediumImageUrl item)
                                 (:largeImageUrl item)]
                     ]
                 (ds/save! (Book. isbn (:title item) (:author item) thumbnail*))
                 (ds/retrieve Book isbn)
                 )
               )
             )
           )
         )
       )
  )

;; History
(defn get-history [key] (ds/retrieve History key))

(defn create-history [book user point read? comment & {:keys [date] :or {date (now)}}]
  (ds/retrieve History (ds/save! (History. book user point read? comment date)))
  )

(defn get-histories-from [key val & {:keys [limit page] :or {limit *default-limit*, page 1}}]
  (ds/query :kind History :filter (= key val) :sort [:date] :limit limit :offset (* limit (dec page)))
  )
(def get-histories-from-user (partial get-histories-from :user))
(def get-histories-from-book (partial get-histories-from :book))

;; Collections
(defn get-collection [key-or-id] (ds/retrieve Collection key-or-id))
(defn create-collection [book user & {:keys [point read? date] :or {point 1, read? false, date (now)}}]
  (let [id (collection-id book user)]
    (aif (get-collection id) it
      (get-collection (ds/save! (Collection. id book user point read? date))))
    )
  )

(defn update-collection [book user & {:keys [point read? date point-plus? comment] :or {date (now), point-plus? false}}]
  (let [before (create-collection book user)
        after (get-collection
                (ds/save! (assoc before
                                 :point (if point-plus? (inc (:point col)) (aif point it (:point col)))
                                 :read? (aif read? it (:read? col))
                                 :date date)))]
    (create-history book user (:point after) (:read? after) (aif comment it "") :date date)
    after
    )
  )

(defn get-collections-from [key val & {:keys [sort read? limit page] :or {sort :date, limit *default-limit*, page 1}}]
  (let [offset (* limit (dec page))]
    (if read?
      (ds/query :kind Collection :filter [(= key val) (= :read? read?)] :sort [sort] :limit limit :offset offset)
      (ds/query :kind Collection :filter (= key val) :sort [sort] :limit limit :offset offset)
      )
    )
  )
(def get-collections-from-user (partial get-collections-from :user))
(def get-collections-from-book (partial get-collections-from :book))
