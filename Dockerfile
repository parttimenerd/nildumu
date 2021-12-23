# roughly based on https://github.com/diffblue/cbmc/Dockerfile

FROM ubuntu:20.04 as builder

LABEL maintainer="Johannes Bechberger"
LABEL version="1.0"
LABEL Description="Nildumu evaluation setup"

ENV DEBIAN_FRONTEND noninteractive
ENV DEBCONF_NONINTERACTIVE_SEEN true
# Timezone data is needed during the installation of dependencies,
# since cmake depends on the tzdata package. In an interactive terminal,
# the user selects the timezone at installation time. Since this needs
# to be a non-interactive terminal, we need to setup some sort of default.
# The UTC one seemed the most suitable one.
RUN echo 'tzdata tzdata/Areas select Etc' | debconf-set-selections; \
    echo 'tzdata tzdata/Zones/Etc select UTC' | debconf-set-selections; \
    apt-get update && apt-get upgrade -y && apt-get install --no-install-recommends -y ca-certificates \
           bison ccache cmake curl flex g++ g++-multilib gcc gcc-multilib git jq \
           libboost-program-options-dev libc6-dev libgmp-dev libxml2-utils make ninja-build patch unzip wget zlib1g-dev \
            python3 python3-pip ant maven nano vim emacs; \
    update-ca-certificates -f;

# Setup JAVA_HOME -- useful for docker commandline
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64/
RUN export JAVA_HOME

COPY . /nildumu
WORKDIR /nildumu
WORKDIR /nildumu/eval-programs/dsharpy

RUN ./update_eval

WORKDIR /nildumu

RUN ./download_solvers
RUN mvn compile -DskipTests=true > /dev/null
