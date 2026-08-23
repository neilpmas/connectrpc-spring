# connectrpc-spring Docs

## README

The top level README and CONTRIBUTING guidelines documentation are generated from sources in this module on `mvn package` using [`asciidoctor-reducer`](https://github.com/asciidoctor/asciidoctor-reducer) and [`downdoc`](https://github.com/opendevise/downdoc).

## Antora Site

To build the Antora site locally run the following command from the project root directory:
```
./mvnw -pl connectrpc-spring-docs package
```
You can then view the output by opening `connectrpc-spring-docs/target/antora/site/index.html`.

## Javadoc

The site's aggregated Javadoc comes from the root `javadoc` profile (`./mvnw package -P javadoc -DskipTests`), which this module's Antora collector runs automatically when building the site itself.
