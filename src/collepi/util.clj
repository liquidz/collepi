(ns collepi.util
  (:require
     [appengine-magic.services.datastore :as ds]
     [appengine-magic.services.user :as du]
     [clojure.contrib.json :as json]
     [clojure.contrib.string :as string]
     )
  (:import
     [java.util TimeZone Calendar]
     [com.google.appengine.api.datastore Key KeyFactory ]
     )
  )

(defmacro aif [expr then & [else]]
  `(let [~'it ~expr] (if ~'it ~then ~else))
  )

(defn key? [obj] (instance? Key obj))
(defn key->str [obj] (if (key? obj) (KeyFactory/keyToString obj)))
(defn str->key [obj]
  (if (string? obj)
    (try (KeyFactory/stringToKey obj)
      (catch Exception e nil)
      )
    )
  )
(defn entity? [obj] (extends? ds/EntityProtocol (class obj)))
(defn get-kind [entity] (.getKind (ds/get-key-object entity)))


(defn remove-extra-key [m] (dissoc m :secret-mail))

(def delete-html-tag (partial string/replace-re #"<.+?>" ""))


(defn convert-map [m]
  (apply
    hash-map
    (interleave
      (map keyword (keys m))
      (map (comp string/trim delete-html-tag) (vals m))))
  )

(defn map-val-map [f m]
  (apply hash-map (mapcat (fn [[k v]] [k (f v)]) m))
  )

(defn- json-conv [obj]
  (cond
    (or (seq? obj) (list? obj)) (map json-conv obj)
    (map? obj) (map-val-map json-conv (remove-extra-key obj))
    ;(key? obj) (key->entity obj) ;(key->str obj)
    :else obj
    )
  )
(defn to-json [obj] (json/json-str (json-conv obj)))


(def parse-int #(Integer/parseInt %))
; 1366 x 768

(defn params->limit-and-page [params]
  (let [limit (aif (:limit params) (parse-int it) *default-limit*)
        page (aif (:page params) (parse-int it) 1)]
    [(if (pos? limit) limit *default-limit*)
     (if (pos? page) page 1)]))




(defn default-response [obj]
  (if (map? obj) obj {:status 200 :headers {"Content-Type" "text/html"} :body obj})
  )


(defn with-session
  ([session res m]
   (assoc (default-response res) :session (conj (aif session it {}) m)))
  ([res m] (with-session nil res m))
  )

(defn with-message
  ([session res msg] (with-session session res {:message msg}))
  ([res msg] (with-message nil res msg))
  )



(defn calendar-format
  ([calendar-obj format-str timezone-str]
   (.setTimeZone calendar-obj (TimeZone/getTimeZone timezone-str))
   (format format-str calendar-obj))
  ([calendar-obj format-str] (calendar-format calendar-obj format-str "Asia/Tokyo")))

(def today-calendar-format (partial calendar-format (Calendar/getInstance)))
(def today (partial today-calendar-format "%1$tY/%1$tm/%1$td"))
(def now (partial today-calendar-format "%1$tY/%1$tm/%1$td %1$tH:%1$tM:%1$tS"))

(defn n-days-ago [n]
  (let [cal (Calendar/getInstance)]
    (.add cal Calendar/DATE (* -1 n))
    (calendar-format cal "%1$tY/%1$tm/%1$td")
    )
  )

(defn time->day [s] (first (string/split #"\s+" s)))

(defn today? [date]
  (= (today) (time->day date))
  )



