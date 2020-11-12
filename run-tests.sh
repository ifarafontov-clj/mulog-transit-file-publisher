#!/bin/bash
set -e
mkdir -p classes
clojure -e "(compile 'ifarafontov.NoopFlushOutputStream)" 
declare -a TESTS=("ifarafontov.transit-publisher-unit-test"
                  "ifarafontov.transit-publisher-no-rotate-integration-test"
                  "ifarafontov.transit-publisher-rotate-age-integration-test"
                  "ifarafontov.transit-publisher-rotate-size-integration-test")

for test in "${TESTS[@]}"
do
	clojure -A:test:runner -n "$test"
done