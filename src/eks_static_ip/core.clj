(ns eks-static-ip.core
  (:require [cognitect.aws.client.api :as aws]
            [datascript.core :as datascript]
            [taoensso.timbre :as timbre])
  (:import [com.amazonaws.services.lambda.runtime RequestHandler])
  (:gen-class
    :name eks.StaticIPHandler
    :implements [com.amazonaws.services.lambda.runtime.RequestHandler]))

(defprotocol Invokable
  (invoke [this req]))

(defrecord AWSClient [client]
  Invokable
  (invoke [_ req] (aws/invoke client req)))

(defn create-client [x]
  (AWSClient. (aws/client x)))

(defn describe-instances [client name values]
  (let [resp (invoke client {:op :DescribeInstances
                             :request {:MaxResults 1000
                                       :Filters [{:Name (format "tag:%s" name) :Values values}]}})]
    (mapcat (fn [reservation]
              (map (fn [{:keys [InstanceId PublicIpAddress]}]
                     (if PublicIpAddress
                       {:instances/id InstanceId :instances/public-ip PublicIpAddress}
                       {:instances/id InstanceId}))
                   (get reservation :Instances [])))
            (get resp :Reservations []))))

(defn describe-addresses [client name values]
  (let [resp (invoke client {:op :DescribeAddresses
                             :request {:MaxResults 1000
                                       :Filters [{:Name (format "tag:%s" name) :Values values}]}})]
    (map (fn [{:keys [PublicIp AllocationId]}]
           {:addresses/public-ip PublicIp
            :addresses/allocation-id AllocationId})
         (get resp :Addresses []))))

(defn find-instances-without-static-ip [db]
  (map first (datascript/q '[:find ?id
                             :where [?instance :instances/id ?id]
                                    (not-join [?instance]
                                      [?instance :instances/public-ip ?ip]
                                      [_ :addresses/public-ip ?ip])]
                           db)))

(defn find-available-ips [db]
  (map first (datascript/q '[:find ?id
                             :where [?address :addresses/allocation-id ?id]
                                    [?address :addresses/public-ip ?ip]
                                    (not [_ :instances/public-ip ?ip])]
                           db)))

(defn assign-static-ips! [client db]
  (let [instances (timbre/spy (into [] (find-instances-without-static-ip db)))
        ips (timbre/spy (into [] (find-available-ips db)))]
    (doseq [[instance-id allocation-id] (map vector instances ips)]
      (timbre/info (format "Assigning allocation %s to instance %s." allocation-id instance-id))
      (invoke client {:op :AssociateAddress
                      :request {:InstanceId instance-id
                                :AllocationId allocation-id}}))))

(defn env
  ([x] (System/getenv x))
  ([x y] (or (env x) y)))

(def instance-tag-key (env "INETANCE_TAG_KEY" "EKS-NODE-POOL"))
(def instance-tag-value (env "INETANCE_TAG_VALUE"))
(def eip-tag-key (env "EIP_TAG_KEY" "EKS-IP-POOL"))
(def eip-tag-value (env "EIP_TAG_VALUE"))

(defn -handleRequest [_ input context]
  (timbre/debug (format "instance-tag-key %s" instance-tag-key))
  (timbre/debug (format "instance-tag-value %s" instance-tag-value))
  (timbre/debug (format "eip-tag-key %s" eip-tag-key))
  (timbre/debug (format "eip-tag-value %s" eip-tag-value))
  (let [client (create-client {:api :ec2})
        conn (datascript/create-conn)]
    (datascript/transact! conn (timbre/spy (into [] (describe-instances client instance-tag-key [instance-tag-value]))))
    (datascript/transact! conn (timbre/spy (into [] (describe-addresses client eip-tag-key [eip-tag-value]))))
    (assign-static-ips! client @conn)
    ""))
