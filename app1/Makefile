.PHONY: dev build install clean

dev:
	npx shadow-cljs compile app1-esm && (npx shadow-cljs watch app1-esm &) && npx @rspack/cli serve

build:
	npx shadow-cljs compile app1-esm && npx @rspack/cli build

install:
	npm install

clean:
	rm -rf node_modules js-out dist .shadow-cljs .clj-kondo
