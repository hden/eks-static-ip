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
                                                                                     :PublicIpAddress "ip"
                                                                                     :NetworkInterfaces [{:NetworkInterfaceId "eni"}]}]}]}))]
      (is (= [{:instance/id "id" :instance/public-ip "ip" :instance/network-interface "eni"}]
             (core/describe-instances client "foo" ["bar"])))))

  (testing "without public-ip"
    (let [calls (atom [])
          client (create-fake-client calls (constantly {:Reservations [{:Instances [{:InstanceId "id"
                                                                                     :NetworkInterfaces [{:NetworkInterfaceId "eni"}]}]}]}))]
      (is (= [{:instance/id "id" :instance/network-interface "eni"}]
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

      (is (= [{:address/allocation-id "id"
               :address/public-ip "ip"}]
             resp)))))

(def fixtures [{:instance/id "id1" :instance/network-interface "eni1"}
               {:instance/id "id2" :instance/network-interface "eni2" :instance/public-ip "ip2"}
               {:instance/id "id3" :instance/network-interface "eni3" :instance/public-ip "ip3"}
               {:address/allocation-id "A" :address/public-ip "ip1"}
               {:address/allocation-id "B" :address/public-ip "ip2"}
               {:address/allocation-id "C" :address/public-ip "ip3"}])

(deftest assign-static-ips!
  (testing "invokation"
    (let [calls (atom [])
          client (create-fake-client calls)
          db (create-db fixtures)]
      (core/assign-static-ips! client db)
      (is (= [{:op :AssociateAddress
               :request {:AllowReassociation true
                         :NetworkInterfaceId "eni1"
                         :AllocationId "A"}}]
             @calls)))))

(deftest queries
  (let [db (create-db fixtures)]
    (testing "find-instances-without-static-ip"
      (is (= #{{:instance/id "id1" :instance/network-interface "eni1"}}
             (set (core/find-instances-without-static-ip db)))))

    (testing "find-available-ips"
      (is (= #{"A"}
             (set (core/find-available-ips db)))))))
