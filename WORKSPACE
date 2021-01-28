workspace(name = "mytest")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "io_bazel_rules_docker",
    sha256 = "1698624e878b0607052ae6131aa216d45ebb63871ec497f26c67455b34119c80",
    strip_prefix = "rules_docker-0.15.0",
    urls = ["https://github.com/bazelbuild/rules_docker/releases/download/v0.15.0/rules_docker-v0.15.0.tar.gz"],
)

load(
    "@io_bazel_rules_docker//repositories:repositories.bzl",
    container_repositories = "repositories",
)
container_repositories()

load("@io_bazel_rules_docker//repositories:deps.bzl", container_deps = "deps")

container_deps()

load(
    "@io_bazel_rules_docker//container:container.bzl",
    "container_pull",
)

# Docker Hub	index.docker.io
# Google Container Registry	gcr.io
# Gitlab Container Registry	registry.gitlab.com
# Github Packages1	docker.pkg.github.com


# BASE-IMAGE:
container_pull(
    name = "nav_java_12",
    registry = "index.docker.io",
    repository = "navikt/java",
    tag = "12",
    digest = "sha256:5030f54c1f029b8f5f1e523266e835abc8620715ddff51252c760022fa85ae5c"
    # TODO: ev. "sign"-script bør sjekke om dette er nyeste versjon og gi beskjed hvis ikke:
    # digest ovenfor er pinned av denne i navikt/java:12 :
    # "RepoDigests": [
    #    "navikt/java@sha256:5030f54c1f029b8f5f1e523266e835abc8620715ddff51252c760022fa85ae5c"
    # ],
)

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
http_archive(
    name = "rules_pkg",
    url = "https://github.com/bazelbuild/rules_pkg/releases/download/0.3.0/rules_pkg-0.3.0.tar.gz",
    sha256 = "6b5969a7acd7b60c02f816773b06fcf32fbe8ba0c7919ccdc2df4f8fb923804a",
)
load("@rules_pkg//:deps.bzl", "rules_pkg_dependencies")
rules_pkg_dependencies()