(ns collepi.test.controller
  (:use
     [collepi core util model]
     :reload)
  (:use clojure.test ring.mock.request clj-gravatar.core)

  (:require
     [appengine-magic.testing :as ae-testing]
     [appengine-magic.services.datastore :as ds]
     [appengine-magic.services.user :as du]
     [clojure.contrib.json :as json]
     [clojure.contrib.string :as string]
     )
  )

(use-fixtures :each (ae-testing/local-services
                      :all :hook-helper (ae-testing/login "hoge@fuga.com")))


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

; }}}

(defn put-test-data []
  (let [user1 (create-user "hoge@fuga.com" "hoge")
        user2 (create-user "fuga@fuga.com" "fuga")
        item1 (create-item "1" :title "a" :author "b" :smallimage "c" :mediumimage "cc" :largeimage "ccc" :static? true)
        item2 (create-item "2" :title "d" :author "e" :smallimage "f" :mediumimage "ff" :largeimage "fff" :static? true)
        col1 (update-collection item1 user1 :comment "aaa" :read? false :date "YYYY-01-01")
        col2 (update-collection item1 user2 :comment "bbb" :read? true :date "YYYY-01-02")
        col3 (update-collection item2 user1 :comment "ccc" :read? true :date "YYYY-01-03")
        col4 (update-collection item2 user2 :comment "ddd" :read? false :date "YYYY-01-04")]
    [
     (entity->key-str user1) (entity->key-str user2)
     (entity->key-str item1) (entity->key-str item2)
     (entity->key-str col1) (entity->key-str col2)
     (entity->key-str col3) (entity->key-str col4)
     ]
    )
  )

(deftest test-check-login-controller
  (let [res (body->json (testGET "/check/login"))]
    (are [x y] (= x y)
      true (:loggedin res)
      "hoge" (:nickname res)
      (gravatar-image "hoge@fuga.com") (:avatar res)
      )
    )
  )

(deftest test-get-user-controller
  (let [uri "/user?"
        [user-key-str] (put-test-data)]

    (let [res (body->json (testGET uri "key=" user-key-str))]
      (are [x y] (= x y)
        nil (body->json (testGET uri "key=unknown"))

        "hoge" (:nickname res)
        (gravatar-image "hoge@fuga.com") (:avatar res)
        true (nil? (:email res))
        2 (count (:collection res))
        2 (count (:history res))

        "2" (-> res :collection first :item :isbn)
        "1" (-> res :collection second :item :isbn)

        "hoge" (-> res :collection first :user :nickname)
        "hoge" (-> res :collection second :user :nickname)
        nil (-> res :collection first :user :email)

        "ccc" (-> res :history first :comment)
        "aaa" (-> res :history second :comment)
        )
      )
    )
  )

(deftest test-get-item-controller
  (let [uri "/item?"
        [_ _ item-key-str] (put-test-data)]

    (let [res (body->json (testGET uri "key=" item-key-str))]
      (are [x y] (= x y)
        nil (body->json (testGET uri "key=unknown"))

        "1" (:isbn res)
        "a" (:title res)
        "b" (:author res)
        "c" (:smallimage res)
        "cc" (:mediumimage res)
        "ccc" (:largeimage res)

        "fuga" (-> res :collection first :user :nickname)
        "hoge" (-> res :collection second :user :nickname)
        nil (-> res :collection first :user :email)

        "1" (-> res :collection first :item :isbn)
        "1" (-> res :collection second :item :isbn)

        "bbb" (-> res :history first :comment)
        "aaa" (-> res :history second :comment)
        )
      )

    (let [res (body->json (testGET uri "isbn=2"))]
      (are [x y] (= x y)
        nil (body->json (testGET uri "isbn=unknown"))

        "2" (:isbn res)
        "d" (:title res)
        )
      )
    )
  )

(deftest test-get-collection-list
  (let [uri "/collection/list?"]
    (is (zero? (count (body->json (testGET uri)))))

    (put-test-data)

    (are [x y] (= x (count (body->json y)))
      4 (testGET uri)
      1 (testGET uri "limit=1"))

    (let [res (body->json (testGET uri))]
      (are [x y] (= x y)
        "fuga" (-> res first :user :nickname)
        nil (-> res first :user :email)
        "2" (-> res first :item :isbn)
        nil (-> res first :read?)
        false (-> res first :read)

        "hoge" (-> res second :user :nickname)
        nil (-> res second :user :email)
        "2" (-> res second :item :isbn)
        nil (-> res second :read?)
        true (-> res second :read)))
    )
  )

(deftest test-get-collections-from-user
  (let [uri "/collection/user?"
        [user-key-str] (put-test-data)]

    (are [x y] (= x (count (body->json y)))
      2 (testGET uri "key=" user-key-str)
      1 (testGET uri "key=" user-key-str "&limit=1")
      0 (testGET uri "key=unknown")
      )

    (are [x y z] (= x (y (body->json z)))
      2 (comp count :result) (testGET uri "with_total=true&key=" user-key-str)
      1 (comp count :result) (testGET uri "with_total=true&key=" user-key-str "&limit=1")
      2 :total (testGET uri "with_total=true&key=" user-key-str "&limit=1")
      )

    (let [res (body->json (testGET uri "key=" user-key-str))]
      (are [x y] (= x y)
        nil (body->json (testGET uri "key=unknown"))

        "hoge" (-> res first :user :nickname)
        nil (-> res first :user :email)
        "2" (-> res first :item :isbn)
        nil (-> res first :read?)
        true (-> res first :read)

        "hoge" (-> res second :user :nickname)
        nil (-> res second :user :email)
        "1" (-> res second :item :isbn)
        nil (-> res second :read?)
        false (-> res second :read)
        )
      )

    (let [res (body->json (testGET uri "key=" user-key-str "&read=true"))]
      (are [x y] (= x y)
        1 (count res)
        "2" (-> res first :item :isbn)
        )
      )
    )
  )

(deftest test-get-collections-from-item
  (let [uri "/collection/item?"
        [_ _ item-key-str] (put-test-data)]

    (are [x y] (= x (count (body->json y)))
      2 (testGET uri "key=" item-key-str)
      1 (testGET uri "key=" item-key-str "&limit=1")
      0 (testGET uri "key=unknown")
      )

    (are [x y z] (= x (y (body->json z)))
      2 (comp count :result) (testGET uri "with_total=true&key=" item-key-str)
      1 (comp count :result) (testGET uri "with_total=true&key=" item-key-str "&limit=1")
      2 :total (testGET uri "with_total=true&key=" item-key-str "&limit=1")
      )

    (let [res (body->json (testGET uri "key=" item-key-str))]
      (are [x y] (= x y)
        nil (body->json (testGET uri "key=unknown"))

        "fuga" (-> res first :user :nickname)
        nil (-> res first :user :email)
        "1" (-> res first :item :isbn)
        nil (-> res first :read?)
        true (-> res first :read)

        "hoge" (-> res second :user :nickname)
        nil (-> res second :user :email)
        "1" (-> res second :item :isbn)
        nil (-> res second :read?)
        false (-> res second :read)
        )
      )

    (let [res (body->json (testGET uri "key=" item-key-str "&read=true"))]
      (are [x y] (= x y)
        1 (count res)
        "fuga" (-> res first :user :nickname)
        )
      )
    )
  )

(deftest test-get-my-collections
  (let [uri "/my/collection?"]
    (is (zero? (count (body->json (testGET uri)))))
    (is (zero? (count (:result (body->json (testGET uri "with_total=true"))))))
    (is (zero? (:total (body->json (testGET uri "with_total=true")))))

    (put-test-data)

    (are [x y z] (= x (y (body->json z)))
      2 count (testGET uri)
      1 count (testGET uri "limit=1")

      2 (comp count :result) (testGET uri "with_total=true")
      2 :total (testGET uri "with_total=true")
      1 (comp count :result) (testGET uri "with_total=true&limit=1")
      2 :total (testGET uri "with_total=true&limit=1")
      )

    (let [res (body->json (testGET uri))]
      (are [x y] (= x y)
        "hoge" (-> res first :user :nickname)
        "2" (-> res first :item :isbn)
        ))))

(deftest test-get-history-list
  (let [uri "/history/list?"]
    (is (zero? (count (body->json (testGET uri)))))

    (put-test-data)

    (are [x y] (= x (count (body->json y)))
      4 (testGET uri)
      1 (testGET uri "limit=1"))

    (let [res (body->json (testGET uri))]
      (are [x y] (= x y)
        "ddd" (-> res first :comment)
        "ccc" (-> res second :comment)

        "fuga" (-> res first :user :nickname)
        "hoge" (-> res second :user :nickname)
        nil (-> res first :user :email)

        "2" (-> res first :item :isbn)
        "2" (-> res second :item :isbn)

        nil (-> res first :read?)
        nil (-> res second :read?)

        false (-> res first :read)
        true (-> res second :read)
        )
      )
    )
  )

(deftest test-get-histories-from-user
  (let [uri "/history/user?"
        [user-key-str] (put-test-data)]

    (are [x y] (= x (count (body->json y)))
      0 (testGET uri "key=unknown")
      2 (testGET uri "key=" user-key-str)
      1 (testGET uri "key=" user-key-str "&limit=1")
      )

    (let [res (body->json (testGET uri "key=" user-key-str))]
      (are [x y] (= x y)
        nil (body->json (testGET uri "key=unknown"))

        "ccc" (-> res first :comment)
        "aaa" (-> res second :comment)

        "hoge" (-> res first :user :nickname)
        "hoge" (-> res second :user :nickname)
        nil (-> res first :user :email)

        "2" (-> res first :item :isbn)
        "1" (-> res second :item :isbn)

        nil (-> res first :read?)
        nil (-> res second :read?)
        true (-> res first :read)
        false (-> res second :read)
        )
      )
    )
  )

(deftest test-get-histories-from-item
  (let [uri "/history/item?"
        [_ _ item-key-str] (put-test-data)
        ]

    (are [x y] (= x (count (body->json y)))
      0 (testGET uri "key=unknown")
      2 (testGET uri "key=" item-key-str)
      1 (testGET uri "key=" item-key-str "&limit=1")
      )

    (let [res (body->json (testGET uri "key=" item-key-str))]
      (are [x y] (= x y)
        nil (body->json (testGET uri "key=unknown"))

        "bbb" (-> res first :comment)
        "aaa" (-> res second :comment)

        "fuga" (-> res first :user :nickname)
        "hoge" (-> res second :user :nickname)
        nil (-> res first :user :email)

        "1" (-> res first :item :isbn)
        "1" (-> res second :item :isbn)

        nil (-> res first :read?)
        nil (-> res second :read?)
        true (-> res first :read)
        false (-> res second :read)
        )
      )
    )
  )

(deftest test-get-my-histories
  (let [uri "/my/history?"]
    (is (zero? (count (body->json (testGET uri)))))
    (is (zero? (count (:result (body->json (testGET uri "with_total=true"))))))
    (is (zero? (:total (body->json (testGET uri "with_total=true")))))

    (put-test-data)

    (are [x y z] (= x (y (body->json z)))
      2 count (testGET uri)
      1 count (testGET uri "limit=1")

      2 (comp count :result) (testGET uri "with_total=true")
      2 :total (testGET uri "with_total=true")
      1 (comp count :result) (testGET uri "with_total=true&limit=1")
      2 :total (testGET uri "with_total=true&limit=1")
      )

    (let [res (body->json (testGET uri))]
      (are [x y] (= x y)
        "hoge" (-> res first :user :nickname)
        "2" (-> res first :item :isbn)))))

(deftest test-update-collection-controller
  (let [uri "/update/collection"]
    (is (not (body->json (testPOST {:isbn ""} uri))))
    (is (body->json (testPOST {:isbn "4001156768"} uri)))

    (let [user-key (ds/get-key-object (get-user "hoge@fuga.com"))
          item-key (ds/get-key-object (get-item "4001156768"))]

      (is (not (nil? user-key)))
      (is (not (nil? item-key)))

      (let [item (get-item "4001156768")]
        (are [x y] (= x y)
          "4001156768" (:isbn item)
          false (string/blank? (:title item))
          false (string/blank? (:author item))
          false (string/blank? (:smallimage item))
          false (string/blank? (:mediumimage item))
          false (string/blank? (:largeimage item))))

      (let [col (get-collection-list)]
        (are [x y] (= x y)
          1 (count col)
          user-key (-> col first :user)
          item-key (-> col first :item)
          1 (-> col first :point)
          false (-> col first :read?)
          true (-> col first :date today?)))

      (let [his (get-history-list)]
        (are [x y] (= x y)
          1 (count his)
          user-key (-> his first :user)
          item-key (-> his first :item)
          1 (-> his first :point)
          false (-> his first :read?)
          nil (-> his first :comment)
          true (-> his first :date today?)))

      (testPOST {:isbn "4001156768" :comment "hello" :read "true"} uri)

      (let [col (get-collection-list)]
        (are [x y] (= x y)
          1 (count col)
          user-key (-> col first :user)
          item-key (-> col first :item)
          2 (-> col first :point)
          true (-> col first :read?)
          true (-> col first :date today?)))

      (let [his (get-history-list)]
        (are [x y] (= x y)
          2 (count his)
          user-key (-> his first :user)
          item-key (-> his first :item)
          2 (-> his first :point)
          true (-> his first :read?)
          "hello" (-> his first :comment)
          true (-> his first :date today?)))
      )
    )
  )

(deftest test-get-comment-list-controller
  (let [uri "/comment/list"]
    (is (zero? (count (body->json (testGET uri)))))

    (let [[user-key-str _ item-key-str] (put-test-data)
          ]
      (let [res (body->json (testGET uri))]
        (are [x y] (= x y)
          4 (count res)
          "ddd" (-> res first :comment)
          "2" (-> res first :item :isbn)
          "fuga" (-> res first :user :nickname)))

      ; create history without comment
      (update-collection (get-item (str->key item-key-str)) (get-user (str->key user-key-str)))

      (let [res (body->json (testGET uri))]
        (are [x y] (= x y)
          4 (count res)
          "ddd" (-> res first :comment)
          "2" (-> res first :item :isbn)
          "fuga" (-> res first :user :nickname)))
      )
    )
  )
