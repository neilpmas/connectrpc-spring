# Contributing

## Setup

```sh
git clone https://github.com/neilpmas/connectrpc-spring.git
cd connectrpc-spring
./mvnw verify
```

## Development

```sh
./mvnw test     # run tests
./mvnw verify   # full build, including checkstyle
```

## Submitting a PR

1. Fork the repo
2. Create a branch: `git checkout -b my-fix`
3. Make your changes — tests must pass and checkstyle must be clean
4. Open a pull request against `main`
