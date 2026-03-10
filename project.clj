(defproject llm-clj "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :jvm-opts ["-Djava.net.preferIPv4Stack=true"
             "-Djava.net.preferIPv4Addresses=true"]
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.async "1.6.681"]
                 [clj-http "3.13.0"]
                 [cheshire "5.13.0"]
                 [metosin/malli "0.17.0"]]
  :source-paths ["src" "examples"]
  :plugins [[lein-cljfmt "0.9.2"]
            [lein-kibit "0.1.8"]]
  :cljfmt {:load-config-file? true}
  :aliases {"lint" ["do" ["cljfmt" "check"] ["kibit"]]
            "lint-fix" ["cljfmt" "fix"]
            "format" ["cljfmt" "fix"]}
  :repl-options {:init-ns llm-clj.core})
