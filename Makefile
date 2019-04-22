.PHONY: uberjar test clean

uberjar: src/eks_static_ip/core.clj
	lein uberjar

test:
	lein test

clean:
	-@rm -rf lambda*
