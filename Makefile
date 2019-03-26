.PHONY: test clean

lambda.zip: src/eks_static_ip/core.clj
	-@mkdir -p classes
	clojure -A:aot
	clojure -A:pack mach.pack.alpha.aws-lambda -e eks-static-ip.core lambda.zip

test:
	clojure -Atest

clean:
	-@rm -rf lambda*
