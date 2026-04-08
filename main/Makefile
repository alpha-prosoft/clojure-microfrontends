.PHONY: dev build install clean

dev:
	npx shadow-cljs compile main-esm && (npx shadow-cljs watch main-esm &) && npx @rspack/cli serve

build:
	npx shadow-cljs compile main-esm && npx @rspack/cli build

install:
	npm install

clean:
	rm -rf node_modules js-out dist .shadow-cljs .clj-kondo
