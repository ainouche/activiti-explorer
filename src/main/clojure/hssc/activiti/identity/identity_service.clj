(ns hssc.activiti.identity.identity-service
  (:use [hssc.util :only [def-bean-maker]])
  (:require [clojure.java.io :as jio]
            [clojure.string :as s])
  (:import org.activiti.engine.ActivitiException
           (org.activiti.engine.identity
            GroupQuery
            UserQuery
            User
            Group
            Picture))
  (:gen-class
    :name hssc.activiti.identity.IdentityServiceImpl
    :implements [org.activiti.engine.IdentityService]
    :methods [[getInstance [] org.activiti.engine.IdentityService]]))

(def user-fixtures
  (list
    {:first-name "Gary"
     :last-name "Fredericks"
     :id "gaf26"}
    {:first-name "Kermit"
     :last-name "the Frog"
     :id "kermit"
     :groups ["admin"
              "manager"
              "management"
              "accountancy"
              "engineering"
              "sales"]}
    {:first-name "Fozzie"
     :last-name "Bear"
     :id "fozzie"
     :groups ["user" "accountancy"]}
    {:first-name "Gonzo"
     :last-name "the Great"
     :id "gonzo"
     :groups ["manager" "management" "accountancy" "sales"]}))
(defn user-by-id [id] (first (filter #(= id (:id %)) user-fixtures)))


(def-bean-maker make-user
  User
  email first-name id last-name password)

(def-bean-maker make-group
  Group
  id name type)

; Cannot use defrecord for either of these as they have to implement
; their own count function
(declare add-group-filter)
(deftype GroupQueryImpl [info]
  GroupQuery
  (groupId [self group-id]
    (add-group-filter self #(= % group-id)))
  (groupMember [self group-member-user-id]
    (add-group-filter self (set (:groups (user-by-id group-member-user-id)))))
  (groupName [_ group-name] _)
  (groupNameLike [_ group-name-like] _)
  (groupType [_ group-type] _)
  (orderByGroupId [_] _)
  (orderByGroupName [_] _)
  (orderByGroupType [_] _)
  (asc [_] _)
  (count [self] (clojure.core/count (.list self)))
  (desc [_] _)
  (list [_]
    (for [group-name (distinct (mapcat :groups user-fixtures)),
          :when (every? #(% group-name) (:filters info))]
      (make-group {:id group-name, :name (s/capitalize group-name)})))
  (listPage [self first-result max-results]
    (->>
      self
      .list
      (drop first-result)
      (take max-results)))
  (singleResult [self]
    (let [res (.list self)]
      (cond
        (= 1 (count res))
          (first res)
        (< 1 (count res))
          (throw (new ActivitiException "singleResult called on groupQuery with more than one result!"))))))

(defn- add-group-filter
  [group-query f]
  (new GroupQueryImpl (update-in (.info group-query) [:filters] conj f)))

(declare add-filter)
(deftype UserQueryImpl [info]
  UserQuery
  (memberOfGroup [_ group-id]
    (new UserQueryImpl
         (update-in info :filters conj #(some #{group-id} (:groups %)))))
  (orderByUserEmail [_] _)
  (orderByUserFirstName [_] _)
  (orderByUserId [_] _)
  (orderByUserLastName [_] _)
  (userEmail [self email]
    (add-filter self #(= email (:email %))))
  (userEmailLike [_ email-like] _)
  (userFirstName [self first-name]
    (add-filter self #(= first-name (:first-name %))))
  (userFirstNameLike [_ first-name-like] _)
  (userId [self user-id]
    (add-filter self #(= user-id (:id %))))
  (userLastName [self last-name]
    (add-filter self #(= last-name (:last-name %))))
  (userLastNameLike [_ last-name-like] _)
  (asc [_] _)
  (count [self] (clojure.core/count (.list self)))
  (desc [_] _)
  (list [_]
    (let [{:keys [id]} info]
      (map make-user
        (reduce #(filter %2 %1) user-fixtures (:filters info)))))
  (listPage [self first-result max-results]
    (->> self
      .list
      (drop first-result)
      (take max-results)))
  (singleResult [self]
    (let [res (.list self)]
      (cond
        (= 1 (count res))
          (first res)
        (< 1 (count res))
          (throw (new ActivitiException "singleResult called on userQuery with more than one result!"))))))

(defn- add-filter
  [user-query f]
  (new UserQueryImpl (update-in (.info user-query) [:filters] conj f)))

; Temporary implementations of the IdentityService methods that
; gives good error information.
(defmacro defsn
  [& names]
  (cons 'do
        (for [name names]
          `(defn ~(symbol (str "-" name)) [& args#]
            (throw (new Exception (str "Stub method called: IdentityService#" '~name (pr-str args#))))))))

(defsn
 checkPassword
;createGroupQuery
 createMembership
;createUserQuery
 deleteGroup
 deleteMembership
 deleteUser
 deleteUserAccount
 deleteUserInfo
 getUserAccount
 getUserAccountNames
;getUserInfo
 getUserInfoKeys
;getUserPicture
 newGroup
 newUser
 saveGroup
 saveUser
 setAuthenticatedUserId
 setUserAccount
 setUserInfo
 setUserPicture)

(defn -getUserInfo
  [_ user-id info-key]
  (get-in (user-by-id user-id) [:info info-key]))

(defn resource-bytes
  "Returns a byte-array."
  [path]
  (let [is (jio/input-stream (jio/resource path))]
    (loop [bs [], next-byte (.read is)]
      (if (neg? next-byte)
        (let [ba (make-array Byte/TYPE (count bs))]
          (doseq [[int i] (map vector bs (range))] (aset ba i (.byteValue int)))
          ba)
        (recur (conj bs next-byte) (.read is))))))


(def fozzie-picture
  (new Picture
    (resource-bytes "org/activiti/explorer/images/fozzie.jpg")
    "image/jpg"))

(defn -createGroupQuery [_] (new GroupQueryImpl {}))
(defn -createUserQuery  [_] (new UserQueryImpl  {}))
(defn -getUserPicture   [_ user-id] fozzie-picture)

(def get-instance (constantly (new hssc.activiti.identity.IdentityServiceImpl)))