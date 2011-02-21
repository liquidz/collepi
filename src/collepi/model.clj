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
     [clojure.contrib.io :as io]
     [clojure.contrib.logging :as log]
     )
  )

(declare get-item)


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
;(ds/defentity Item [^:key isbn title author thumbnail])
(ds/defentity Item [^:key isbn title author smallimage mediumimage largeimage])
(ds/defentity Collection [^:key id item user point read? secret? date])
(ds/defentity History [item user point read? comment date])

(defn collection-id [item user]
  (sha1str (str (:isbn item) (:email user))))

(defmacro query-collection [& {:keys [filter limit page]}]
  `(ds/query :kind Collection :filter ~filter :sort [[:date :desc] [:point :desc]]
             :limit ~limit :offset (if (and ~limit ~page) (* ~limit (dec ~page)))))
(defmacro query-history [& {:keys [filter limit page]}]
  `(ds/query :kind History :filter ~filter :sort [[:date :desc] [:point :desc]]
             :limit ~limit :offset (if (and ~limit ~page) (* ~limit (dec ~page)))))

;; User

(defn create-user
  ([user]
   (create-user (.getEmail user) (.getNickname user)))
  ([email nickname & {:keys [date] :or {date (now)}}]
   (aif (ds/retrieve User email) it
        (do (ds/save! (User. email nickname (gravatar-image email) date))
          (ds/retrieve User email)))))
(defn get-user [key-or-email]
  (when key-or-email (ds/retrieve User key-or-email)))


;; Item
(defn get-item [key-or-isbn] (when key-or-isbn (ds/retrieve Item key-or-isbn)))
(defn create-item [isbn & {:keys [static? title author smallimage mediumimage largeimage] :or {static? false}}]
  (aif (ds/retrieve Item isbn) it
       (if static?
         (do
           (ds/save! (Item. isbn title author smallimage mediumimage largeimage))
           (ds/retrieve Item isbn)
           )
         (with-rakuten-developer-id
           key/*rakuten-developer-id*
           (let [itemdata (rakuten-book-search :isbn isbn)]
             (when-not (= "NotFound" (-> itemdata :Header :Status))
               (let [item (-> itemdata :Body :BooksBookSearch :Items :Item first)]
                 (ds/save! (Item. isbn (:title item) (:author item)
                                  (:smallImageUrl item)
                                  (:mediumImageUrl item)
                                  (:largeImageUrl item)))
                 (ds/retrieve Item isbn)
                 )
               )
             )
           )
         )
       )
  )

(def *sort* '([:date :desc] [:point :desc]))

;; History
(defn get-history [key] (when key (ds/retrieve History key)))
(defn get-history-list [& {:keys [limit page] :or {limit *default-limit*, page 1}}]
  (query-history :limit limit :page page))

(defn create-history [item user point read? comment & {:keys [date] :or {date (now)}}]
  (ds/retrieve History (ds/save! (History. item user point read? comment date)))
  )

(defn get-histories-from [key val & {:keys [limit page] :or {limit *default-limit*, page 1}}]
  (let [val* (if (entity? val) (ds/get-key-object val) val)]
    (query-history :filter (= key val*) :limit limit :page page)))
(def get-histories-from-user (partial get-histories-from :user))
(def get-histories-from-item (partial get-histories-from :item))

;; Collections
(defn get-collection [key-or-id] (when key-or-id (ds/retrieve Collection key-or-id)))
(defn get-collection-list [& {:keys [limit page] :or {limit *default-limit*, page 1}}]
  (query-collection :limit limit :page page))
(defn create-collection [item user & {:keys [point read? secret? date] :or {point 1, read? false, secret? false, date (now)}}]
  (let [id (collection-id item user)]
    (aif (get-collection id) it
      (get-collection (ds/save! (Collection. id item user point read? secret? date))))))

(defn update-collection [item user & {:keys [point read? date point-plus? comment] :or {date (now), point-plus? false, comment nil}}]
  (let [id (collection-id item user)
        first? (nil? (if id (get-collection id)))
        before (create-collection item user)
        after (get-collection
                (ds/save! (assoc before
                                 :point (if point-plus?
                                          (if first? (:point before) (inc (:point before)))
                                          (aif point it (:point before)))
                                 :read? (aif read? it (:read? before))
                                 :date date)))]
    (create-history item user (:point after) (:read? after) comment :date date)
    after
    )
  )

(defn get-collections-from [key val & {:keys [read? limit page] :or {limit *default-limit*, page 1}}]
  (let [offset (* limit (dec page))
        val* (if (entity? val) (ds/get-key-object val) val)]
    (if (not (nil? read?))
      (query-collection :filter [(= key val*) (= :read? read?)] :limit limit :page page)
      (query-collection :filter (= key val*) :limit limit :page page))))

(def get-collections-from-user (partial get-collections-from :user))
(def get-collections-from-item (partial get-collections-from :item))

(defn get-comment-list [& {:keys [limit page] :or {limit *default-limit*, page 1}}]
  (take limit (drop (* limit (dec page)) (remove #(nil? (:comment %)) (query-history)))))
