(defproject eks-static-ip "0.1.0"
  :description "Assign static external IPs to EKS nodes."
  :url "https://github.com/hden/eks-static-ip"
  :license {:name "Apache License"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[com.cognitect.aws/api "0.8.273"]
                 [com.cognitect.aws/endpoints "1.1.11.526"]
                 [com.cognitect.aws/ec2 "711.2.413.0"]
                 [com.amazonaws/aws-lambda-java-core "1.2.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [datascript "0.18.2"]]
  :aot :all)
