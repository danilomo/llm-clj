.PHONY: help lint format lint-clj-kondo check clean test

help:
	@echo "Available targets:"
	@echo "  test           - Run tests"
	@echo "  lint           - Run all linters (cljfmt check + kibit + clj-kondo)"
	@echo "  format         - Auto-format code with cljfmt"
	@echo "  lint-clj-kondo - Run clj-kondo linter only"
	@echo "  check          - Run lint without fixing"
	@echo "  clean          - Clean linting caches"

# Run tests
test:
	lein test

# Run all linters
lint: lint-clj-kondo
	lein cljfmt check
	lein kibit

# Auto-format code
format:
	lein cljfmt fix

# Run clj-kondo
lint-clj-kondo:
	clj-kondo --lint src:test:examples --config .clj-kondo/config.edn

# Check only (no fixes)
check: lint

# Clean caches
clean:
	rm -rf .clj-kondo/.cache
	rm -rf .lsp/.cache
