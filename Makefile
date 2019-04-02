.PHONY: uberjar test clean

uberjar: src/eks_static_ip/core.clj
	lein uberjar

test:
	clojure -Atest

clean:
	-@rm -rf lambda*
