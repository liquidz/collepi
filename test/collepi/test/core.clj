(ns collepi.test.core
  (:use
     [collepi core model util]
     :reload)
  (:use clojure.test ring.mock.request
     clj-gravatar.core)

  (:require
     [appengine-magic.testing :as ae-testing]
     [appengine-magic.services.datastore :as ds]
     [appengine-magic.services.user :as du]
     [clojure.contrib.json :as json]
     [clojure.contrib.string :as string]
     )

  (:import
     [com.google.appengine.tools.development.testing
      LocalServiceTestConfig
      LocalServiceTestHelper
      LocalUserServiceTestConfig]
     )
  )

(defmacro with-test-user [mail & body]
  `(let [domain# (second (string/split #"@" ~mail))
         helper# (.. (LocalServiceTestHelper.
                       (into-array LocalServiceTestConfig [(LocalUserServiceTestConfig.)]))
                   (setEnvIsLoggedIn true) (setEnvEmail ~mail) (setEnvAuthDomain domain#))]
     (.setUp helper#)
     (do ~@body)
     ; tearDown effects another tearDown in ae-testing/local-services
     ;(.tearDown helper#)
     )
  )

(use-fixtures :each (ae-testing/local-services :all))

; private {{{
(defn- body->json [res] (-> res :body json/read-json))
(defn- testGET [& urls] (app-handler (request :get (apply str urls))))
(defn- testGET-with-session [res & urls]
  (let [ring-session (first (get (:headers res) "Set-Cookie"))]
    (app-handler (header (request :get (apply str urls)) "Cookie" ring-session))
    )
  )
(defn- testPOST [data & urls]
  (app-handler (body (request :post (apply str urls)) data)))
(defn- testPOST-with-session [res data & urls]
  (let [ring-session (first (get (:headers res) "Set-Cookie"))]
    (app-handler (header (body (request :post (apply str urls)) data) "Cookie" ring-session))
    )
  )

(defn- entity->key-str [e] (key->str (ds/get-key-object e)))
; }}}

(deftest test-with-test-user
  (is (not (du/user-logged-in?)))
  (is (nil? (du/current-user)))
  (with-test-user
    "hoge@fuga.com"
    (are [x y] (= x y)
      true (du/user-logged-in?)
      "hoge@fuga.com" (.getEmail (du/current-user))
      "hoge" (.getNickname (du/current-user))
      )
    )
  )

;; User
(deftest test-create-user
  (with-test-user
    "hoge@fuga.com"
    (let [user  (create-user (du/current-user))
          avatar (gravatar-image "hoge@fuga.com")
          ]
      (are [x y] (= x y)
        "hoge@fuga.com" (:email user)
        "hoge" (:nickname user)
        avatar (:avatar user)
        )
      )
    )
  (let [user (create-user "neko@fuga.com" "neko")
        avatar (gravatar-image "neko@fuga.com")]
    (are [x y] (= x y)
      "neko@fuga.com" (:email user)
      "neko" (:nickname user)
      avatar (:avatar user)
      )
    )
  )

(deftest test-get-user
  (let [user (create-user "hoge@fuga.com" "hoge")
        key (ds/get-key-object user)]
    (are [x y] (= x y)
      user (get-user "hoge@fuga.com")
      user (get-user key)
      )
    )
  )

;; Book
(deftest test-create-book
  (let [book1 (create-book "4001156768")
        book2 (create-book "123" :static? true :title "aa" :author "bb" :thumbnail "cc")]
    (are [x y] (= x y)
      false (string/blank? (:title book1))
      false (string/blank? (:author book1))
      false (nil? (:thumbnail book1))

      "aa" (:title book2)
      "bb" (:author book2)
      "cc" (:thumbnail book2)
      )
    )
  )

(deftest test-get-book
  (let [isbn "4001156768"
        key (ds/get-key-object (create-book isbn :static? true))]

    (are [x y] (= x y)
      true (nil? (get-book "unknown"))
      false (nil? (get-book isbn))
      false (nil? (get-book key))
      )
    )
  )




;; Collection
(deftest test-create-collection
  (let [user (create-user "hoge@fuga.com" "hoge")
        user-key (ds/get-key-object user)
        book (create-book "1" :static? true)
        book-key (ds/get-key-object book)
        col (create-collection book user)
        ]
    (are [x y] (= x y)
      user-key (:user col)
      book-key (:book col)
      1 (:point col)
      false (:read? col)
      true (today? (:date col))
      )
    )
  )

(deftest test-get-collections-from-user
  (let [
        user1 (create-user "hoge@fuga.com" "hoge")
        user2 (create-user "fuga@fuga.com" "fuga")
        book1 (create-book "1" :static? true)
        book2 (create-book "1" :static? true)
        ]
    (create-collection book1 user1 :date "YYYY-01-01")
    (create-collection book2 user1 :date "YYYY-01-02")
    (create-collection book2 user2)

    (are [x y] (= x y)
      2 (count (get-collections-from-user user1))
      1 (count (get-collections-from-user user1 :limit 1))
      (:book (first (get-collections-from-user user1)))
      )
    )
  )

(deftest test-update-collection
  (let [user (create-user "hoge@fuga.com" "hoge")
        book1 (create-book "1" :static? true)
        book1-key (ds/get-key-object book1)
        book2 (create-book "2" :static? true)
        ]

    (let [col (update-collection book1 user :date "dummy")]
      (are [x y] (= x y)
        book1-key (:book col)
        1 (:point col)
        false (:read? col)
        dummy (:date col)

        1 (count (get-collections-from-user user))
        )
      )

    (let [col (update-collection book1 user :point-plus? true :read? true)]
      (are [x y] (= x y)
        2 (:point col)
        true (:read? col)
        true (today? (:date col))

        1 (count (get-collections-from-user user))
        )
      )

    (update-collection book2 user)
    (is (= 2 (get-collections-from-user user)))
    )
  )

;; History
(deftest test-create-history
  (let [user (create-user "hoge@fuga.com" "hoge")
        user-key (ds/get-key-object user)
        book (create-book "1" :static? true)
        book-key (ds/get-key-object book)
        history (create-history book user 1 false "hello")]
    (are [x y] (= x y)
      user-key (:user history)
      book-key (:book history)
      1 (:point history)
      false (:read? history)
      true (today? (:date history))
      )
    )
  )

(deftest get-histories-from)




;; Controller Test

;(deftest test-check-login
;  (with-test-user
;    "hoge@fuga.com"
;    (let [res (body->json (testGET "/check/login"))]
;      (are [x y] (= x y)
;        true (:loggedin res)
;        "hoge" (:nickname res)
;        (gravatar-image "hoge@fuga.com") (:avatar res)
;        )
;      )
;    )
;  )
;
;(deftest test-get-recent-user-ranks
;  (let [uri "/recent/user/rank?"
;        user1 (create-user "hoge@fuga.com" "hoge")
;        user2 (create-user "fuga@fuga.com" "fuga")
;        book1 (create-book "1" :static? true)
;        book2 (create-book "2" :static? true)
;        book3 (create-book "3" :static? true)
;        ]
;    (set-rank book1 user1 1 "") (set-rank book2 user1 2 "") (set-rank book3 user1 3 "")
;    (set-rank book2 user2 1 "" :date "1900-01-01")
;
;    (are [x y] (= x y)
;      1 (count (body->json (testGET uri "limit=1")))
;      book1 (-> (testGET uri "limit=1") body->json first first :book get-book)
;      book2 (-> (testGET uri "limit=1&page=2") body->json first first :book get-book)
;      )
;
;    (let [res (body->json (testGET uri))]
;      (are [x y] (= x y)
;        2 (count res)
;        3 (count (first res))
;        1 (count (second res))
;
;        book1 (-> res first first :book get-book)
;        user1 (-> res first first :user get-user)
;        1 (-> res first first :rank)
;        2 (-> res first second :rank)
;        3 (-> res first (nth 2) :rank)
;        book2 (-> res second first :book get-book)
;        user2 (-> res second first :user get-user)
;        1 (-> res second first :rank)
;        )
;      )
;    )
;  )
;(deftest test-get-user-rank
;  (let [uri "/user/rank?"
;        user (create-user "hoge@fuga.com" "hoge")
;        user-key-str (key->str (ds/get-key-object user))
;        book1 (create-book "1" :static? true)
;        book2 (create-book "2" :static? true)
;        book3 (create-book "3" :static? true)]
;    (set-rank book1 user 1 "" :date "YYYY-01-01")
;    (set-rank book2 user 2 "" :date "YYYY-01-02")
;    (set-rank book3 user 2 "" :date "YYYY-01-03")
;
;    ; count test
;    (are [x y] (= x (count (body->json y)))
;      3 (testGET uri "key=" user-key-str)
;      1 (testGET uri "key=" user-key-str "&limit=1")
;      )
;
;    (let [res (body->json (testGET uri "key=" user-key-str))]
;      (are [x y] (= x y)
;        1 (-> res first :rank)
;        2 (-> res second :rank)
;        2 (-> res (nth 2) :rank)
;
;        book1 (-> res first :book get-book)
;        book3 (-> res second :book get-book)
;        book2 (-> res (nth 2) :book get-book)
;        )
;      )
;    )
;  )
;
;(deftest test-register-book
;  (with-test-user
;    "hoge@fuga.com"
;    (let [uri "/register/book?"
;          user (create-user (du/current-user))]
;      (create-book "123" :static? true)
;
;      (is (body->json (testGET uri "isbn=4001156768")))
;      (is (body->json (testGET uri "isbn=404275001X")))
;      (is (body->json (testGET uri "isbn=123")))
;      (is (not (body->json (testGET uri))))
;
;      (are [x y] (= x y)
;        3 (count (get-ranks-from-user user))
;        true (every? #(= 4 (:rank %)) (get-ranks-from-user user))
;        )
;      )
;    )
;  )
;
;(deftest test-set-book-rank
;  (with-test-user
;    "hoge@fuga.com"
;    (let [uri "/set/rank"
;          user (create-user (du/current-user))
;          book1 (create-book "1" :static? true)i
;          book2 (create-book "2" :static? true)
;          book3 (create-book "3" :static? true)i
;          book4 (create-book "4" :static? true)]
;
;      (is (body->json (testPOST {:count "3" :isbn1 "1" :pop1 "aa" :isbn2 "2" :pop2 "bb" :isbn3 "3" :pop3 "cc"} uri)))
;      (is (body->json (testPOST {:count "1" :isbn1 "4" :pop1 ""} uri)))
;      (is (not (body->json (testPOST {:count "1" :isbn1 "5" :pop1 ""} uri))))
;
;      (let [res (get-ranks-from-user user)]
;        (are [x y] (= x y)
;          4 (count res)
;          1 (:rank (first res))
;          book4 (-> res first :book get-book)
;          book2 (-> res second :book get-book)
;          book3 (-> res (nth 2) :book get-book)
;          book1 (-> res (nth 3) :book get-book)
;          4 (-> res (nth 3) :rank)
;          )
;        )
;      )
;    )
;  )
;
;(deftest test-search
;  (create-book "1" :static? true :title "hello" :author "neko")
;  (create-book "2" :static? true :title "world" :author "neko")
;  (create-book "3" :static? true :title "hoge" :author "fuga")
;
;  (let [uri "/search?"]
;    (are [x y] (= x y)
;      3 (count (body->json (testGET uri "keyword=")))
;      2 (count (body->json (testGET uri "keyword=neko")))
;      1 (count (body->json (testGET uri "keyword=neko&limit=1")))
;      3 (count (body->json (testGET uri "keyword=e")))
;      )
;    )
;  )
;
;(deftest test-comment
;  (with-test-user
;    "hoge@fuga.com"
;    (let [uri "/put/comment"
;          user (create-user "fuga@fuga.com" "fuga")
;          user2 (create-user (du/current-user))
;          book (create-book "1" :static? true)
;          rank (create-rank book user 1 "")
;          rank-key-str (-> rank ds/get-key-object key->str)]
;
;      (is (body->json (testPOST {:key rank-key-str :text "helloworld"} uri)))
;      (is (not (body->json (testPOST {:key "unknown" :text "helloworld"} uri))))
;
;      (let [res (get-comments-from-user user2)]
;        (are [x y] (= x y)
;          1 (count res)
;          "helloworld" (-> res first :text)
;          (:isbn book) (-> res first :isbn)
;          user2 (-> res first :user get-user)
;          user (-> res first :rank get-rank :user get-user)
;          book (-> res first :rank get-rank :book get-book)
;          )
;        )
;      )
;    )
;  )
;
;(deftest test-get-comments
;  (let [uri "/comment?"
;        user1 (create-user "hoge@fuga.com" "hoge")
;        user2 (create-user "fuga@fuga.com" "fuga")
;        book (create-book "1" :static? true)
;        rank (create-rank book user1 1 "")
;        rank-key-str (-> rank ds/get-key-object key->str)]
;    (create-comment rank user1 "helloworld" :date "YYYY-01-01")
;    (create-comment rank user2 "nekoinu" :date "YYYY-01-02")
;
;    (let [res (body->json (testGET uri "key=" rank-key-str))]
;      (are [x y] (= x y)
;        2 (count res)
;        1 (count (body->json (testGET uri "key=" rank-key-str "&limit=1")))
;
;        "nekoinu" (-> res first :text)
;        "helloworld" (-> res second :text)
;        )
;      )
;    )
;  )
;
;(deftest test-recent-comments
;  (let [uri "/recent/comment"
;        user (create-user "hoge@fuga.com" "hoge")
;        book (create-book "1" :static? true)
;        rank (create-rank book user 1 "")]
;    (create-comment rank user "aa" :date "YYYY-01-01")
;    (create-comment rank user "bb" :date "YYYY-01-02")
;    (create-comment rank user "cc" :date "YYYY-01-03")
;
;    (are [x y] (= x y)
;      3 (count (body->json (testGET uri)))
;      1 (count (body->json (testGET uri "&limit=1")))
;      "cc" (:text (first (body->json (testGET uri))))
;      "bb" (:text (first (body->json (testGET uri "&limit=1&page=2"))))
;      "aa" (:text (first (body->json (testGET uri "&limit=1&page=3"))))
;      )
;    )
;  )
;
;  ; book ranking (future)
;
