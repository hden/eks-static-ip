(ns eks-static-ip.core-test
  (:require [clojure.test :refer :all]
            [datascript.core :as datascript]
            [eks-static-ip.core :as core]))

(defrecord FakeAWSClient [store f]
  core/Invokable
  (invoke [_ req]
    (swap! store conj req)
    (f req)))

(defn create-fake-client
  ([] (create-fake-client (atom [])))
  ([store] (create-fake-client store identity))
  ([store f] (FakeAWSClient. store f)))

(defn create-db [coll]
  (let [conn (datascript/create-conn)]
    (datascript/transact! conn coll)
    @conn))

(deftest describe-instances
  (testing "invokation"
    (let [calls (atom [])
          client (create-fake-client calls)]
      (core/describe-instances client "foo" ["bar"])
      (is (= [{:op :DescribeInstances
               :request {:MaxResults 1000
                         :Filters [{:Name "tag:foo", :Values ["bar"]}]}}]
             @calls))))

  (testing "with public-ip"
    (let [calls (atom [])
          client (create-fake-client calls (constantly {:Reservations [{:Instances [{:InstanceId "id"
                                                                                     :PublicIpAddress "ip"}]}]}))]
      (is (= [{:instances/id "id" :instances/public-ip "ip"}]
             (core/describe-instances client "foo" ["bar"])))))

  (testing "without public-ip"
    (let [calls (atom [])
          client (create-fake-client calls (constantly {:Reservations [{:Instances [{:InstanceId "id"}]}]}))]
      (is (= [{:instances/id "id"}]
             (core/describe-instances client "foo" ["bar"]))))))

(deftest describe-addresses
  (testing "invokation"
    (let [calls (atom [])
          client (create-fake-client calls (constantly {:Addresses [{:PublicIp "ip" :AllocationId "id"}]}))
          resp (core/describe-addresses client "foo" ["bar"])]
      (is (= [{:op :DescribeAddresses
               :request {:MaxResults 1000
                         :Filters [{:Name "tag:foo" :Values ["bar"]}]}}]
             @calls))

      (is (= [{:addresses/allocation-id "id"
               :addresses/public-ip "ip"}]
             resp)))))

(def fixtures [{:instances/id "foo"}
               {:instances/id "bar" :instances/public-ip "bar"}
               {:instances/id "baz" :instances/public-ip "baz"}
               {:addresses/allocation-id "A" :addresses/public-ip "bar"}
               {:addresses/allocation-id "B" :addresses/public-ip "B"}])

(deftest assign-static-ips!
  (testing "invokation"
    (let [calls (atom [])
          client (create-fake-client calls)
          db (create-db fixtures)]
      (core/assign-static-ips! client db)
      (is (= [{:op :AssociateAddress
               :request {:InstanceId "baz"
                         :AllocationId "B"}}]
             @calls)))))

(deftest queries
  (let [db (create-db fixtures)]
    (testing "find-instances-without-static-ip"
      (is (= #{"foo" "baz"}
             (set (core/find-instances-without-static-ip db)))))

    (testing "find-available-ips"
      (is (= #{"B"}
             (set (core/find-available-ips db)))))))
